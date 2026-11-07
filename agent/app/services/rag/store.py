"""知识库存储与检索：落库 + 混合打分。

一次检索打两路分，再融合：

    score = 0.65 × 向量余弦 + 0.35 × BM25 + 场景加成

为什么不全用向量：纯向量对「报名需要收几个字段」这种精确词面问题不敏感，
BM25 恰好补这一路；反过来纯 BM25 不懂近义（「纳新」≈「招新」），向量补它。
融合权重 0.65/0.35 是在这套语料上试出来的，改语料要重新看一次效果。

向量存 JSON 文本、在 Python 里算余弦：语料几十块，全表扫一遍是微秒级，
为这点数据引入向量数据库只会多一个挂掉的服务。
"""

from __future__ import annotations

import hashlib
import json
import math
import threading
from collections import Counter
from dataclasses import dataclass, field
from typing import Any

from app.core.config import get_settings
from app.db.base import get_conn
from app.services.rag.corpus import Chunk, load_chunks
from app.services.rag.embedding import (
    EMBED_VERSION,
    EmbeddingError,
    cosine,
    embed_texts,
    hash_embed,
    tokenize,
)

VEC_WEIGHT = 0.65
BM25_WEIGHT = 0.35
BM25_K1 = 1.2
BM25_B = 0.75
BM25_SATURATION = 10.0   # BM25 饱和常数：把无上界的原始分压到 0~1
SCENE_BOOST = 0.10
COMMON_BOOST = 0.02

# 检索侧停用词：在这个语料里满地跑、或者压根不带主题信息的词。
#
# 为什么必须有一层：语料从 34 块涨到 76 块之后，「完全无关的内容」这种句子也能
# 撞上「内容」「完全」「无关」三个词，凑出 0.26 分挤进结果，把噪音喂给模型。
# 语料越大这个坑越深——任何一句中文都能和几块资料有词面重叠。
#
# 这不是通用的中文停用词表，是**针对本语料**挑的：加词之前先看一眼
# tests/test_rag.py 里那几条召回断言会不会被误伤。
_QUERY_STOPWORDS: frozenset[str] = frozenset(
    {
        # 虚词 / 疑问 / 客套
        "什么", "怎么", "如何", "多少", "哪些", "哪个", "一下", "一个",
        "我们", "你们", "可以", "需要", "应该", "能不", "帮我", "请问",
        "这个", "那个", "还有", "以及",
        # 泛义词：任何活动页都会出现，不指向任何主题
        "内容", "东西", "问题", "情况", "时候", "时间", "部分", "方面",
        # 「完全无关」这类元描述
        "完全", "无关", "有关", "相关",
    }
)


@dataclass
class SearchHit:
    chunk: Chunk
    score: float
    vec_score: float = 0.0
    bm25_score: float = 0.0

    def as_dict(self) -> dict[str, Any]:
        return {
            "docId": self.chunk.doc_id,
            "title": self.chunk.title,
            "heading": self.chunk.heading,
            "scene": self.chunk.scene,
            "kind": self.chunk.kind,
            "score": round(self.score, 4),
            "vecScore": round(self.vec_score, 4),
            "bm25Score": round(self.bm25_score, 4),
            "content": self.chunk.content,
        }


@dataclass
class _Index:
    rows: list[dict[str, Any]] = field(default_factory=list)
    vectors: list[list[float]] = field(default_factory=list)
    token_counts: list[Counter] = field(default_factory=list)
    lengths: list[int] = field(default_factory=list)
    df: Counter = field(default_factory=Counter)
    avgdl: float = 1.0
    stamp: tuple[int, int] = (0, 0)

    @property
    def size(self) -> int:
        return len(self.rows)


_lock = threading.Lock()
_index: _Index | None = None


def _chash(chunk: Chunk) -> str:
    """块的指纹。带上算法版本，换分词/换维度后旧块会被当成"已不存在"而重建。"""
    raw = f"{EMBED_VERSION}|{chunk.doc_id}|{chunk.index}|{chunk.heading}|{chunk.content}"
    return hashlib.md5(raw.encode("utf-8")).hexdigest()


def _stamp() -> tuple[int, int]:
    with get_conn() as conn:
        row = conn.fetchone("SELECT COUNT(*) AS c, COALESCE(MAX(id), 0) AS m FROM kb_chunks")
    if not row:
        return (0, 0)
    return (int(row.get("c") or 0), int(row.get("m") or 0))


def invalidate() -> None:
    global _index
    with _lock:
        _index = None


# ---------------------------------------------------------------------------
# 建库
# ---------------------------------------------------------------------------
def rebuild(force: bool = False) -> dict[str, Any]:
    """重建索引。幂等：同样的语料跑一百次，块数不会变。"""
    chunks = load_chunks()
    if not chunks:
        return {"docs": 0, "chunks": 0, "inserted": 0, "deleted": 0, "dim": 0, "skipped": "语料为空"}

    vectors = embed_texts([f"{c.title}\n{c.heading}\n{c.content}" for c in chunks])
    if len(vectors) != len(chunks):
        raise EmbeddingError("向量数量与语料块数量不一致，怀疑 embedding 返回有问题")

    dim = len(vectors[0]) if vectors else 0
    hashes = [_chash(c) for c in chunks]

    inserted = 0
    with get_conn() as conn:
        backend = get_settings().db_backend
        insert_sql = (
            "INSERT IGNORE INTO kb_chunks "
            "(chash, doc_id, title, heading, scene, kind, content, vector, dim) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
            if backend == "mysql"
            else "INSERT OR IGNORE INTO kb_chunks "
            "(chash, doc_id, title, heading, scene, kind, content, vector, dim) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )
        for chunk, vector, chash in zip(chunks, vectors, hashes):
            inserted += conn.execute(
                insert_sql,
                (
                    chash,
                    chunk.doc_id,
                    chunk.title,
                    chunk.heading,
                    chunk.scene,
                    chunk.kind,
                    chunk.content,
                    json.dumps(vector, separators=(",", ":")),
                    dim,
                ),
            )

        # 删掉语料里已经不存在的块（改了 md 之后旧块不能留在库里）
        if hashes:
            placeholders = ",".join("?" for _ in hashes)
            deleted = conn.execute(
                f"DELETE FROM kb_chunks WHERE chash NOT IN ({placeholders})", tuple(hashes)
            )
        else:
            deleted = conn.execute("DELETE FROM kb_chunks")

        if force:
            pass  # force 只在语义上表示「忽略缓存」，落库动作本身已经幂等

    invalidate()
    docs = {c.doc_id for c in chunks}
    return {
        "docs": len(docs),
        "chunks": len(chunks),
        "inserted": inserted,
        "deleted": max(0, deleted),
        "dim": dim,
        "backend": get_settings().db_backend,
    }


# ---------------------------------------------------------------------------
# 载入内存索引
# ---------------------------------------------------------------------------
def _load_index() -> _Index:
    global _index
    stamp = _stamp()

    with _lock:
        if _index is not None and _index.stamp == stamp:
            return _index

    with get_conn() as conn:
        rows = conn.fetchall(
            "SELECT doc_id, title, heading, scene, kind, content, vector, dim "
            "FROM kb_chunks ORDER BY id"
        )

    index = _Index(stamp=stamp)
    df: Counter = Counter()
    for row in rows:
        try:
            vector = json.loads(row["vector"])
        except (TypeError, ValueError):
            continue
        if not isinstance(vector, list) or not vector:
            continue
        tokens = Counter(tokenize(f"{row['title']} {row['content']}"))
        index.rows.append(row)
        index.vectors.append([float(v) for v in vector])
        index.token_counts.append(tokens)
        index.lengths.append(sum(tokens.values()) or 1)
        df.update(tokens.keys())

    index.df = df
    index.avgdl = (sum(index.lengths) / len(index.lengths)) if index.lengths else 1.0

    with _lock:
        _index = index
    return index


def _bm25(index: _Index, q_tokens: list[str], i: int) -> float:
    if not q_tokens or not index.size:
        return 0.0
    n = index.size
    tf = index.token_counts[i]
    dl = index.lengths[i]
    score = 0.0
    for token in set(q_tokens):
        freq = tf.get(token, 0)
        if not freq:
            continue
        dfi = index.df.get(token, 0)
        idf = math.log(1 + (n - dfi + 0.5) / (dfi + 0.5))
        denom = freq + BM25_K1 * (1 - BM25_B + BM25_B * dl / index.avgdl)
        score += idf * (freq * (BM25_K1 + 1)) / (denom or 1.0)
    return score


# ---------------------------------------------------------------------------
# 检索
# ---------------------------------------------------------------------------
def _strip_query_stopwords(text: str) -> str:
    """在字符串层面把停用词替换成空格。长的先剥，避免「相关内容」只去掉一半。"""
    cleaned = text
    for word in sorted(_QUERY_STOPWORDS, key=len, reverse=True):
        if word in cleaned:
            cleaned = cleaned.replace(word, " ")
    return cleaned


def search(
    query: str,
    *,
    k: int | None = None,
    scene: str = "",
    min_score: float | None = None,
) -> list[SearchHit]:
    """混合检索。任何一步出问题都退回纯词面匹配，绝不抛异常给上层。"""
    settings = get_settings()
    top_k = k or settings.rag_top_k
    threshold = settings.rag_min_score if min_score is None else min_score

    query = (query or "").strip()
    if not query:
        return []

    try:
        index = _load_index()
    except Exception:  # noqa: BLE001 - 库还没建好时不该把对话打挂
        return []
    if not index.size:
        return []

    # 先**在原文层面**剥掉停用词，再分词。
    # 为什么不在 token 层面过滤：tokenize 会同时产出双字和三字窗口，
    # 「无关」被过滤了，它的变体「无关的」「全无关」「关的」还会漏出来。
    # 在字符串上剥一次，所有变体一起消失——这是唯一不漏的做法。
    query_for_match = _strip_query_stopwords(query)
    q_tokens = [token for token in tokenize(query_for_match) if token not in _QUERY_STOPWORDS]
    if not q_tokens:
        # 整句话全是虚词和泛义词：没有任何能对上语料的东西。
        # 这时候硬凑资料比不检索更糟——模型会拿一段无关规范当依据去写。
        return []
    try:
        if settings.embedding_backend == "api":
            # 走真 embedding 时喂原句（模型需要自然语言），停用词对它无害
            q_vec = embed_texts([query])[0]
        else:
            # 本地哈希向量本质就是词面统计，喂剥过的文本，
            # 别让停用词把余弦相似度也一起抬起来
            q_vec = hash_embed(query_for_match)
    except Exception:  # noqa: BLE001 - 向量算不出来时只走 BM25
        q_vec = []

    raw: list[SearchHit] = []
    for i, row in enumerate(index.rows):
        vec_score = cosine(q_vec, index.vectors[i]) if q_vec else 0.0
        bm25 = _bm25(index, q_tokens, i)
        raw.append(
            SearchHit(
                chunk=Chunk(
                    doc_id=row["doc_id"],
                    title=row["title"],
                    scene=row["scene"],
                    kind=row["kind"],
                    heading=row.get("heading") or "",
                    content=row["content"],
                    index=i,
                ),
                score=vec_score * VEC_WEIGHT + bm25 * BM25_WEIGHT,
                vec_score=vec_score,
                bm25_score=bm25,
            )
        )

    # 先把两路分融合成「内容分」，**用内容分做完过滤再加场景加成**。
    # 顺序很关键：如果先加成就再过滤，一句无关的话也能靠「通用规范 +0.02」混过及格线。
    # 场景加成只负责把已经及格的资料往前排，不许把不及格拉上来。
    for hit in raw:
        saturated = hit.bm25_score / (hit.bm25_score + BM25_SATURATION) if hit.bm25_score > 0 else 0.0
        hit.score = hit.vec_score * VEC_WEIGHT + saturated * BM25_WEIGHT

    qualified = [hit for hit in raw if hit.score >= threshold]
    for hit in qualified:
        if scene and hit.chunk.scene == scene:
            hit.score += SCENE_BOOST
        elif hit.chunk.scene == "common":
            hit.score += COMMON_BOOST

    qualified.sort(key=lambda h: h.score, reverse=True)

    picked: list[SearchHit] = []
    seen: set[tuple[str, str]] = set()
    for hit in qualified:
        key = (hit.chunk.doc_id, hit.chunk.content[:40])
        if key in seen:
            continue
        seen.add(key)
        picked.append(hit)
        if len(picked) >= top_k:
            break
    return picked


def stats() -> dict[str, Any]:
    """给 /kb/stats 和自检用：库里到底有什么。"""
    with get_conn() as conn:
        total = conn.fetchone(
            "SELECT COUNT(*) AS c, COUNT(DISTINCT doc_id) AS d FROM kb_chunks"
        ) or {}
        rows = conn.fetchall("SELECT scene, COUNT(*) AS c FROM kb_chunks GROUP BY scene")
        dims = conn.fetchall("SELECT DISTINCT dim FROM kb_chunks")
    return {
        "chunks": int(total.get("c") or 0),
        "docs": int(total.get("d") or 0),
        "scenes": {row["scene"] or "common": int(row["c"]) for row in rows},
        "dim": int(dims[0]["dim"]) if dims else 0,
        "backend": get_settings().db_backend,
        "embedding_backend": get_settings().embedding_backend,
    }

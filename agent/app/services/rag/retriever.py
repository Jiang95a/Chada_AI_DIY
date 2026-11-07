"""检索层：把知识库变成「能塞进提示词的一段话」。

单独一层的理由：store 只管命中什么，这一层管**怎么给模型看**。
包括引用编号、长度上限、以及那句最关键的约束——
「资料里没有的，一律写待补充」。

场景识别也放这里：先判断用户这句话属于招新/报名/投票/打卡哪一类，
再用场景给检索结果加权。这一步能把召回准确率提上去，成本只有几行正则。
"""

from __future__ import annotations

import re
from typing import Any

from app.core.config import get_settings
from app.services.rag import store
from app.services.rag.store import SearchHit

# 场景词表。**顺序有意义**：先命中的赢。
# 心屿（心理社团）的场景必须排在通用活动场景前面——
# 「心理打卡」应该命中 wellbeing，而不是 checkin。
# 注意：前端 web/src/services/planner.ts 也有一张 SCENE_RULES，但那套是给
# 「本地兜底生成 DocPlan」用的模板分类（心理社团招新/知识问答/打卡/心晴活动），
# 和这里的「语料场景」不是同一个概念，不要硬对齐。
SCENE_RULES: list[tuple[str, re.Pattern[str]]] = [
    ("care", re.compile(r"危机|转介|自伤|自杀|轻生|求助入口|求助信息|热线|紧急")),
    ("group", re.compile(r"团辅|团体辅导|团体活动|工作坊|破冰|围坐|带领者|入组|小组")),
    ("treehole", re.compile(r"树洞|匿名留言|倾诉|心事|悄悄话|留言墙|匿名墙")),
    ("wellbeing", re.compile(r"心理|心晴|情绪|正念|减压|舒压|自我关怀|关怀|内耗|焦虑|emo")),
    ("recruit", re.compile(r"招新|纳新|招募|新成员|加入我们|社团|部门面试")),
    ("vote", re.compile(r"投票|票选|评选|人气|作品|角逐")),
    ("checkin", re.compile(r"打卡|坚持|每日|习惯|早起|自律|连签")),
    ("signup", re.compile(r"报名|讲座|分享会|比赛|夜跑|演出|沙龙|训练营|活动|门票")),
]

SCENE_LABEL = {
    "care": "危机与转介",
    "group": "团体辅导",
    "treehole": "匿名树洞",
    "wellbeing": "心理活动",
    "recruit": "社团招新",
    "signup": "活动报名",
    "vote": "投票评选",
    "checkin": "每日打卡",
    "common": "通用规范",
}


def detect_scene(text: str) -> str:
    for scene, pattern in SCENE_RULES:
        if pattern.search(text or ""):
            return scene
    return ""


def search(query: str, *, k: int | None = None, scene: str | None = None) -> list[SearchHit]:
    """带场景自动识别的检索入口。"""
    settings = get_settings()
    if not settings.rag_enabled:
        return []
    target_scene = scene if scene is not None else detect_scene(query)
    return store.search(query, k=k, scene=target_scene)


def _format_hit(hit: SearchHit, index: int) -> str:
    label = f"{hit.chunk.title} · {hit.chunk.heading}" if hit.chunk.heading else hit.chunk.title
    return f"[资料{index}] 《{label}》（相关度 {hit.score:.2f}）\n{hit.chunk.content}"


def build_context(query: str, *, k: int | None = None, scene: str | None = None) -> tuple[str, list[SearchHit]]:
    """把命中的资料拼成一段可注入的文本，超长按上限截断。"""
    settings = get_settings()
    hits = search(query, k=k, scene=scene)
    if not hits:
        return "", []

    limit = max(200, settings.rag_context_chars)
    pieces: list[str] = []
    used: list[SearchHit] = []
    total = 0
    for i, hit in enumerate(hits, start=1):
        block = _format_hit(hit, i)
        if total + len(block) > limit and pieces:
            break
        pieces.append(block)
        used.append(hit)
        total += len(block)
    return "\n\n".join(pieces), used


CONTEXT_HEADER = """# 参考资料（校园活动知识库检索结果）

下面是从本地知识库里检索到的资料。用它来提高准确度，但守住三条：
1. 只把资料当**参考**，不要把资料原文整段抄给用户。
2. 资料里没有的具体事实（时间、地点、名额、联系人），一律写「待补充」，绝不推测。
3. 引用资料时说明来自哪一份，例如「按《报名表单设计规范》……」。
"""


def knowledge_message(query: str, *, scene: str | None = None) -> tuple[str, list[SearchHit]]:
    """给 agent_service 用：返回一条可以直接追加到 messages 里的 system 文本。"""
    context, hits = build_context(query, scene=scene)
    if not context:
        return "", []
    return f"{CONTEXT_HEADER}\n{context}", hits


def search_as_text(query: str, *, k: int | None = None, scene: str | None = None) -> str:
    """给工具 search_knowledge 用：模型要的是能直接读的字符串。"""
    hits = search(query, k=k, scene=scene)
    if not hits:
        return (
            f"知识库里没有和「{query}」相关的资料。"
            "不要编造内容，改用通用常识回答，并说明这是常识而非资料。"
        )
    lines = [f"命中 {len(hits)} 条资料："]
    for hit in hits:
        label = f"{hit.chunk.title} · {hit.chunk.heading}" if hit.chunk.heading else hit.chunk.title
        lines.append(f"\n[{label}] 相关度 {hit.score:.2f}\n{hit.chunk.content}")
    return "\n".join(lines)


def stats() -> dict[str, Any]:
    data = store.stats()
    data["enabled"] = get_settings().rag_enabled
    return data

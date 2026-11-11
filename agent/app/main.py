r"""FastAPI 入口。

启动：
    .\start.ps1
或者：
    .\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --port 8000

然后打开 http://127.0.0.1:8000/docs

接口地图（答辩时按这个顺序讲）：
    POST /chat                     —— 主对话（带 RAG 检索 + 护栏 + 审计）
    POST /chat/stream              —— 同上，SSE 流式
    POST /plan                     —— 直接产出一份完整的 PageDocument（不调模型）
    POST /poster/extract           —— 多模态：海报 -> 结构化活动信息
    POST /kb/search                —— RAG：检索命中了哪几块资料
    GET  /kb/stats                 —— 知识库里有几个文档、几块、几个场景
    POST /kb/rebuild               —— 重建索引
    GET  /mcp/manifest             —— MCP 能力清单（工具 + inputSchema）
    POST /mcp/rpc                  —— JSON-RPC 入口，与 stdio 共用同一实现
    GET  /audit/logs               —— AI 调用审计
    GET  /health                   —— 探活 + 能力开关
"""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app import __version__
from app.core.config import get_settings
from app.db.base import init_db
from app.routers import audit as audit_router
from app.routers import chat as chat_router
from app.routers import knowledge as kb_router
from app.routers import mcp as mcp_router
from app.routers import plan as plan_router
from app.routers import poster as poster_router
from app.routers import vision as vision_router
from app.schemas.chat_schema import HealthResponse
from app.services.rag import store as kb_store
from app.services.tools import ALL_TOOLS

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)-7s %(name)s | %(message)s",
)
logger = logging.getLogger("app")


def _bootstrap_kb() -> None:
    """启动时把知识库建起来。

    没建过就建（首次启动要几毫秒，纯本地哈希向量）；
    建过就跳过——别每次重启都重算一遍。
    建失败只警告，不拦启动：知识库挂了，对话还能靠模型自己的常识跑。
    """
    settings = get_settings()
    if not settings.rag_enabled:
        logger.info("RAG 已关闭（RAG_ENABLED=false）")
        return
    try:
        info = kb_store.stats()
        if info["chunks"] == 0:
            result = kb_store.rebuild()
            logger.info("知识库已初始化：%s", result)
        else:
            logger.info(
                "知识库就绪：%s 个文档 / %s 块 / 维度 %s",
                info["docs"],
                info["chunks"],
                info["dim"],
            )
    except Exception as exc:  # noqa: BLE001 - 建库失败不能让服务起不来
        logger.warning("知识库初始化失败（不影响主流程）：%s", exc)


@asynccontextmanager
async def lifespan(_: FastAPI):
    # 启动时做：建表、建知识库、打印配置摘要（不含 key）、提示缺什么
    init_db()
    settings = get_settings()
    logger.info("配置就绪：provider=%s model=%s", settings.llm_provider, settings.model_name)
    if not settings.has_key:
        # 这句话必须点明「.env 找没找到」。以前只报「未配置 API KEY」，
        # 而 provider 用的是代码里的默认值、照样显示「就绪」，
        # 于是有人明明填了密钥却看到这两行同时成立，以为是自己填错了。
        env_path = settings.env_file_path
        if env_path.is_file():
            logger.warning(
                "未配置 LLM_API_KEY —— /chat 会返回 503，其余接口正常。"
                "已读到配置文件 %s，但其中的 LLM_API_KEY 为空：请把密钥填在这个文件里再重启",
                env_path,
            )
        else:
            logger.warning(
                "未配置 LLM_API_KEY —— /chat 会返回 503，其余接口正常。"
                "没有找到配置文件 %s：请 `copy .env.example .env` 后填入密钥。"
                "（代码已按 agent 目录定位该文件，与你在哪个目录启动无关）",
                env_path,
            )

    vision = settings.vision_summary()
    if vision["ready"]:
        logger.info("视觉模型就绪：%s @ %s", vision["model"], vision["base_url"])
    else:
        logger.warning("未配置视觉模型 —— /poster/extract 会返回 503，前端应回退到手动填写")

    if settings.cors_is_wildcard:
        logger.warning(
            "CORS_ORIGINS=* ：已自动关闭 allow_credentials（浏览器不接受通配符带凭证）。"
            "上线请改成具体域名。"
        )

    _bootstrap_kb()
    logger.info("工具已注册（%s 个）：%s", len(ALL_TOOLS), ", ".join(t.name for t in ALL_TOOLS))
    yield
    logger.info("服务已停止")


settings = get_settings()
app = FastAPI(
    title="校园活动智能体服务",
    version=__version__,
    description=(
        "对话 + 工具调用 + 会话记忆 + 知识库检索（RAG）+ 海报理解（多模态）+ MCP。"
        "给前端和 backend 对接用。"
    ),
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_list,
    # 通配符 + credentials 是浏览器会直接拒绝的组合，配置层已经处理过
    allow_credentials=settings.allow_credentials,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(chat_router.router)
app.include_router(plan_router.router)
app.include_router(vision_router.router)
app.include_router(poster_router.router)
app.include_router(kb_router.router)
app.include_router(mcp_router.router)
app.include_router(audit_router.router)


@app.get("/health", response_model=HealthResponse, tags=["meta"], summary="探活")
def health() -> HealthResponse:
    current = get_settings()
    return HealthResponse(
        ok=True,
        app=current.app_name,
        llm=current.safe_summary(),
        tools=[tool.name for tool in ALL_TOOLS],
    )


@app.get("/", tags=["meta"], summary="服务信息")
def index() -> dict:
    return {
        "service": settings.app_name,
        "version": __version__,
        "docs": "/docs",
        "health": "/health",
        "endpoints": [
            "POST /chat",
            "POST /chat/stream",
            "GET /chat/{thread_id}/history",
            "POST /plan",
            "POST /poster/extract",
            "POST /kb/search",
            "GET /kb/stats",
            "POST /kb/rebuild",
            "GET /mcp/manifest",
            "POST /mcp/rpc",
            "GET /audit/logs",
        ],
    }

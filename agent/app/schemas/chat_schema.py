"""接口的数据契约。

这一层只做一件事：定义「进什么、出什么」，并且让 FastAPI 自动校验。
业务逻辑不要写在这里。
"""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field

Role = Literal["system", "user", "assistant", "tool"]


class ChatMessage(BaseModel):
    role: Role
    content: str = ""


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000, description="用户这句话")
    thread_id: str | None = Field(default=None, description="会话 id，不传就开新会话")
    system_extra: str | None = Field(
        default=None, max_length=1000, description="本次附加要求，不改变人设"
    )
    temperature: float | None = Field(default=None, ge=0.0, le=2.0)


class ToolCallTrace(BaseModel):
    """一次工具调用记录。前端可以拿它显示「正在查…」。"""

    name: str
    args: dict[str, Any] = Field(default_factory=dict)
    result: str = ""


class ChatResponse(BaseModel):
    thread_id: str
    reply: str
    tool_calls: list[ToolCallTrace] = Field(default_factory=list)
    latency_ms: int = 0
    """命中的护栏规则 + 检索命中数，前端可以显示「本次带了几条资料/拦了什么」。"""
    flags: list[str] = Field(default_factory=list)
    rag_hits: int = 0
    document: dict[str, Any] | None = Field(
        default=None,
        description=(
            "本轮回复若是一份页面方案，这里是补齐后的完整 PageDocument"
            "（含 id / slug / theme / meta / page.id / block.id / style），可直接落库与 apply；"
            "普通问答时为 null。"
        ),
    )


class HistoryResponse(BaseModel):
    thread_id: str
    messages: list[ChatMessage] = Field(default_factory=list)


class HealthResponse(BaseModel):
    ok: bool = True
    app: str = ""
    llm: dict[str, Any] = Field(default_factory=dict)
    tools: list[str] = Field(default_factory=list)

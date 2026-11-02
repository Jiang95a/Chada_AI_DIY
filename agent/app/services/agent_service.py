"""智能体的核心：LangGraph 图 + 对外函数。

图长这样（最小可用的 ReAct 循环）：

    START → agent ──(有 tool_calls)──► tools ──► agent ...
              └────(没有 tool_calls)────► END

设计上刻意做的事：
1. 不用 langgraph.prebuilt.ToolNode，自己写工具节点。
   因为你要能解释"工具到底是怎么被调用的"——答辩会问。
2. state 里的 messages 用 operator.add 累积，所以调用完要切片取出"本轮新增"的部分。
3. 工具调用有轮次上限，防止模型自己跟自己玩到天荒地老。
"""

from __future__ import annotations

import logging
import operator
import time
import uuid
from functools import lru_cache
from typing import Annotated, Any, Iterator, TypedDict

from langchain_core.messages import (
    AIMessage,
    AnyMessage,
    HumanMessage,
    SystemMessage,
    ToolMessage,
)
from langgraph.graph import END, START, StateGraph

from app.core.config import get_settings
from app.core.guard import sanitize_system_extra, screen_input, screen_output
from app.core.prompts import build_system_prompt
from app.schemas.chat_schema import ChatResponse, ToolCallTrace
from app.services import audit
from app.services.llm_client import build_llm
from app.services.memory.chat_history import get_history
from app.services.page_document import upgrade_reply
from app.services.rag import knowledge_message
from app.services.tools import ALL_TOOLS, TOOL_MAP

logger = logging.getLogger(__name__)

MAX_TOOL_ROUNDS = 4          # 最多来回几轮工具调用
RECURSION_LIMIT = 14         # LangGraph 的步数上限，超了会抛异常


class AgentState(TypedDict):
    messages: Annotated[list[AnyMessage], operator.add]


# ---------------------------------------------------------------------------
# 图
# ---------------------------------------------------------------------------
def build_agent_graph(llm: Any = None):
    """llm 可注入：测试时传假模型，不联网、不花钱。

    注意 bind_tools 的位置：不绑定工具，模型永远不会请求调用工具。
    """
    model = (llm if llm is not None else build_llm()).bind_tools(ALL_TOOLS)

    def call_model(state: AgentState) -> dict:
        response = model.invoke(state["messages"])
        return {"messages": [response]}

    def call_tools(state: AgentState) -> dict:
        last = state["messages"][-1]
        outputs: list[AnyMessage] = []
        for call in getattr(last, "tool_calls", None) or []:
            name = call.get("name", "")
            args = call.get("args") or {}
            tool = TOOL_MAP.get(name)
            if tool is None:
                content = f"未知工具：{name}"
                logger.warning("unknown tool: %s", name)
            else:
                try:
                    content = str(tool.invoke(args))
                except Exception as exc:  # noqa: BLE001
                    logger.exception("tool %s failed", name)
                    content = f"工具执行失败：{exc}"
            outputs.append(
                ToolMessage(content=content, tool_call_id=call.get("id", ""), name=name)
            )
        return {"messages": outputs}

    def route(state: AgentState) -> str:
        last = state["messages"][-1]
        if not getattr(last, "tool_calls", None):
            return END
        used_rounds = sum(1 for msg in state["messages"] if isinstance(msg, ToolMessage))
        if used_rounds >= MAX_TOOL_ROUNDS:
            logger.warning("tool rounds limit reached")
            return END
        return "tools"

    graph = StateGraph(AgentState)
    graph.add_node("agent", call_model)
    graph.add_node("tools", call_tools)
    graph.add_edge(START, "agent")
    graph.add_conditional_edges("agent", route, {"tools": "tools", END: END})
    graph.add_edge("tools", "agent")
    return graph.compile()


@lru_cache(maxsize=1)
def get_graph():
    """进程级单例。每次请求重建图没必要，编译是有开销的。"""
    return build_agent_graph()


# ---------------------------------------------------------------------------
# 内部工具函数
# ---------------------------------------------------------------------------
def new_thread_id() -> str:
    return uuid.uuid4().hex


def _history_to_messages(thread_id: str) -> list[AnyMessage]:
    limit = get_settings().history_limit
    out: list[AnyMessage] = []
    for item in get_history().load(thread_id, limit=limit):
        if item.role == "user":
            out.append(HumanMessage(content=item.content))
        elif item.role == "assistant":
            out.append(AIMessage(content=item.content))
    return out


def _extract_reply(messages: list[AnyMessage]) -> str:
    """从最后往后找第一条有正文的 AI 消息。

    为什么要找而不是直接取最后一条：
    最后一轮可能只返回了 tool_calls、正文为空；也可能因为轮次上限被强行结束。
    """
    for msg in reversed(messages):
        if isinstance(msg, AIMessage):
            content = msg.content
            if isinstance(content, str) and content.strip():
                return content.strip()
            if isinstance(content, list):  # 多模态块的情况
                texts = [str(b.get("text", "")) for b in content if isinstance(b, dict)]
                joined = "".join(texts).strip()
                if joined:
                    return joined
    return ""


def _collect_traces(messages: list[AnyMessage]) -> list[ToolCallTrace]:
    traces: list[ToolCallTrace] = []
    for msg in messages:
        if isinstance(msg, ToolMessage):
            traces.append(
                ToolCallTrace(
                    name=msg.name or "",
                    args={},
                    result=str(msg.content)[:300],
                )
            )
    return traces


def _make_graph(llm: Any, streaming: bool):
    if llm is not None:
        return build_agent_graph(llm)
    if streaming:
        return build_agent_graph(build_llm(streaming=True))
    return get_graph()


def _prepare_messages(
    message: str,
    system_extra: str | None,
    thread_id: str,
) -> tuple[list[AnyMessage], list[str], int]:
    """拼本轮要发给模型的完整消息列表，并返回（消息, 命中的护栏规则, 检索命中数）。

    顺序很重要：人设 -> 参考资料 -> 历史 -> 本轮问题。
    参考资料放在历史前面，是因为它描述的是"世界"，不是"对话"；
    放在历史后面会让模型误以为那些资料是用户刚说的话。
    """
    settings = get_settings()

    guard = sanitize_system_extra(system_extra)
    flags: list[str] = list(guard.flags)
    flags.extend(screen_input(message).flags)

    messages: list[AnyMessage] = [SystemMessage(content=build_system_prompt(guard.text))]

    hits = 0
    if settings.rag_enabled:
        try:
            kb_text, matched = knowledge_message(message)
        except Exception as exc:  # noqa: BLE001 - 检索挂了也不能让对话挂
            logger.warning("RAG 检索失败，本轮不带资料：%s", exc)
            kb_text, matched = "", []
        if kb_text:
            messages.append(SystemMessage(content=kb_text))
            hits = len(matched)

    messages.extend(_history_to_messages(thread_id))
    messages.append(HumanMessage(content=message))
    return messages, flags, hits


# ---------------------------------------------------------------------------
# 对外：一次性返回
# ---------------------------------------------------------------------------
def chat(
    message: str,
    thread_id: str | None = None,
    system_extra: str | None = None,
    temperature: float | None = None,
) -> ChatResponse:
    started = time.perf_counter()
    tid = thread_id or new_thread_id()
    history = get_history()
    settings = get_settings()

    llm = build_llm(temperature=temperature) if temperature is not None else None
    graph = _make_graph(llm, streaming=False)

    messages, flags, rag_hits = _prepare_messages(message, system_extra, tid)

    try:
        result = graph.invoke({"messages": messages}, config={"recursion_limit": RECURSION_LIMIT})
    except Exception as exc:
        audit.log_call(
            kind="chat",
            thread_id=tid,
            model=settings.model_name,
            latency_ms=int((time.perf_counter() - started) * 1000),
            ok=False,
            flags=flags,
            error=str(exc),
        )
        raise

    full: list[AnyMessage] = result["messages"]
    fresh = full[len(messages):]                 # 本轮新增的（工具消息 + AI 回复）
    reply = _extract_reply(fresh) or _extract_reply(full)
    if not reply:
        reply = "（这一轮没有拿到有效回复，可能是工具调用次数到上限了。把问题拆小一点再问一次。）"

    # 输出侧再过一遍：剥掉可执行标签、截断、检查有没有把系统提示词抄出来
    screened = screen_output(reply)
    if screened.text:
        reply = screened.text
    elif screened.flags:
        # 整段都被消毒掉了。此时**绝对不能**回退到 reply ——
        # 那等于把没消过毒的原文原样吐回去，护栏就白装了。
        reply = "（这段回复里有不允许的内容，已被拦截。换个说法再问一次。）"
    flags.extend(screened.flags)

    # 模型这一轮如果给的是一份页面方案（DocPlan），在这里就补成完整 PageDocument。
    # 后端的 /ai/projects/{id}/generate 会把 reply 原样解析后存进 ai_proposals；
    # 缺 id / slug / theme / meta 的话，用户点「采用」时会被 schema 校验打回 422。
    # 不是方案的回复（普通问答）原样返回，一个字都不动。
    reply, document_notes, document = upgrade_reply(reply)
    if document_notes:
        logger.info("本轮回复已补齐为完整 PageDocument（%d 处）：%s", len(document_notes), document_notes)

    history.append(tid, "user", message)
    history.append(tid, "assistant", reply)

    latency = int((time.perf_counter() - started) * 1000)
    audit.log_call(
        kind="chat",
        thread_id=tid,
        model=settings.model_name,
        latency_ms=latency,
        ok=True,
        flags=flags,
    )
    return ChatResponse(
        thread_id=tid,
        reply=reply,
        tool_calls=_collect_traces(fresh),
        latency_ms=latency,
        flags=flags,
        rag_hits=rag_hits,
        document=document,
    )


# ---------------------------------------------------------------------------
# 对外：流式
# ---------------------------------------------------------------------------
def stream_chat(
    message: str,
    thread_id: str | None = None,
    system_extra: str | None = None,
) -> Iterator[str]:
    """逐 token 产出文本。

    流式下拿不到完整的 ToolMessage 轨迹（都还在流里），所以只负责吐文本；
    历史在生成结束后一次性补写。
    """
    tid = thread_id or new_thread_id()
    graph = _make_graph(None, streaming=True)
    settings = get_settings()

    messages, flags, _rag_hits = _prepare_messages(message, system_extra, tid)

    collected: list[str] = []
    started = time.perf_counter()
    try:
        for chunk, _meta in graph.stream(
            {"messages": messages},
            config={"recursion_limit": RECURSION_LIMIT},
            stream_mode="messages",
        ):
            text = getattr(chunk, "content", "")
            if isinstance(text, str) and text:
                collected.append(text)
                yield text
            elif isinstance(text, list):
                for block in text:
                    if isinstance(block, dict) and block.get("type") == "text":
                        piece = str(block.get("text", ""))
                        if piece:
                            collected.append(piece)
                            yield piece
    finally:
        reply = "".join(collected).strip()
        if reply:
            get_history().append(tid, "user", message)
            get_history().append(tid, "assistant", reply)
            audit.log_call(
                kind="stream",
                thread_id=tid,
                model=settings.model_name,
                latency_ms=int((time.perf_counter() - started) * 1000),
                ok=True,
                flags=flags,
            )

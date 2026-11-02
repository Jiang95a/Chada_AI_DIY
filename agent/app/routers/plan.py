"""把「方案」直接变成一份完整 PageDocument 的接口。

**为什么单开一个端点，而不是只让 /chat 顺手升级。**

1. **它完全不调模型。** 给定场景和活动信息，直接用受控组件模板拼出文档。
   答辩现场没网、没 key、额度用光，这条链路照样能跑，评委能当场看到产物。
2. **它是确定性的。** 同样的入参永远得到同一份结构（只有随机 id 不同），
   不像模型那样每次都不一样——E2E 脚本需要这种东西。
3. **它是「智能体返回完整 PageDocument」这句话的机器可验证版本。**
   POST 一次，把 ``document`` 原样丢进 ``ai_proposals`` 就能 apply，
   不用等后端 ``DocumentRepair`` 再去补。

给一份现成的 ``plan`` 也行：这时只做补齐，不重新生成——前后端联调时用来验证
「模型吐出来的半成品，补完能不能过 schema」。
"""

from __future__ import annotations

import logging
from typing import Any

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.services.page_document import to_page_document
from app.services.tools.planner import build_plan

logger = logging.getLogger(__name__)
router = APIRouter(tags=["plan"])


class PlanRequest(BaseModel):
    """要么给 plan（只补齐），要么给场景 + 活动信息（按模板拼一份新的）。"""

    plan: dict[str, Any] | None = Field(
        default=None,
        description="现成的方案或半成品文档，只做补齐，不重新生成",
    )
    scene: str = Field(default="signup", description="recruit 招新 / signup 报名 / vote 投票 / checkin 打卡")
    title: str = Field(default="", max_length=160, description="活动名称，缺省时用「未命名活动」")
    org: str = Field(default="", description="主办方；用户没提供的就不要编")
    start_at: str = Field(default="", description="开始时间")
    place: str = Field(default="", description="地点")
    deadline: str = Field(default="", description="报名截止时间")
    capacity: int = Field(default=0, ge=0, description="名额，0 表示不限")
    contact: str = Field(default="", description="联系方式")
    slug: str | None = Field(default=None, description="访问路径，不给就由标题推导")
    meta: dict[str, Any] | None = Field(default=None, description="直接给 meta 时优先于上面的散字段")


class PlanResponse(BaseModel):
    ok: bool = True
    source: str = Field(description="template = 从场景模板拼的；plan = 补齐调用方给的方案")
    document: dict[str, Any] = Field(description="完整 PageDocument，能直接落 ai_proposals 并 apply")
    notes: list[str] = Field(default_factory=list, description="补齐过程中动了哪些字段")
    stats: dict[str, int] = Field(default_factory=dict, description="页数 / 组件数等速览")


def _merge_meta(req: PlanRequest) -> dict[str, Any]:
    """散字段 + meta 合并，meta 里显式给的值优先。"""
    merged: dict[str, Any] = {
        "title": req.title,
        "org": req.org,
        "startAt": req.start_at,
        "place": req.place,
        "deadline": req.deadline,
        "capacity": req.capacity,
        "contact": req.contact,
    }
    if isinstance(req.meta, dict):
        for key, value in req.meta.items():
            if value is not None:
                merged[key] = value
    return merged


@router.post("/plan", response_model=PlanResponse, summary="直接产出一份完整的 PageDocument")
def plan(req: PlanRequest) -> PlanResponse:
    try:
        if req.plan is not None:
            source = "plan"
            raw: Any = req.plan
        else:
            source = "template"
            raw = build_plan(req.scene, _merge_meta(req))

        result = to_page_document(raw, title=req.title or None, slug=req.slug)
    except Exception as exc:  # noqa: BLE001 - 补齐逻辑本身出错也要给个明确状态码
        logger.exception("plan build failed")
        raise HTTPException(
            status_code=500,
            detail={"code": "PLAN_BUILD_FAILED", "message": f"生成文档失败：{exc}"},
        ) from exc

    if result is None:
        raise HTTPException(
            status_code=422,
            detail={"code": "PLAN_EMPTY", "message": "方案里没有任何可用页面（pages 为空或全是空页）"},
        )

    if not result.ok:
        logger.warning("plan 自检未通过：%s", result.errors)
        raise HTTPException(
            status_code=422,
            detail={
                "code": "PLAN_INVALID",
                "message": "生成的文档不符合 PageDocument 契约",
                "errors": result.errors,
            },
        )

    pages = result.document["pages"]
    block_count = sum(len(page["blocks"]) for page in pages)
    return PlanResponse(
        ok=True,
        source=source,
        document=result.document,
        notes=result.notes,
        stats={"pages": len(pages), "blocks": block_count},
    )

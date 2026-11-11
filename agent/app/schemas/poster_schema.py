"""海报解析的数据契约。

字段设计有一条原则：**每一个抽出来的值都要能指回图上的原文**。
所以有 evidence 这个字典——评委问「你怎么证明这不是模型编的」，
答案是「每个字段都附了它在海报里的原文，前端可以让用户逐个核对」。

抽不出来就留空 + 写进 unclear，不许填一个"看起来合理"的值。
"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class PosterInfo(BaseModel):
    """一张活动海报里能抽出来的结构化信息。"""

    title: str = Field(default="", description="活动名称，海报上最显眼的那句")
    org: str = Field(default="", description="主办方全称，例如「光影摄影社」")
    startAt: str = Field(default="", description="开始时间，统一成 11月8日 周六 19:00 这种格式")
    place: str = Field(default="", description="地点，尽量到楼栋和房间号")
    deadline: str = Field(default="", description="报名截止时间，没有就留空")
    capacity: int | None = Field(default=None, description="名额上限，只在海报明确写了数字时才填")
    fee: str = Field(default="", description="费用，免费就写「免费」")
    contact: str = Field(default="", description="联系方式：手机号、QQ 群号或微信")
    intro: str = Field(default="", description="海报上的一句宣传语，可留空")
    signupFields: list[str] = Field(
        default_factory=list, description="海报上要求填写的信息，例如姓名、学号、意向部门"
    )
    evidence: dict[str, str] = Field(
        default_factory=dict, description="字段名 -> 它在海报上的原文，用于人工核对"
    )
    unclear: list[str] = Field(
        default_factory=list, description="看得到但读不清的字段，例如「地点字号太小」"
    )
    confidence: float = Field(default=0.0, ge=0.0, le=1.0, description="整体把握程度")


class PosterExtractResponse(BaseModel):
    """接口返回。source 永远如实标注：vision 表示真的调了视觉模型。"""

    ok: bool = True
    source: str = "vision"
    model: str = ""
    latencyMs: int = 0
    image: dict[str, Any] = Field(default_factory=dict)
    info: PosterInfo = Field(default_factory=PosterInfo)
    warnings: list[str] = Field(default_factory=list)


# ====================================================================== 图像生成


class PosterGenerateRequest(BaseModel):
    """页面工坊「AI 生成海报」的入参。

    prompt 由前端根据活动字段拼出来，只包含公开的活动视觉描述——
    不许带报名者信息、匿名反馈原文或任何个人敏感信息（见 docs/poster-generation-api.md 的安全条款）。
    """

    prompt: str = Field(min_length=4, max_length=800, description="画面描述")
    aspectRatio: str = Field(default="3:4", description="3:4 / 4:3 / 1:1 / 16:9")
    style: str = Field(default="warm-editorial-campus", max_length=60, description="版式风格标识")


class PosterGenerateResponse(BaseModel):
    """出图结果。

    url 是上游返回的图片地址（可能几小时就失效），b64 是同一张图的 base64——
    给调用方一次落盘/入库的机会，不要依赖上游 URL 长期可用。
    """

    ok: bool = True
    source: str = "image"
    model: str = ""
    latencyMs: int = 0
    prompt: str = ""
    aspectRatio: str = "3:4"
    url: str = ""
    b64: str = ""
    revisedPrompt: str = ""
    size: str = ""
    warnings: list[str] = Field(default_factory=list)


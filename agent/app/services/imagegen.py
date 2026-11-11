"""图像生成：一句话画面描述 -> 一张海报底图。

和 vision.py（读图）是两条独立的链路，故意不复用配置：
能读图的模型（qwen-vl / glm-4v）和能画图的模型（wanx / cogview / dall-e）不是同一批，
混用会导致「识别能用但生成永远 503」这种很难查的问题。

协议选 OpenAI 兼容的 ``POST {base}/images/generations``：
OpenAI、通义万相兼容模式、智谱 CogView 和各类中转都吃这套，换供应商只改 .env。

三条铁律，和 vision.py 一致：

1. **没配就说没配**。没填 IMAGE_MODEL / IMAGE_API_KEY -> ImageNotConfigured -> 503，
   前端继续用「上传已有海报」。绝不返回一张假图。
2. **失败不自动重试**。每调一次都是真金白银，重试是用户的决定，不是我们的。
   所以超时、限流、格式错误一律抛 ImageFailed -> 502，让前端提示「稍后重试」。
3. **原图要能留下来**。上游返回的 url 通常几小时就失效，所以同时把 base64 回传给
   调用方（业务后端），由它落盘成自己的 /files 地址再返回给浏览器。

为什么用 httpx 而不是 langchain：langchain 的 ChatModel 抽象不覆盖 images/generations，
硬套反而要绕。
"""

from __future__ import annotations

import base64
import logging
import re
import time
from typing import Any

import httpx

from app.core.config import get_settings

logger = logging.getLogger(__name__)


class ImageError(RuntimeError):
    """图像生成链路的基类异常。接口层按类型翻译成不同状态码。"""


class ImageNotConfigured(ImageError):
    """没配图像模型。接口层 -> 503，前端引导改用上传海报，不要提示重试。"""


class ImagePromptError(ImageError):
    """提示词不合规（太长、包含不该有的内容）。接口层 -> 422。"""


class ImageFailed(ImageError):
    """上游调不通、超时、限流或返回看不懂。接口层 -> 502。"""


# 前端传的是比例，上游多数只认具体尺寸，这里换算。
# 3:4 给 1024x1365 —— 不少上游要求边长是 8 的倍数，1365 不是，所以向下取到 1360。
_RATIO_TO_SIZE: dict[str, str] = {
    "3:4": "1024x1360",
    "4:3": "1360x1024",
    "1:1": "1024x1024",
    "16:9": "1360x768",
    "9:16": "768x1360",
}

_ALLOWED_RATIOS = tuple(_RATIO_TO_SIZE)

# 风格标识 -> 追加到提示词尾部的画面要求。
# 做成白名单而不是让前端塞任意文本，是因为这段会直接发给付费的上游，
# 也避免有人拿它当「任意提示词通道」绕过 prompt 长度和内容校验。
_STYLE_HINTS: dict[str, str] = {
    "warm-editorial-campus": "暖米色与低饱和绿色调，编辑式留白排版，校园场景，柔和自然光，画面没有文字",
    "minimal-calm": "极简构图，大量留白，莫兰迪色系，安静克制，画面没有文字",
    "photo-soft": "写实摄影质感，浅景深，柔和逆光，胶片颗粒，画面没有文字",
}

# 提示词里出现这些就拒掉。只挡明显不该进图的个人信息，
# 不做「敏感话题」过滤——心理活动海报本身就要能描述情绪。
_BLOCKED_PATTERNS = (
    "身份证",
    "学号",
    "手机号",
    "银行卡",
    "密码",
    "@qq.com",
    "@163.com",
)

# 光有「手机号」这三个字挡不住用户直接写号码，所以补几条数字模式。
# 前端还有一处会顺手把 prompt 原样显示出来，号码一旦进了上游就收不回来了。
_BLOCKED_REGEX = (
    # 中国大陆手机号，允许中间带空格或连字符
    re.compile(r"(?<!\d)1[3-9]\d[\s-]?\d{4}[\s-]?\d{4}(?!\d)"),
    # 银行卡/学号一类的长数字串
    re.compile(r"(?<!\d)\d{9,}(?!\d)"),
)


def normalize_aspect_ratio(value: str | None) -> str:
    """把前端传来的比例收敛到白名单，非法值退回 3:4 而不是报错。"""
    text = (value or "").strip().replace("：", ":")
    return text if text in _RATIO_TO_SIZE else "3:4"


def validate_prompt(prompt: str) -> str:
    """校验并规整提示词。空、过短、过长、含个人信息都会被拒。"""
    text = (prompt or "").strip()
    if len(text) < 4:
        raise ImagePromptError("画面描述太短了：至少写几个词，比如「傍晚草坪上做减压游园会」")
    if len(text) > 800:
        raise ImagePromptError("画面描述太长了：800 字以内就够，写清楚场景、氛围和留白位置即可")
    lowered = text.lower()
    for pattern in _BLOCKED_PATTERNS:
        if pattern in lowered:
            raise ImagePromptError(
                f"画面描述里不要包含「{pattern}」这类个人信息。海报底图只需要公开的场景与氛围描述"
            )
    for regex in _BLOCKED_REGEX:
        if regex.search(text):
            raise ImagePromptError(
                "画面描述里不要包含手机号、学号这类号码。海报底图只需要公开的场景与氛围描述"
            )
    return text


def build_prompt(prompt: str, style: str | None) -> str:
    """把用户描述和风格要求拼成最终提示词。"""
    hint = _STYLE_HINTS.get((style or "").strip(), _STYLE_HINTS["warm-editorial-campus"])
    return f"{prompt.strip()}。{hint}"


def _extract_image(payload: dict[str, Any]) -> tuple[str, str]:
    """从上游响应里取 (b64 或 url, revised_prompt)。

    上游返回有两种形态：``data[0].b64_json``（OpenAI 标准）和 ``data[0].url``。
    两种都要认，否则换个供应商就静默失败。
    """
    data = payload.get("data")
    if not isinstance(data, list) or not data:
        raise ImageFailed("图像服务返回里没有 data 数组，可能是模型名不对")
    first = data[0]
    if not isinstance(first, dict):
        raise ImageFailed("图像服务返回的 data[0] 格式看不懂")
    b64 = str(first.get("b64_json") or "").strip()
    url = str(first.get("url") or "").strip()
    revised = str(first.get("revised_prompt") or "").strip()
    if not b64 and not url:
        raise ImageFailed("图像服务既没返回 b64_json 也没返回 url")
    return b64 or url, revised


def generate_poster(
    prompt: str,
    aspect_ratio: str | None = "3:4",
    style: str | None = None,
) -> dict[str, Any]:
    """真正去调上游出图。返回一个可以直接喂给 PosterGenerateResponse 的 dict。

    调用方负责把异常翻译成 HTTP 状态码：见 routers/poster.py。
    """
    settings = get_settings()

    # 先校验提示词，再检查配置。
    # 顺序有讲究：提示词不合规是用户当场就能改的错（422），而「没配图像模型」是环境状态（503）。
    # 反过来先报 503 的话，用户会以为改提示词没用、干脆放弃，
    # 等运维配上模型后又变成「怎么突然报提示词错」——两边的坑都踩一遍。
    final_prompt = build_prompt(validate_prompt(prompt), style)

    if not settings.image_ready:
        raise ImageNotConfigured(
            "尚未接入图像模型。请在 agent/.env 里填写 IMAGE_MODEL 与 IMAGE_API_KEY 后重启服务；"
            "在那之前请使用「上传已有海报」"
        )

    ratio = normalize_aspect_ratio(aspect_ratio)
    size = _RATIO_TO_SIZE[ratio]

    body = {
        "model": settings.image_model.strip(),
        "prompt": final_prompt,
        "n": 1,
        "size": size,
        "response_format": "b64_json",
    }

    started = time.perf_counter()
    try:
        with httpx.Client(timeout=settings.image_timeout) as client:
            resp = client.post(
                f"{settings.image_base_url_value.rstrip('/')}/images/generations",
                headers={
                    "Authorization": f"Bearer {settings.image_api_key_value}",
                    "Content-Type": "application/json",
                },
                json=body,
            )
    except httpx.TimeoutException as exc:
        raise ImageFailed(
            f"图像服务 {settings.image_timeout} 秒内没有返回，请稍后重试或改用上传已有海报"
        ) from exc
    except httpx.HTTPError as exc:
        raise ImageFailed(f"连不上图像服务：{exc}") from exc

    latency_ms = int((time.perf_counter() - started) * 1000)

    if resp.status_code == 429:
        raise ImageFailed("图像服务限流了，请过一会儿再试（每次生成都会产生费用，不要连点）")
    if resp.status_code >= 400:
        detail = ""
        try:
            err = resp.json()
            if isinstance(err, dict):
                inner = err.get("error")
                detail = str(inner.get("message") if isinstance(inner, dict) else inner or err)[:300]
        except ValueError:
            detail = resp.text[:300]
        raise ImageFailed(f"图像服务返回 {resp.status_code}：{detail or '无详细信息'}")

    try:
        payload = resp.json()
    except ValueError as exc:
        raise ImageFailed("图像服务返回的不是 JSON") from exc
    if not isinstance(payload, dict):
        raise ImageFailed("图像服务返回的 JSON 结构看不懂")

    image, revised = _extract_image(payload)

    # 统一成 base64 交给业务后端落盘。
    # 上游给 url 时这里顺手下载一次：它的有效期常常只有几小时，
    # 直接透传会让用户过两天打开页面看到裂图，而那时钱已经花掉了。
    is_remote = image.startswith(("http://", "https://"))
    b64 = "" if is_remote else image
    warnings: list[str] = []
    if is_remote:
        try:
            with httpx.Client(timeout=settings.image_timeout) as client:
                img_resp = client.get(image)
                img_resp.raise_for_status()
                b64 = base64.b64encode(img_resp.content).decode("ascii")
        except httpx.HTTPError as exc:
            logger.warning("下载上游生成的图片失败: %s", exc)
            warnings.append("图片没能转存到服务器，使用的是上游临时地址，可能会过期")

    logger.info(
        "海报生成完成 model=%s size=%s latency=%sms", settings.image_model, size, latency_ms
    )

    return {
        "ok": True,
        "source": "image",
        "model": settings.image_model.strip(),
        "latencyMs": latency_ms,
        "prompt": final_prompt,
        "aspectRatio": ratio,
        # url 仅作留痕：业务后端拿到 b64 就落盘，只把自己的 /files 地址返回给浏览器
        "url": image if is_remote else "",
        "b64": b64,
        "revisedPrompt": revised,
        "size": size,
        "warnings": warnings,
    }

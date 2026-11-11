"""图像生成路由：一句话描述 -> 一张海报底图。

失败时的状态码约定（和 /poster/extract 保持同一套心智模型）：
    503 = 没配图像模型（引导改用上传，不要提示重试）
    422 = 提示词不合规（告诉用户怎么改）
    502 = 上游不干活（提示稍后重试，但绝不自动重试——每次都花钱）

刻意不做自动重试：用户点一次是一次付费请求。幂等由业务后端按
client_request_id 兜住（同一个 client_request_id 不会重复出图）。
"""

from __future__ import annotations

from fastapi import APIRouter, HTTPException, Request

from app.core.config import get_settings
from app.core.guard import get_rate_limiter
from app.schemas.poster_schema import PosterGenerateRequest, PosterGenerateResponse
from app.services import audit, imagegen

router = APIRouter(prefix="/poster", tags=["vision"])


def _check_rate(request: Request) -> None:
    client = request.client.host if request.client else "unknown"
    allowed, wait = get_rate_limiter().check(f"poster-gen:{client}")
    if not allowed:
        raise HTTPException(
            status_code=429,
            detail={"code": "RATE_LIMITED", "message": f"请求太频繁，{wait} 秒后再试"},
            headers={"Retry-After": str(wait)},
        )


@router.post("/generate", response_model=PosterGenerateResponse, summary="生成活动海报底图")
async def generate(payload: PosterGenerateRequest, request: Request) -> PosterGenerateResponse:
    _check_rate(request)
    settings = get_settings()

    if not settings.image_enabled:
        raise HTTPException(
            status_code=503,
            detail={
                "code": "IMAGE_DISABLED",
                "message": "图像生成已被配置关闭（IMAGE_ENABLED=false），请改用上传已有海报",
                "next": "upload",
            },
        )

    try:
        result = imagegen.generate_poster(
            prompt=payload.prompt,
            aspect_ratio=payload.aspectRatio,
            style=payload.style,
        )
    except imagegen.ImageNotConfigured as exc:
        audit.log_call(kind="poster", ok=False, flags=["image_not_configured"], error=str(exc))
        raise HTTPException(
            status_code=503,
            detail={"code": "IMAGE_NOT_CONFIGURED", "message": str(exc), "next": "upload"},
        ) from exc
    except imagegen.ImagePromptError as exc:
        audit.log_call(kind="poster", ok=False, flags=["prompt_invalid"], error=str(exc))
        raise HTTPException(
            status_code=422,
            detail={"code": "PROMPT_INVALID", "message": str(exc), "next": "edit_prompt"},
        ) from exc
    except imagegen.ImageFailed as exc:
        audit.log_call(kind="poster", ok=False, flags=["image_failed"], error=str(exc))
        raise HTTPException(
            status_code=502,
            detail={"code": "IMAGE_FAILED", "message": str(exc), "next": "retry_later"},
        ) from exc

    audit.log_call(
        kind="poster",
        model=str(result.get("model") or ""),
        latency_ms=int(result.get("latencyMs") or 0),
        ok=True,
        flags=list(result.get("warnings") or [])[:5],
    )

    return PosterGenerateResponse(**result)

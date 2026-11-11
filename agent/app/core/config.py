"""全局配置。

规矩：
1. 所有密钥只从环境变量 / .env 读，代码里一个字符都不许出现。
2. 所有可调参数（模型、温度、超时、数据库）都在这儿，别散落在业务里。
3. 用 lru_cache 保证整个进程只有一个 Settings 实例。
"""

from __future__ import annotations

from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

# .env 必须是绝对路径。
#
# 原来写的是 env_file=".env" —— 相对路径，相对于**进程的当前工作目录**。
# 这意味着从仓库根目录启动（`uvicorn app.main:app` 没先 cd agent/）时，
# pydantic-settings 去根目录找 .env，找不到就静默回落到默认值：
# API KEY 变成空字符串，而 LLM_PROVIDER=deepseek 是写在代码里的默认值，
# 于是日志出现「配置就绪 provider=deepseek」和「未配置 LLM API KEY」同时成立的自相矛盾——
# 这正是队友反馈「一填密钥运行命令就自动清空」的真实原因：.env 压根没被读到。
#
# 这里按文件位置反推 agent/.env，从任何目录启动都能读到同一份配置。
_AGENT_ROOT = Path(__file__).resolve().parents[2]
_ENV_FILE = _AGENT_ROOT / ".env"

# 各家「兼容 OpenAI 协议」的地址和常用模型。
# 换供应商只要改 .env 里的 LLM_PROVIDER，代码一行不用动。
PROVIDER_PRESETS: dict[str, dict[str, str]] = {
    "deepseek": {"base_url": "https://api.deepseek.com/v1", "model": "deepseek-chat"},
    "openai": {"base_url": "https://api.openai.com/v1", "model": "gpt-4o-mini"},
    "qwen": {
        "base_url": "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "model": "qwen-plus",
    },
    "zhipu": {"base_url": "https://open.bigmodel.cn/api/paas/v4", "model": "glm-4-plus"},
    "custom": {"base_url": "", "model": ""},
}


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=str(_ENV_FILE),
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # ---- 应用 ----
    app_name: str = "campus-agent"
    debug: bool = True

    # ---- 模型 ----
    llm_provider: Literal["deepseek", "openai", "qwen", "zhipu", "custom"] = "deepseek"
    llm_api_key: str = ""
    llm_base_url: str = ""          # 留空则用 PROVIDER_PRESETS
    llm_model: str = ""             # 留空则用 PROVIDER_PRESETS
    llm_temperature: float = 0.3
    llm_timeout: int = 60
    llm_max_tokens: int = 2048

    # ---- 服务 ----
    host: str = "127.0.0.1"
    port: int = 8000
    cors_origins: str = "*"         # 逗号分隔，如 "http://localhost:5173,http://127.0.0.1:3000"
    history_limit: int = 20         # 每次带给模型的历史消息条数上限

    # ---- 视觉 / 多模态 ----
    # 和主模型分开配：主模型可以就用 deepseek（便宜、擅长写方案），
    # 但海报解析必须是一个能读图的模型（qwen-vl / glm-4v / gpt-4o）。
    # 三个值留空 = 没配视觉模型，/poster/extract 会老实返回 503，不会瞎编。
    vision_enabled: bool = True
    vision_model: str = ""
    vision_api_key: str = ""        # 留空则复用 LLM_API_KEY
    vision_base_url: str = ""       # 留空则复用 LLM_BASE_URL
    vision_timeout: int = 90
    poster_max_bytes: int = 5 * 1024 * 1024   # 和前端上传限制保持一致

    # ---- 图像生成 / 海报出图 ----
    # 和「视觉（读图）」是两回事，别复用 VISION_*：读图的模型反过来不一定能画图。
    # 走 OpenAI 兼容的 POST {base}/images/generations，所以 OpenAI、通义万相兼容模式、
    # 智谱 CogView、以及各类中转都能直接换。
    # 四个值留空 = 没接入图像模型，/poster/generate 老实返回 503，
    # 前端页面继续提供「上传已有海报」，绝不假装生成成功。
    image_enabled: bool = True
    image_model: str = ""           # 如 dall-e-3 / wanx-v1 / cogview-3
    image_api_key: str = ""         # 留空则复用 LLM_API_KEY
    image_base_url: str = ""        # 留空则复用 LLM_BASE_URL
    image_timeout: int = 150        # 出图很慢，比 vision_timeout 还宽
    image_size: str = "1024x1024"   # 上游只认具体尺寸，比例由我们换算

    # ---- RAG ----
    rag_enabled: bool = True
    rag_top_k: int = 4
    rag_min_score: float = 0.20     # 低于这个「内容分」的资料宁可不要，别把噪音喂给模型
    rag_context_chars: int = 1800   # 注入提示词的资料总长度上限
    embedding_backend: Literal["local", "api"] = "local"
    embedding_model: str = ""       # embedding_backend=api 时才用，留空按 provider 取默认
    embedding_dim: int = 512        # local 模式的哈希维度

    # ---- AI 安全 ----
    guard_enabled: bool = True
    rate_limit_per_minute: int = 40     # 单 IP 每分钟最多几次 AI 调用
    # 客户端可控的附加要求，卡死上限。
    # 原值 300 会截断后端 AiService.PLAN_SYSTEM_EXTRA（实测 435 字）——
    # 被截掉的正好是尾巴上「block.type 只能是：nav, banner, … countdown」那段白名单，
    # 等于模型从来没收到过受控组件清单。上限对齐 schemas/chat_schema.py 的 max_length=1000。
    system_extra_max_chars: int = 1000

    # ---- 危机关怀 ----
    # 识别到自伤 / 自杀表达时，**停止生成**、直接给求助资源（见 core/guard.py 的 crisis_reply）。
    # 号码做成配置项：每个学校、每个地区的资源都不一样，上线前必须换成真实的。
    # 换的时候别只换号码——crisis_center_hint 里的地点、时间也要跟着改成你校的。
    crisis_guard_enabled: bool = True
    crisis_hotline_name: str = "全国心理援助热线"
    crisis_hotline: str = "12356"
    crisis_emergency: str = "110 / 120"
    crisis_center_hint: str = "学校心理健康教育中心（工作日可预约）"

    # ---- 数据库 ----
    # sqlite：零依赖，开发与测试用；mysql：联调与部署用（需 pip install pymysql）
    db_backend: Literal["sqlite", "mysql"] = "sqlite"

    # sqlite 专用
    db_path: str = "data/agent.db"

    # mysql 专用
    db_host: str = "127.0.0.1"
    db_port: int = 3306
    db_user: str = "root"
    db_password: str = ""
    db_name: str = "campus_agent"

    # ---- 校验 ----
    @field_validator("llm_provider", mode="before")
    @classmethod
    def _normalize_provider(cls, v: object) -> str:
        text = str(v or "").strip().lower()
        return text or "deepseek"

    @field_validator("db_backend", mode="before")
    @classmethod
    def _normalize_db(cls, v: object) -> str:
        text = str(v or "").strip().lower()
        if text in {"mysql", "mariadb"}:
            return "mysql"
        if text in {"postgres", "postgresql", "pg"}:
            raise ValueError("本服务只支持 sqlite / mysql，Postgres 未接入")
        return text or "sqlite"

    # ---- 派生属性 ----
    @property
    def base_url(self) -> str:
        return self.llm_base_url.strip() or PROVIDER_PRESETS[self.llm_provider]["base_url"]

    @property
    def model_name(self) -> str:
        return self.llm_model.strip() or PROVIDER_PRESETS[self.llm_provider]["model"]

    @property
    def cors_list(self) -> list[str]:
        items = [x.strip() for x in self.cors_origins.split(",") if x.strip()]
        return items or ["*"]

    @property
    def cors_is_wildcard(self) -> bool:
        return "*" in self.cors_list

    @property
    def allow_credentials(self) -> bool:
        """通配符 + allow_credentials 是浏览器会直接拒绝的组合，别自欺欺人。

        本项目前端不带 cookie（没有登录态），所以通配符时关掉 credentials 才是对的。
        """
        return not self.cors_is_wildcard

    # ---- 能力开关：给 /health、自检和答辩看 ----
    @property
    def vision_ready(self) -> bool:
        """视觉模型是否可用：要显式配置模型名，并且有 key（专属或复用主 key）。"""
        return bool(self.vision_enabled and self.vision_model.strip() and self.vision_api_key_value)

    @property
    def vision_api_key_value(self) -> str:
        return (self.vision_api_key or self.llm_api_key).strip()

    @property
    def image_ready(self) -> bool:
        """图像生成是否可用：要显式配置模型名，并且有 key（专属或复用主 key）。"""
        return bool(self.image_enabled and self.image_model.strip() and self.image_api_key_value)

    @property
    def image_api_key_value(self) -> str:
        return (self.image_api_key or self.llm_api_key).strip()

    @property
    def image_base_url_value(self) -> str:
        return (self.image_base_url or self.base_url).strip()

    def image_summary(self) -> dict[str, object]:
        return {
            "enabled": self.image_enabled,
            "ready": self.image_ready,
            "model": self.image_model or "（未配置）",
            "base_url": self.image_base_url_value,
            "key_configured": bool(self.image_api_key_value),
        }

    @property
    def vision_base_url_value(self) -> str:
        return (self.vision_base_url or self.base_url).strip()

    @property
    def embedding_ready(self) -> bool:
        if self.embedding_backend == "api":
            return bool(self.llm_api_key.strip())
        return True  # local 哈希向量不需要任何外部服务

    def vision_summary(self) -> dict[str, object]:
        return {
            "enabled": self.vision_enabled,
            "ready": self.vision_ready,
            "model": self.vision_model or "（未配置）",
            "base_url": self.vision_base_url_value,
            "key_configured": bool(self.vision_api_key_value),
        }

    def rag_summary(self) -> dict[str, object]:
        return {
            "enabled": self.rag_enabled,
            "embedding_backend": self.embedding_backend,
            "embedding_ready": self.embedding_ready,
            "top_k": self.rag_top_k,
            "min_score": self.rag_min_score,
        }

    @property
    def has_key(self) -> bool:
        return bool(self.llm_api_key.strip())

    @property
    def env_file_path(self) -> Path:
        """本次实际读取的 .env 路径，供启动日志点明「到底读了哪个文件」。"""
        return _ENV_FILE

    def db_summary(self) -> dict[str, object]:
        """给自检和日志用：不含密码。"""
        if self.db_backend == "mysql":
            return {
                "backend": "mysql",
                "host": self.db_host,
                "port": self.db_port,
                "database": self.db_name,
                "user": self.db_user,
            }
        return {"backend": "sqlite", "path": self.db_path}

    def safe_summary(self) -> dict[str, object]:
        """给 /health 用：绝不包含 key 本身，只说有没有。"""
        return {
            "provider": self.llm_provider,
            "model": self.model_name,
            "base_url": self.base_url,
            "key_configured": self.has_key,
            "temperature": self.llm_temperature,
            "history_limit": self.history_limit,
            "vision": self.vision_summary(),
            "image": self.image_summary(),
            "rag": self.rag_summary(),
            "guard_enabled": self.guard_enabled,
            "db": self.db_summary(),
        }


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()

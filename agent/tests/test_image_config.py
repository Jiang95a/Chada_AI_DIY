"""图像生成的「就绪」判断。

这个判断原来只要「有一个 key」就返回 True —— 但 IMAGE_API_KEY 留空时会回退到
LLM_API_KEY（通常是 deepseek），把它发到百炼的 /images/generations 必然 401。
结果是 /health 说就绪、用户点一次生成才报错，排查方向全歪。

这里把三条规则钉住，改坏了会红。
"""

from __future__ import annotations

from app.core.config import Settings


def _settings(**overrides) -> Settings:
    base = {
        "image_enabled": True,
        "image_model": "wanx-v1",
        "llm_api_key": "sk-deepseek",
        "llm_base_url": "https://api.deepseek.com/v1",
    }
    base.update(overrides)
    return Settings(**base)


def test_ready_when_dedicated_key_is_set():
    s = _settings(image_api_key="sk-dashscope", image_base_url="https://dashscope.aliyuncs.com/compatible-mode/v1")
    assert s.image_ready is True


def test_not_ready_when_reusing_llm_key_across_providers():
    """最容易踩的坑：key 留空 + base_url 指到别家 → 必须是 False。"""
    s = _settings(image_api_key="", image_base_url="https://dashscope.aliyuncs.com/compatible-mode/v1")
    assert s.image_ready is False
    assert s.image_summary()["key_reused_from_llm"] is True


def test_ready_when_reusing_llm_key_on_same_endpoint():
    """同一家供应商时复用主 key 是说得通的，结尾斜杠不算不同家。"""
    s = _settings(image_api_key="", image_base_url="https://api.deepseek.com/v1/")
    assert s.image_ready is True


def test_not_ready_without_model():
    assert _settings(image_model="", image_api_key="sk-x").image_ready is False


def test_not_ready_when_disabled():
    assert _settings(image_enabled=False, image_api_key="sk-x").image_ready is False


def test_not_ready_without_any_key():
    assert _settings(llm_api_key="", image_api_key="").image_ready is False

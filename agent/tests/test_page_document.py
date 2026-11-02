"""PageDocument 补齐的测试。

这里不自己发明标准：直接加载后端那份 ``page-document.schema.json``
（``server/src/main/resources/schema/page-document.schema.json``）当裁判。
schema 改了、agent 没跟上，这些测试会红。

「为什么值得测这么细」：这份文档是要落库、要被 apply、要生成公开链接的。
一个 ``style.align`` 写成空串，后端就是 422，用户那边表现成「点采用没反应」。
"""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

import pytest

from app.services import block_catalog as catalog
from app.services.page_document import (
    MAX_BLOCKS_PER_PAGE,
    MAX_PAGES,
    TITLE_MAX,
    SLUG_PATTERN,
    extract_json_object,
    slugify,
    to_page_document,
    upgrade_reply,
    validate_document,
)
from app.services.tools.planner import build_plan

# ---------------------------------------------------------------------------
# 极简 JSON Schema 校验器：只实现这份 schema 用到的关键字
# ---------------------------------------------------------------------------
SCHEMA_PATH = (
    Path(__file__).resolve().parents[2]
    / "server"
    / "src"
    / "main"
    / "resources"
    / "schema"
    / "page-document.schema.json"
)


@pytest.fixture(scope="module")
def schema() -> dict[str, Any]:
    if not SCHEMA_PATH.exists():
        pytest.skip(f"找不到 PageDocument schema：{SCHEMA_PATH}")
    return json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))


@pytest.fixture(scope="module")
def defs(schema) -> dict[str, Any]:
    return schema.get("$defs", {})


def _type_ok(value: Any, expected: str) -> bool:
    if expected == "null":
        return value is None
    if expected == "boolean":
        return isinstance(value, bool)
    if expected == "integer":
        return isinstance(value, int) and not isinstance(value, bool)
    if expected == "number":
        return isinstance(value, (int, float)) and not isinstance(value, bool)
    if expected == "string":
        return isinstance(value, str)
    if expected == "array":
        return isinstance(value, list)
    if expected == "object":
        return isinstance(value, dict)
    raise AssertionError(f"schema 里出现了本校验器不认识的 type：{expected}")


def schema_errors(instance: Any, schema: dict[str, Any], defs: dict[str, Any], path: str = "$") -> list[str]:
    if "$ref" in schema:
        name = schema["$ref"].rsplit("/", 1)[-1]
        return schema_errors(instance, defs[name], defs, path)

    if "oneOf" in schema:
        branches = [schema_errors(instance, sub, defs, path) for sub in schema["oneOf"]]
        if not any(not branch for branch in branches):
            return [f"{path}: 不满足 oneOf 任何一个分支"]
        return []

    errors: list[str] = []

    expected = schema.get("type")
    if expected is not None:
        options = expected if isinstance(expected, list) else [expected]
        if not any(_type_ok(instance, option) for option in options):
            return [f"{path}: 类型应为 {options}，实际 {type(instance).__name__}"]

    if "enum" in schema and instance not in schema["enum"]:
        errors.append(f"{path}: {instance!r} 不在枚举里")

    if isinstance(instance, str):
        if "maxLength" in schema and len(instance) > schema["maxLength"]:
            errors.append(f"{path}: 长度 {len(instance)} 超过 {schema['maxLength']}")
        if "pattern" in schema and not re.match(schema["pattern"], instance):
            errors.append(f"{path}: 不匹配 {schema['pattern']}")

    if isinstance(instance, (int, float)) and not isinstance(instance, bool):
        if "minimum" in schema and instance < schema["minimum"]:
            errors.append(f"{path}: {instance} 小于 {schema['minimum']}")
        if "maximum" in schema and instance > schema["maximum"]:
            errors.append(f"{path}: {instance} 大于 {schema['maximum']}")

    if isinstance(instance, dict):
        for key in schema.get("required", []):
            if key not in instance:
                errors.append(f"{path}: 缺少必填字段 {key}")
        for key, sub in (schema.get("properties") or {}).items():
            if key in instance:
                errors += schema_errors(instance[key], sub, defs, f"{path}.{key}")

    if isinstance(instance, list):
        if "minItems" in schema and len(instance) < schema["minItems"]:
            errors.append(f"{path}: 少于 {schema['minItems']} 项")
        if "maxItems" in schema and len(instance) > schema["maxItems"]:
            errors.append(f"{path}: 多于 {schema['maxItems']} 项")
        items = schema.get("items")
        if isinstance(items, dict):
            for index, item in enumerate(instance):
                errors += schema_errors(item, items, defs, f"{path}[{index}]")

    return errors


# ---------------------------------------------------------------------------
# 0. 契约同步：agent 抄过去的常量必须和后端 schema 一致
# ---------------------------------------------------------------------------
def test_block_types_match_schema(schema) -> None:
    assert schema["$defs"]["block"]["properties"]["type"]["enum"] == list(catalog.BLOCK_TYPES)


def test_page_roles_match_schema(schema) -> None:
    assert schema["$defs"]["page"]["properties"]["role"]["enum"] == list(catalog.PAGE_ROLES)


def test_limits_match_schema(schema) -> None:
    assert schema["properties"]["pages"]["minItems"] == 1
    assert schema["properties"]["pages"]["maxItems"] == MAX_PAGES
    assert schema["$defs"]["page"]["properties"]["blocks"]["maxItems"] == MAX_BLOCKS_PER_PAGE
    assert schema["properties"]["title"]["maxLength"] == TITLE_MAX
    assert schema["properties"]["slug"]["pattern"] == SLUG_PATTERN.pattern
    assert set(schema["$defs"]["theme"]["required"]) == {"primary", "accent", "bg", "text"}


# ---------------------------------------------------------------------------
# 1. 模板方案 → 合法 PageDocument
# ---------------------------------------------------------------------------
FULL_META = {
    "title": "摄影社 2026 秋季招新",
    "org": "光影摄影社",
    "startAt": "2026-11-10 19:00",
    "place": "大学生活动中心 301",
    "deadline": "2026-11-08 23:59",
    "capacity": 60,
    "contact": "微信 photo_club",
}


@pytest.mark.parametrize("scene", ["recruit", "signup", "vote", "checkin"])
def test_template_plan_becomes_schema_valid_document(scene, schema, defs) -> None:
    plan = build_plan(scene, dict(FULL_META))
    result = to_page_document(plan, title=FULL_META["title"])

    assert result is not None, "模板方案应该能修成文档"
    assert result.ok, result.errors
    assert schema_errors(result.document, schema, defs) == []

    document = result.document
    assert document["title"] == FULL_META["title"]
    assert SLUG_PATTERN.match(document["slug"])
    assert document["meta"]["capacity"] == 60
    assert document["theme"]["primary"].startswith("#")
    assert isinstance(document["createdAt"], int)
    assert document["published"] is None

    roles = [page["role"] for page in document["pages"]]
    assert roles.count("home") == 1
    assert len(document["pages"]) >= 3

    for page in document["pages"]:
        assert page["id"]
        assert page["blocks"], f"{page['name']} 不该是空页"
        for block in page["blocks"]:
            assert block["id"]
            assert block["type"] in catalog.BLOCK_TYPES
            assert isinstance(block["props"], dict)
            assert block["style"]["align"] in ("left", "center", "right")
            assert block["hidden"] is False


def test_button_page_id_points_to_real_page() -> None:
    document = to_page_document(build_plan("recruit", dict(FULL_META))).document
    page_ids = {page["id"] for page in document["pages"]}

    buttons = [
        block
        for page in document["pages"]
        for block in page["blocks"]
        if block["type"] == "button"
    ]
    assert buttons, "招新模板必须有按钮"

    for button in buttons:
        action = button["props"]["action"]
        if action["type"] == "page":
            assert action["pageId"], "跳转按钮必须补上 pageId，否则点了没反应"
            assert action["pageId"] in page_ids


# ---------------------------------------------------------------------------
# 2. 幂等：完整文档再过一遍，id 一个都不能变
# ---------------------------------------------------------------------------
def test_idempotent_keeps_every_id() -> None:
    first = to_page_document(build_plan("signup", dict(FULL_META)), now_ms=1_700_000_000_000).document
    second = to_page_document(first, now_ms=1_700_000_900_000).document

    assert second["slug"] == first["slug"]
    assert second["id"] == first["id"]
    assert [page["id"] for page in second["pages"]] == [page["id"] for page in first["pages"]]
    assert [
        [block["id"] for block in page["blocks"]] for page in second["pages"]
    ] == [[block["id"] for block in page["blocks"]] for page in first["pages"]]
    assert second["createdAt"] == first["createdAt"]
    assert second["updatedAt"] != first["updatedAt"]


# ---------------------------------------------------------------------------
# 3. slug 永远不会非法
# ---------------------------------------------------------------------------
@pytest.mark.parametrize(
    "title",
    ["摄影社 2026 秋季招新", "", "   ", "A", "!!!", "Hello World!", "2026 Fall 招新", "---"],
)
def test_slugify_never_produces_invalid_slug(title) -> None:
    assert SLUG_PATTERN.match(slugify(title)), f"{title!r} -> {slugify(title)!r}"


def test_slugify_matches_backend_rules() -> None:
    """逐条对齐后端 Ids.slugify。

    两边算不出同一个 slug，发布时会把草稿里的链接悄悄换掉 ——
    用户二维码都发出去了，才发现指向另一个地址。
    """
    assert slugify("Café 招新") == "cafe"                 # 剥音标
    assert slugify("2026") == "activity-2026"             # 纯数字加前缀
    assert slugify("摄影社 2026 秋季招新") == "activity-2026"  # 中文剥完只剩年份
    assert slugify("Hello World!") == "hello-world"       # 常规

    short = slugify("A")                                  # 不足 3 位补 4 位随机尾
    assert short.startswith("a-") and len(short) == 6

    blank = slugify("")                                   # 空 → activity-<6 位>
    assert blank.startswith("activity-") and len(blank) == len("activity-") + 6

    long_slug = slugify("a" * 100)                        # 超长截断到 48
    assert len(long_slug) <= 48
    assert SLUG_PATTERN.match(long_slug)


# ---------------------------------------------------------------------------
# 4. 脏输入：模型吐什么都要能兜住
# ---------------------------------------------------------------------------
def test_unknown_block_type_is_dropped() -> None:
    plan = {
        "title": "测试",
        "pages": [
            {
                "name": "首页",
                "role": "home",
                "blocks": [
                    {"type": "card", "props": {}},
                    {"type": "信息卡片", "props": {}},
                    {"type": "text", "props": {"text": "留下了"}},
                ],
            }
        ],
    }
    result = to_page_document(plan)
    assert result is not None and result.ok
    types = [block["type"] for block in result.document["pages"][0]["blocks"]]
    assert types == ["text"]
    assert sum(1 for note in result.notes if "未知组件" in note) == 2


def test_missing_ids_and_slug_are_filled() -> None:
    plan = {"pages": [{"name": "首页", "blocks": [{"type": "heading", "props": {"text": "标题"}}]}]}
    document = to_page_document(plan).document

    assert document["id"]
    assert document["slug"]
    assert document["title"]
    assert document["pages"][0]["id"]
    assert document["pages"][0]["blocks"][0]["id"]
    assert document["pages"][0]["role"] == "home"


def test_duplicate_home_is_demoted() -> None:
    plan = {
        "pages": [
            {"name": "首页", "role": "home", "blocks": [{"type": "text", "props": {"text": "a"}}]},
            {"name": "又一页", "role": "home", "blocks": [{"type": "text", "props": {"text": "b"}}]},
        ]
    }
    document = to_page_document(plan).document
    assert [page["role"] for page in document["pages"]] == ["home", "content"]


def test_no_home_promotes_first_page() -> None:
    plan = {
        "pages": [
            {"name": "第一页", "role": "content", "blocks": [{"type": "text", "props": {"text": "a"}}]},
            {"name": "第二页", "role": "content", "blocks": [{"type": "text", "props": {"text": "b"}}]},
        ]
    }
    document = to_page_document(plan).document
    assert document["pages"][0]["role"] == "home"


def test_components_key_is_accepted() -> None:
    plan = {"pages": [{"name": "首页", "components": [{"component": "text", "properties": {"text": "老的键名"}}]}]}
    document = to_page_document(plan).document
    block = document["pages"][0]["blocks"][0]
    assert block["type"] == "text"
    assert block["props"]["text"] == "老的键名"


def test_wrapped_document_and_bare_array() -> None:
    inner = {"title": "壳里的", "pages": [{"name": "首页", "blocks": [{"type": "text", "props": {"text": "x"}}]}]}
    assert to_page_document({"document": inner}).document["title"] == "壳里的"

    bare = [{"name": "首页", "blocks": [{"type": "text", "props": {"text": "y"}}]}]
    assert to_page_document(bare, title="裸数组").document["title"] == "裸数组"


def test_empty_pages_returns_none() -> None:
    assert to_page_document({"pages": []}) is None
    assert to_page_document({"pages": [{"name": "空页", "blocks": []}]}) is None
    assert to_page_document(None) is None


def test_style_align_is_never_empty() -> None:
    """空串不在 enum 里，后端会直接 422 —— 这是队友反馈过的原始问题之一。"""
    plan = {
        "pages": [
            {
                "name": "首页",
                "blocks": [{"type": "text", "props": {"text": "x"}, "style": {"align": "", "bg": ""}}],
            }
        ]
    }
    block = to_page_document(plan).document["pages"][0]["blocks"][0]
    assert block["style"]["align"] in ("left", "center", "right")


def test_hidden_string_false_is_not_truthy() -> None:
    plan = {"pages": [{"name": "首页", "blocks": [{"type": "text", "props": {"text": "x"}, "hidden": "false"}]}]}
    assert to_page_document(plan).document["pages"][0]["blocks"][0]["hidden"] is False


# ---------------------------------------------------------------------------
# 5. 自检能抓出坏文档
# ---------------------------------------------------------------------------
def test_validate_document_catches_breakage() -> None:
    document = to_page_document(build_plan("recruit", dict(FULL_META))).document
    assert validate_document(document) == []

    document["pages"][0]["blocks"][0]["style"]["align"] = ""
    assert validate_document(document)

    broken = json.loads(json.dumps(document))
    broken["pages"][0]["blocks"][0]["style"]["align"] = "left"
    del broken["pages"][0]["blocks"][0]["id"]
    assert validate_document(broken)


# ---------------------------------------------------------------------------
# 6. 回复升级：只碰「真的是方案」的回复
# ---------------------------------------------------------------------------
def test_upgrade_reply_leaves_prose_alone() -> None:
    prose = "招新活动一般放在开学第一周。时间、地点、报名方式必须写清楚，别用公文腔。"
    assert upgrade_reply(prose) == (prose, [], None)


def test_upgrade_reply_ignores_truncated_json() -> None:
    broken = '先这样：{"pages": [{"name": "首页"'
    assert upgrade_reply(broken) == (broken, [], None)


def test_upgrade_reply_ignores_plain_json_without_pages() -> None:
    payload = '{"answer": "把时间写清楚", "tips": ["少用公文腔"]}'
    assert upgrade_reply(payload) == (payload, [], None)


def test_upgrade_reply_upgrades_plan(schema, defs) -> None:
    plan = build_plan("recruit", dict(FULL_META))
    text = "好的，方案如下：\n```json\n" + json.dumps(plan, ensure_ascii=False) + "\n```"

    upgraded, notes, document = upgrade_reply(text)

    assert document is not None
    assert schema_errors(document, schema, defs) == []
    assert json.loads(upgraded) == document
    assert notes, "补齐过程应该留下说明"
    # 方案里的中间字段不该混进文档
    assert "intent" not in document
    assert "reason" not in document


def test_extract_json_object_handles_code_fence_and_noise() -> None:
    text = '以下是结果：\n```json\n{"a": {"b": 1}}\n```\n还有别的吗'
    assert extract_json_object(text) == {"a": {"b": 1}}
    assert extract_json_object("没有 JSON") is None


# ---------------------------------------------------------------------------
# 7. 接口
# ---------------------------------------------------------------------------
@pytest.fixture()
def client():
    from fastapi.testclient import TestClient

    from app.main import app

    with TestClient(app) as test_client:
        yield test_client


def test_plan_endpoint_returns_valid_document(client, schema, defs) -> None:
    response = client.post("/plan", json={"scene": "recruit", **{
        "title": FULL_META["title"],
        "org": FULL_META["org"],
        "place": FULL_META["place"],
        "start_at": FULL_META["startAt"],
        "deadline": FULL_META["deadline"],
        "capacity": FULL_META["capacity"],
        "contact": FULL_META["contact"],
    }})
    assert response.status_code == 200

    body = response.json()
    assert body["ok"] is True
    assert body["source"] == "template"
    assert schema_errors(body["document"], schema, defs) == []
    assert body["stats"]["pages"] >= 3
    assert body["stats"]["blocks"] >= 10


def test_plan_endpoint_upgrades_supplied_plan(client) -> None:
    raw_plan = build_plan("vote", {"title": "十佳社团投票"})
    response = client.post("/plan", json={"plan": raw_plan})
    assert response.status_code == 200

    document = response.json()["document"]
    assert document["pages"][0]["id"]
    assert document["pages"][0]["blocks"][0]["id"]


def test_plan_endpoint_rejects_empty_plan(client) -> None:
    response = client.post("/plan", json={"plan": {"pages": []}})
    assert response.status_code == 422
    assert response.json()["detail"]["code"] == "PLAN_EMPTY"


def test_plan_endpoint_is_deterministic(client) -> None:
    """除了随机 id，同样的入参必须得到同样的结构 —— E2E 靠这个。"""
    payload = {"scene": "checkin", "title": "图书馆早起打卡"}

    def shape(body: dict[str, Any]) -> list[Any]:
        return [
            (page["name"], page["role"], [block["type"] for block in page["blocks"]])
            for page in body["document"]["pages"]
        ]

    first = client.post("/plan", json=payload).json()
    second = client.post("/plan", json=payload).json()
    assert shape(first) == shape(second)


# ---------------------------------------------------------------------------
# 8. 回归：system_extra 不许再把后端的规划提示词截断
# ---------------------------------------------------------------------------
def test_system_extra_keeps_the_whole_backend_planner_prompt() -> None:
    from app.core.guard import sanitize_system_extra

    # 与 server/.../AiService.PLAN_SYSTEM_EXTRA 逐字一致
    planner_prompt = (
        "你是校园活动页面规划器。只输出一个 JSON 对象，不要输出解释、不要在 JSON 外写文字、不要用代码块包裹。"
        '结构：{"intent":"","reason":"","title":"","theme":{"primary":"#rrggbb","accent":"#rrggbb",'
        '"bg":"#rrggbb","text":"#rrggbb","radius":12,"fontScale":1,"spacing":12},'
        '"pages":[{"name":"首页","role":"home","blocks":[{"type":"banner","props":{}}]}]}。'
        "pages 里至少有一页 role=home。block.type 只能是："
        "nav, banner, heading, text, image, infoCard, button, form, radio, checkbox, qrcode, divider, countdown。"
    )
    assert len(planner_prompt) > 300, "这条测试的意义在于它比旧的 300 字上限长"

    result = sanitize_system_extra(planner_prompt)

    assert "system_extra_truncated" not in result.flags
    assert result.text == planner_prompt
    assert "countdown" in result.text, "尾巴上的组件白名单必须完整送到模型"

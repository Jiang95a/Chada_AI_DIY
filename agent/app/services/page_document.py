"""把「方案」修成一份完整的 PageDocument。

**为什么智能体要自己吐完整文档，而不是全靠后端补。**

后端 {@code DocumentRepair} 上线前，agent 产出的只是 DocPlan：页面和组件都没有 id，
没有 slug / theme / meta。后端原样存进 ``ai_proposals``，用户点「采用」时
``saveDraft`` 的 schema 校验就 422 —— 前端显示得好好的，一点就炸。

后端的修复器已经能把 DocPlan 补齐了，但那是**兜底**，不是**契约**。
``system_extra`` 里写的是「只输出 JSON」，从来没写过「要有 id」；模型自然不给。
所以这里做的事是：**在智能体出口就把文档补全**，让后端拿到的东西本来就是完整的。
后端那层修复继续留着（历史脏数据、别的调用方），两层不冲突——它们补的是同一套默认值。

**三个对齐来源，改的时候一起看：**

================================================  ==========================================
``server/src/main/resources/schema/page-document.schema.json``  契约本体，本文件的硬约束都从它来
``server/.../service/DocumentRepair.java``        后端怎么兜底；这里的默认值要能对上
``web/src/services/planner.ts``                   前端 ``normalizePlan`` 能吃多宽（很宽，超集不影响）
================================================  ==========================================

**关键性质：幂等。** 一份已经完整的文档再过一遍，id 一个都不会变——
否则「重新生成」会换掉页面 id，前端的按钮跳转、发布链接全部失联。
"""

from __future__ import annotations

import json
import re
import secrets
import time
import unicodedata
from dataclasses import dataclass, field
from typing import Any, Final

from app.services import block_catalog as catalog

# ---------------------------------------------------------------------------
# 契约常量（逐项对应 page-document.schema.json）
# ---------------------------------------------------------------------------

MAX_PAGES: Final[int] = 10                 # schema: pages.maxItems
MAX_BLOCKS_PER_PAGE: Final[int] = 80       # schema: page.blocks.maxItems
MAX_ITEMS_PER_CARD: Final[int] = 6         # 前端 normalizeBlockProps 也是 6
MAX_FIELDS_PER_FORM: Final[int] = 12       # 校验器 12 条以上就报错，这里直接截断
MAX_OPTIONS: Final[int] = 12
TITLE_MAX: Final[int] = 160                # schema: title.maxLength
PAGE_NAME_MAX: Final[int] = 80             # schema: page.name.maxLength

SLUG_PATTERN: Final[re.Pattern[str]] = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")

ALIGNS: Final[tuple[str, ...]] = ("left", "center", "right")
BUTTON_STYLES: Final[tuple[str, ...]] = ("solid", "gradient", "outline")
FONTS: Final[tuple[str, ...]] = ("sans", "serif", "round")

# 与前端 DEFAULT_THEME、后端 DocumentRepair.DEFAULT_COLORS 保持同一套取值
DEFAULT_THEME: Final[dict[str, Any]] = {
    "primary": "#4f46e5",
    "accent": "#8b5cf6",
    "bg": "#f7f8fc",
    "text": "#111827",
    "radius": 16,
    "fontScale": 1,
    "spacing": 1,
    "buttonStyle": "gradient",
    "font": "sans",
}

DEFAULT_META: Final[dict[str, Any]] = {
    "org": "",
    "startAt": "",
    "place": "",
    "deadline": "",
    "capacity": 0,
    "contact": "",
}

# DocPlan 有、PageDocument 没有的键。输出前剥掉，别把方案的「草稿痕迹」带进库里。
PLAN_ONLY_KEYS: Final[tuple[str, ...]] = (
    "intent",
    "reason",
    "source",
    "validation",
    "notes",
    "documents",
)

_HEX_COLOR: Final[re.Pattern[str]] = re.compile(r"^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")
_ID_SAFE: Final[re.Pattern[str]] = re.compile(r"[^A-Za-z0-9_-]")


@dataclass
class DocumentResult:
    """补齐结果。``notes`` 是「动了什么」，``errors`` 是「自检没过哪些」。"""

    document: dict[str, Any]
    notes: list[str] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        return not self.errors


# ---------------------------------------------------------------------------
# 小工具
# ---------------------------------------------------------------------------
def _text(value: Any, limit: int) -> str:
    """一律拿到一个长度可控的字符串，None / 数字 / 对象都不许漏过去。"""
    if value is None:
        return ""
    if not isinstance(value, str):
        value = str(value)
    return value.strip()[:limit]


def _bool(value: Any, fallback: bool = False) -> bool:
    """``bool("false")`` 是 True —— 这个坑不兜住，hidden 会全部翻转。"""
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return bool(value)
    if isinstance(value, str):
        lowered = value.strip().lower()
        if lowered in ("1", "true", "yes", "y", "on"):
            return True
        if lowered in ("0", "false", "no", "n", "off", ""):
            return False
    return fallback


def _num(value: Any, fallback: float, low: float, high: float) -> float:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return fallback
    if number != number or number in (float("inf"), float("-inf")):   # NaN / inf
        return fallback
    return max(low, min(high, number))


def _color(value: Any, fallback: str) -> str:
    if isinstance(value, str):
        candidate = value.strip()
        if not candidate.startswith("#"):
            candidate = "#" + candidate
        if _HEX_COLOR.match(candidate):
            return candidate.lower()
    return fallback


def _id_or_new(value: Any, prefix: str) -> str:
    """保留模型给的 id（幂等的前提），不合法就新生成一个。"""
    if isinstance(value, str):
        cleaned = _ID_SAFE.sub("", value.strip())[:64]
        if cleaned:
            return cleaned
    return catalog.new_id(prefix)


def slugify(title: str | None) -> str:
    """标题 → 合法 slug。**逐条照抄后端 ``Ids.slugify``**，两边产出必须一致。

    规则（对应 server/.../support/Ids.java:53-76）：
      1. NFD 规范化后剥掉音标（``Café`` → ``cafe``）；
      2. 非 a-z0-9 一律变连字符、收边；
      3. 超 48 位截断再去尾连字符；
      4. 空的 → ``activity-<6位>``；
      5. **纯数字**加 ``activity-`` 前缀（否则「2026 秋季招新」只剩 ``2026``，没辨识度）；
      6. 不足 3 位 → 补 ``-<4位>``。

    为什么必须和后端一致：同一份标题在两边算出不同 slug，发布时会把草稿里的链接换掉，
    用户把二维码发出去了才发现指向另一个地址。
    """
    normalized = unicodedata.normalize("NFD", title or "")
    normalized = "".join(char for char in normalized if not unicodedata.combining(char))
    candidate = re.sub(r"[^a-z0-9]+", "-", normalized.lower()).strip("-")
    if len(candidate) > 48:
        candidate = candidate[:48].rstrip("-")
    if not candidate:
        return "activity-" + _slug_token(6)
    if re.fullmatch(r"[0-9]+", candidate):
        candidate = "activity-" + candidate
    if len(candidate) < 3:
        candidate = candidate + "-" + _slug_token(4)
    return candidate


# 后端 Ids.randomToken 的字母表：刻意去掉了 l / o / 0 / 1，避免手抄链接时看错
_SLUG_ALPHABET: Final[str] = "abcdefghijkmnpqrstuvwxyz23456789"


def _slug_token(length: int) -> str:
    return "".join(secrets.choice(_SLUG_ALPHABET) for _ in range(length))


def _slug(value: Any) -> str:
    if not isinstance(value, str):
        return ""
    candidate = value.strip().lower()
    return candidate if SLUG_PATTERN.match(candidate) else ""


# ---------------------------------------------------------------------------
# 各部件补齐
# ---------------------------------------------------------------------------
def normalize_theme(raw: Any) -> dict[str, Any]:
    source = raw if isinstance(raw, dict) else {}
    theme: dict[str, Any] = {
        "primary": _color(source.get("primary"), DEFAULT_THEME["primary"]),
        "accent": _color(source.get("accent"), DEFAULT_THEME["accent"]),
        "bg": _color(source.get("bg"), DEFAULT_THEME["bg"]),
        "text": _color(source.get("text"), DEFAULT_THEME["text"]),
        # schema 的上下界：radius 0..64、fontScale 0.5..2、spacing 0..3
        "radius": _num(source.get("radius"), DEFAULT_THEME["radius"], 0, 64),
        "fontScale": _num(source.get("fontScale"), DEFAULT_THEME["fontScale"], 0.5, 2),
        "spacing": _num(source.get("spacing"), DEFAULT_THEME["spacing"], 0, 3),
    }
    button_style = source.get("buttonStyle")
    if button_style in BUTTON_STYLES:
        theme["buttonStyle"] = button_style
    else:
        theme["buttonStyle"] = DEFAULT_THEME["buttonStyle"]
    font = source.get("font")
    theme["font"] = font if font in FONTS else DEFAULT_THEME["font"]
    return theme


def normalize_meta(raw: Any) -> dict[str, Any]:
    source = raw if isinstance(raw, dict) else {}
    try:
        capacity = max(0, int(source.get("capacity") or 0))
    except (TypeError, ValueError):
        capacity = 0
    return {
        "org": _text(source.get("org"), 60),
        "startAt": _text(source.get("startAt"), 60),
        "place": _text(source.get("place"), 60),
        "deadline": _text(source.get("deadline"), 60),
        "capacity": capacity,
        "contact": _text(source.get("contact"), 60),
    }


def normalize_style(raw: Any) -> dict[str, Any]:
    source = raw if isinstance(raw, dict) else {}
    align = source.get("align")
    return {
        "bg": _text(source.get("bg"), 40),
        "paddingX": _num(source.get("paddingX"), 0, 0, 64),
        "paddingY": _num(source.get("paddingY"), 0, 0, 64),
        "radius": _num(source.get("radius"), 0, 0, 64),
        # 空串不在 enum 里，会直接把整份文档卡成 422 —— 后端 DocumentRepair 也踩过这个坑
        "align": align if align in ALIGNS else "left",
    }


def _normalize_options(raw: Any, fallback: list[str]) -> list[str]:
    if isinstance(raw, list):
        options = [_text(item, 40) for item in raw if isinstance(item, (str, int, float))]
        options = [item for item in options if item]
        if len(options) >= 2:
            return options[:MAX_OPTIONS]
    return list(fallback)


def _normalize_items(raw: Any, fallback: list[dict[str, Any]]) -> list[dict[str, Any]]:
    if not isinstance(raw, list) or not raw:
        return [dict(item) for item in fallback]

    items: list[dict[str, Any]] = []
    for entry in raw[:MAX_ITEMS_PER_CARD]:
        if not isinstance(entry, dict):
            continue
        icon = entry.get("icon")
        items.append(
            {
                "id": _id_or_new(entry.get("id"), "it"),
                "icon": icon if icon in catalog.INFO_ICONS else "time",
                "label": _text(entry.get("label"), 20),
                "value": _text(entry.get("value"), 80) or "待补充",
            }
        )
    return items or [dict(item) for item in fallback]


def _normalize_field(raw: Any, index: int) -> dict[str, Any] | None:
    if not isinstance(raw, dict):
        return None
    field_type = raw.get("type")
    if field_type not in catalog.FIELD_TYPES:
        field_type = "text"
    options = _normalize_options(raw.get("options"), [])
    if field_type in ("select", "radio", "checkbox") and len(options) < 2:
        options = ["选项一", "选项二"]
    return {
        "id": _id_or_new(raw.get("id"), "f"),
        "label": _text(raw.get("label"), 40) or f"字段 {index}",
        "type": field_type,
        "required": _bool(raw.get("required"), False),
        "placeholder": _text(raw.get("placeholder"), 80),
        "options": options,
    }


def _normalize_fields(raw: Any, fallback: list[dict[str, Any]]) -> list[dict[str, Any]]:
    if not isinstance(raw, list) or not raw:
        return [dict(entry) for entry in fallback]

    fields: list[dict[str, Any]] = []
    for index, entry in enumerate(raw[:MAX_FIELDS_PER_FORM], start=1):
        normalized = _normalize_field(entry, index)
        if normalized is not None:
            fields.append(normalized)
    return fields or [dict(entry) for entry in fallback]


def _normalize_action(raw: Any) -> dict[str, Any]:
    """按钮动作。字符串写法（"submit"）按语义翻译成对象，别让它落库后渲染不出来。"""
    if isinstance(raw, str):
        kind = "submit" if raw.strip().lower() in ("submit", "提交") else "page"
        return {"type": kind, "pageId": "", "url": "", "targetRole": "form"}
    if not isinstance(raw, dict):
        return {"type": "page", "pageId": "", "url": "", "targetRole": "form"}

    kind = raw.get("type")
    if kind not in catalog.BUTTON_ACTIONS:
        kind = "submit" if str(raw.get("type") or "").strip().lower() == "submit" else "page"
    action: dict[str, Any] = {
        "type": kind,
        "pageId": _text(raw.get("pageId"), 64),
        "url": _text(raw.get("url"), 2000),
    }
    target_role = raw.get("targetRole")
    if isinstance(target_role, str) and target_role.strip():
        action["targetRole"] = target_role.strip()
    elif kind == "page" and not action["pageId"]:
        action["targetRole"] = "form"
    return action


def normalize_props(block_type: str, raw: Any) -> dict[str, Any]:
    """按组件类型补齐 props：模型给的优先，缺的用 catalog 默认值垫底。"""
    source = raw if isinstance(raw, dict) else {}
    defaults = catalog.default_props(block_type, {})

    props: dict[str, Any] = dict(source)
    for key, value in defaults.items():
        if props.get(key) is None:
            props[key] = value

    if block_type == "nav":
        props["title"] = _text(props.get("title"), 40) or "活动标题"
        props["right"] = _text(props.get("right"), 20)
        props["showBack"] = _bool(props.get("showBack"), True)

    elif block_type == "banner":
        props["title"] = _text(props.get("title"), 60) or defaults["title"]
        props["subtitle"] = _text(props.get("subtitle"), 120) or defaults.get("subtitle", "")
        props["image"] = _text(props.get("image"), 2000)
        props["height"] = int(_num(props.get("height"), 220, 80, 640))

    elif block_type in ("heading", "text"):
        props["text"] = _text(props.get("text"), 2000) or defaults["text"]
        if block_type == "heading":
            size = props.get("size")
            props["size"] = size if size in catalog.HEADING_SIZES else "lg"
            props["sub"] = _text(props.get("sub"), 80)
        else:
            size = props.get("size")
            props["size"] = size if size in catalog.TEXT_SIZES else "md"

    elif block_type == "image":
        props["url"] = _text(props.get("url"), 2000)
        props["caption"] = _text(props.get("caption"), 80)
        ratio = props.get("ratio")
        props["ratio"] = ratio if ratio in catalog.IMAGE_RATIOS else "4:3"

    elif block_type == "infoCard":
        props["title"] = _text(props.get("title"), 40) or "活动信息"
        props["items"] = _normalize_items(props.get("items"), defaults.get("items", []))

    elif block_type == "button":
        props["text"] = _text(props.get("text"), 24) or "立即报名"
        variant = props.get("variant")
        props["variant"] = variant if variant in catalog.BUTTON_VARIANTS else "primary"
        props["full"] = _bool(props.get("full"), True)
        props["action"] = _normalize_action(props.get("action"))

    elif block_type == "form":
        props["title"] = _text(props.get("title"), 40) or "报名信息"
        props["buttonText"] = _text(props.get("buttonText"), 20) or "提交报名"
        props["successHint"] = _text(props.get("successHint"), 80) or defaults.get("successHint", "")
        props["fields"] = _normalize_fields(props.get("fields"), defaults.get("fields", []))

    elif block_type in ("radio", "checkbox"):
        props["question"] = _text(props.get("question"), 60) or defaults["question"]
        props["options"] = _normalize_options(props.get("options"), defaults.get("options", []))
        props["required"] = _bool(props.get("required"), block_type == "radio")
        props["hint"] = _text(props.get("hint"), 80)
        if block_type == "checkbox":
            props["max"] = int(_num(props.get("max"), 0, 0, MAX_OPTIONS))

    elif block_type == "qrcode":
        props["url"] = _text(props.get("url"), 2000)
        props["label"] = _text(props.get("label"), 40) or "扫码查看活动详情"
        props["size"] = int(_num(props.get("size"), 132, 80, 240))

    elif block_type == "divider":
        props["label"] = _text(props.get("label"), 40)

    elif block_type == "countdown":
        props["target"] = _text(props.get("target"), 40)
        props["label"] = _text(props.get("label"), 40) or "距离报名截止"
        props["doneText"] = _text(props.get("doneText"), 40) or "报名已截止"

    return props


def normalize_block(raw: Any, notes: list[str]) -> dict[str, Any] | None:
    """一个组件。类型不合法直接丢——前端认不出它，留着只会白屏。"""
    if not isinstance(raw, dict):
        notes.append("丢弃了一个不是对象的组件")
        return None

    block_type = raw.get("type") or raw.get("component") or raw.get("kind")
    if not catalog.is_block_type(block_type):
        notes.append(f"丢弃未知组件类型：{block_type!r}")
        return None

    props_raw = raw.get("props")
    if not isinstance(props_raw, dict):
        props_raw = raw.get("properties")
        if isinstance(props_raw, dict):
            notes.append(f"{block_type} 用了 properties 键，已改名为 props")
        else:
            props_raw = {}
            notes.append(f"补 {block_type}.props")

    return {
        "id": _id_or_new(raw.get("id"), block_type),
        "type": block_type,
        "props": normalize_props(block_type, props_raw),
        "style": normalize_style(raw.get("style")),
        "hidden": _bool(raw.get("hidden"), False),
    }


def infer_role(name: str, index: int) -> str:
    """页面没写 role 时按名字猜，猜不出来再看位置。和前端 ``inferRole`` 同一套判据。"""
    if any(word in name for word in ("报名", "表单", "填写", "登记")):
        return "form"
    if any(word in name for word in ("成功", "完成", "提交后", "谢谢")):
        return "success"
    if index == 1:
        return "home"
    return "content"


def normalize_page(raw: Any, index: int, notes: list[str]) -> dict[str, Any] | None:
    if not isinstance(raw, dict):
        notes.append(f"丢弃了第 {index} 页（不是对象）")
        return None

    name = _text(raw.get("name"), PAGE_NAME_MAX) or _text(raw.get("title"), PAGE_NAME_MAX)
    if not name:
        name = f"页面 {index}"
        notes.append(f"补第 {index} 页的 name")

    role = raw.get("role")
    if role not in catalog.PAGE_ROLES:
        role = infer_role(name, index)
        notes.append(f"第 {index} 页 role 不合法，推断为 {role}")

    blocks_raw = raw.get("blocks")
    if not (isinstance(blocks_raw, list) and blocks_raw):
        blocks_raw = raw.get("components")
        if isinstance(blocks_raw, list) and blocks_raw:
            notes.append(f"第 {index} 页用了 components 键，已改名为 blocks")
        else:
            blocks_raw = []

    blocks: list[dict[str, Any]] = []
    for entry in blocks_raw[:MAX_BLOCKS_PER_PAGE]:
        block = normalize_block(entry, notes)
        if block is not None:
            blocks.append(block)

    page: dict[str, Any] = {
        "id": _id_or_new(raw.get("id"), "page"),
        "name": name,
        "role": role,
        "blocks": blocks,
    }
    background = raw.get("bg")
    if isinstance(background, str) and background.strip():
        page["bg"] = background.strip()[:40]
    return page


def _ensure_single_home(pages: list[dict[str, Any]], notes: list[str]) -> None:
    """必须恰好一个 home。0 个 → 第一页顶上；多个 → 后面几个降成 content。

    后端 ``DocumentRepair`` 只保证「至少一个」，多出来的会在公开页里抢入口。
    """
    homes = [page for page in pages if page["role"] == "home"]
    if not homes:
        pages[0]["role"] = "home"
        notes.append("没有 home 页，已把第 1 页设为 home")
        return
    for page in homes[1:]:
        page["role"] = "content"
        notes.append(f"「{page['name']}」是重复的 home，已降为 content")


def _link_buttons(pages: list[dict[str, Any]], notes: list[str]) -> None:
    """按钮只有 targetRole、没有 pageId 时，按角色找到真实页面 id。

    前端预览会自己按 role 兜底，所以「看着能点」；但文档一旦落库被别处消费，
    只有 pageId 才是准的。规则与后端 ``DocumentRepair.findPageIdByRole`` 一致：
    找不到目标角色就退到第一页。
    """
    first_page = pages[0]["id"]
    by_role: dict[str, str] = {}
    for page in pages:
        by_role.setdefault(page["role"], page["id"])

    touched = 0
    for page in pages:
        for block in page["blocks"]:
            if block["type"] != "button":
                continue
            action = block["props"].get("action")
            if not isinstance(action, dict) or action.get("type") != "page":
                continue
            if action.get("pageId"):
                continue
            target_role = action.get("targetRole") or "form"
            action["pageId"] = by_role.get(target_role, first_page)
            touched += 1
    if touched:
        notes.append(f"补了 {touched} 个跳转按钮的 pageId")


def _normalize_published(raw: Any) -> dict[str, Any] | None:
    if not isinstance(raw, dict):
        return None
    url = _text(raw.get("url"), 2000)
    slug = _slug(raw.get("slug"))
    if not url or not slug:
        return None
    published: dict[str, Any] = {"url": url, "slug": slug}
    at = raw.get("at")
    if isinstance(at, (int, float)) and not isinstance(at, bool):
        published["at"] = int(at)
    return published


# ---------------------------------------------------------------------------
# 校验：不出门就自己先过一遍，别让后端替我们 422
# ---------------------------------------------------------------------------
def validate_document(document: Any) -> list[str]:
    """按 ``page-document.schema.json`` 的硬约束自检。返回空列表 = 合法。"""
    errors: list[str] = []
    if not isinstance(document, dict):
        return ["$ 必须是一个对象"]

    title = document.get("title")
    if not isinstance(title, str) or not title.strip():
        errors.append("title 必须是非空字符串")
    elif len(title) > TITLE_MAX:
        errors.append(f"title 超长（{len(title)} > {TITLE_MAX}）")

    slug = document.get("slug")
    if slug is not None and (not isinstance(slug, str) or not SLUG_PATTERN.match(slug)):
        errors.append("slug 必须匹配 ^[a-z0-9]+(-[a-z0-9]+)*$")

    theme = document.get("theme")
    if theme is not None:
        if not isinstance(theme, dict):
            errors.append("theme 必须是对象")
        else:
            for key in ("primary", "accent", "bg", "text"):
                if not isinstance(theme.get(key), str):
                    errors.append(f"theme.{key} 必须是字符串")
            radius = theme.get("radius")
            if radius is not None and not (isinstance(radius, (int, float)) and 0 <= radius <= 64):
                errors.append("theme.radius 必须在 0..64")
            font_scale = theme.get("fontScale")
            if font_scale is not None and not (isinstance(font_scale, (int, float)) and 0.5 <= font_scale <= 2):
                errors.append("theme.fontScale 必须在 0.5..2")
            spacing = theme.get("spacing")
            if spacing is not None and not (isinstance(spacing, (int, float)) and 0 <= spacing <= 3):
                errors.append("theme.spacing 必须在 0..3")
            button_style = theme.get("buttonStyle")
            if button_style is not None and button_style not in BUTTON_STYLES:
                errors.append(f"theme.buttonStyle 只能是 {BUTTON_STYLES}")
            font = theme.get("font")
            if font is not None and font not in FONTS:
                errors.append(f"theme.font 只能是 {FONTS}")

    meta = document.get("meta")
    if meta is not None:
        if not isinstance(meta, dict):
            errors.append("meta 必须是对象")
        else:
            capacity = meta.get("capacity")
            if capacity is not None and not (
                isinstance(capacity, int) and not isinstance(capacity, bool) and capacity >= 0
            ):
                errors.append("meta.capacity 必须是非负整数")

    for key in ("createdAt", "updatedAt"):
        value = document.get(key)
        if value is not None and not isinstance(value, (int, str)):
            errors.append(f"{key} 必须是整数或字符串")

    published = document.get("published")
    if published is not None:
        if not isinstance(published, dict) or not isinstance(published.get("url"), str) \
                or not isinstance(published.get("slug"), str):
            errors.append("published 要么是 null，要么带 url 和 slug 两个字符串")

    pages = document.get("pages")
    if not isinstance(pages, list) or not pages:
        errors.append("pages 至少一页")
        return errors
    if len(pages) > MAX_PAGES:
        errors.append(f"页面太多（{len(pages)} > {MAX_PAGES}）")

    for page_index, page in enumerate(pages):
        path = f"pages[{page_index}]"
        if not isinstance(page, dict):
            errors.append(f"{path} 必须是对象")
            continue
        if not isinstance(page.get("id"), str) or not page.get("id"):
            errors.append(f"{path}.id 必须是非空字符串")
        name = page.get("name")
        if name is not None and (not isinstance(name, str) or len(name) > PAGE_NAME_MAX):
            errors.append(f"{path}.name 必须是不超过 {PAGE_NAME_MAX} 字的字符串")
        role = page.get("role")
        if role is not None and role not in catalog.PAGE_ROLES:
            errors.append(f"{path}.role 只能是 {catalog.PAGE_ROLES}")

        blocks = page.get("blocks")
        if not isinstance(blocks, list):
            errors.append(f"{path}.blocks 必须是数组")
            continue
        if len(blocks) > MAX_BLOCKS_PER_PAGE:
            errors.append(f"{path}.blocks 超过 {MAX_BLOCKS_PER_PAGE} 个")

        for block_index, block in enumerate(blocks):
            bpath = f"{path}.blocks[{block_index}]"
            if not isinstance(block, dict):
                errors.append(f"{bpath} 必须是对象")
                continue
            if not isinstance(block.get("id"), str) or not block.get("id"):
                errors.append(f"{bpath}.id 必须是非空字符串")
            if block.get("type") not in catalog.BLOCK_TYPES:
                errors.append(f"{bpath}.type 不在受控组件白名单里")
            if not isinstance(block.get("props"), dict):
                errors.append(f"{bpath}.props 必须是对象")
            style = block.get("style")
            if style is not None:
                if not isinstance(style, dict):
                    errors.append(f"{bpath}.style 必须是对象")
                elif style.get("align") is not None and style.get("align") not in ALIGNS:
                    errors.append(f"{bpath}.style.align 只能是 {ALIGNS}")
            if block.get("hidden") is not None and not isinstance(block.get("hidden"), bool):
                errors.append(f"{bpath}.hidden 必须是布尔值")

    return errors


# ---------------------------------------------------------------------------
# JSON 抽取：和前端 extractJson、后端 AiService.extractJson 同一套括号配对
# ---------------------------------------------------------------------------
def extract_json_object(text: str) -> Any | None:
    """从模型回复里抠第一个完整 JSON 对象，允许前后有解释文字或 ``` 包裹。"""
    if not text:
        return None
    cleaned = re.sub(r"```json", "```", text, flags=re.IGNORECASE).replace("```", "")
    start = cleaned.find("{")
    if start < 0:
        return None

    depth = 0
    in_string = False
    escaped = False
    for index in range(start, len(cleaned)):
        char = cleaned[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                try:
                    return json.loads(cleaned[start : index + 1])
                except ValueError:
                    return None
    return None


# ---------------------------------------------------------------------------
# 主入口
# ---------------------------------------------------------------------------
def _coerce_payload(raw: Any) -> dict[str, Any] | None:
    """接受四种形态：文档对象、``{document:{...}}`` 外壳、裸 pages 数组、JSON 字符串。"""
    if isinstance(raw, str):
        raw = extract_json_object(raw)
    if isinstance(raw, list):
        return {"pages": raw}
    if not isinstance(raw, dict):
        return None
    inner = raw.get("document")
    if isinstance(inner, dict) and isinstance(inner.get("pages"), list):
        return inner
    return raw


def to_page_document(
    raw: Any,
    *,
    title: str | None = None,
    slug: str | None = None,
    doc_id: str | None = None,
    now_ms: int | None = None,
) -> DocumentResult | None:
    """把任意形态的候选稿修成完整 PageDocument。

    修不出来（没有页面、页面全空）返回 ``None``；修出来了但自检不过，
    返回 ``DocumentResult`` 且 ``errors`` 非空——调用方自己决定是报错还是退回原样。
    """
    payload = _coerce_payload(raw)
    if payload is None:
        return None

    pages_raw = payload.get("pages")
    if not isinstance(pages_raw, list) or not pages_raw:
        return None

    notes: list[str] = []
    pages: list[dict[str, Any]] = []
    for index, item in enumerate(pages_raw[:MAX_PAGES], start=1):
        page = normalize_page(item, index, notes)
        if page is not None:
            pages.append(page)

    if not pages or not any(page["blocks"] for page in pages):
        return None

    _ensure_single_home(pages, notes)
    _link_buttons(pages, notes)

    resolved_title = (
        _text(payload.get("title"), TITLE_MAX)
        or _text(title, TITLE_MAX)
        or "未命名活动"
    )
    resolved_slug = (
        _slug(payload.get("slug"))
        or _slug(slug)
        or slugify(resolved_title)
    )

    now = int(now_ms if now_ms is not None else time.time() * 1000)
    existing_created = payload.get("createdAt")
    created_at = (
        existing_created
        if isinstance(existing_created, int) and not isinstance(existing_created, bool)
        else now
    )

    document: dict[str, Any] = {
        "id": doc_id or _id_or_new(payload.get("id"), "doc"),
        "title": resolved_title,
        "slug": resolved_slug,
        "theme": normalize_theme(payload.get("theme")),
        "meta": normalize_meta(payload.get("meta")),
        "pages": pages,
        "createdAt": created_at,
        "updatedAt": now,
        "published": _normalize_published(payload.get("published")),
    }
    for key in PLAN_ONLY_KEYS:
        document.pop(key, None)

    return DocumentResult(document=document, notes=notes, errors=validate_document(document))


def looks_like_plan(payload: Any) -> bool:
    """只有「每一页都带 blocks/components 数组」才算方案。

    判据故意收窄：免得普通问答里随手写的示例 JSON 也被升级成文档。
    """
    if not isinstance(payload, dict):
        return False
    pages = payload.get("pages")
    if not isinstance(pages, list) or not pages:
        return False
    for page in pages:
        if not isinstance(page, dict):
            return False
        if not isinstance(page.get("blocks") if "blocks" in page else page.get("components"), list):
            return False
    return True


def upgrade_reply(
    reply: str,
    *,
    title: str | None = None,
    slug: str | None = None,
) -> tuple[str, list[str], dict[str, Any] | None]:
    """如果这段回复里是一份方案，就换成完整的 PageDocument。

    不是方案（普通问答、散文、被截断的半个 JSON）→ 原样返回，一个字都不动。
    返回 ``(新回复, 补齐说明, 文档或 None)``。
    """
    payload = extract_json_object(reply)
    if not looks_like_plan(payload):
        return reply, [], None

    result = to_page_document(payload, title=title, slug=slug)
    if result is None or not result.ok:
        return reply, [], None

    return json.dumps(result.document, ensure_ascii=False), result.notes, result.document

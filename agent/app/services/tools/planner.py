"""产品级工具：出方案、校验方案。

为什么这件事必须由工具做，而不是让模型直接吐 JSON：
模型每次吐的组件名都可能有新花样（"card" / "信息卡片" / "InfoCard"），
落进前端就是一个白块。工具里写死 13 个受控组件，模型只能选，不能造。

这也是「Agent 与工作流」那 8 分真正想看到的东西：
工具调用驱动产品动作，而不是工具调完了、页面还是前端自己拼的。
"""

from __future__ import annotations

import json
from typing import Any, Final

from langchain_core.tools import tool

from app.core.config import get_settings
from app.services import block_catalog as catalog

# ---------------------------------------------------------------------------
# 场景模板：只定结构，不定文案。文案由模型按知识库规范填。
# ---------------------------------------------------------------------------

SCENE_PAGES: dict[str, list[tuple[str, str, tuple[str, ...]]]] = {
    "recruit": [
        ("首页", "home", ("nav", "banner", "infoCard", "text", "button")),
        ("了解我们", "content", ("heading", "text", "image", "heading", "text", "button")),
        ("报名", "form", ("nav", "heading", "form")),
        ("提交成功", "success", ("nav", "heading", "text", "qrcode")),
    ],
    "signup": [
        ("首页", "home", ("nav", "banner", "infoCard", "text", "button")),
        ("活动详情", "content", ("heading", "text", "infoCard", "divider", "text", "button")),
        ("报名", "form", ("nav", "heading", "form", "countdown")),
        ("提交成功", "success", ("nav", "heading", "text", "qrcode")),
    ],
    "vote": [
        ("首页", "home", ("nav", "banner", "text", "heading", "text", "button")),
        ("投票", "form", ("nav", "heading", "radio", "form")),
        ("投票成功", "success", ("nav", "heading", "text", "qrcode")),
    ],
    "checkin": [
        ("首页", "home", ("nav", "banner", "infoCard", "text", "countdown", "button")),
        ("今日打卡", "form", ("nav", "heading", "form")),
        ("打卡成功", "success", ("nav", "heading", "text", "qrcode")),
    ],
    # ---- 心理社团（心屿）----
    # 和普通活动的区别只有一条，但每条都硬：第一屏不放表单、说明页写清边界、
    # 成功页必须带求助入口（下面 build_plan 会自动补）。
    "wellbeing": [
        ("首页", "home", ("nav", "banner", "infoCard", "text", "button")),
        ("这场怎么进行", "content", ("heading", "text", "divider", "heading", "text", "button")),
        ("报名", "form", ("nav", "heading", "text", "form")),
        ("报名成功", "success", ("nav", "heading", "text", "qrcode")),
    ],
    "group": [
        ("首页", "home", ("nav", "banner", "infoCard", "text", "button")),
        ("这场怎么进行", "content", ("heading", "text", "heading", "text", "button")),
        ("报名", "form", ("nav", "heading", "text", "form")),
        ("报名成功", "success", ("nav", "heading", "text", "qrcode")),
    ],
    "treehole": [
        ("首页", "home", ("nav", "banner", "text", "button")),
        ("写一句", "form", ("nav", "heading", "text", "form")),
        ("已经放进去了", "success", ("nav", "heading", "text")),
    ],
}

# 这几个场景要额外挂求助入口；别的一律不加（普通招新页挂热线很奇怪）
WELLBEING_SCENES: Final[frozenset[str]] = frozenset({"wellbeing", "group", "treehole"})

# 顺序有意义：normalize_scene 会按这个顺序做子串匹配，
# 所以「心理」必须排在「打卡」「报名」前面，否则「心理打卡」会被判成 checkin。
SCENE_ALIAS = {
    # 心理社团优先
    "心理": "wellbeing",
    "心晴": "wellbeing",
    "情绪": "wellbeing",
    "自我关怀": "wellbeing",
    "正念": "wellbeing",
    "减压": "wellbeing",
    "舒压": "wellbeing",
    "wellbeing": "wellbeing",
    "团辅": "group",
    "团体": "group",
    "工作坊": "group",
    "group": "group",
    "树洞": "treehole",
    "倾诉": "treehole",
    "留言墙": "treehole",
    "treehole": "treehole",
    # 通用活动场景
    "招新": "recruit",
    "纳新": "recruit",
    "recruit": "recruit",
    "报名": "signup",
    "活动": "signup",
    "signup": "signup",
    "投票": "vote",
    "评选": "vote",
    "vote": "vote",
    "打卡": "checkin",
    "checkin": "checkin",
}

# 按钮指向哪个页面角色：首页的按钮跳表单，表单里的按钮提交
BUTTON_TARGET: dict[str, dict[str, str]] = {
    "home": {"type": "page", "pageId": "", "url": "", "targetRole": "form"},
    "content": {"type": "page", "pageId": "", "url": "", "targetRole": "form"},
    "form": {"type": "submit", "pageId": "", "url": ""},
    "success": {"type": "none", "pageId": "", "url": ""},
}


def normalize_scene(scene: str) -> str:
    key = (scene or "").strip()
    if key in SCENE_PAGES:
        return key
    if key in SCENE_ALIAS:
        return SCENE_ALIAS[key]
    for alias, target in SCENE_ALIAS.items():
        if alias in key:
            return target
    return "signup"


def care_card_props() -> dict[str, Any]:
    """求助入口。用现成的 infoCard 表达，**不新增组件类型**。

    为什么不加一个 helpCard：加一个 block 类型要改三端 7 个文件
    （schema 的 enum、DocumentRepair、前端 BlockView / Inspector / blockCatalog、
    agent 的 block_catalog），为一张信息卡不值得。infoCard 的 contact 图标
    本来就够用，而且这样三端一行都不用改。

    号码来自配置（CRISIS_*），各校上线前自己换——写死学活 305 那种是编的。
    """
    settings = get_settings()
    return {
        "title": "如果现在不太好受",
        "items": [
            {
                "id": catalog.new_id("it"),
                "icon": "contact",
                "label": "心理中心",
                "value": settings.crisis_center_hint,
            },
            {
                "id": catalog.new_id("it"),
                "icon": "contact",
                "label": settings.crisis_hotline_name,
                "value": settings.crisis_hotline,
            },
            {
                "id": catalog.new_id("it"),
                "icon": "contact",
                "label": "紧急情况",
                "value": settings.crisis_emergency,
            },
        ],
    }


def build_plan(scene: str, meta: dict[str, Any]) -> dict[str, Any]:
    """按场景拼一份 DocPlan。前端 normalizePlan 能直接吃。"""
    scene_key = normalize_scene(scene)
    title = str(meta.get("title") or "").strip()

    base_meta = dict(meta)
    base_meta["title"] = title
    if title:
        base_meta.setdefault("subtitle", "")

    pages: list[dict[str, Any]] = []
    for page_name, role, block_types in SCENE_PAGES[scene_key]:
        blocks: list[dict[str, Any]] = []
        for block_type in block_types:
            props = catalog.default_props(block_type, base_meta)
            if block_type == "button":
                props["action"] = dict(BUTTON_TARGET.get(role, BUTTON_TARGET["home"]))
                if role == "success":
                    props["text"] = "回到首页"
                    props["variant"] = "soft"
            blocks.append({"type": block_type, "props": props})
        # 心理类活动的成功页自动挂求助入口：这是内容要求，不是设计建议
        if role == "success" and scene_key in WELLBEING_SCENES:
            blocks.append({"type": "infoCard", "props": care_card_props()})
        pages.append({"name": page_name, "role": role, "blocks": blocks})

    route = " → ".join(name for name, _, _ in SCENE_PAGES[scene_key])
    return {
        "intent": f"draft_page_plan:{scene_key}",
        "reason": f"按「{scene_key}」场景的标准结构生成：{route}",
        "title": title,
        "meta": {
            "org": base_meta.get("org") or "",
            "startAt": base_meta.get("startAt") or "",
            "place": base_meta.get("place") or "",
            "deadline": base_meta.get("deadline") or "",
            "capacity": int(base_meta.get("capacity") or 0),
            "contact": base_meta.get("contact") or "",
        },
        "pages": pages,
        "source": "agent",
    }


# ---------------------------------------------------------------------------
# 校验
# ---------------------------------------------------------------------------
def _err(target: list[dict[str, str]], path: str, message: str) -> None:
    target.append({"path": path, "message": message})


def validate_plan(plan: dict[str, Any]) -> dict[str, Any]:
    """结构校验。规则很硬：前端渲染不了的东西，在这里就拦下来。"""
    errors: list[dict[str, str]] = []
    warnings: list[dict[str, str]] = []

    if not isinstance(plan, dict):
        return {"ok": False, "errors": [{"path": "$", "message": "方案必须是 JSON 对象"}], "warnings": []}

    pages = plan.get("pages")
    if not isinstance(pages, list) or not pages:
        return {"ok": False, "errors": [{"path": "pages", "message": "缺少 pages 数组"}], "warnings": []}
    if len(pages) > 8:
        _err(errors, "pages", f"页面太多（{len(pages)} 个），移动端最多 8 页")

    roles: list[str] = []
    for pi, page in enumerate(pages):
        ppath = f"pages[{pi}]"
        if not isinstance(page, dict):
            _err(errors, ppath, "页面必须是对象")
            continue
        role = page.get("role")
        if role not in catalog.PAGE_ROLES:
            _err(errors, f"{ppath}.role", f"role 只能是 {list(catalog.PAGE_ROLES)}")
        else:
            roles.append(role)
        if not str(page.get("name") or "").strip():
            _err(errors, f"{ppath}.name", "页面必须有名字")

        blocks = page.get("blocks")
        if not isinstance(blocks, list) or not blocks:
            _err(errors, f"{ppath}.blocks", "页面至少要有一个组件")
            continue
        if len(blocks) > 20:
            _err(errors, f"{ppath}.blocks", f"组件太多（{len(blocks)} 个），一页最多 20 个")

        seen_types: dict[str, int] = {}
        for bi, block in enumerate(blocks):
            bpath = f"{ppath}.blocks[{bi}]"
            if not isinstance(block, dict):
                _err(errors, bpath, "组件必须是对象")
                continue
            block_type = block.get("type")
            if not catalog.is_block_type(block_type):
                _err(errors, f"{bpath}.type", f"未知组件 {block_type!r}，只能是 {list(catalog.BLOCK_TYPES)}")
                continue
            seen_types[block_type] = seen_types.get(block_type, 0) + 1
            props = block.get("props") if isinstance(block.get("props"), dict) else {}
            _validate_block(block_type, props, bpath, errors, warnings)

        if seen_types.get("nav", 0) > 1:
            _err(errors, f"{ppath}.blocks", "一页只能有一个导航栏")
        if seen_types.get("form", 0) > 1:
            _err(errors, f"{ppath}.blocks", "一页只能有一个表单")
        if seen_types.get("banner", 0) > 1:
            _err(errors, f"{ppath}.blocks", "一页只能有一个 Banner")

    if roles.count("home") != 1:
        _err(errors, "pages", f"必须恰好有一个 role=home 的页面（当前 {roles.count('home')} 个）")
    if "form" not in roles:
        _warnings_add(warnings, "pages", "没有表单页，用户无法报名")
    if "success" not in roles:
        _warnings_add(warnings, "pages", "没有成功页，提交后没有反馈")

    return {
        "ok": not errors,
        "errors": errors,
        "warnings": warnings,
        "summary": f"{len(errors)} 个错误 / {len(warnings)} 个提醒",
    }


def _warnings_add(target: list[dict[str, str]], path: str, message: str) -> None:
    target.append({"path": path, "message": message})


def _validate_block(
    block_type: str,
    props: dict[str, Any],
    path: str,
    errors: list[dict[str, str]],
    warnings: list[dict[str, str]],
) -> None:
    for key in catalog.REQUIRED_PROPS.get(block_type, ()):
        if key not in props or props.get(key) in (None, "", []):
            _err(errors, f"{path}.props.{key}", f"{block_type} 缺少必填属性 {key}")

    if block_type == "infoCard":
        items = props.get("items")
        if not isinstance(items, list) or not items:
            _err(errors, f"{path}.props.items", "信息卡片至少要有一行")
        else:
            for ii, item in enumerate(items):
                if not isinstance(item, dict):
                    _err(errors, f"{path}.props.items[{ii}]", "信息行必须是对象")
                    continue
                if not str(item.get("label") or "").strip():
                    _err(errors, f"{path}.props.items[{ii}].label", "信息行缺少 label")
                if str(item.get("icon") or "") not in catalog.INFO_ICONS:
                    _warnings_add(warnings, f"{path}.props.items[{ii}].icon", f"icon 不在 {list(catalog.INFO_ICONS)} 里，会被替换")
                value = str(item.get("value") or "")
                if value in ("待补充", ""):
                    _warnings_add(warnings, f"{path}.props.items[{ii}].value", "这一行还是「待补充」，发布前要补")

    if block_type == "button":
        action = props.get("action")
        if not isinstance(action, dict):
            _err(errors, f"{path}.props.action", "按钮必须有 action 对象")
        else:
            action_type = action.get("type")
            if action_type not in catalog.BUTTON_ACTIONS:
                _err(errors, f"{path}.props.action.type", f"action.type 只能是 {list(catalog.BUTTON_ACTIONS)}")
            if action_type == "page" and not (action.get("pageId") or action.get("targetRole")):
                _err(errors, f"{path}.props.action", "跳转按钮必须给 pageId 或 targetRole，否则点了没反应")
            if action_type == "url" and not str(action.get("url") or "").strip():
                _err(errors, f"{path}.props.action.url", "外链按钮必须有 url")

    if block_type == "form":
        fields = props.get("fields")
        if not isinstance(fields, list) or not fields:
            _err(errors, f"{path}.props.fields", "表单至少要有一个字段")
        else:
            if len(fields) > 12:
                _err(errors, f"{path}.props.fields", f"字段太多（{len(fields)} 个），最多 12 个")
            required_count = 0
            for fi, field in enumerate(fields):
                fpath = f"{path}.props.fields[{fi}]"
                if not isinstance(field, dict):
                    _err(errors, fpath, "字段必须是对象")
                    continue
                if not str(field.get("label") or "").strip():
                    _err(errors, f"{fpath}.label", "字段缺少 label")
                field_type = str(field.get("type") or "")
                if field_type not in catalog.FIELD_TYPES:
                    _err(errors, f"{fpath}.type", f"字段类型只能是 {list(catalog.FIELD_TYPES)}")
                if field_type in ("select", "radio", "checkbox"):
                    options = field.get("options")
                    if not isinstance(options, list) or len(options) < 2:
                        _err(errors, f"{fpath}.options", "选择类字段至少要两个选项")
                if field.get("required"):
                    required_count += 1
            if required_count > 4:
                _warnings_add(warnings, f"{path}.props.fields", f"必填 {required_count} 个，超过 4 个提交率会明显下降")

    if block_type in ("radio", "checkbox"):
        options = props.get("options")
        if not isinstance(options, list) or len(options) < 2:
            _err(errors, f"{path}.props.options", "选项至少要两个")

    if block_type == "countdown" and not str(props.get("target") or "").strip():
        _warnings_add(warnings, f"{path}.props.target", "倒计时没有截止时间，会一直显示 0")

    if block_type == "qrcode" and not str(props.get("url") or "").strip():
        _warnings_add(warnings, f"{path}.props.url", "二维码 url 为空时会自动用发布链接，属正常")


# ---------------------------------------------------------------------------
# 工具
# ---------------------------------------------------------------------------
@tool
def list_block_types() -> str:
    """列出页面能用的全部受控组件（13 种）及每种组件必填的属性。

    开始设计页面前先调用它，不要凭记忆写组件名——写错了前端渲染不出来。
    """
    return json.dumps(catalog.catalog_digest(), ensure_ascii=False, indent=2)


@tool
def draft_page_plan(
    scene: str,
    title: str = "",
    start_at: str = "",
    place: str = "",
    org: str = "",
    deadline: str = "",
    capacity: int = 0,
    contact: str = "",
) -> str:
    """按场景生成一份标准的页面结构方案（多页面 + 受控组件），返回可直接渲染的 JSON。

    scene 取：
      wellbeing（心理活动/心晴分享会）/ group（团体辅导）/ treehole（匿名树洞）
      recruit（招新）/ signup（报名）/ vote（投票）/ checkin（打卡）

    心理类的三个场景会自动在成功页挂一张「求助入口」信息卡，这是内容要求，别删。
    用户没提供的字段就留空，不要自己编。生成后建议再用 validate_page_plan 校验一遍。
    """
    meta = {
        "title": title,
        "startAt": start_at,
        "place": place,
        "org": org,
        "deadline": deadline,
        "capacity": capacity,
        "contact": contact,
    }
    plan = build_plan(scene, meta)
    check = validate_plan(plan)
    plan["validation"] = check
    return json.dumps(plan, ensure_ascii=False, indent=2)


@tool
def validate_page_plan(plan_json: str) -> str:
    """校验一份页面方案能不能被前端渲染：组件名、必填属性、按钮跳转、表单字段、页面角色。

    参数是方案 JSON 字符串。返回 {ok, errors, warnings}，errors 非空就必须改，别硬发。
    """
    try:
        plan = json.loads(plan_json)
    except ValueError as exc:
        return json.dumps(
            {"ok": False, "errors": [{"path": "$", "message": f"不是合法 JSON：{exc}"}], "warnings": []},
            ensure_ascii=False,
        )
    return json.dumps(validate_plan(plan), ensure_ascii=False, indent=2)

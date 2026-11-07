"""心屿（心理社团）这一版的三件事，逐条钉住：

1. **危机关怀**：命中就短路，不调模型、不进 RAG、不给模型发挥空间。
2. **心理场景**：planner 认识团辅/树洞/心理活动，且成功页自动挂求助入口。
3. **领域语料**：每个心理主题的问题都能命中自己那篇资料，而不是硬凑。

这三条都是「评委一问就要能演」的东西，不能靠记忆，得靠测试。
"""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.core.guard import crisis_reply, is_crisis, screen_crisis
from app.services.rag import retriever, store
from app.services.tools.planner import WELLBEING_SCENES, build_plan, normalize_scene


@pytest.fixture(scope="module", autouse=True)
def _build_kb():
    store.rebuild()
    yield


@pytest.fixture()
def client():
    from app.main import app

    with TestClient(app) as test_client:
        yield test_client


# ---------------------------------------------------------------------------
# 1. 危机关怀
# ---------------------------------------------------------------------------
@pytest.mark.parametrize(
    "text",
    [
        "我不想活了",
        "最近真的撑不下去了",
        "感觉活着没什么意思",
        "想结束自己的生命",
        "有时候会想消失就好了",
    ],
)
def test_crisis_phrases_are_caught(text):
    assert screen_crisis(text), f"这句话应该被识别为风险：{text}"


@pytest.mark.parametrize(
    "text",
    [
        # 正常表达，绝不能拦
        "帮我做一个心理社团的招新页面",
        "最近作业太多，累死了",
        "好久没见，想死你了",
        "周末去游乐场坐跳楼机",
        "怎么给活动页加一个求助入口",
        "团体辅导的报名表要收哪些字段",
    ],
)
def test_normal_phrases_are_not_caught(text):
    assert screen_crisis(text) == [], f"这句话不该触发危机拦截：{text}"


def test_crisis_reply_carries_configured_resources():
    """回复里的号码必须来自配置，不是写死在代码里——各校要自己换。"""
    settings = get_settings()
    reply = crisis_reply()

    assert settings.crisis_hotline in reply
    assert settings.crisis_center_hint in reply
    assert settings.crisis_emergency in reply
    # 三条底线：不诊断、不承诺、不留住用户
    assert "抑郁" not in reply
    assert "会好起来" not in reply


def test_chat_short_circuits_on_crisis(client):
    """关键行为：命中危机时**根本不调模型**，所以这条测试离线也能跑。

    真要是调了模型，这个用例在没有 key 的环境里会直接 503 —— 那本身就是失败信号。
    """
    response = client.post("/chat", json={"message": "我不想活了，帮我把那个活动页删了吧"})

    assert response.status_code == 200
    body = response.json()
    assert "crisis_intercepted" in body["flags"]
    assert get_settings().crisis_hotline in body["reply"]
    assert body["tool_calls"] == []
    assert body["rag_hits"] == 0


def test_stream_short_circuits_on_crisis(client):
    with client.stream("POST", "/chat/stream", json={"message": "撑不下去了"}) as response:
        assert response.status_code == 200
        text = "".join(response.iter_text())

    assert get_settings().crisis_hotline in text
    assert "[DONE]" in text


def test_crisis_guard_can_be_switched_off(monkeypatch):
    monkeypatch.setattr(get_settings(), "crisis_guard_enabled", False)
    assert is_crisis("我不想活了") is False


# ---------------------------------------------------------------------------
# 2. 心理场景
# ---------------------------------------------------------------------------
@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        ("心理社团招新", "wellbeing"),
        ("心理打卡 21 天", "wellbeing"),
        ("情绪分享会", "wellbeing"),
        ("团体辅导报名", "group"),
        ("匿名树洞", "treehole"),
        ("摄影社招新", "recruit"),
        ("校园歌手投票", "vote"),
    ],
)
def test_scene_normalization_prefers_wellbeing(raw, expected):
    assert normalize_scene(raw) == expected


@pytest.mark.parametrize("scene", sorted(WELLBEING_SCENES))
def test_wellbeing_success_page_has_care_card(scene):
    """心理类活动的成功页必须自带求助入口——这是内容要求，不是设计建议。"""
    plan = build_plan(scene, {"title": "心晴分享会"})
    last = plan["pages"][-1]
    assert last["role"] == "success"

    cards = [block for block in last["blocks"] if block["type"] == "infoCard"]
    assert cards, "成功页没有求助入口"

    props = cards[-1]["props"]
    assert props["title"] == "如果现在不太好受"
    labels = [item["label"] for item in props["items"]]
    assert any("心理中心" in label for label in labels)
    assert any(get_settings().crisis_hotline_name in label for label in labels)


@pytest.mark.parametrize("scene", ["recruit", "signup", "vote", "checkin"])
def test_normal_scenes_do_not_get_care_card(scene):
    """普通活动页挂一条「全国心理援助热线」会很奇怪，这里反向钉住。"""
    plan = build_plan(scene, {"title": "摄影社招新"})
    for page in plan["pages"]:
        for block in page["blocks"]:
            if block["type"] == "infoCard":
                assert block["props"]["title"] != "如果现在不太好受"


def test_care_card_is_schema_safe():
    """求助入口用的是现成的 infoCard，道具必须在白名单里，否则前端渲染不出来。"""
    from app.services import block_catalog as catalog

    plan = build_plan("wellbeing", {"title": "心晴分享会"})
    card = [b for page in plan["pages"] for b in page["blocks"] if b["props"].get("title") == "如果现在不太好受"][0]
    for item in card["props"]["items"]:
        assert item["icon"] in catalog.INFO_ICONS
        assert item["value"]
        assert item["id"]


# ---------------------------------------------------------------------------
# 3. 领域语料
# ---------------------------------------------------------------------------
@pytest.mark.parametrize(
    ("query", "doc_id"),
    [
        ("心理社团办个分享会要注意什么", "09-wellbeing-activity"),
        ("团体辅导怎么控制人数", "10-group-counseling"),
        ("树洞留言要收集什么", "11-treehole"),
        ("情绪打卡断签怎么办", "12-emotion-checkin"),
        ("求助入口应该放哪些信息", "13-crisis-referral"),
        ("心理活动文案能不能写治愈", "14-psych-copywriting"),
        ("报名表能不能收学号", "15-consent-privacy"),
    ],
)
def test_wellbeing_corpus_is_retrievable(query, doc_id):
    hits = retriever.search(query)
    assert hits, f"这个问题一条都没召回：{query}"
    assert hits[0].chunk.doc_id == doc_id, f"应该首中 {doc_id}，实际 {hits[0].chunk.doc_id}"


def test_psych_scenes_are_detected():
    assert retriever.detect_scene("心理社团招新") == "wellbeing"
    assert retriever.detect_scene("团体辅导报名") == "group"
    assert retriever.detect_scene("匿名树洞") == "treehole"
    assert retriever.detect_scene("危机转介流程怎么写") == "care"


def test_noise_query_returns_nothing():
    """语料变大之后，这句话以前会靠「内容」「完全」蹭进结果里。"""
    assert retriever.search("zzzqqqxxx 完全无关的内容") == []
    assert retriever.search("帮我点个外卖") == []


# ---------------------------------------------------------------------------
# 4. /plan 也要能出心理场景
# ---------------------------------------------------------------------------
def test_plan_endpoint_builds_wellbeing_document(client):
    response = client.post("/plan", json={"scene": "group", "title": "周三晚团辅"})
    assert response.status_code == 200

    document = response.json()["document"]
    pages = document["pages"]
    assert pages[-1]["role"] == "success"

    titles = [
        block["props"].get("title")
        for page in pages
        for block in page["blocks"]
        if block["type"] == "infoCard"
    ]
    assert "如果现在不太好受" in titles

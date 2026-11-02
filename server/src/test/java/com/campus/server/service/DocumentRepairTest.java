package com.campus.server.service;

import com.campus.server.common.PageDocumentValidator;
import com.campus.server.support.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DocumentRepair 的单测。
 *
 * <p>为什么值得单独测：agent 的输出是不确定的，修复器是唯一挡在
 * 「脏数据入库」和「用户点采用 422」之间的东西。这里把见过的畸形形态全部固化成用例，
 * 以后 agent 换模型 / 改提示词，靠这组用例兜住。
 *
 * <p>每个用例都额外跑一遍 {@link PageDocumentValidator} —— 修复器的唯一验收标准就是
 * 「修完之后 schema 校验能过」。
 */
class DocumentRepairTest {

    private final DocumentRepair repair = new DocumentRepair();
    private final PageDocumentValidator validator = new PageDocumentValidator();

    private JsonNode json(String raw) {
        try {
            return JsonUtil.mapper().readTree(raw);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** 修复 + 校验，返回修好的文档。修不出来就断言失败。 */
    private JsonNode repairAndValidate(String raw) {
        DocumentRepair.RepairResult result = repair.repair(json(raw), "光影摄影社 · 2026 秋季招新", "photo-2026");
        assertNotNull(result, "修复结果不应为 null");
        try {
            validator.validateOrThrow(result.document());
        } catch (RuntimeException ex) {
            // 把 schema 的具体报错打出来，否则只知道「不合法」不知道差哪儿
            throw new AssertionError("修复后仍不合法：" + ex.getMessage()
                    + "；修复记录=" + result.notes()
                    + "；文档=" + result.document().toString(), ex);
        }
        return result.document();
    }

    @Test
    @DisplayName("agent 的真实输出（DocPlan，无 id 无 slug）能被修成合法 PageDocument")
    void repairsPlainDocPlan() {
        String plan = """
                {
                  "intent": "社团招新",
                  "reason": "秋季招新需要一个报名页",
                  "title": "光影摄影社 2026 秋季招新",
                  "theme": {"primary": "#0f766e", "accent": "#14b8a6", "bg": "#f8fafc", "text": "#0b1220"},
                  "pages": [
                    {"name": "首页", "role": "home", "blocks": [
                      {"type": "nav", "props": {"title": "光影摄影社"}},
                      {"type": "banner", "props": {"title": "2026 秋季招新", "subtitle": "欢迎加入"}},
                      {"type": "button", "props": {"text": "立即报名", "action": {"type": "page", "targetRole": "form"}}}
                    ]},
                    {"name": "报名", "role": "form", "blocks": [
                      {"type": "form", "props": {"title": "报名信息", "fields": [
                        {"label": "姓名", "type": "text", "required": true}
                      ]}}
                    ]}
                  ]
                }
                """;
        JsonNode doc = repairAndValidate(plan);

        assertNotNull(doc.get("title"));
        assertEquals("photo-2026", doc.get("slug").asText());
        assertNotNull(doc.get("theme").get("primary"));
        assertNotNull(doc.get("meta"), "meta 必须补齐");
        assertEquals(2, doc.get("pages").size());

        // 每一页、每一个组件都必须有 id —— 这正是原来会 422 的原因
        for (JsonNode page : doc.get("pages")) {
            assertFalse(page.get("id").asText().isBlank(), "page.id 不能为空");
            for (JsonNode block : page.get("blocks")) {
                assertFalse(block.get("id").asText().isBlank(), "block.id 不能为空");
                assertNotNull(block.get("props"));
            }
        }

        // 按钮的 targetRole 必须被翻译成真实 pageId，否则点「立即报名」跳不过去
        String formPageId = doc.get("pages").get(1).get("id").asText();
        String buttonPageId = doc.get("pages").get(0).get("blocks").get(2)
                .get("props").get("action").get("pageId").asText();
        assertEquals(formPageId, buttonPageId, "按钮应指向 role=form 的那一页");
    }

    @Test
    @DisplayName("裸数组 / components 键 / 字符串化 JSON 都要能吃下")
    void repairsLooseShapes() {
        // 裸数组
        JsonNode fromArray = repairAndValidate("""
                [{"name": "首页", "role": "home", "components": [
                  {"component": "heading", "properties": {"text": "随便写点"}}
                ]}]
                """);
        assertEquals("heading", fromArray.get("pages").get(0).get("blocks").get(0).get("type").asText());

        // 外层套壳 {document:{...}}
        JsonNode fromShell = repair.repair(json(
                "{\"document\":{\"title\":\"挂在壳里的\",\"pages\":[{\"role\":\"home\",\"blocks\":[]}]}}"),
                "兜底标题", "fallback-slug").document();
        assertEquals("挂在壳里的", fromShell.get("title").asText());
        validator.validateOrThrow(fromShell);

        // 整段被塞进字符串
        JsonNode fromText = repair.repair(JsonUtil.mapper().getNodeFactory()
                .textNode("{\"pages\":[{\"role\":\"home\",\"blocks\":[{\"type\":\"text\",\"props\":{\"text\":\"hi\"}}]}]}"),
                "兜底标题", "fallback-slug").document();
        validator.validateOrThrow(fromText);
    }

    @Test
    @DisplayName("非法组件类型被丢弃，非法 role 被改成 content，缺 home 时把第一页设为 home")
    void dropsIllegalBlocksAndFixesRoles() {
        JsonNode doc = repairAndValidate("""
                {"title":"测试活动","pages":[
                  {"name":"甲","role":"不存在的角色","blocks":[
                    {"type":"notARealType","props":{}},
                    {"type":"carousel","props":{}},
                    {"type":"text","props":{"text":"保留我"}}
                  ]}
                ]}
                """);
        JsonNode page = doc.get("pages").get(0);
        assertEquals(1, page.get("blocks").size(), "两个非法组件应被丢弃");
        assertEquals("text", page.get("blocks").get(0).get("type").asText());
        assertEquals("home", page.get("role").asText(), "没有 home 时第一页要顶上");
    }

    @Test
    @DisplayName("form 缺 fields、infoCard 缺 items、选择类组件选项不够，都给垫底值")
    void fillsDefaultProps() {
        JsonNode doc = repairAndValidate("""
                {"title":"测试活动","pages":[
                  {"role":"home","blocks":[
                    {"type":"form","props":{}},
                    {"type":"infoCard","props":{"title":"活动信息"}},
                    {"type":"radio","props":{"question":"想报哪个组？","options":["摄影组"]}}
                  ]}
                ]}
                """);
        JsonNode blocks = doc.get("pages").get(0).get("blocks");
        assertTrue(blocks.get(0).get("props").get("fields").size() >= 1, "form 必须至少有一个字段");
        assertTrue(blocks.get(1).get("props").get("items").size() >= 1, "infoCard 必须至少有一行");
        assertTrue(blocks.get(2).get("props").get("options").size() >= 2, "单选至少要两项");
    }

    @Test
    @DisplayName("完全没法修的输入返回 null，而不是抛异常")
    void returnsNullForHopelessInput() {
        assertNull(repair.repair(null, "t", "s"));
        assertNull(repair.repair(json("{}"), "t", "s"), "没有 pages");
        assertNull(repair.repair(json("{\"pages\":[]}"), "t", "s"), "pages 是空的");
        assertNull(repair.repair(json("\"不是 JSON\""), "t", "s"), "纯文本");
    }

    @Test
    @DisplayName("同一份输入修两次结果稳定：id 不会每次都变")
    void repairIsIdempotent() {
        String plan = """
                {"title":"稳定测试","pages":[{"role":"home","blocks":[{"type":"text","props":{"text":"x"}}]}]}
                """;
        JsonNode once = repairAndValidate(plan);
        // 第二次把「已修好的」文档再喂进去，不应该再改动任何 id
        DocumentRepair.RepairResult twice = repair.repair(once, "稳定测试", "stable");
        assertNotNull(twice);
        assertEquals(once.get("pages").get(0).get("id").asText(),
                twice.document().get("pages").get(0).get("id").asText());
        assertEquals(once.get("pages").get(0).get("blocks").get(0).get("id").asText(),
                twice.document().get("pages").get(0).get("blocks").get(0).get("id").asText());
    }

    @Test
    @DisplayName("diffSummary 能说清「换掉多少页多少组件」")
    void diffSummaryCountsPagesAndBlocks() {
        JsonNode before = json("""
                {"title":"旧标题","pages":[
                  {"role":"home","blocks":[{"type":"text"},{"type":"banner"}]}
                ]}
                """);
        JsonNode after = json("""
                {"title":"新标题","pages":[
                  {"role":"home","blocks":[{"type":"text"}]},
                  {"role":"form","blocks":[{"type":"form"},{"type":"button"}]}
                ]}
                """);
        String summary = repair.diffSummary(before, after);
        assertTrue(summary.contains("1 页 / 2 个组件"), summary);
        assertTrue(summary.contains("2 页 / 3 个组件"), summary);
        assertTrue(summary.contains("旧标题"), summary);
        assertTrue(summary.contains("新标题"), summary);
    }
}

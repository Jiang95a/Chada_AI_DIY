package com.campus.server.service;

import com.campus.server.dto.Dtos;
import com.campus.server.support.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回看智能体的输出校验。
 *
 * <p>这一层是整个功能的安全阀：模型说什么它管不了，但「什么能出这个接口」由它决定。
 * 所以这里不测"能不能调通模型"，只测**该丢的有没有丢、该拦的有没有拦**。
 *
 * <p>只用纯构造的 facts，不需要 Spring 上下文，也不需要数据库。
 */
class InsightAnalysisServiceTest {

    /** validate 不依赖其它协作者，传 null 即可；只有 objectMapper 会用到。 */
    private final InsightAnalysisService service =
            new InsightAnalysisService(null, null, null, JsonUtil.mapper());

    private static long at(String date) {
        return LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static Dtos.InsightFactsDto facts(Dtos.InsightEventDto... events) {
        return new Dtos.InsightFactsDto(
                "2026-10-01", "2026-10-30", "2026-10-30T00:00:00.000Z",
                2, 1, 2, events.length,
                List.of(), List.of(events), List.of(),
                Map.of("压力", 2L), 0L);
    }

    private static Dtos.InsightFactsDto sampleFacts() {
        return facts(
                new Dtos.InsightEventDto("activity", "garden", "游园会", at("2026-10-12"),
                        "input", List.of("压力"), "期末压力很大", null),
                new Dtos.InsightEventDto("activity", "club", "社团招新", at("2026-10-18"),
                        "input", List.of("表达"), "今天说出来了，轻一点", null));
    }

    private static String okJson() {
        return """
                {
                  "title": "你不是不会说，你是在等一个不被打断的场合",
                  "findings": [
                    {
                      "text": "10/12 你写的是压力，10/18 第一次写「说出来了」。",
                      "evidence": [
                        { "at": "2026-10-12", "activityTitle": "游园会", "quote": "期末压力很大" },
                        { "at": "2026-10-18", "activityTitle": "社团招新", "quote": "今天说出来了，轻一点" }
                      ]
                    }
                  ],
                  "change": "从压力到说出口",
                  "nextStep": "下次活动只带一句话去，不要求自己讲完整。",
                  "question": "如果不用让任何人满意，你最先想试哪件事？",
                  "confidence": "medium",
                  "visualPreset": "window-light"
                }
                """;
    }

    // ---------------------------------------------------------------- 正常路径

    @Test
    void acceptsContractConformantOutput() {
        Dtos.InsightBodyDto body = service.validate(okJson(), sampleFacts(), true);

        assertNotNull(body);
        assertEquals(1, body.findings().size());
        assertEquals(2, body.findings().get(0).evidence().size());
        assertEquals("2026-10-12", body.findings().get(0).evidence().get(0).at());
        // limits 必须由服务端写死，模型改不了
        assertTrue(body.limits().contains("不是心理评估"));
    }

    @Test
    void toleratesCodeFenceAndTrailingProse() {
        String raw = "好的，这是结果：\n```json\n" + okJson() + "\n```\n希望有帮助。";
        Dtos.InsightBodyDto body = service.validate(raw, sampleFacts(), true);
        assertNotNull(body);
    }

    @Test
    void fallsBackOnUnknownConfidenceAndPreset() {
        String raw = okJson().replace("\"medium\"", "\"very-high\"")
                .replace("\"window-light\"", "\"neon-cyber\"");
        Dtos.InsightBodyDto body = service.validate(raw, sampleFacts(), true);

        assertNotNull(body);
        assertEquals("medium", body.confidence());
        assertEquals("window-light", body.visualPreset());
    }

    // ---------------------------------------------------------------- 该丢的

    @Test
    void dropsFindingWithoutEvidence() {
        // 唯一一条发现没有证据 → 整段不合格（宁可不给，也不给一段没依据的话）
        String raw = """
                {"title":"标题","findings":[{"text":"没有证据的结论","evidence":[]}],
                 "change":"","nextStep":"只带一句话去","question":"你想试哪件事？",
                 "confidence":"medium","visualPreset":"window-light"}
                """;
        assertNull(service.validate(raw, sampleFacts(), true));
    }

    @Test
    void keepsFindingsWithEvidenceAndDropsTheRest() {
        String raw = """
                {"title":"标题","findings":[
                  {"text":"没有证据的结论","evidence":[]},
                  {"text":"有证据的结论","evidence":[{"at":"2026-10-12","activityTitle":"游园会"}]}
                ],"change":"","nextStep":"只带一句话去","question":"你想试哪件事？",
                 "confidence":"medium","visualPreset":"window-light"}
                """;
        Dtos.InsightBodyDto body = service.validate(raw, sampleFacts(), true);

        assertNotNull(body);
        assertEquals(1, body.findings().size());
        assertEquals("有证据的结论", body.findings().get(0).text());
    }

    @Test
    void dropsEvidenceWithUnknownDate() {
        String raw = okJson().replace("\"2026-10-12\"", "\"2026-09-01\"");
        Dtos.InsightBodyDto body = service.validate(raw, sampleFacts(), true);

        assertNotNull(body);
        // 编造的日期必须被剔除，只剩真实存在的那一条
        assertEquals(1, body.findings().get(0).evidence().size());
        assertEquals("2026-10-18", body.findings().get(0).evidence().get(0).at());
    }

    @Test
    void dropsEvidenceWithUnknownActivityTitle() {
        String raw = okJson().replace("\"游园会\"", "\"不存在的活动\"");
        Dtos.InsightBodyDto body = service.validate(raw, sampleFacts(), true);

        assertNotNull(body);
        assertEquals(1, body.findings().get(0).evidence().size());
    }

    @Test
    void stripsQuoteThatIsNotFromFacts() {
        String raw = okJson().replace("\"期末压力很大\"", "\"我昨天说我恨这个世界\"");
        Dtos.InsightBodyDto body = service.validate(raw, sampleFacts(), true);

        assertNotNull(body);
        // 编的引文被清空，而不是原样返回
        assertNull(body.findings().get(0).evidence().get(0).quote());
    }

    @Test
    void respectsAllowQuoteFalse() {
        Dtos.InsightBodyDto body = service.validate(okJson(), sampleFacts(), false);

        assertNotNull(body);
        for (Dtos.InsightEvidenceDto evidence : body.findings().get(0).evidence()) {
            assertNull(evidence.quote(), "allowQuote=false 时不允许出现任何原话");
        }
    }

    @Test
    void rejectsDiagnosisWords() {
        String raw = okJson().replace(
                "你不是不会说，你是在等一个不被打断的场合",
                "你有抑郁症的倾向");
        assertNull(service.validate(raw, sampleFacts(), true));
    }

    @Test
    void rejectsAbsolutePersonalityClaim() {
        String raw = okJson().replace(
                "下次活动只带一句话去，不要求自己讲完整。",
                "你总是这样，必须改。");
        assertNull(service.validate(raw, sampleFacts(), true));
    }

    @Test
    void rejectsEmptyFindings() {
        String raw = """
                {"title":"标题","findings":[],"change":"","nextStep":"只带一句话去",
                 "question":"你想试哪件事？","confidence":"medium","visualPreset":"window-light"}
                """;
        assertNull(service.validate(raw, sampleFacts(), true));
    }

    @Test
    void rejectsNonJson() {
        assertNull(service.validate("抱歉，我不太确定。", sampleFacts(), true));
        assertNull(service.validate("", sampleFacts(), true));
        assertNull(service.validate(null, sampleFacts(), true));
    }

    @Test
    void rejectsTruncatedJson() {
        String raw = """
                {"title":"标题","findings":[{"text":"x","evidence":[
                """;
        assertNull(service.validate(raw, sampleFacts(), true));
    }

    // ---------------------------------------------------------------- 提示词

    @Test
    void promptOmitsUserTextWhenQuotingDisabled() {
        Dtos.InsightFactsDto facts = sampleFacts();
        JsonNode withoutQuote = service.factsAsPromptJson(facts, false);
        JsonNode withQuote = service.factsAsPromptJson(facts, true);

        String plain = withoutQuote.toString();
        assertFalse(plain.contains("期末压力很大"), "不允许引用时不能把原文塞进提示词");
        assertTrue(withQuote.toString().contains("期末压力很大"));
    }

    @Test
    void promptCapsSummaryLength() {
        String longText = "压".repeat(400);
        Dtos.InsightFactsDto facts = facts(
                new Dtos.InsightEventDto("activity", "x", "活动", at("2026-10-12"),
                        "input", List.of(), longText, null));
        String prompt = service.factsAsPromptJson(facts, true).toString();
        assertFalse(prompt.contains("压".repeat(200)), "单条素材必须截断，不能整段进提示词");
    }

    @Test
    void systemPromptForbidsDiagnosis() {
        String prompt = service.systemPrompt();
        assertTrue(prompt.contains("不诊断") || prompt.contains("不做诊断"));
        assertTrue(prompt.contains("JSON"));
    }

    @Test
    void extractJsonObjectHandlesFenceAndNoise() {
        JsonNode node = InsightAnalysisService.extractJsonObject("前言\n```json\n{\"a\":{\"b\":1}}\n```\n后记");
        assertNotNull(node);
        assertEquals(1, node.path("a").path("b").asInt());
    }
}

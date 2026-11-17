package com.campus.server.service;

import com.campus.server.dto.Dtos;
import com.campus.server.entity.InsightRunEntity;
import com.campus.server.mapper.InsightRunMapper;
import com.campus.server.security.CurrentUser;
import com.campus.server.support.Ids;
import com.campus.server.support.InsightAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 回看智能体：把「我的轨迹」的事实底稿变成一段可解释的回看。
 *
 * <p><b>它的职责边界（产品文档第 5 节）</b>：只根据 facts 复述与关联，
 * 不做诊断、不做风险分级、不预测、不评判人格。工程上靠三层保证：
 * <ol>
 *   <li><b>前置闸门</b>：0 条 → insufficient_data；危机词 → crisis；两种都不调模型。</li>
 *   <li><b>输出校验</b>：严格 JSON、每条发现必须有证据、日期与活动名必须能在 facts 里找到、
 *       诊断词/绝对化表述直接判不合格。</li>
 *   <li><b>不采信模型的自述</b>：limits 由服务端写死，confidence / visualPreset 走白名单。</li>
 * </ol>
 *
 * <p><b>失败策略</b>：调用或校验失败重试一次，仍失败返回 {@code analysis_error}，
 * 绝不返回半成品——半成品比报错更危险，用户会把它当成结论。
 *
 * <p><b>隐私</b>：请求体里的计数一律不信，facts 由服务端按 token 重新聚合；
 * 表单答案从不进入 facts，也就不可能进入提示词；日志只记状态与条数，不记原文。
 */
@Service
public class InsightAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(InsightAnalysisService.class);

    /** 提示词版本。改提示词必须改这个串，否则缓存与复现记录会指错。 */
    static final String PROMPT_VERSION = "review-v1";

    private static final int MAX_DAYS = 365;
    private static final int DEFAULT_DAYS = 30;
    private static final int PATTERN_MIN_EVENTS = 3;
    private static final int PATTERN_MIN_SPAN_DAYS = 7;
    private static final int MAX_FINDINGS = 3;
    private static final int MAX_EVIDENCE = 3;
    private static final int MAX_PROMPT_EVENTS = 60;
    private static final int MAX_PROMPT_CHARS = 120;

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final long MINUTE_MS = 60_000L;
    private static final int PER_MINUTE = 2;
    private static final int PER_DAY = 10;

    private static final String LIMITS_TEXT = "这是基于你留下记录的回顾，不是心理评估，也不能替代专业帮助。";

    /** 危机词规则先行：不依赖模型判断，也不允许模型参与判断。 */
    private static final Pattern CRISIS = Pattern.compile(
            "(不想活|活不下去|活够了|想死|想去死|不如死了|死了算了|自杀|轻生|结束(自己的)?生命"
                    + "|了结自己|割腕|跳楼|跳下去|吞药|烧炭|自伤|自残|伤害自己"
                    + "|撑不下去|熬不下去|没人在乎|想消失)");

    /** 诊断词与绝对化表述：命中即判不合格（宁可报错，也不放一段像诊断的话出去）。 */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "(抑郁症|焦虑症|心理疾病|人格障碍|确诊|患病|症状|风险等级|风险评估|风险评分"
                    + "|你总是|你永远|你一定|你必然|你根本|毫无疑问)");

    private static final Set<String> CONFIDENCE = Set.of("low", "medium", "high");
    private static final Set<String> VISUAL_PRESETS = Set.of(
            "window-light", "quiet-room", "night-desk", "open-page");

    private final InsightService insightService;
    private final InsightRunMapper runMapper;
    private final InsightAiClient aiClient;
    private final ObjectMapper objectMapper;

    /** 限流：每用户每分钟 2 次、每天 10 次（施工需求 2.5）。进程内足够，单实例部署。 */
    private final Map<String, Deque<Long>> minuteHits = new ConcurrentHashMap<>();
    private final Map<String, int[]> dayHits = new ConcurrentHashMap<>();
    private final Map<String, String> dayKey = new ConcurrentHashMap<>();

    public InsightAnalysisService(InsightService insightService, InsightRunMapper runMapper,
                                  InsightAiClient aiClient, ObjectMapper objectMapper) {
        this.insightService = insightService;
        this.runMapper = runMapper;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    // ================================================================ 主流程

    public Dtos.InsightAnalyzeResponseDto analyze(Dtos.InsightAnalyzeRequest request) {
        String userId = CurrentUser.require().userId();
        rateLimit(userId);

        int days = clampDays(request == null ? null : request.days());
        boolean allowQuote = request == null || request.allowQuote() == null || request.allowQuote();

        // 事实由服务端按 token 重新聚合：前端传什么都改变不了这份数据
        Dtos.InsightFactsDto facts = insightService.facts(days);

        // ---- 闸门一：危机。优先级高于数据门槛 ----
        String crisisKeyword = detectCrisis(facts);
        if (crisisKeyword != null) {
            log.info("回看命中危机规则，跳过模型：user={} days={}", userId, days);
            Dtos.InsightAnalyzeResponseDto response = crisisResponse();
            saveRun(userId, facts, days, allowQuote, "crisis", response);
            return response;
        }

        // ---- 闸门二：零记录 ----
        if (facts.events().isEmpty()) {
            Dtos.InsightAnalyzeResponseDto response = insufficientData(
                    "现在还没有任何记录。先留下一条活动或工具记录，存到 3 条以上再回来看看。");
            saveRun(userId, facts, days, allowQuote, "insufficient_data", response);
            return response;
        }

        // 1~2 条允许分析，但只复述；3 条以上且跨 7 天才允许说「重复 / 变化 / 模式」
        boolean allowPattern = facts.events().size() >= PATTERN_MIN_EVENTS
                && spanDays(facts) >= PATTERN_MIN_SPAN_DAYS;

        // ---- 缓存：同一用户 + 同一 facts 哈希 + 24 小时 ----
        // 哈希里带上 allowQuote：关掉引用的请求不能命中一份带原话的缓存，那是泄漏。
        String factsHash = factsHash(facts, allowQuote);
        InsightRunEntity cached = runMapper.findFresh(userId, factsHash,
                LocalDateTime.now(ZoneOffset.UTC).minus(CACHE_TTL));
        if (cached != null && cached.getResponseJson() != null) {
            Dtos.InsightAnalyzeResponseDto hit = readResponse(cached.getResponseJson());
            if (hit != null) {
                return hit;
            }
        }

        // ---- 闸门三：没配模型 ----
        if (!aiClient.ready()) {
            return failAndSave(userId, facts, days, allowQuote, "还没配置回看模型（AI_API_KEY / AI_MODEL），"
                    + "这段回看暂时生成不了。记录都还在，配好之后可以直接重试。");
        }

        // ---- 调用 + 校验，失败重试一次 ----
        String systemPrompt = systemPrompt();
        String userPrompt = userPrompt(facts, allowQuote, allowPattern);
        String lastError = "模型输出不符合契约";

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String raw = aiClient.complete(systemPrompt, userPrompt);
                Dtos.InsightBodyDto body = validate(raw, facts, allowQuote);
                if (body == null) {
                    lastError = "模型输出不符合契约";
                    log.info("回看第 {} 次校验未通过：user={}", attempt, userId);
                    continue;
                }
                Dtos.InsightAnalyzeResponseDto response = okResponse(body);
                log.info("回看成功：user={} days={} events={} findings={}",
                        userId, days, facts.events().size(), body.findings().size());
                saveRun(userId, facts, days, allowQuote, "ok", response);
                return response;
            } catch (InsightAiClient.AiCallException ex) {
                lastError = ex.getMessage();
                log.warn("回看模型调用失败（第 {} 次）：{}", attempt, ex.getMessage());
            }
        }

        return failAndSave(userId, facts, days, allowQuote,
                "这次没能生成可靠的回顾（" + lastError + "）。你的记录没有丢，稍后重试一次。");
    }

    // ================================================================ 闸门

    /** 危机词只扫 facts 里的 summary / change / tags——这些是用户自己写下的内容。 */
    private String detectCrisis(Dtos.InsightFactsDto facts) {
        for (Dtos.InsightEventDto event : facts.events()) {
            String hit = firstCrisis(event.summary(), event.change(), joinTags(event.tags()));
            if (hit != null) {
                return hit;
            }
        }
        for (Dtos.InsightActivityDto activity : facts.activities()) {
            String hit = firstCrisis(activity.summary(), joinTags(activity.tags()));
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private String firstCrisis(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && CRISIS.matcher(value).find()) {
                return value;
            }
        }
        return null;
    }

    private String joinTags(List<String> tags) {
        return tags == null ? "" : String.join(" ", tags);
    }

    private long spanDays(Dtos.InsightFactsDto facts) {
        if (facts.events().isEmpty()) {
            return 0;
        }
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (Dtos.InsightEventDto event : facts.events()) {
            min = Math.min(min, event.at());
            max = Math.max(max, event.at());
        }
        return (max - min) / (24L * 60 * 60 * 1000) + 1;
    }

    /** 每用户每分钟 2 次、每天 10 次。超限抛 429（BizException 的 RATE_LIMITED）。 */
    private void rateLimit(String userId) {
        long now = System.currentTimeMillis();
        Deque<Long> hits = minuteHits.computeIfAbsent(userId, key -> new ArrayDeque<>());
        synchronized (hits) {
            while (!hits.isEmpty() && now - hits.peekFirst() > MINUTE_MS) {
                hits.pollFirst();
            }
            if (hits.size() >= PER_MINUTE) {
                throw new com.campus.server.common.BizException(
                        com.campus.server.common.ErrorCode.RATE_LIMITED,
                        "回看太频繁了，休息一下再来（每分钟最多 " + PER_MINUTE + " 次）");
            }
            hits.addLast(now);
        }

        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String key = userId + "@" + today;
        int[] counter = dayHits.computeIfAbsent(userId, k -> new int[1]);
        String previousDay = dayKey.put(userId, today);
        synchronized (counter) {
            if (previousDay != null && !previousDay.equals(today)) {
                counter[0] = 0;
            }
            if (counter[0] >= PER_DAY) {
                throw new com.campus.server.common.BizException(
                        com.campus.server.common.ErrorCode.RATE_LIMITED,
                        "今天的回看次数用完了（每天最多 " + PER_DAY + " 次）");
            }
            counter[0] += 1;
        }
        dayKey.put(userId, today);
        if (dayHits.size() > 5000) {
            dayHits.keySet().removeIf(id -> !id.equals(key));
        }
    }

    // ================================================================ 提示词

    String systemPrompt() {
        return """
                你是「回看助手」，不是心理咨询师，也不是聊天陪伴者。
                你只根据给定的 facts 写一段回看，不补充 facts 之外的事实。

                必须遵守：
                1. 只输出一个 JSON 对象，不要 markdown 代码块，不要在 JSON 外写任何字。
                2. 不做诊断、不给风险等级、不预测将来、不评判人格、不使用「你总是/你一定」这类绝对化表述。
                3. 每条 finding 至少带 1 条 evidence；evidence 的 at 用 YYYY-MM-DD，activityTitle 必须与 facts 里的名字逐字一致。
                4. quote 只能从 facts 里该条记录的 summary 或 change 中截取原文，不要改写。
                5. findings 最多 3 条，建议 2 条。
                6. nextStep 只能 1 条，且必须是一个具体行为（不是状态、不承诺效果）。
                7. question 只能 1 条，开放、不诱导。
                8. 数据不足时不要编造模式；只有在被允许时才可以提到重复、变化、模式。

                输出结构（字段名必须一致）：
                {"title":"","findings":[{"text":"","evidence":[{"at":"YYYY-MM-DD","activityTitle":"","quote":""}]}],
                 "change":"","nextStep":"","question":"","confidence":"low|medium|high","visualPreset":"window-light"}
                """;
    }

    String userPrompt(Dtos.InsightFactsDto facts, boolean allowQuote, boolean allowPattern) {
        StringBuilder sb = new StringBuilder();
        sb.append("下面是这个人最近 ").append(daysOf(facts)).append(" 天的记录。\n\n");
        sb.append("可用素材（只有这些，不要编）：\n");
        sb.append(factsAsPromptJson(facts, allowQuote).toPrettyString()).append("\n\n");
        sb.append("约束：\n");
        sb.append("- 活动数 ").append(facts.activityCount())
                .append("，表单提交 ").append(facts.formCount())
                .append("，时间点 ").append(facts.events().size())
                .append("，工具记录 ").append(facts.toolRecordCount()).append("。\n");
        sb.append("- ").append(allowPattern
                        ? "素材足够，可以谨慎地提到重复、变化或模式，但每一条都必须有证据。"
                        : "素材很少（不足 3 条或跨度不到 7 天），只做单点复述，不要声称任何模式。")
                .append('\n');
        sb.append("- ").append(allowQuote
                        ? "本次允许引用用户写下的原话（summary / change），quote 字段照抄原文。"
                        : "本次不允许引用原话，所有 quote 字段留空字符串。")
                .append('\n');
        return sb.toString();
    }

    private int daysOf(Dtos.InsightFactsDto facts) {
        try {
            LocalDate start = LocalDate.parse(facts.periodStart());
            LocalDate end = LocalDate.parse(facts.periodEnd());
            return (int) (end.toEpochDay() - start.toEpochDay()) + 1;
        } catch (Exception ex) {
            return DEFAULT_DAYS;
        }
    }

    /**
     * 把 facts 压成提示词里的素材。
     *
     * <p>三条刻意的限制：
     * <ul>
     *   <li>只取最近 60 个时间点：长了会挤爆上下文，而且更早的细节对「最近这段」没有帮助；</li>
     *   <li>summary / change 每条最多 120 字：防止用户写的一大段原文整段进提示词；</li>
     *   <li>{@code allowQuote=false} 时连 summary / change 都不给——不给它，它就没法引用。</li>
     * </ul>
     */
    ObjectNode factsAsPromptJson(Dtos.InsightFactsDto facts, boolean allowQuote) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("periodStart", facts.periodStart());
        root.put("periodEnd", facts.periodEnd());
        root.put("activityCount", facts.activityCount());
        root.put("formCount", facts.formCount());
        root.put("toolRecordCount", facts.toolRecordCount());
        root.put("eventCount", facts.events().size());

        ArrayNode events = root.putArray("events");
        List<Dtos.InsightEventDto> all = facts.events();
        int from = Math.max(0, all.size() - MAX_PROMPT_EVENTS);
        for (int i = from; i < all.size(); i++) {
            Dtos.InsightEventDto event = all.get(i);
            ObjectNode node = events.addObject();
            node.put("at", dateOf(event.at()));
            node.put("source", event.source());
            node.put("title", safe(event.activityTitle()));
            node.put("kind", safe(event.kind()));
            if (event.tags() != null && !event.tags().isEmpty()) {
                ArrayNode tags = node.putArray("tags");
                event.tags().forEach(tag -> tags.add(safe(tag)));
            }
            if (allowQuote) {
                String summary = cut(safe(event.summary()), MAX_PROMPT_CHARS);
                if (!summary.isEmpty()) {
                    node.put("summary", summary);
                }
                String change = cut(safe(event.change()), MAX_PROMPT_CHARS);
                if (!change.isEmpty()) {
                    node.put("change", change);
                }
            }
        }
        return root;
    }

    // ================================================================ 输出校验

    /**
     * 把模型输出校验成 {@link Dtos.InsightBodyDto}；不合格返回 {@code null}（调用方会重试一次）。
     *
     * <p>校验顺序有意为之：先结构、再证据、最后文案——
     * 结构不对时后面都没意义，先做证据校验能避免在一段没有依据的话上做词表检查。
     */
    Dtos.InsightBodyDto validate(String raw, Dtos.InsightFactsDto facts, boolean allowQuote) {
        JsonNode root = extractJsonObject(raw);
        if (root == null || !root.isObject()) {
            return null;
        }

        String title = cut(safe(root.path("title").asText("")), 80);
        if (title.isEmpty() || FORBIDDEN.matcher(title).find()) {
            return null;
        }

        Map<String, String> knownDates = knownDates(facts);
        Set<String> knownTitles = knownTitles(facts);
        List<String> quotable = quotableTexts(facts);

        List<Dtos.InsightFindingDto> findings = new ArrayList<>();
        JsonNode findingsNode = root.get("findings");
        if (findingsNode != null && findingsNode.isArray()) {
            for (JsonNode node : findingsNode) {
                if (findings.size() >= MAX_FINDINGS) {
                    break;
                }
                String text = cut(safe(node.path("text").asText("")), 300);
                if (text.isEmpty() || FORBIDDEN.matcher(text).find()) {
                    continue;
                }
                List<Dtos.InsightEvidenceDto> evidence = new ArrayList<>();
                JsonNode evidenceNode = node.get("evidence");
                if (evidenceNode != null && evidenceNode.isArray()) {
                    for (JsonNode item : evidenceNode) {
                        if (evidence.size() >= MAX_EVIDENCE) {
                            break;
                        }
                        String at = normalizeDate(safe(item.path("at").asText("")));
                        String activityTitle = cut(safe(item.path("activityTitle").asText("")), 80);
                        if (at == null || !knownDates.containsKey(at) || !knownTitles.contains(activityTitle)) {
                            continue;
                        }
                        String quote = "";
                        if (allowQuote) {
                            quote = cut(safe(item.path("quote").asText("")), 120);
                            if (!quote.isEmpty() && !isQuotable(quote, quotable)) {
                                quote = "";
                            }
                        }
                        evidence.add(new Dtos.InsightEvidenceDto(at, activityTitle,
                                quote.isEmpty() ? null : quote));
                    }
                }
                // 没有 evidence 的结论必须删除（产品文档 6.2）
                if (evidence.isEmpty()) {
                    continue;
                }
                findings.add(new Dtos.InsightFindingDto(text, evidence));
            }
        }
        if (findings.isEmpty()) {
            return null;
        }

        String change = cut(safe(root.path("change").asText("")), 200);
        String nextStep = cut(safe(root.path("nextStep").asText("")), 200);
        String question = cut(safe(root.path("question").asText("")), 200);
        if (FORBIDDEN.matcher(change).find() || FORBIDDEN.matcher(nextStep).find()
                || FORBIDDEN.matcher(question).find()) {
            return null;
        }
        if (question.isEmpty()) {
            return null;
        }

        String confidence = safe(root.path("confidence").asText(""));
        if (!CONFIDENCE.contains(confidence)) {
            confidence = "medium";
        }
        String visualPreset = safe(root.path("visualPreset").asText(""));
        if (!VISUAL_PRESETS.contains(visualPreset)) {
            visualPreset = "window-light";
        }

        // limits 不采信模型：由服务端写死，任何模型都改不掉这句话
        return new Dtos.InsightBodyDto(title, findings, change, nextStep, question,
                confidence, visualPreset, LIMITS_TEXT);
    }

    /** 从一段文本里抠第一个完整 JSON 对象，容忍 ```json 包裹与前后解释。 */
    static JsonNode extractJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.replaceAll("```json", "```").replace("```", "");
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    try {
                        return new ObjectMapper().readTree(text.substring(start, i + 1));
                    } catch (Exception ex) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private Map<String, String> knownDates(Dtos.InsightFactsDto facts) {
        Map<String, String> dates = new java.util.LinkedHashMap<>();
        for (Dtos.InsightEventDto event : facts.events()) {
            dates.put(dateOf(event.at()), event.activityTitle());
        }
        return dates;
    }

    private Set<String> knownTitles(Dtos.InsightFactsDto facts) {
        Set<String> titles = new LinkedHashSet<>();
        for (Dtos.InsightEventDto event : facts.events()) {
            if (event.activityTitle() != null && !event.activityTitle().isBlank()) {
                titles.add(event.activityTitle());
            }
        }
        for (Dtos.InsightActivityDto activity : facts.activities()) {
            if (activity.activityTitle() != null && !activity.activityTitle().isBlank()) {
                titles.add(activity.activityTitle());
            }
        }
        return titles;
    }

    private List<String> quotableTexts(Dtos.InsightFactsDto facts) {
        List<String> texts = new ArrayList<>();
        for (Dtos.InsightEventDto event : facts.events()) {
            if (event.summary() != null) {
                texts.add(event.summary());
            }
            if (event.change() != null) {
                texts.add(event.change());
            }
        }
        for (Dtos.InsightActivityDto activity : facts.activities()) {
            if (activity.summary() != null) {
                texts.add(activity.summary());
            }
        }
        return texts;
    }

    /** 引用必须是原文片段：命中任何一条素材即可，防止模型自己编一句「用户说过」。 */
    private boolean isQuotable(String quote, List<String> sources) {
        for (String source : sources) {
            if (source.contains(quote)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeDate(String value) {
        String text = value == null ? "" : value.trim();
        if (text.length() < 10) {
            return null;
        }
        String head = text.substring(0, 10);
        try {
            LocalDate.parse(head);
            return head;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String dateOf(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    // ================================================================ 响应构造

    private Dtos.InsightAnalyzeResponseDto okResponse(Dtos.InsightBodyDto body) {
        return new Dtos.InsightAnalyzeResponseDto("ok", Ids.uuid(), Ids.nowIso(),
                aiClient.modelName(), PROMPT_VERSION, body, null, null);
    }

    private Dtos.InsightAnalyzeResponseDto insufficientData(String message) {
        return new Dtos.InsightAnalyzeResponseDto("insufficient_data", null, Ids.nowIso(),
                null, null, null, message, null);
    }

    /** 危机响应：只给资源，不给分析。资源写死，不给模型任何发挥空间。 */
    private Dtos.InsightAnalyzeResponseDto crisisResponse() {
        List<Dtos.InsightResourceDto> resources = List.of(
                new Dtos.InsightResourceDto("紧急情况", "120 / 110"),
                new Dtos.InsightResourceDto("全国心理援助热线", "12356"));
        return new Dtos.InsightAnalyzeResponseDto("crisis", null, Ids.nowIso(), null, null,
                null, "如果你现在可能伤害自己或别人，请先联系一个可信任的人，或者直接打下面的电话。",
                resources);
    }

    private Dtos.InsightAnalyzeResponseDto failAndSave(String userId, Dtos.InsightFactsDto facts,
                                                       int days, boolean allowQuote, String message) {
        Dtos.InsightAnalyzeResponseDto response = new Dtos.InsightAnalyzeResponseDto(
                "analysis_error", null, Ids.nowIso(), null, null, null, message, null);
        saveRun(userId, facts, days, allowQuote, "analysis_error", response);
        return response;
    }

    /**
     * 输入事实的 sha256。
     *
     * <p>哈希里必须带 {@code allowQuote}：它决定了提示词里有没有用户原话，
     * 同一份 facts 在两种模式下是两次不同的分析。混用会让「关掉引用」的人
     * 命中一份带原话的缓存——那是实打实的隐私泄漏，不是体验问题。
     */
    private String factsHash(Dtos.InsightFactsDto facts, boolean allowQuote) {
        return Ids.sha256(factsAsPromptJson(facts, allowQuote).toString());
    }

    private void saveRun(String userId, Dtos.InsightFactsDto facts, int days, boolean allowQuote,
                         String status, Dtos.InsightAnalyzeResponseDto response) {
        try {
            InsightRunEntity run = new InsightRunEntity();
            run.setId(response.insightId() == null ? Ids.uuid() : response.insightId());
            run.setUserId(userId);
            run.setFactsHash(factsHash(facts, allowQuote));
            run.setDays(days);
            run.setModel(aiClient.modelName());
            run.setPromptVersion(PROMPT_VERSION);
            run.setStatus(status);
            run.setResponseJson(objectMapper.valueToTree(response));
            runMapper.insert(run);
        } catch (Exception ex) {
            // 记录失败不能影响主流程：用户已经拿到结果了
            log.warn("写回看记录失败：{}", ex.getClass().getSimpleName());
        }
    }

    private Dtos.InsightAnalyzeResponseDto readResponse(JsonNode node) {
        try {
            return objectMapper.treeToValue(node, Dtos.InsightAnalyzeResponseDto.class);
        } catch (Exception ex) {
            return null;
        }
    }

    // ================================================================ 小工具

    private static int clampDays(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_DAYS;
        }
        return Math.min(requested, MAX_DAYS);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}

package com.campus.server.service;

import com.campus.server.dto.Dtos;
import com.campus.server.mapper.InsightMapper;
import com.campus.server.mapper.SubmissionMapper;
import com.campus.server.mapper.TraceMapper;
import com.campus.server.security.CurrentUser;
import com.campus.server.support.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 「我的轨迹」的数据底稿。
 *
 * <p>这个服务不调用任何模型，只做三件事：
 * <ol>
 *   <li>按 token 取出当前用户一段时间内的活动轨迹和表单提交事实；</li>
 *   <li>把 trace.payload.history 展开成时间点，给后续分析用；</li>
 *   <li>返回稳定的 DTO，不让智能体直接接触数据库或表单原文。</li>
 * </ol>
 *
 * <p>隐私边界：表单答案不返回，只返回「哪天提交过哪场活动」；
 * 轨迹也只返回摘要、标签、变化，不返回输入原文（原文本来就没有落库）。
 */
@Service
public class InsightService {

    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 365;
    private static final int MAX_TRACES = 500;
    private static final int MAX_FORMS = 500;
    private static final int MAX_EVENTS = 400;

    private final TraceMapper traceMapper;
    private final SubmissionMapper submissionMapper;
    private final InsightMapper insightMapper;
    private final ObjectMapper objectMapper;

    public InsightService(TraceMapper traceMapper, SubmissionMapper submissionMapper,
                          InsightMapper insightMapper, ObjectMapper objectMapper) {
        this.traceMapper = traceMapper;
        this.submissionMapper = submissionMapper;
        this.insightMapper = insightMapper;
        this.objectMapper = objectMapper;
    }

    public Dtos.InsightFactsDto facts(Integer requestedDays) {
        CurrentUser current = CurrentUser.require();
        int days = requestedDays == null || requestedDays <= 0 ? DEFAULT_DAYS : Math.min(requestedDays, MAX_DAYS);

        LocalDate end = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = end.minusDays(days - 1L);
        LocalDateTime since = start.atStartOfDay();
        long sinceMs = since.toInstant(ZoneOffset.UTC).toEpochMilli();

        List<Map<String, Object>> traceRows =
                traceMapper.listByUserSince(current.userId(), sinceMs, MAX_TRACES);
        List<Map<String, Object>> formRows =
                submissionMapper.listMineSince(current.userId(), since, MAX_FORMS);

        List<Dtos.InsightEventDto> events = new ArrayList<>();
        Map<String, ActivityAccumulator> activityMap = new LinkedHashMap<>();
        Map<String, Long> tagCounts = new LinkedHashMap<>();

        for (Map<String, Object> row : traceRows) {
            String slug = text(row.get("public_slug"));
            String title = text(row.get("activity_title"));
            String domain = text(row.get("domain"));
            long updatedAt = number(row.get("updated_at_ms"));
            String summary = text(row.get("summary"));
            JsonNode payload = asJson(row.get("payload"));
            if (payload == null) {
                payload = objectMapper.createObjectNode();
            }

            ActivityAccumulator activity = activityMap.computeIfAbsent(slug,
                    key -> new ActivityAccumulator(key, title, domain, updatedAt, summary));
            activity.touch(updatedAt, summary);

            JsonNode history = payload.get("history");
            if (history != null && history.isArray() && !history.isEmpty()) {
                for (JsonNode item : history) {
                    if (!item.isObject()) {
                        continue;
                    }
                    long at = item.path("at").asLong(updatedAt);
                    List<String> tags = readTags(item.get("tags"));
                    String eventSummary = item.path("summary").asText("");
                    String change = item.path("change").asText("");
                    if (eventSummary.isBlank()) {
                        eventSummary = summary;
                    }
                    events.add(new Dtos.InsightEventDto("activity", slug, title, at,
                            item.path("kind").asText("input"), tags,
                            blankToNull(eventSummary), blankToNull(change)));
                    activity.addTags(tags);
                    countTags(tagCounts, tags);
                }
            } else {
                List<String> tags = readTags(payload.get("tags"));
                String change = textOrNull(payload, "change");
                events.add(new Dtos.InsightEventDto("activity", slug, title, updatedAt,
                        "input", tags, blankToNull(summary), change));
                activity.addTags(tags);
                countTags(tagCounts, tags);
            }
        }

        List<Dtos.InsightFormDto> forms = new ArrayList<>();
        for (Map<String, Object> row : formRows) {
            String slug = text(row.get("public_slug"));
            String title = text(row.get("activity_title"));
            LocalDateTime submittedAt = toDateTime(row.get("submitted_at"));
            String submittedIso = Ids.iso(submittedAt);
            forms.add(new Dtos.InsightFormDto(slug, title, submittedIso, text(row.get("status"))));

            long at = submittedAt == null ? 0L : submittedAt.toInstant(ZoneOffset.UTC).toEpochMilli();
            ActivityAccumulator activity = activityMap.computeIfAbsent(slug,
                    key -> new ActivityAccumulator(key, title, "mental", at, ""));
            activity.touch(at, "");
        }

        events.sort(Comparator.comparingLong(Dtos.InsightEventDto::at));
        if (events.size() > MAX_EVENTS) {
            events = new ArrayList<>(events.subList(events.size() - MAX_EVENTS, events.size()));
        }

        List<Dtos.InsightActivityDto> activities = activityMap.values().stream()
                .sorted(Comparator.comparingLong(ActivityAccumulator::lastAt).reversed())
                .limit(100)
                .map(ActivityAccumulator::toDto)
                .toList();

        List<Map.Entry<String, Long>> sortedTags = new ArrayList<>(tagCounts.entrySet());
        sortedTags.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        Map<String, Long> topTags = new LinkedHashMap<>();
        sortedTags.stream().limit(20).forEach(entry -> topTags.put(entry.getKey(), entry.getValue()));

        long activityCount = insightMapper.countActivities(current.userId(), sinceMs, since);
        long formCount = insightMapper.countForms(current.userId(), since);
        long traceCount = insightMapper.countTraces(current.userId(), sinceMs);

        return new Dtos.InsightFactsDto(
                start.toString(),
                end.toString(),
                Ids.nowIso(),
                activityCount,
                formCount,
                traceCount,
                events.size(),
                activities,
                events,
                forms,
                topTags
        );
    }

    private static void countTags(Map<String, Long> counts, List<String> tags) {
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            counts.merge(tag, 1L, Long::sum);
        }
    }

    private List<String> readTags(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        Set<String> tags = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                tags.add(item.asText());
            } else if (item.isObject()) {
                String content = item.path("content").asText("");
                if (!content.isBlank()) {
                    tags.add(content);
                }
            }
        }
        return new ArrayList<>(tags);
    }

    private JsonNode asJson(Object value) {
        if (value instanceof JsonNode node) {
            return node;
        }
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static long number(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static LocalDateTime toDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.util.Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
        }
        return null;
    }

    private static final class ActivityAccumulator {
        private final String activityId;
        private final String title;
        private final String domain;
        private long lastAt;
        private int eventCount;
        private String summary;
        private final Set<String> tags = new LinkedHashSet<>();

        private ActivityAccumulator(String activityId, String title, String domain,
                                    long lastAt, String summary) {
            this.activityId = activityId;
            this.title = title;
            this.domain = domain;
            this.lastAt = lastAt;
            this.summary = summary;
        }

        private void touch(long at, String nextSummary) {
            this.lastAt = Math.max(this.lastAt, at);
            if (nextSummary != null && !nextSummary.isBlank()) {
                this.summary = nextSummary;
            }
        }

        private void addTags(List<String> nextTags) {
            eventCount += 1;
            for (String tag : nextTags) {
                if (tag != null && !tag.isBlank() && tags.size() < 8) {
                    tags.add(tag);
                }
            }
        }

        private long lastAt() {
            return lastAt;
        }

        private Dtos.InsightActivityDto toDto() {
            return new Dtos.InsightActivityDto(activityId, title, domain, lastAt, eventCount,
                    new ArrayList<>(tags), summary == null ? "" : summary);
        }
    }
}

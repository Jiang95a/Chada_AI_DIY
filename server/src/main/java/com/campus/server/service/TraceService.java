package com.campus.server.service;

import com.campus.server.common.BizException;
import com.campus.server.dto.Dtos;
import com.campus.server.entity.ParticipantTraceEntity;
import com.campus.server.entity.ProjectEntity;
import com.campus.server.mapper.ProjectMapper;
import com.campus.server.mapper.TraceMapper;
import com.campus.server.security.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 参与轨迹（{@code /traces}）。
 *
 * <p>这是「动态参与」的后端落点。前端在活动页按用户输入实时更新轨迹
 * （{@code web/src/services/traceParser.ts} 的关键词规则引擎），
 * 手机会先写 localStorage，登录状态下再同步到这里。
 *
 * <p>三条设计约束，都和「这是心理场景」有关：
 * <ul>
 *   <li><b>只存自己的</b>：owner 一律取 token，请求体里的 userId 直接忽略。
 *       没有任何接口能读别人的轨迹，连组织者也不行——轨迹里是参与者自己写的
 *       「未决问题」「想做出的改变」，比报名答案私密得多。</li>
 *   <li><b>整条覆盖，不做增量合并</b>：前端每次 PUT 的是完整轨迹，
 *       服务端如果自作聪明做字段合并，会出现「前端删掉的标签在服务端复活」这类问题。</li>
 *   <li><b>时间戳以客户端为准</b>：前端用 {@code updatedAt} 判断本地和远端哪个更新
 *       （{@code web/src/stores/trace.ts:181}）。服务端若用自己的时钟改写，
 *       时钟偏差会让用户的本地改动被判定为「更旧」而丢失。</li>
 * </ul>
 */
@Service
public class TraceService {

    private static final int DEFAULT_LIST_LIMIT = 50;
    private static final int MAX_LIST_LIMIT = 200;

    private final TraceMapper traceMapper;
    private final ProjectMapper projectMapper;
    private final ObjectMapper objectMapper;

    public TraceService(TraceMapper traceMapper, ProjectMapper projectMapper, ObjectMapper objectMapper) {
        this.traceMapper = traceMapper;
        this.projectMapper = projectMapper;
        this.objectMapper = objectMapper;
    }

    /** GET /traces?activityId={slug}。没有就返回 null（前端据此显示「只存在这台设备」）。 */
    public Dtos.ParticipantTraceDto get(String slug) {
        CurrentUser current = CurrentUser.require();
        ProjectEntity project = requireProject(slug);
        ParticipantTraceEntity row = traceMapper.find(current.userId(), project.getId());
        if (row == null) {
            return null;
        }
        return toDto(row, slug);
    }

    /** PUT /traces/{activityId}。整条覆盖；返回落库后的样子。 */
    public Dtos.ParticipantTraceDto save(String slug, Dtos.SaveTraceRequest request) {
        CurrentUser current = CurrentUser.require();
        ProjectEntity project = requireProject(slug);

        String domain = normalizeDomain(request.domain());
        long updatedAt = request.updatedAt() == null || request.updatedAt() <= 0
                ? System.currentTimeMillis()
                : request.updatedAt();

        // 回传给前端的 activityId 必须是 slug（前端拿它当 key），
        // 但落库和查询用 projectId —— slug 改名后轨迹不会变孤儿。
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("userId", current.userId());
        payload.put("activityId", slug);
        payload.put("domain", domain);
        payload.put("semester", nullToEmpty(request.semester()));
        payload.put("summary", nullToEmpty(request.summary()));
        putIfPresent(payload, "openQuestion", request.openQuestion());
        putIfPresent(payload, "change", request.change());
        putIfPresent(payload, "promise", request.promise());
        payload.set("tags", arrayOrEmpty(request.tags()));
        payload.set("sessionContext", objectOrEmpty(request.sessionContext()));
        payload.set("notes", arrayOrEmpty(request.notes()));
        payload.put("updatedAt", updatedAt);

        ParticipantTraceEntity entity = new ParticipantTraceEntity();
        entity.setUserId(current.userId());
        entity.setProjectId(project.getId());
        entity.setDomain(domain);
        entity.setSemester(nullToEmpty(request.semester()));
        // summary 列是给列表页用的冗余，和 payload.summary 必须一致
        entity.setSummary(nullToEmpty(request.summary()));
        entity.setPayload(payload);
        entity.setUpdatedAtMs(updatedAt);
        traceMapper.upsert(entity);

        return toDto(traceMapper.find(current.userId(), project.getId()), slug);
    }

    /** GET /traces —— 「我的轨迹」页跨活动列出。 */
    public List<Dtos.TraceListItemDto> list(Integer limit) {
        CurrentUser current = CurrentUser.require();
        int size = limit == null || limit <= 0 ? DEFAULT_LIST_LIMIT : Math.min(limit, MAX_LIST_LIMIT);

        List<Dtos.TraceListItemDto> items = new ArrayList<>();
        for (Map<String, Object> row : traceMapper.listByUser(current.userId(), size)) {
            JsonNode payload = asJson(row.get("payload"));
            if (payload == null) {
                continue;
            }
            JsonNode tags = payload.get("tags");
            int tagCount = tags != null && tags.isArray() ? tags.size() : 0;
            String summary = toStr(row.get("summary"));
            String slug = toStr(row.get("public_slug"));
            // 与前端 list() 同口径：既没总结也没标签的轨迹不进列表，
            // 否则用户点进活动什么都不做也会留下一条空记录（web/src/stores/trace.ts:268）
            if (isBlank(summary) && tagCount == 0) {
                continue;
            }
            items.add(new Dtos.TraceListItemDto(
                    slug,
                    toStr(row.get("activity_title")),
                    toStr(row.get("domain")),
                    toStr(row.get("semester")),
                    summary,
                    tagCount,
                    toLong(row.get("updated_at_ms"))));
        }
        return items;
    }

    /** DELETE /traces/{activityId}。删不存在的也算成功（前端删除是幂等的）。 */
    public void delete(String slug) {
        CurrentUser current = CurrentUser.require();
        ProjectEntity project = requireProject(slug);
        traceMapper.delete(current.userId(), project.getId());
    }

    private ProjectEntity requireProject(String slug) {
        if (isBlank(slug)) {
            throw BizException.invalid("缺少活动标识");
        }
        ProjectEntity project = projectMapper.findByPublicSlug(slug);
        if (project == null) {
            // 活动被删或 slug 写错时给 404，前端 traces.ts 把它当「暂不可用」静默降级到本地
            throw BizException.notFound("活动不存在");
        }
        return project;
    }

    private Dtos.ParticipantTraceDto toDto(ParticipantTraceEntity row, String slug) {
        JsonNode payload = row.getPayload() == null ? objectMapper.createObjectNode() : row.getPayload();
        return new Dtos.ParticipantTraceDto(
                row.getUserId(),
                slug,
                row.getDomain(),
                nullToEmpty(row.getSemester()),
                nullToEmpty(row.getSummary()),
                textOrNull(payload, "openQuestion"),
                textOrNull(payload, "change"),
                textOrNull(payload, "promise"),
                payload.has("tags") ? payload.get("tags") : objectMapper.createArrayNode(),
                payload.has("sessionContext") ? payload.get("sessionContext") : objectMapper.createObjectNode(),
                payload.has("notes") ? payload.get("notes") : objectMapper.createArrayNode(),
                row.getUpdatedAtMs() == null ? 0L : row.getUpdatedAtMs());
    }

    private JsonNode arrayOrEmpty(JsonNode node) {
        return node != null && node.isArray() ? node : objectMapper.createArrayNode();
    }

    private JsonNode objectOrEmpty(JsonNode node) {
        return node != null && node.isObject() ? node : objectMapper.createObjectNode();
    }

    private void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
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
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 只接受 mental / career，其他一律归到 mental。
     * 不报错是因为这是前端枚举，写错不该让用户丢掉整条轨迹；
     * 表格上的 CHECK 约束兜的是「绕过接口直接改库」的情况。
     */
    private String normalizeDomain(String domain) {
        return "career".equals(domain) ? "career" : "mental";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String toStr(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}

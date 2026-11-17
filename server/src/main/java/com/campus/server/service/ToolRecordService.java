package com.campus.server.service;

import com.campus.server.common.BizException;
import com.campus.server.dto.Dtos;
import com.campus.server.entity.ToolRecordEntity;
import com.campus.server.mapper.ToolRecordMapper;
import com.campus.server.security.CurrentUser;
import com.campus.server.support.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具记录（能量牌局 / 三栏）的落库与读取。
 *
 * <p>为什么需要它：这些内容原先只写 localStorage，换设备就没了，
 * 回看智能体也就只能看到活动表单，看不到用户在工具里写下的东西。
 *
 * <p><b>隐私边界（硬要求）</b>：
 * <ul>
 *   <li>userId 一律来自 {@link CurrentUser#require()}，请求体里的任何 userId 字段都不采信；</li>
 *   <li>查询与删除都带 user_id 条件，越权只会拿到空结果，不会泄漏别人一条记录；</li>
 *   <li>不校验登录态以外的任何东西——但这张表的全部接口都在 /api/v1 下，由 AuthInterceptor 拦未登录。</li>
 * </ul>
 */
@Service
public class ToolRecordService {

    private static final int MAX_DAYS = 365;
    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_RECORDS = 500;

    private static final int MAX_TITLE = 160;
    private static final int MAX_SUMMARY = 500;
    private static final int MAX_TAGS = 8;
    private static final int MAX_TAG_LEN = 24;

    private static final Set<String> TYPES = Set.of("energy", "thought");

    private final ToolRecordMapper mapper;
    private final ObjectMapper objectMapper;

    public ToolRecordService(ToolRecordMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 新建一条记录。
     *
     * <p>type 只接受 energy / thought；超出白名单直接 400，
     * 而不是「存下来以后再说」——库里那条 CHECK 约束会拒，报出来的会是 500。
     */
    @Transactional
    public Dtos.ToolRecordDto create(Dtos.ToolRecordCreateRequest request) {
        String userId = CurrentUser.require().userId();
        if (request == null) {
            throw BizException.invalid("缺少请求体");
        }
        String type = trim(request.type());
        if (!TYPES.contains(type)) {
            throw BizException.invalid("type 只能是 energy 或 thought");
        }

        String title = cut(trim(request.title()), MAX_TITLE);
        String summary = cut(trim(request.summary()), MAX_SUMMARY);
        if (title.isEmpty() && summary.isEmpty()) {
            throw BizException.invalid("标题和内容不能同时为空");
        }

        ToolRecordEntity entity = new ToolRecordEntity();
        entity.setId(Ids.uuid());
        entity.setUserId(userId);
        entity.setType(type);
        entity.setTitle(title);
        entity.setSummary(summary);
        entity.setTags(toTagsJson(request.tags()));
        entity.setCreatedAtMs(normalizeTime(request.createdAtMs()));
        mapper.insert(entity);

        return toDto(entity);
    }

    /** 当前用户最近 N 天的记录，按用户设备上的时间倒序。 */
    public List<Dtos.ToolRecordDto> list(Integer requestedDays) {
        String userId = CurrentUser.require().userId();
        int days = requestedDays == null || requestedDays <= 0
                ? DEFAULT_DAYS : Math.min(requestedDays, MAX_DAYS);
        long sinceMs = LocalDate.now(ZoneOffset.UTC)
                .minusDays(days - 1L)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli();

        List<Dtos.ToolRecordDto> out = new ArrayList<>();
        for (ToolRecordEntity entity : mapper.listByUserSince(userId, sinceMs, MAX_RECORDS)) {
            out.add(toDto(entity));
        }
        return out;
    }

    /**
     * 删除。删不到（不是本人的、或不存在）返回 404。
     *
     * <p>故意不区分「不存在」和「不是你的」——否则接口就变成了「这个 id 是否存在」的探测器。
     */
    @Transactional
    public void delete(String id) {
        String userId = CurrentUser.require().userId();
        if (trim(id).isEmpty()) {
            throw BizException.invalid("缺少 id");
        }
        if (mapper.delete(id, userId) == 0) {
            throw BizException.notFound("记录不存在");
        }
    }

    /** 聚合接口用：当前用户最近 N 天的原始记录（不分页，服务内部消费）。 */
    public List<ToolRecordEntity> recentForInsight(String userId, long sinceMs) {
        return mapper.listByUserSince(userId, sinceMs, MAX_RECORDS);
    }

    private Dtos.ToolRecordDto toDto(ToolRecordEntity entity) {
        return new Dtos.ToolRecordDto(
                entity.getId(),
                entity.getType(),
                nullToEmpty(entity.getTitle()),
                nullToEmpty(entity.getSummary()),
                readTags(entity),
                entity.getCreatedAtMs() == null ? 0L : entity.getCreatedAtMs()
        );
    }

    private List<String> readTags(ToolRecordEntity entity) {
        List<String> tags = new ArrayList<>();
        if (entity.getTags() == null || !entity.getTags().isArray()) {
            return tags;
        }
        entity.getTags().forEach(node -> {
            if (node != null && node.isTextual() && !node.asText().isBlank()) {
                tags.add(node.asText());
            }
        });
        return tags;
    }

    private com.fasterxml.jackson.databind.JsonNode toTagsJson(List<String> raw) {
        ArrayNode array = objectMapper.createArrayNode();
        Set<String> seen = new LinkedHashSet<>();
        if (raw != null) {
            for (String item : raw) {
                String tag = cut(trim(item), MAX_TAG_LEN);
                if (!tag.isEmpty() && seen.size() < MAX_TAGS) {
                    seen.add(tag);
                }
            }
        }
        seen.forEach(array::add);
        return array;
    }

    /**
     * 客户端时间戳：没给就用服务器时间；给了就用它，但夹在未来 1 天以内。
     *
     * <p>为什么要夹：用户设备时钟错乱时会写出 2099 年的记录，
     * 那条记录会永远排在时间线最前面，把「最近 30 天」挤满。
     */
    private long normalizeTime(Long raw) {
        long now = System.currentTimeMillis();
        if (raw == null || raw <= 0) {
            return now;
        }
        long upper = now + 24L * 60 * 60 * 1000;
        return Math.min(raw, upper);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 让 InsightService 复用同一份「多少条工具记录」口径。 */
    public long count(String userId, long sinceMs) {
        return mapper.countByUserSince(userId, sinceMs);
    }

    /** 给 InsightService 用：把 tags 读成字符串列表，避免两处各写一遍解析。 */
    public List<String> tagsOf(ToolRecordEntity entity) {
        return readTags(entity);
    }

    /** 给测试与聚合用：把一条实体摊平成 Map，字段名与 facts 里的事件保持一致。 */
    public Map<String, Object> flatten(ToolRecordEntity entity) {
        return Map.of(
                "id", entity.getId(),
                "type", nullToEmpty(entity.getType()),
                "title", nullToEmpty(entity.getTitle()),
                "summary", nullToEmpty(entity.getSummary()),
                "tags", readTags(entity),
                "createdAtMs", entity.getCreatedAtMs() == null ? 0L : entity.getCreatedAtMs()
        );
    }
}

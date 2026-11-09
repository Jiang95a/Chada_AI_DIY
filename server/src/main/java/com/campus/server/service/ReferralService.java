package com.campus.server.service;

import com.campus.server.common.BizException;
import com.campus.server.dto.Dtos;
import com.campus.server.entity.ProjectEntity;
import com.campus.server.entity.ReferralRequestEntity;
import com.campus.server.entity.UserNotificationEntity;
import com.campus.server.mapper.WellbeingMapper;
import com.campus.server.security.CurrentUser;
import com.campus.server.support.Ids;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 转介工单：匿名活动里唯一能把「需要被联系」这件事送出去的口子。
 *
 * <p>设计上刻意做窄：
 * <ul>
 *   <li>只收 3 个字面量字段（是否想被联系、联系方式、希望时间），<b>绝不收 answers</b>，
 *       所以组织者拿到工单也看不到参与者填了些什么；</li>
 *   <li>组织者列表接口不返回 {@code user_id}，只返回工单本身的字段；</li>
 *   <li>状态只有 pending → contacted → closed，不做「已了解内容」这种会诱发窥探的中转态。</li>
 * </ul>
 *
 * <p>超时提醒：待办超过 {@link #OVERDUE_HOURS} 小时未标记「已联系」，前端把它标红。
 * 这是这个模块真正的价值——它证明「匿名」不等于「没有人管」。
 */
@Service
public class ReferralService {

    /** 超过这个小时数还没标记已联系，就标红。 */
    public static final int OVERDUE_HOURS = 24;

    /** 只有这三个状态；其他值一律 400，避免前端写出奇怪的状态机。 */
    private static final Set<String> STATUSES = Set.of("pending", "contacted", "closed");

    private final WellbeingMapper wellbeingMapper;
    private final ProjectService projectService;
    private final NotificationService notificationService;

    public ReferralService(WellbeingMapper wellbeingMapper, ProjectService projectService,
                           NotificationService notificationService) {
        this.wellbeingMapper = wellbeingMapper;
        this.projectService = projectService;
        this.notificationService = notificationService;
    }

    /**
     * 参与者在公开活动页点「希望有人联系我」。
     *
     * <p>不需要登录也能提交：很多参与者正是在匿名档下才会勾这个。
     * 登录信息有就记，没有就留空——反正组织者看不到。
     */
    public Dtos.ReferralReceipt submit(String slug, Dtos.ReferralRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.wantsContact())) {
            throw BizException.invalid("请先勾选「希望有人联系我」");
        }
        ProjectEntity project = projectService.loadPublishedBySlug(slug);

        ReferralRequestEntity row = new ReferralRequestEntity();
        row.setId(Ids.uuid());
        row.setProjectId(project.getId());
        // 匿名也能提交，所以 userId 允许为空；有登录态就顺手记上（只用于回执归属）。
        row.setUserId(CurrentUser.optionalUserId());
        row.setContact(trimToNull(request.contact()));
        row.setPreferredTime(trimToNull(request.preferredTime()));
        row.setStatus("pending");
        wellbeingMapper.insertReferral(row);

        pushOrganizerTodo(project);

        return new Dtos.ReferralReceipt(row.getId(), "pending", Ids.nowIso(),
                "已把你的请求转给活动组织者。他们能看到你的联系方式，但看不到你填写过的其他内容。");
    }

    private void pushOrganizerTodo(ProjectEntity project) {
        try {
            UserNotificationEntity n = new UserNotificationEntity();
            n.setId(Ids.uuid());
            n.setUserId(project.getOwnerId());
            n.setType("referral");
            n.setTitle("「" + project.getTitle() + "」有新的转介待办");
            n.setDetail("有参与者希望被联系。请在 " + OVERDUE_HOURS + " 小时内处理，你只能看到联系方式。");
            n.setTargetPath("/notifications");
            n.setDedupeKey("referral:" + project.getId() + ":" + System.currentTimeMillis() / 1000);
            wellbeingMapper.insertNotificationIgnoreDuplicate(n);
        } catch (RuntimeException ignored) {
            // 通知失败不能影响参与者提交——这条工单本身已经落库了
        }
    }

    /** 组织者待办列表。按状态过滤可选；超过 24 小时未处理的带 pendingHours 供前端标红。 */
    public Map<String, Object> listForOwner(String status, int limit) {
        String ownerId = CurrentUser.require().userId();
        String safeStatus = (status == null || status.isBlank()) ? null : status.trim();
        if (safeStatus != null && !STATUSES.contains(safeStatus)) {
            throw BizException.invalid("status 只能是 pending / contacted / closed");
        }
        int safeLimit = Math.min(Math.max(limit, 1), 200);

        List<Map<String, Object>> rows =
                wellbeingMapper.listReferralsForOwner(ownerId, safeStatus, safeLimit);

        List<Dtos.ReferralTodoDto> items = new ArrayList<>();
        long pendingCount = 0;
        long overdueCount = 0;
        for (Map<String, Object> row : rows) {
            Dtos.ReferralTodoDto dto = toTodo(row);
            items.add(dto);
            if ("pending".equals(dto.status())) {
                pendingCount++;
                if (dto.pendingHours() >= OVERDUE_HOURS) {
                    overdueCount++;
                }
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("pending", pendingCount);
        data.put("overdue", overdueCount);
        data.put("overdueThresholdHours", OVERDUE_HOURS);
        // total 是当前过滤条件下的总数，不是全量
        data.put("total", wellbeingMapper.countReferralsForOwner(ownerId, safeStatus));
        return data;
    }

    /** 组织者标记状态。只能动自己项目下的工单。 */
    public Dtos.ReferralUpdateDto updateStatus(String id, String status) {
        String ownerId = CurrentUser.require().userId();
        String safeStatus = status == null ? "" : status.trim();
        if (!STATUSES.contains(safeStatus)) {
            throw BizException.invalid("status 只能是 pending / contacted / closed");
        }
        int updated = wellbeingMapper.updateReferralStatus(id, ownerId, safeStatus);
        if (updated == 0) {
            throw BizException.notFound("转介待办不存在，或不属于你");
        }
        ReferralRequestEntity row = wellbeingMapper.findReferral(id);
        return new Dtos.ReferralUpdateDto(id, safeStatus,
                row == null || row.getHandledAt() == null ? null : Ids.iso(row.getHandledAt()));
    }

    private Dtos.ReferralTodoDto toTodo(Map<String, Object> row) {
        LocalDateTime createdAt = toDateTime(row.get("created_at"));
        LocalDateTime handledAt = toDateTime(row.get("handled_at"));
        long pendingHours = createdAt == null ? 0
                : Duration.between(createdAt,
                        handledAt == null ? LocalDateTime.now() : handledAt).toHours();
        return new Dtos.ReferralTodoDto(
                toStr(row.get("id")),
                toStr(row.get("activity_title")),
                toStr(row.get("public_slug")),
                toStr(row.get("contact")),
                toStr(row.get("preferred_time")),
                toStr(row.get("status")),
                createdAt == null ? null : createdAt.toString(),
                handledAt == null ? null : handledAt.toString(),
                Math.max(pendingHours, 0));
    }

    private static LocalDateTime toDateTime(Object value) {
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }

    private static String toStr(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

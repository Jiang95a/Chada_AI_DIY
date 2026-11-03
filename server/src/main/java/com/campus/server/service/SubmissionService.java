package com.campus.server.service;

import com.campus.server.common.BizException;
import com.campus.server.common.ErrorCode;
import com.campus.server.dto.Dtos;
import com.campus.server.entity.ActivityConsentEntity;
import com.campus.server.entity.FormSubmissionEntity;
import com.campus.server.entity.MiscEntities.ActivityEventEntity;
import com.campus.server.entity.ProjectEntity;
import com.campus.server.entity.UserNotificationEntity;
import com.campus.server.mapper.MiscMapper;
import com.campus.server.mapper.ProjectMapper;
import com.campus.server.mapper.SubmissionMapper;
import com.campus.server.mapper.WellbeingMapper;
import com.campus.server.security.CurrentUser;
import com.campus.server.support.Ids;
import com.campus.server.support.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 公开页提交 + 组织者查看提交列表。
 *
 * <p>幂等：前端每次进表单生成一个 idempotencyKey（放在请求体里），
 * 后端用 (project_id, idempotency_key) 唯一索引兜底，重复提交返回同一条记录而不是报错。
 */
@Service
public class SubmissionService {

    private final SubmissionMapper submissionMapper;
    private final ProjectMapper projectMapper;
    private final MiscMapper miscMapper;
    private final ProjectService projectService;
    private final DocumentNormalizer normalizer;
    private final PrivacyService privacyService;
    private final ConsentService consentService;
    private final WellbeingMapper wellbeingMapper;

    public SubmissionService(SubmissionMapper submissionMapper, ProjectMapper projectMapper,
                             MiscMapper miscMapper, ProjectService projectService,
                             DocumentNormalizer normalizer, PrivacyService privacyService,
                             ConsentService consentService, WellbeingMapper wellbeingMapper) {
        this.submissionMapper = submissionMapper;
        this.projectMapper = projectMapper;
        this.miscMapper = miscMapper;
        this.projectService = projectService;
        this.normalizer = normalizer;
        this.privacyService = privacyService;
        this.consentService = consentService;
        this.wellbeingMapper = wellbeingMapper;
    }

    // ================================================================ 公开提交

    @Transactional
    public Dtos.SubmissionResult submit(String slug, Dtos.SubmissionRequest request) {
        ProjectEntity project = projectMapper.findByPublicSlug(slug);
        if (project == null || !"published".equals(project.getStatus())) {
            throw BizException.notFound("活动不存在或未发布");
        }

        String status = projectService.publicStatus(project);
        if ("closed".equals(status)) {
            throw new BizException(ErrorCode.SUBMISSION_CLOSED, "活动已关闭，不再接受提交");
        }
        if ("full".equals(status)) {
            throw new BizException(ErrorCode.SUBMISSION_CLOSED, "报名人数已满");
        }

        String pageId = request.pageId();
        String formComponentId = request.formComponentId();
        if (pageId == null || pageId.isBlank() || formComponentId == null || formComponentId.isBlank()) {
            throw BizException.invalid("pageId 和 formComponentId 都不能为空");
        }

        JsonNode publishedDocument = project.getPublishedVersionId() == null
                ? project.getDraftDocument()
                : loadPublishedDocument(project);

        JsonNode answers = request.answers();
        if (answers == null || !answers.isObject()) {
            throw BizException.invalid("answers 必须是对象");
        }

        // 校验必填项（按发布出去的文档，而不是草稿）
        List<Map<String, Object>> required = normalizer.requiredFields(publishedDocument, pageId, formComponentId);
        List<String> missing = new ArrayList<>();
        for (Map<String, Object> field : required) {
            // 前端的答案 key 是 "<blockId>:<fieldId>"（web/src/views/PublicActivityView.vue:65），
            // 依次兜底再试 "<pageId>:<fieldId>" 和裸 fieldId，兼容手写调用和旧数据。
            String fieldId = String.valueOf(field.get("id"));
            JsonNode value = firstPresent(answers,
                    formComponentId + ":" + fieldId,
                    pageId + ":" + fieldId,
                    fieldId);
            boolean empty = value == null || value.isNull()
                    || (value.isTextual() && value.asText().isBlank())
                    || (value.isArray() && value.isEmpty());
            if (empty) {
                missing.add(String.valueOf(field.get("label")));
            }
        }
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.PAGE_DOCUMENT_INVALID,
                    "还有必填项没填", Map.of("missing", missing));
        }

        String rawKey = Ids.idempotencyKey(request.idempotencyKey());
        String idemKey = Ids.scopedIdempotencyKey(project.getId(), rawKey);

        FormSubmissionEntity existing = submissionMapper.findByIdempotencyKey(project.getId(), idemKey);
        if (existing != null) {
            return new Dtos.SubmissionResult(existing.getId(), existing.getStatus(),
                    Ids.iso(existing.getSubmittedAt()));
        }

        // ---- 隐私：服务端判定，不信前端传的 anonymous 就是这个活动的规则 ----
        Dtos.PrivacySettings privacy = privacyService.read(project);
        Dtos.SubmissionPrivacy privacyInput = request.privacy();

        String consentId = null;
        LocalDateTime purgeAfter = null;
        if (Boolean.TRUE.equals(privacy.getConsentRequired())) {
            String rawConsentId = privacyInput == null ? null : privacyInput.consentId();
            if (rawConsentId == null || rawConsentId.isBlank()) {
                // 旧前端没传 consentId：只放行"确实要求同意"之外的场景已在上面的 if 里排除，
                // 所以这里一定缺同意，直接拒绝，不能用"再调一次 consents 就行"来蒙混。
                throw new BizException(ErrorCode.PAGE_DOCUMENT_INVALID,
                        "该活动要求先阅读并同意参与说明", Map.of("missing", List.of("consent")));
            }
            ActivityConsentEntity consent = consentService.requireValid(project.getId(), rawConsentId);
            consentId = consent.getId();
            purgeAfter = consent.getPurgeAfter();
        }

        boolean anonymous = privacyService.resolveAnonymous(privacy, 
                privacyInput == null ? null : privacyInput.anonymous());
        if (purgeAfter == null && privacy.getRetentionDays() != null && privacy.getRetentionDays() > 0) {
            purgeAfter = LocalDateTime.now(ZoneOffset.UTC).plusDays(privacy.getRetentionDays());
        }

        // 匿名活动：答案里如果真夹带了能认出人的东西，不该原样落地。
        // 这里不做字段级猜测（那会把"昵称"这种正常字段也误伤），
        // 只在记录层面留匿名编号，并把 submitter_hash 清空。
        JsonNode storedAnswers = answers;

        FormSubmissionEntity entity = new FormSubmissionEntity();
        entity.setId(Ids.uuid());
        entity.setProjectId(project.getId());
        entity.setVersionId(project.getPublishedVersionId());
        entity.setPageId(pageId);
        entity.setFormComponentId(formComponentId);
        entity.setAnswers(storedAnswers);
        entity.setIdempotencyKey(idemKey);
        entity.setConsentId(consentId);
        entity.setPrivacyModeSnapshot(privacy.getPrivacyMode());
        entity.setRetentionDaysSnapshot(privacy.getRetentionDays());
        entity.setAnonymous(anonymous);
        entity.setAnonymousToken(anonymous ? Ids.randomToken(8) : null);
        entity.setPurgeAfter(purgeAfter);
        entity.setSubmitterHash(anonymous ? null : Ids.randomToken(32));
        entity.setStatus("valid");

        try {
            submissionMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            // 并发下同时进来了两次，回读已有的那条，对前端依然是幂等的
            FormSubmissionEntity again = submissionMapper.findByIdempotencyKey(project.getId(), idemKey);
            if (again == null) {
                throw new BizException(ErrorCode.DUPLICATE_SUBMISSION, "重复提交");
            }
            return new Dtos.SubmissionResult(again.getId(), again.getStatus(), Ids.iso(again.getSubmittedAt()));
        }

        FormSubmissionEntity saved = submissionMapper.findByIdempotencyKey(project.getId(), idemKey);
        recordEvent(project.getId(), pageId, "submission_succeeded");
        notifyOrganizer(project, anonymous);
        return new Dtos.SubmissionResult(saved.getId(), saved.getStatus(), Ids.iso(saved.getSubmittedAt()));
    }

    // ================================================================ 组织者查看

    public Dtos.PagedResultHolder<Dtos.SubmissionDto> list(String projectId, String status,
                                                            int page, int pageSize) {
        projectService.loadOwned(projectId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 200);
        long total = submissionMapper.count(projectId, status);
        List<FormSubmissionEntity> rows = total == 0
                ? List.of()
                : submissionMapper.page(projectId, status, safeSize, (safePage - 1) * safeSize);
        List<Dtos.SubmissionDto> data = rows.stream()
                .map(s -> new Dtos.SubmissionDto(s.getId(), s.getPageId(), s.getFormComponentId(),
                        s.getAnswers(), s.getStatus(), Ids.iso(s.getSubmittedAt())))
                .toList();
        return new Dtos.PagedResultHolder<>(data, new Dtos.PageMeta(safePage, safeSize, total));
    }

    /**
     * 带隐私信息的报名列表。匿名记录不返回 submitterHash，
     * 只给组织者 anonymousToken 用来现场核对，对不上人。
     */
    public Dtos.PagedResultHolder<Dtos.SubmissionDetailDto> listDetailed(String projectId, String status,
                                                                          int page, int pageSize) {
        projectService.loadOwned(projectId);
        assertExportAllowed(projectId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 200);
        long total = submissionMapper.count(projectId, status);
        List<FormSubmissionEntity> rows = total == 0
                ? List.of()
                : submissionMapper.page(projectId, status, safeSize, (safePage - 1) * safeSize);
        List<Dtos.SubmissionDetailDto> data = rows.stream()
                .map(s -> new Dtos.SubmissionDetailDto(
                        s.getId(), s.getPageId(), s.getFormComponentId(),
                        s.getAnswers(), s.getStatus(), Ids.iso(s.getSubmittedAt()),
                        s.getPrivacyModeSnapshot(), s.isAnonymous(),
                        s.getAnonymousToken(), s.getConsentId(),
                        Ids.iso(s.getSubmittedAt())))
                .toList();
        return new Dtos.PagedResultHolder<>(data, new Dtos.PageMeta(safePage, safeSize, total));
    }

    /** 看板上的隐私聚合。 */
    public Dtos.SubmissionSummaryDto summary(String projectId) {
        ProjectEntity project = projectService.loadOwned(projectId);
        Dtos.PrivacySettings privacy = privacyService.read(project);
        long total = submissionMapper.count(projectId, null);
        long valid = submissionMapper.countValid(projectId);
        long anon = submissionMapper.countAnonymous(projectId);
        long consented = submissionMapper.countConsented(projectId);

        Map<String, Long> byStatus = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : submissionMapper.byStatus(projectId)) {
            byStatus.put(String.valueOf(row.get("s")), toLong(row.get("c")));
        }
        List<Map<String, Object>> byDay = new ArrayList<>();
        for (Map<String, Object> row : submissionMapper.byDay(projectId)) {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("date", String.valueOf(row.get("d")));
            item.put("count", toLong(row.get("c")));
            byDay.add(item);
        }
        return new Dtos.SubmissionSummaryDto(total, valid, anon, consented,
                privacy.getPrivacyMode(), byDay, byStatus);
    }

    /**
     * 到期清理：匿名/保留期限到点的记录抹掉答案，保留空壳让统计不缩水。
     * 定时任务和"打开看板"时都会调用，所以必须幂等。
     */
    public int purgeExpired(String projectId) {
        projectService.loadOwned(projectId);
        return submissionMapper.purgeExpiredOfProject(projectId);
    }

    /** 导出前必须确认这个活动允许导出（匿名活动也可以允许，看组织者设置）。 */
    public void assertExportAllowed(String projectId) {
        ProjectEntity project = projectService.loadOwned(projectId);
        Dtos.PrivacySettings privacy = privacyService.read(project);
        if (!Boolean.TRUE.equals(privacy.getAllowExport())) {
            throw BizException.forbidden("这个活动已在隐私设置里关闭数据导出");
        }
    }

    /** 导出用：不分页，一次取全部（V1 上限一万条）。 */
    public List<FormSubmissionEntity> allForExport(String projectId) {
        projectService.loadOwned(projectId);
        return submissionMapper.page(projectId, null, 10000, 0);
    }

    // ================================================================ 内部

    private JsonNode loadPublishedDocument(ProjectEntity project) {
        JsonNode document = project.getDraftDocument();
        return document == null ? JsonUtil.mapper().createObjectNode() : document;
    }

    private void recordEvent(String projectId, String pageId, String type) {
        try {
            ActivityEventEntity event = new ActivityEventEntity();
            event.setProjectId(projectId);
            event.setPageId(pageId);
            event.setEventType(type);
            ObjectNode meta = JsonUtil.mapper().createObjectNode();
            meta.put("at", System.currentTimeMillis());
            event.setMetadata(meta);
            miscMapper.insertEvent(event);
        } catch (RuntimeException ignored) {
            // 埋点失败不影响主流程
        }
    }

    /**
     * 有人报名就给组织者留一条通知。dedupe_key 用项目 + 提交 id，
     * 同一条报名不会重复打扰；通知里绝不出现提交人的信息。
     */
    private void notifyOrganizer(ProjectEntity project, boolean anonymous) {
        try {
            UserNotificationEntity n = new UserNotificationEntity();
            n.setId(Ids.uuid());
            n.setUserId(project.getOwnerId());
            n.setType("submission");
            n.setTitle("「" + project.getTitle() + "」收到新的参与记录");
            n.setDetail(anonymous ? "这是一条匿名参与记录，详情里只有匿名编号。" : "前往参与数据查看最新报名。");
            n.setTargetPath("/published");
            n.setDedupeKey("submission:" + project.getId() + ":" + System.currentTimeMillis() / 1000);
            wellbeingMapper.insertNotificationIgnoreDuplicate(n);
        } catch (RuntimeException ignored) {
            // 通知失败不影响报名
        }
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    /** 只有项目拥有者能看到提交列表；这条在 Controller 里靠 CurrentUser 保证。 */
    public void assertOwner(String projectId) {
        projectService.loadOwned(projectId);
        CurrentUser.require();
    }

    /** 依次尝试几个 key，返回第一个非 null 的值。 */
    private JsonNode firstPresent(JsonNode answers, String... keys) {
        for (String key : keys) {
            JsonNode value = answers.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}

package com.campus.server.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 请求体 / 响应体。字段名严格按 docs/api/openapi.yaml，
 * 个别地方补了前端实际用到的字段（已在注释里标出）。
 */
public final class Dtos {

    private Dtos() {
    }

    // ================================================================ Auth

    public record RegisterRequest(
            @jakarta.validation.constraints.NotBlank(message = "邮箱不能为空")
            @jakarta.validation.constraints.Email(message = "邮箱格式不正确")
            @jakarta.validation.constraints.Size(max = 190, message = "邮箱过长")
            String email,

            @jakarta.validation.constraints.NotBlank(message = "密码不能为空")
            @jakarta.validation.constraints.Size(min = 8, max = 128, message = "密码长度需在 8-128 之间")
            String password,

            @jakarta.validation.constraints.NotBlank(message = "昵称不能为空")
            @jakarta.validation.constraints.Size(min = 1, max = 80, message = "昵称长度需在 1-80 之间")
            String displayName
    ) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record RefreshRequest(String refreshToken) {
    }

    public record UserDto(String id, String email, String displayName, String status) {
    }

    public record UpdateProfileRequest(
            @jakarta.validation.constraints.NotBlank(message = "昵称不能为空")
            @jakarta.validation.constraints.Size(max = 80, message = "昵称最多 80 字")
            String displayName
    ) {
    }

    public record FeedbackRequest(
            @jakarta.validation.constraints.NotBlank(message = "反馈内容不能为空")
            @jakarta.validation.constraints.Size(max = 3000, message = "反馈内容最多 3000 字")
            String content,
            String category,
            String contact
    ) {
    }

    public record FeedbackResult(String id, String status, String createdAt) {
    }

    public record AuthResponse(String accessToken, String refreshToken, UserDto user) {
    }

    // ================================================================ Templates

    public record TemplateDto(String id, String code, String name, String category,
                              String description, String coverUrl, JsonNode document) {
    }

    // ================================================================ Projects

    public record CreateProjectRequest(
            @jakarta.validation.constraints.NotBlank(message = "标题不能为空")
            @jakarta.validation.constraints.Size(max = 160, message = "标题最多 160 字")
            String title,
            String description,
            String templateId,
            JsonNode initialDocument,
            /** 活动类型，如 社团招新；不传则从模板名推导 */
            String activityType,
            /** 卡片配色主题；不传则从模板 category 推导 */
            String coverTone,
            /** 卡片角标文字，如 PHOTO / 26 */
            String coverCode,
            /** 卡片大标题，支持 \n 换行 */
            @jakarta.validation.constraints.Size(max = 160, message = "封面标题最多 160 字")
            String coverTitle
    ) {
    }

    public record UpdateProjectRequest(String title, String description,
                                       String activityType, String coverTone,
                                       String coverCode,
                                       @jakarta.validation.constraints.Size(max = 160, message = "封面标题最多 160 字")
                                       String coverTitle) {
    }

    public record DuplicateProjectRequest(
            @jakarta.validation.constraints.NotBlank(message = "标题不能为空")
            @jakarta.validation.constraints.Size(max = 160, message = "标题最多 160 字")
            String title
    ) {
    }

    public record ProjectDto(String id,
                             String title,
                             String description,
                             String activityType,
                             String coverTone,
                             String coverCode,
                             String coverTitle,
                             String status,
                             long draftRevision,
                             String publicSlug,
                             String publicUrl,
                             String publishedVersionId,
                             long submissionCount,
                             String createdAt,
                             String updatedAt,
                             JsonNode draftDocument) {
    }

    // ================================================================ Draft

    public record DraftDto(long revision, JsonNode document, String updatedAt) {
    }

    /**
     * 刻意不在字段上加 @NotNull：文档缺失属于「页面文档不合法」（422），
     * 而不是「参数格式错误」（400）。缺字段的判断放在 ProjectService.saveDraft 里。
     */
    public record SaveDraftRequest(Long baseRevision, JsonNode document) {
    }

    public record SaveDraftResult(long revision, String savedAt) {
    }

    /**
     * 局部更新草稿：patch 按 RFC 7386 JSON Merge Patch 语义合并进现有文档。
     * baseRevision 可选；给了就必须等于服务端当前 revision，否则 409。
     */
    public record PatchDraftRequest(Long baseRevision, JsonNode patch) {
    }

    // ================================================================ Versions

    public record VersionDto(String id, int versionNo, String source, JsonNode document, String createdAt) {
    }

    public record CreateVersionRequest(
            @jakarta.validation.constraints.NotNull(message = "baseRevision 不能为空")
            Long baseRevision,
            String source
    ) {
    }

    // ================================================================ Publish

    public record PublishRequest(String versionId, String slug,
                                 JsonNode publishSettings) {
    }

    public record PublishResult(String publicSlug, String publicUrl, String publishedVersionId, String publishedAt) {
    }

    // ================================================================ Public

    public record PublicActivityDto(String slug, String title, String status, JsonNode document,
                                    JsonNode publishSettings,
                                    String activityType, String coverTone, String coverCode,
                                    String coverTitle,
                                    /** 当前生效的隐私设置，前端直接拿来渲染提示条，不用再猜。 */
                                    PrivacySettings privacy) {
    }

    /** 公开活动列表里的一条，只暴露白名单字段（绝不带 draftDocument / ownerId）。 */
    public record PublicActivitySummaryDto(String slug, String title, String description,
                                           String activityType, String coverTone,
                                           String coverCode, String coverTitle,
                                           String status, String updatedAt) {
    }

    public record SubmissionRequest(
            String pageId,
            String formComponentId,
            JsonNode answers,
            String idempotencyKey,
            /**
             * 知情同意 + 匿名意愿。旧前端不传这个字段时为 null，
             * 服务端按活动自身的隐私设置判定（要求同意就拒绝、不要求就放行）。
             */
            SubmissionPrivacy privacy
    ) {
    }

    public record PublicEventRequest(String eventType, String pageId, JsonNode metadata) {
    }

    public record SubmissionResult(String submissionId, String status, String submittedAt) {
    }

    public record SubmissionDto(String id, String pageId, String formComponentId,
                                JsonNode answers, String status, String submittedAt) {
    }

    // ================================================================ Assets

    public record AssetUploadResult(String assetId, String objectKey, String url, String mimeType,
                                    long sizeBytes, Integer width, Integer height) {
    }

    // ================================================================ AI

    public record AiJobDto(String id, String projectId, String kind, String status,
                           JsonNode result, String errorCode, String errorMessage, String createdAt) {
    }

    public record AiProposalDto(String id, String jobId, String projectId, long baseRevision,
                                JsonNode target, String summary, JsonNode candidateDocument,
                                JsonNode operations, JsonNode rationale, String risk, String status) {
    }

    public record AiProposalApplyRequest(
            @jakarta.validation.constraints.NotNull(message = "baseRevision 不能为空")
            Long baseRevision
    ) {
    }

    // ================================================================ Exports

    public record ExportJobDto(String id, String projectId, String status, String objectKey,
                               String errorMessage, String createdAt, String finishedAt) {
    }

    // ================================================================ Health & Analytics

    /** 分页元信息，序列化进 ApiResponse 的 pagination 字段。 */
    public record PageMeta(int page, int pageSize, long total) {
    }

    /** 服务层往控制器传「列表 + 分页」的载体。 */
    public record PagedResultHolder<T>(java.util.List<T> data, PageMeta meta) {
    }

    public record HealthDto(String status, String app, Map<String, Object> database,
                            Map<String, Object> agent, String time) {
    }

    public record AnalyticsSummaryDto(long submissions,
                                      long validSubmissions,
                                      long pageViews,
                                      long publishedPages,
                                      List<Map<String, Object>> byDay,
                                      /** 按事件类型分项计数：public_page_view / form_submitted … */
                                      Map<String, Long> byEventType,
                                      /** 表单开始填写的次数（form_started），用来算转化率 */
                                      long formStarted,
                                      /** 提交成功率 = validSubmissions / formStarted，formStarted 为 0 时给 0 */
                                      double conversionRate) {
    }

    /** 事件明细里的一条。 */
    public record ActivityEventDto(String id, String eventType, String pageId,
                                   JsonNode metadata, String occurredAt) {
    }

    /** 供前端 select 使用的键值对。 */
    @Data
    public static class Option {
        private String value;
        private String label;
    }

    // ================================================================ 隐私 / 知情同意

    public static final String DEFAULT_CONSENT_TEXT =
            "我知道本次活动只收集参与所需的信息，信息仅由活动组织者查看，"
            + "不用于心理诊断或未经同意的公开传播。";

    /**
     * 活动隐私设置。前端 {@code web/src/composables/useActivityPrivacy.ts} 的接口。
     *
     * <p>以前只存 localStorage，换台电脑就丢。现在落进 {@code projects.publish_settings.privacy}，
     * 公开页拿到的就是组织者真正设置的那一份。
     *
     * <p>用包装类而不是 record：Jackson 反序列化 record 时缺字段会传 null，
     * 这里需要对 null 做缺省值兜底，用 setter 更自然。
     */
    @Data
    public static class PrivacySettings {
        /** real_name | optional | anonymous；缺省 optional */
        private String privacyMode = "optional";
        private Boolean consentRequired = Boolean.TRUE;
        private String consentText = DEFAULT_CONSENT_TEXT;
        /** 信息保留天数，缺省 30；null 表示不自动清理 */
        private Integer retentionDays = 30;
        /** 是否允许组织者导出报名数据 */
        private Boolean allowExport = Boolean.TRUE;
        /** 是否允许参与者中途离开（只做提示，不强制） */
        private Boolean allowLeave = Boolean.TRUE;
        private Boolean quietSpace = Boolean.TRUE;

        public static PrivacySettings defaults() {
            return new PrivacySettings();
        }
    }

    /** GET/PUT /projects/{id}/privacy 的返回体。 */
    public record PrivacyDto(String projectId, PrivacySettings privacy, String updatedAt) {
    }

    /** POST /public/activities/{slug}/consents 的结果。 */
    public record ConsentResult(String consentId, String privacyMode, Integer retentionDays,
                                String consentText, String recordExpiresAt) {
    }

    /** 公开提交时额外可带的隐私字段（老前端不传也能工作）。 */
    public record SubmissionPrivacy(String consentId, String consentText,
                                    Boolean anonymous, Boolean consentAccepted) {
    }

    /** 组织者看到的报名记录，多带隐私 / 匿名信息。 */
    public record SubmissionDetailDto(String id, String pageId, String formComponentId,
                                      JsonNode answers, String status, String submittedAt,
                                      String privacyMode, boolean anonymous,
                                      String anonymousToken, String consentId, String consentAt) {
    }

    /** 报名数据聚合（不返回任何个人信息）。 */
    public record SubmissionSummaryDto(long total, long valid, long anonymous, long consented,
                                       String privacyMode,
                                       List<Map<String, Object>> byDay,
                                       Map<String, Long> byStatus) {
    }

    /** 活动反馈统计。 */
    public record FeedbackSummaryDto(long total, double averageScore,
                                     Map<String, Long> distribution,
                                     List<String> recentComments) {
    }

    /** POST /public/activities/{slug}/feedback 的请求体。 */
    public record ActivityFeedbackRequest(
            @jakarta.validation.constraints.NotBlank(message = "请选择一个反馈选项")
            String choice,
            @jakarta.validation.constraints.Min(value = 0, message = "评分不能小于 0")
            @jakarta.validation.constraints.Max(value = 5, message = "评分不能大于 5")
            Integer score,
            @jakarta.validation.constraints.Size(max = 500, message = "留言最多 500 字")
            String comment,
            Boolean anonymous
    ) {
    }

    // ================================================================ 通知

    public record NotificationDto(String id, String type, String title, String detail,
                                  String targetPath, boolean read, String createdAt) {
    }
}

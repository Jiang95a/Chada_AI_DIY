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
                              String description, String coverUrl, JsonNode document,
                              /**
                               * 活动大类：{@code wellbeing}（心理）/ {@code career}（生涯）/
                               * {@code general}（通用）。前端按这个分栏，比猜 category 可靠。
                               *
                               * <p>JSON 字段名必须叫 {@code kind}：前端
                               * {@code web/src/services/templates.ts} 的 {@code TemplateApiRecord}
                               * 读的是 {@code template.kind}，且 {@code loadTemplateCatalog(kind)}
                               * 依赖它过滤远程模板。早先叫 {@code activityKind} 时前端取不到值，
                               * 所有模板都会退化成 {@code general}，生涯分栏永远为空。
                               * 数据库列名 {@code activity_kind} 不改，只是对外 JSON 换个名字。
                               */
                              String kind) {
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

    // ================================================================ 转介 / 参与轨迹

    /**
     * 参与者在活动页点「希望有人联系我」时提交的求助请求。
     *
     * <p>刻意不接收 {@code answers}：组织者需要在不知道是谁、不知道说了什么的前提下，
     * 仍然知道「有一个人需要被联系」。这条记录就是那个缺口。
     */
    public record ReferralRequest(
            @jakarta.validation.constraints.NotNull(message = "请先同意参与说明")
            Boolean wantsContact,
            @jakarta.validation.constraints.Size(max = 40, message = "联系方式最多 40 字")
            String contact,
            @jakarta.validation.constraints.Size(max = 60, message = "希望的时间最多 60 字")
            String preferredTime) {
    }

    /** 参与者拿到的回执：只给编号，不给任何可用于反查身份的东西。 */
    public record ReferralReceipt(String referralId, String status, String createdAt,
                                  String message) {
    }

    /** 组织者看到的待办项，不含任何参与者身份信息。 */
    public record ReferralTodoDto(String id, String activityTitle, String publicSlug,
                                  String contact, String preferredTime, String status,
                                  String createdAt, String handledAt,
                                  /** 从提交到现在过去了多少小时，前端用来标红「超过 24 小时未联系」 */
                                  long pendingHours) {
    }

    /** 组织者标记已联系后返回的最新状态。 */
    public record ReferralUpdateDto(String id, String status, String handledAt) {
    }

    /** 成长档案里的一个活动。 */
    public record PortfolioItemDto(String projectId, String title, String activityType,
                                   String category, String slug, String publicUrl,
                                   String role, String participationPath,
                                   String firstJoinedAt, String lastJoinedAt,
                                   long joinedCount, String privacyMode,
                                   boolean consentRecorded) {
    }

    /**
     * 个人成长档案。只返回本人参与的、已发布的活动；
     * 匿名参与的只出现 {@code role = anonymous}，不暴露任何回答内容。
     */
    public record PortfolioDto(String userId, String displayName, long totalActivities,
                               long wellbeingCount, long careerCount,
                               java.util.List<java.util.Map<String, Object>> byMonth,
                               java.util.List<PortfolioItemDto> items) {
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
    public record ConsentResult(String consentId, String privacyMode, Boolean consentRequired,
                                Integer retentionDays,
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

    // ================================================================ AI 海报生成

    /**
     * POST /ai/poster/generate 的请求体。
     *
     * <p>只收画面描述——不收活动字段。活动字段由前端自己拼进 prompt，
     * 后端再去查数据库反而会把报名者信息带上（见 docs/poster-generation-api.md 的安全条款）。
     *
     * <p>{@code clientRequestId} 是幂等键：前端连点或网络重试时，
     * 同一个 id 只出一次图，不重复付费。
     */
    public record PosterGenerateRequest(
            @jakarta.validation.constraints.NotBlank(message = "请先描述想要的画面")
            @jakarta.validation.constraints.Size(min = 4, max = 800, message = "画面描述需要 4~800 字")
            String prompt,
            String aspectRatio,
            String style,
            /** 幂等键：前端每次点击生成一个，重试沿用同一个。 */
            String clientRequestId,
            /**
             * 可选。不传时后端退化成「该用户最近的项目」——ai_jobs.project_id 是 NOT NULL
             * 且有外键，而海报生成在语义上不必然绑定某个项目，见 PosterService#resolveProjectId。
             */
            String projectId
    ) {
    }

    /**
     * 出图结果。{@code url} 是本服务自己 /files 下的永久地址（不是上游临时地址），
     * {@code jobId} 用于运营排查：每一次生成在 ai_jobs 里都有一条记录。
     */
    public record PosterGenerateResponse(String jobId, String url, String prompt, String model,
                                         String aspectRatio, int latencyMs, boolean cached) {
    }

    // ================================================================ 参与轨迹

    /**
     * 一条参与轨迹。**直接沿用前端 {@code ParticipantTrace} 的形状**，
     * 不做服务端自己的拆分——前端 {@code web/src/stores/trace.ts} 拿它整条存本地、
     * 整条比对 updatedAt，任何字段缺失都会让它退化成空轨迹。
     *
     * <p>因此这里 {tags}/{sessionContext}/{notes} 用 JsonNode 原样透传：
     * 它们的结构还在演进（一期是前端关键词规则引擎，三期换成 agent），
     * 服务端不解析、不校验内部字段，只保证「存进去什么样、取出来什么样」。
     */
    public record ParticipantTraceDto(
            /** 归属用户，取自 token，不信任请求体里的值。 */
            String userId,
            /** 与前端一致：这里是活动的 public_slug，不是 UUID。 */
            String activityId,
            /** mental | career */
            String domain,
            String semester,
            String summary,
            String openQuestion,
            String change,
            String promise,
            com.fasterxml.jackson.databind.JsonNode tags,
            com.fasterxml.jackson.databind.JsonNode sessionContext,
            com.fasterxml.jackson.databind.JsonNode notes,
            com.fasterxml.jackson.databind.JsonNode history,
            /** 前端毫秒时间戳，原样回传。 */
            long updatedAt
    ) {
    }

    /**
     * PUT /traces/{activityId} 的请求体。
     *
     * <p>刻意**不校验** userId 是否与 token 一致——而是直接忽略请求体里的 userId，
     * 一律用 token 里的。这样前端即使把本地缓存的旧 userId 带上来也不会写错行，
     * 也不需要前端为「换了设备后本地 userId 过期」这种情况做特殊处理。
     */
    public record SaveTraceRequest(
            String userId,
            String domain,
            String semester,
            @jakarta.validation.constraints.Size(max = 500, message = "总结最多 500 字")
            String summary,
            String openQuestion,
            String change,
            String promise,
            com.fasterxml.jackson.databind.JsonNode tags,
            com.fasterxml.jackson.databind.JsonNode sessionContext,
            com.fasterxml.jackson.databind.JsonNode notes,
            com.fasterxml.jackson.databind.JsonNode history,
            /** 可选：不传则由服务端补当前时间。 */
            Long updatedAt
    ) {
    }

    /** 「我的轨迹」列表项：摘要信息 + 活动标题，不含完整 payload。 */
    public record TraceListItemDto(
            String activityId, String activityTitle, String domain, String semester,
            String summary, int tagCount, long updatedAt
    ) {
    }

    // ================================================================ 回看数据聚合

    /**
     * GET /insights/facts 的返回体。
     *
     * <p>只给「做分析所需的底稿」，不返回表单答案原文，也不做诊断。
     * 智能体如果要引用原话，只能引用用户明确同意保留的 summary/change；
     * 原始输入文本从未落库。
     */
    public record InsightFactsDto(
            String periodStart,
            String periodEnd,
            String generatedAt,
            long activityCount,
            long formCount,
            long traceCount,
            long eventCount,
            List<InsightActivityDto> activities,
            List<InsightEventDto> events,
            List<InsightFormDto> forms,
            Map<String, Long> tagCounts,
            /**
             * 工具记录（能量牌局 / 三栏）条数。
             *
             * <p>单独计数、<b>不计入 activityCount</b>：工具记录不是"参加了一场活动"，
             * 把它混进去会让「这一个月参加了 7 场活动」这种说法变成假话。
             */
            long toolRecordCount
    ) {
    }

    /** 聚合里的活动摘要：不返回 UUID，只返回公开 slug。 */
    public record InsightActivityDto(
            String activityId,
            String activityTitle,
            String domain,
            long lastAt,
            int eventCount,
            List<String> tags,
            String summary
    ) {
    }

    /** 聚合里的时间点。activity 事件来自 trace.history；form 事件只给提交事实。 */
    public record InsightEventDto(
            String source,
            String activityId,
            String activityTitle,
            long at,
            String kind,
            List<String> tags,
            String summary,
            String change
    ) {
    }

    /**
     * 表单提交事实：不含 answers。
     *
     * <p>报名答案是参与者交给组织者的内容，不是成长档案；
     * 聚合接口只证明「发生过一次提交」，不把答案喂给分析。
     */
    public record InsightFormDto(
            String activityId,
            String activityTitle,
            String submittedAt,
            String status
    ) {
    }

    // ================================================================ 回看智能体

    /**
     * 新建工具记录。客户端只给内容，id 与 userId 由服务端生成——
     * 请求体里出现 userId 会被忽略，越权写别人名下是不可能的。
     */
    public record ToolRecordCreateRequest(
            String type,
            String title,
            String summary,
            java.util.List<String> tags,
            Long createdAtMs
    ) {
    }

    /** 工具记录（对外）。 */
    public record ToolRecordDto(
            String id,
            String type,
            String title,
            String summary,
            java.util.List<String> tags,
            long createdAtMs
    ) {
    }

    /** 回看分析请求。days 会被服务端夹到 1~365，其余字段只影响「能不能引用原话」。 */
    public record InsightAnalyzeRequest(
            Integer days,
            Boolean allowQuote,
            String locale
    ) {
    }

    /** 一条证据：日期 + 活动名（可选原话）。模型给的这两项必须能在 facts 里找到。 */
    public record InsightEvidenceDto(
            String at,
            String activityTitle,
            String quote
    ) {
    }

    /** 一条发现。没有 evidence 的发现会被服务端丢掉，不进响应。 */
    public record InsightFindingDto(
            String text,
            java.util.List<InsightEvidenceDto> evidence
    ) {
    }

    /** 回看正文。 */
    public record InsightBodyDto(
            String title,
            java.util.List<InsightFindingDto> findings,
            String change,
            String nextStep,
            String question,
            String confidence,
            String visualPreset,
            String limits
    ) {
    }

    /** 危机时要给的具体资源。 */
    public record InsightResourceDto(
            String label,
            String value
    ) {
    }

    /**
     * 回看分析响应。
     *
     * <p>四种结局共用这一个结构，靠 {@code status} 区分：
     * {@code ok} 带 insight；{@code insufficient_data} 带 message；
     * {@code crisis} 带 message + resources；{@code analysis_error} 带 message。
     *
     * <p>不用四个类，是因为全局 Jackson 配了 {@code default-property-inclusion: non_null}，
     * 空字段会直接不出现——前端拿到的 JSON 形状和契约文档逐字一致。
     */
    public record InsightAnalyzeResponseDto(
            String status,
            String insightId,
            String generatedAt,
            String model,
            String promptVersion,
            InsightBodyDto insight,
            String message,
            java.util.List<InsightResourceDto> resources
    ) {
    }
}

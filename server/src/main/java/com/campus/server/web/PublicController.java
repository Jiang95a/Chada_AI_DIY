package com.campus.server.web;

import com.campus.server.common.ApiResponse;
import com.campus.server.dto.Dtos;
import com.campus.server.service.ConsentService;
import com.campus.server.service.FeedbackService;
import com.campus.server.service.ProjectService;
import com.campus.server.service.ReferralService;
import com.campus.server.service.SubmissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 公开活动页：不需要登录，任何人凭 slug 访问。
 *
 * <p>对应前端 {@code web/src/views/PublicActivityView.vue}。
 * 注意 {@code /public/**} 在 AuthInterceptor 里是放行的。
 */
@RestController
@RequestMapping("/public")
public class PublicController {

    private final ProjectService projectService;
    private final SubmissionService submissionService;
    private final ConsentService consentService;
    private final FeedbackService feedbackService;
    private final ReferralService referralService;

    public PublicController(ProjectService projectService, SubmissionService submissionService,
                            ConsentService consentService, FeedbackService feedbackService,
                            ReferralService referralService) {
        this.projectService = projectService;
        this.submissionService = submissionService;
        this.consentService = consentService;
        this.feedbackService = feedbackService;
        this.referralService = referralService;
    }

    /** 打开活动页：返回发布时的文档快照（不是草稿），并记一次浏览埋点。 */
    @GetMapping("/activities/{slug}")
    public ApiResponse.Envelope<Dtos.PublicActivityDto> getActivity(@PathVariable String slug) {
        return ApiResponse.ok(projectService.getPublic(slug, true));
    }

    /**
     * 公开活动列表：已发布的活动，任何人可看。
     * 只返回白名单字段（slug / 标题 / 封面四项 / 状态 / 更新时间），不带草稿内容。
     */
    @GetMapping("/activities")
    public ApiResponse.PagedEnvelope<java.util.List<Dtos.PublicActivitySummaryDto>> listActivities(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String activityType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Dtos.PagedResultHolder<Dtos.PublicActivitySummaryDto> result =
                projectService.listPublic(q, activityType, page, pageSize);
        return ApiResponse.page(result.data(), result.meta().total(),
                result.meta().page(), result.meta().pageSize());
    }

    /**
     * 提交报名。
     * 幂等：同一个 idempotencyKey 重复提交返回同一条记录，不报错也不重复入库。
     */
    @PostMapping("/activities/{slug}/submissions")
    public ApiResponse.Envelope<Dtos.SubmissionResult> submit(@PathVariable String slug,
                                                              @RequestBody Dtos.SubmissionRequest request) {
        return ApiResponse.ok(submissionService.submit(slug, request));
    }

    @PostMapping("/activities/{slug}/events")
    public ApiResponse.Envelope<Map<String, Object>> recordEvent(
            @PathVariable String slug,
            @RequestBody Dtos.PublicEventRequest request) {
        projectService.recordPublicEvent(slug, request);
        return ApiResponse.ok(Map.of("recorded", true));
    }

    // ============================================================ 隐私 / 知情同意

    /**
     * 看一眼这个活动当前生效的参与说明（不需要先同意）。
     * 前端在填表前先调它，把 privacyMode / 保留天数渲染到提示条上。
     */
    @GetMapping("/activities/{slug}/privacy")
    public ApiResponse.Envelope<Dtos.ConsentResult> describePrivacy(@PathVariable String slug) {
        return ApiResponse.ok(consentService.describe(slug));
    }

    /**
     * 勾选「我已阅读」之后调一次，拿到 consentId，再带着它去提交。
     *
     * <p>只存同意文本的 hash，不存明文身份；匿名档连 submitter_hash 都不落。
     */
    @PostMapping("/activities/{slug}/consents")
    public ApiResponse.Envelope<Dtos.ConsentResult> recordConsent(
            @PathVariable String slug,
            @RequestBody(required = false) Map<String, Object> body) {
        String text = body == null || body.get("consentText") == null
                ? null : String.valueOf(body.get("consentText"));
        return ApiResponse.ok(consentService.record(slug, text));
    }

    // ============================================================ 活动反馈

    /**
     * 活动结束后留一句感受。不收身份，匿名档不留任何可回指信息。
     * 接口幂等性由前端保证（重复提交只影响统计，不产生通知）。
     */
    @PostMapping("/activities/{slug}/feedback")
    public ApiResponse.Envelope<Map<String, Object>> submitFeedback(
            @PathVariable String slug,
            @Valid @RequestBody Dtos.ActivityFeedbackRequest request) {
        feedbackService.submitActivity(slug, request);
        return ApiResponse.ok(Map.of("recorded", true));
    }

    // ============================================================ 转介（希望有人联系我）

    /**
     * 「希望有人联系我」。匿名档下这是唯一能把求助送出去的通道。
     *
     * <p><b>刻意只收三个字段</b>：是否想被联系、联系方式、希望时间。
     * 不收 answers，所以组织者拿到工单也看不到参与者填过什么。
     * 不需要登录——想匿名求助的人本来就不该被要求先注册。
     */
    @PostMapping("/activities/{slug}/referrals")
    public ApiResponse.Envelope<Dtos.ReferralReceipt> submitReferral(
            @PathVariable String slug,
            @Valid @RequestBody Dtos.ReferralRequest request) {
        return ApiResponse.ok(referralService.submit(slug, request));
    }
}

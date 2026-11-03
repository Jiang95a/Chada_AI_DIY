package com.campus.server.web;

import com.campus.server.common.ApiResponse;
import com.campus.server.dto.Dtos;
import com.campus.server.service.FeedbackService;
import com.campus.server.service.PrivacyService;
import com.campus.server.service.ProjectService;
import com.campus.server.service.SubmissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目 + 草稿 + 版本 + 发布。
 *
 * <p>对应前端的 {@code web/src/stores/projects.ts}（目前仍是纯 localStorage，接后端时逐条替换）。
 */
@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final PrivacyService privacyService;
    private final SubmissionService submissionService;
    private final FeedbackService feedbackService;

    public ProjectController(ProjectService projectService, PrivacyService privacyService,
                             SubmissionService submissionService, FeedbackService feedbackService) {
        this.projectService = projectService;
        this.privacyService = privacyService;
        this.submissionService = submissionService;
        this.feedbackService = feedbackService;
    }

    // ================================================================ 项目 CRUD

    @GetMapping
    public ApiResponse.PagedEnvelope<java.util.List<Dtos.ProjectDto>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String activityType,
            // 白名单：title / created_at / updated_at（mapper 里实现）。
            // 默认按更新时间倒序，未识别的值也落默认，不报错。
            @RequestParam(required = false, defaultValue = "updated_at") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Dtos.PagedResultHolder<Dtos.ProjectDto> result =
                projectService.list(q, status, activityType, sort, page, pageSize);
        return ApiResponse.page(result.data(), result.meta().total(), result.meta().page(), result.meta().pageSize());
    }

    @PostMapping
    public ApiResponse.Envelope<Dtos.ProjectDto> create(@Valid @RequestBody Dtos.CreateProjectRequest request) {
        return ApiResponse.ok(projectService.create(request));
    }

    @GetMapping("/{projectId}")
    public ApiResponse.Envelope<Dtos.ProjectDto> get(@PathVariable String projectId) {
        return ApiResponse.ok(projectService.get(projectId));
    }

    @PutMapping("/{projectId}")
    public ApiResponse.Envelope<Dtos.ProjectDto> update(@PathVariable String projectId,
                                                        @RequestBody Dtos.UpdateProjectRequest request) {
        return ApiResponse.ok(projectService.update(projectId, request));
    }

    @DeleteMapping("/{projectId}")
    public ApiResponse.Envelope<java.util.Map<String, Object>> delete(@PathVariable String projectId) {
        projectService.delete(projectId);
        return ApiResponse.ok(java.util.Map.of("deleted", true));
    }

    /** 复用草稿做一份副本（前端「复制项目」）。 */
    @PostMapping("/{projectId}/duplicate")
    public ApiResponse.Envelope<Dtos.ProjectDto> duplicate(@PathVariable String projectId,
                                                           @Valid @RequestBody Dtos.DuplicateProjectRequest request) {
        return ApiResponse.ok(projectService.duplicate(projectId, request));
    }

    // ================================================================ 草稿（乐观锁）

    @GetMapping("/{projectId}/draft")
    public ApiResponse.Envelope<Dtos.DraftDto> getDraft(@PathVariable String projectId) {
        return ApiResponse.ok(projectService.getDraft(projectId));
    }

    /**
     * 保存草稿。
     * baseRevision 与服务端 draft_revision 不一致时返回 409 REVISION_CONFLICT，
     * 前端据此提示「文档已在别处被修改」。
     */
    @PutMapping("/{projectId}/draft")
    public ApiResponse.Envelope<Dtos.SaveDraftResult> saveDraft(@PathVariable String projectId,
                                                                @Valid @RequestBody Dtos.SaveDraftRequest request) {
        return ApiResponse.ok(projectService.saveDraft(projectId, request));
    }

    /**
     * 局部更新草稿（自动保存用）：只传变了的那一小块，比整体 PUT 轻得多。
     * 合并完仍会跑 schema 校验，所以不会把文档写坏。
     */
    @PatchMapping("/{projectId}/draft")
    public ApiResponse.Envelope<Dtos.SaveDraftResult> patchDraft(@PathVariable String projectId,
                                                                 @RequestBody Dtos.PatchDraftRequest request) {
        return ApiResponse.ok(projectService.patchDraft(projectId, request));
    }

    // ================================================================ 版本

    @GetMapping("/{projectId}/versions")
    public ApiResponse.Envelope<List<Dtos.VersionDto>> listVersions(@PathVariable String projectId) {
        return ApiResponse.ok(projectService.listVersions(projectId));
    }

    @PostMapping("/{projectId}/versions")
    public ApiResponse.Envelope<Dtos.VersionDto> createVersion(@PathVariable String projectId,
                                                               @Valid @RequestBody Dtos.CreateVersionRequest request) {
        return ApiResponse.ok(projectService.createVersion(projectId, request));
    }

    // ================================================================ 发布

    @PostMapping("/{projectId}/publish")
    public ApiResponse.Envelope<Dtos.PublishResult> publish(@PathVariable String projectId,
                                                            @RequestBody(required = false) Dtos.PublishRequest request) {
        Dtos.PublishRequest safe = request == null ? new Dtos.PublishRequest(null, null, null) : request;
        return ApiResponse.ok(projectService.publish(projectId, safe));
    }

    @PostMapping("/{projectId}/unpublish")
    public ApiResponse.Envelope<java.util.Map<String, Object>> unpublish(@PathVariable String projectId) {
        projectService.unpublish(projectId);
        return ApiResponse.ok(java.util.Map.of("status", "unpublished"));
    }

    // ================================================================ 隐私设置

    /**
     * 组织者读写这个活动的隐私设置。
     *
     * <p>以前前端把这份设置整个塞在 localStorage（键 {@code diy-campus:activity-privacy:v1}），
     * 换台电脑就回到默认值、"保留 30 天"也只是个字符串没人执行。
     * 现在落进 {@code projects.publish_settings.privacy}，公开页拿到的就是这里 sett 的这一份。
     */
    @GetMapping("/{projectId}/privacy")
    public ApiResponse.Envelope<Dtos.PrivacyDto> getPrivacy(@PathVariable String projectId) {
        return ApiResponse.ok(privacyService.get(projectId, projectService));
    }

    @PutMapping("/{projectId}/privacy")
    public ApiResponse.Envelope<Dtos.PrivacyDto> savePrivacy(@PathVariable String projectId,
                                                             @RequestBody Dtos.PrivacySettings request) {
        return ApiResponse.ok(privacyService.save(projectId, request, projectService));
    }

    // ================================================================ 参与数据

    /** 报名聚合：总数 / 匿名数 / 同意数 / 按天 / 按状态。不含任何个人信息。 */
    @GetMapping("/{projectId}/submissions/summary")
    public ApiResponse.Envelope<Dtos.SubmissionSummaryDto> submissionSummary(@PathVariable String projectId) {
        return ApiResponse.ok(submissionService.summary(projectId));
    }

    /** 活动反馈聚合：分布 + 平均分 + 最近留言。 */
    @GetMapping("/{projectId}/feedback/summary")
    public ApiResponse.Envelope<Dtos.FeedbackSummaryDto> feedbackSummary(@PathVariable String projectId) {
        return ApiResponse.ok(feedbackService.summary(projectId, projectService));
    }

    /** 手动触发一次到期清理（正常由定时任务做，这里给运维兜底）。 */
    @PostMapping("/{projectId}/submissions/purge")
    public ApiResponse.Envelope<java.util.Map<String, Object>> purgeSubmissions(@PathVariable String projectId) {
        return ApiResponse.ok(java.util.Map.of("purged", submissionService.purgeExpired(projectId)));
    }
}

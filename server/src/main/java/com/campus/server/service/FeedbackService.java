package com.campus.server.service;

import com.campus.server.common.BizException;
import com.campus.server.dto.Dtos;
import com.campus.server.entity.ActivityFeedbackEntity;
import com.campus.server.entity.MiscEntities.FeedbackReportEntity;
import com.campus.server.entity.ProjectEntity;
import com.campus.server.mapper.MiscMapper;
import com.campus.server.mapper.ProjectMapper;
import com.campus.server.mapper.WellbeingMapper;
import com.campus.server.security.CurrentUser;
import com.campus.server.support.Ids;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 两条不同的"反馈"：
 *
 * <ul>
 *   <li>{@link #submit(Dtos.FeedbackRequest)} —— 平台问题反馈（feedback_reports），要登录；</li>
 *   <li>{@link #submitActivity(...)} —— 活动结束后的心情反馈（activity_feedback），匿名可投。</li>
 * </ul>
 */
@Service
public class FeedbackService {

    /** 活动反馈只有这几档，避免前端随便传导致看板分布图散架。 */
    public static final Set<String> CHOICES = Set.of("helpful", "neutral", "unhelpful");

    private final MiscMapper miscMapper;
    private final WellbeingMapper wellbeingMapper;
    private final ProjectMapper projectMapper;
    private final PrivacyService privacyService;

    public FeedbackService(MiscMapper miscMapper, WellbeingMapper wellbeingMapper,
                           ProjectMapper projectMapper, PrivacyService privacyService) {
        this.miscMapper = miscMapper;
        this.wellbeingMapper = wellbeingMapper;
        this.projectMapper = projectMapper;
        this.privacyService = privacyService;
    }

    // ------------------------------------------------------------ 平台问题反馈（原有）

    public Dtos.FeedbackResult submit(Dtos.FeedbackRequest request) {
        FeedbackReportEntity feedback = new FeedbackReportEntity();
        feedback.setId(Ids.uuid());
        feedback.setUserId(CurrentUser.require().userId());
        feedback.setCategory(request.category() == null || request.category().isBlank()
                ? "other" : request.category().trim());
        feedback.setContent(request.content().trim());
        feedback.setContact(request.contact() == null ? null : request.contact().trim());
        feedback.setStatus("open");
        miscMapper.insertFeedback(feedback);
        return new Dtos.FeedbackResult(feedback.getId(), feedback.getStatus(), Ids.nowIso());
    }

    // ------------------------------------------------------------ 活动反馈（新增）

    /**
     * 活动结束后参与者说一句「今天感觉怎么样」。
     *
     * <p>刻意做得很轻：一个选项 + 可选评分 + 可选留言，不收身份。
     * 匿名档连 submitter_hash 都不落；非匿名也只是随机串，不对应账号。
     */
    public void submitActivity(String slug, Dtos.ActivityFeedbackRequest request) {
        ProjectEntity project = projectMapper.findByPublicSlug(slug);
        if (project == null || !"published".equals(project.getStatus())) {
            throw BizException.notFound("活动不存在或未发布");
        }
        Dtos.PrivacySettings privacy = privacyService.read(project);
        if (!Boolean.TRUE.equals(privacy.getAllowLeave())) {
            // allowLeave 同时表示"允许参与者留下反馈"，与前端一条设置对应
            throw BizException.forbidden("这个活动已关闭反馈入口");
        }

        String choice = request.choice() == null ? "" : request.choice().trim();
        if (!CHOICES.contains(choice)) {
            throw BizException.invalid("反馈选项只能是 helpful / neutral / unhelpful");
        }

        boolean anonymous = privacyService.resolveAnonymous(privacy, request.anonymous());

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        ActivityFeedbackEntity entity = new ActivityFeedbackEntity();
        entity.setId(Ids.uuid());
        entity.setProjectId(project.getId());
        entity.setChoice(choice);
        entity.setScore(request.score());
        String comment = request.comment();
        entity.setComment(comment == null || comment.isBlank() ? null : comment.trim());
        entity.setAnonymous(anonymous);
        entity.setSubmitterHash(anonymous ? null : Ids.randomToken(32));
        if (privacy.getRetentionDays() != null && privacy.getRetentionDays() > 0) {
            entity.setPurgeAfter(now.plusDays(privacy.getRetentionDays()));
        }
        wellbeingMapper.insertFeedback(entity);
    }

    /** 组织者看到的聚合，只有分布和比例，不返回任何单条记录。 */
    public Dtos.FeedbackSummaryDto summary(String projectId, ProjectService projectService) {
        projectService.loadOwned(projectId);
        long total = wellbeingMapper.countFeedback(projectId);
        Double avg = wellbeingMapper.averageFeedbackScore(projectId);

        Map<String, Long> distribution = new LinkedHashMap<>();
        for (String choice : new String[]{"helpful", "neutral", "unhelpful"}) {
            distribution.put(choice, 0L);
        }
        for (Map<String, Object> row : wellbeingMapper.feedbackDistribution(projectId)) {
            distribution.put(String.valueOf(row.get("k")), toLong(row.get("c")));
        }

        List<String> recent = new ArrayList<>();
        for (String comment : wellbeingMapper.recentComments(projectId)) {
            if (comment != null && !comment.isBlank()) {
                recent.add(comment);
            }
        }
        return new Dtos.FeedbackSummaryDto(total, avg == null ? 0d : avg, distribution, recent);
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}

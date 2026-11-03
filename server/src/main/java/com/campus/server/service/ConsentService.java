package com.campus.server.service;

import com.campus.server.common.BizException;
import com.campus.server.dto.Dtos;
import com.campus.server.entity.ActivityConsentEntity;
import com.campus.server.entity.ProjectEntity;
import com.campus.server.mapper.ProjectMapper;
import com.campus.server.mapper.WellbeingMapper;
import com.campus.server.support.Ids;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 知情同意：公开页勾选后先落一条记录，再让提交带上 consentId。
 *
 * <p>设计取舍：同意记录和提交是两步（而不是提交时顺手写一条），因为
 * 用户是先看到「这个活动会收集什么、留多久」才填表。把同意单独落库，
 * 才能证明「同意发生在收集之前」。
 *
 * <p>记录里存的是同意文本的 sha256，不是明文身份；anon 档连 submitter_hash 都不落。
 */
@Service
public class ConsentService {

    private final WellbeingMapper wellbeingMapper;
    private final ProjectMapper projectMapper;
    private final PrivacyService privacyService;

    public ConsentService(WellbeingMapper wellbeingMapper, ProjectMapper projectMapper,
                          PrivacyService privacyService) {
        this.wellbeingMapper = wellbeingMapper;
        this.projectMapper = projectMapper;
        this.privacyService = privacyService;
    }

    /** 公开页进来先拿当前生效的隐私说明（不需要登录）。 */
    public Dtos.ConsentResult describe(String slug) {
        ProjectEntity project = requirePublished(slug);
        Dtos.PrivacySettings privacy = privacyService.read(project);
        return new Dtos.ConsentResult(null, privacy.getPrivacyMode(),
                privacy.getRetentionDays(), privacy.getConsentText(), null);
    }

    @Transactional
    public Dtos.ConsentResult record(String slug, String consentTextFromClient) {
        ProjectEntity project = requirePublished(slug);
        Dtos.PrivacySettings privacy = privacyService.read(project);

        // 以服务端保存的文本为准：客户端传来的文本只做"是否一致"的核对，
        // 不允许通过前端改一段自己写的话去覆盖组织者发布的说明。
        String text = privacy.getConsentText();
        if (text == null || text.isBlank()) {
            text = Dtos.DEFAULT_CONSENT_TEXT;
        }
        if (consentTextFromClient != null && !consentTextFromClient.isBlank()
                && !consentTextFromClient.equals(text)) {
            throw BizException.invalid("参与说明已更新，请刷新页面重新阅读");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        ActivityConsentEntity consent = new ActivityConsentEntity();
        consent.setId(Ids.uuid());
        consent.setProjectId(project.getId());
        consent.setVersionId(project.getPublishedVersionId());
        consent.setConsentVersion("v1");
        consent.setConsentText(text);
        consent.setConsentHash(Ids.sha256(text));
        consent.setPrivacyMode(privacy.getPrivacyMode());
        consent.setRetentionDays(privacy.getRetentionDays());
        // 匿名档不落任何可以回指到个人的东西
        consent.setSubmitterHash("anonymous".equals(privacy.getPrivacyMode()) ? null : Ids.randomToken(32));
        consent.setAccepted(true);
        consent.setAcceptedAt(now);
        if (privacy.getRetentionDays() != null && privacy.getRetentionDays() > 0) {
            consent.setPurgeAfter(now.plusDays(privacy.getRetentionDays()));
        }
        wellbeingMapper.insertConsent(consent);

        return new Dtos.ConsentResult(consent.getId(), privacy.getPrivacyMode(),
                privacy.getRetentionDays(), text, Ids.iso(consent.getPurgeAfter()));
    }

    /** 提交时校验：consentId 必须存在、属于这个活动、且是接受状态。 */
    public ActivityConsentEntity requireValid(String projectId, String consentId) {
        ActivityConsentEntity consent = wellbeingMapper.findConsent(consentId);
        if (consent == null || !projectId.equals(consent.getProjectId()) || !consent.isAccepted()) {
            throw BizException.invalid("知情同意记录无效，请重新勾选参与说明");
        }
        return consent;
    }

    private ProjectEntity requirePublished(String slug) {
        ProjectEntity project = projectMapper.findByPublicSlug(slug);
        if (project == null || !"published".equals(project.getStatus())) {
            throw BizException.notFound("活动不存在或未发布");
        }
        return project;
    }
}

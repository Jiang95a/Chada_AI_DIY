package com.campus.server.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表 form_submissions。(project_id, idempotency_key) 唯一，重复提交不会产生第二条记录。
 *
 * <p>隐私相关字段说明（见 docs/sql/007_wellbeing_privacy.sql）：
 * <ul>
 *   <li>{@code privacyModeSnapshot} / {@code retentionDaysSnapshot}：报名那一刻适用的规则，
 *       之后组织者改设置也不影响这条记录的定性；</li>
 *   <li>{@code anonymousToken}：给参与者看的匿名编号，现场签到、组织者沟通只看它；</li>
 *   <li>{@code purgeAfter}：到期清除时间，null 表示不参与自动清理。</li>
 * </ul>
 */
@Data
public class FormSubmissionEntity {
    private String id;
    private String projectId;
    /**
     * 参与者本人（登录时才有）。给「我的成长档案」用。
     *
     * <p>注意：组织者查询报名列表时<b>不返回</b>这个字段——它只用于参与者查自己，
     * 不用于组织者点名。
     */
    private String userId;
    private String versionId;
    private String pageId;
    private String formComponentId;
    private JsonNode answers;
    private String idempotencyKey;
    private String consentId;
    private String privacyModeSnapshot;
    private Integer retentionDaysSnapshot;
    private boolean anonymous;
    private String anonymousToken;
    private LocalDateTime purgeAfter;
    private String submitterHash;
    /** valid | invalid */
    private String status;
    private LocalDateTime submittedAt;
}

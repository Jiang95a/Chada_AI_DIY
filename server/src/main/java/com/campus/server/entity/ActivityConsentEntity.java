package com.campus.server.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表 activity_consents：知情同意记录（docs/sql/007_wellbeing_privacy.sql）。
 *
 * <p>这张表是「匿名是服务端保证的」这条主张的物证。存的是同意文本的
 * sha256 而不是明文身份；匿名活动连 submitter_hash 都不落。
 */
@Data
public class ActivityConsentEntity {
    private String id;
    private String projectId;
    private String versionId;
    private String consentVersion;
    private String consentText;
    private String consentHash;
    private String privacyMode;
    private Integer retentionDays;
    private String submitterHash;
    private boolean accepted;
    private LocalDateTime acceptedAt;
    private LocalDateTime purgeAfter;
}

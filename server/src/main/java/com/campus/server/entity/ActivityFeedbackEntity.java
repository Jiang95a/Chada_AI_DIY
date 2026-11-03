package com.campus.server.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 表 activity_feedback：活动结束后的心情反馈（docs/sql/007_wellbeing_privacy.sql）。 */
@Data
public class ActivityFeedbackEntity {
    private String id;
    private String projectId;
    private String choice;
    private Integer score;
    private String comment;
    private boolean anonymous;
    private String submitterHash;
    private LocalDateTime createdAt;
    private LocalDateTime purgeAfter;
}

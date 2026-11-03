package com.campus.server.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 表 user_notifications：工作台铃铛（docs/sql/007_wellbeing_privacy.sql）。 */
@Data
public class UserNotificationEntity {
    private String id;
    private String userId;
    private String type;
    private String title;
    private String detail;
    private String targetPath;
    private String dedupeKey;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}

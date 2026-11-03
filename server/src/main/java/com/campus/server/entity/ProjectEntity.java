package com.campus.server.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表 projects。draft_revision 是前端乐观锁的核心：
 * PUT /draft 时带 baseRevision，和这里的值不相等就返回 409。
 */
@Data
public class ProjectEntity {
    private String id;
    private String ownerId;
    private String templateId;
    private String title;
    private String description;
    /** 活动类型，如 社团招新 / 投票评选；前端 ProjectRecord.type */
    private String activityType;
    /**
     * 隐私档位冗余列：real_name | optional | anonymous。
     * 真正的完整设置在 publish_settings.privacy，这一列只是为了公开列表 / 统计能直接过滤。
     */
    private String privacyMode;
    /** 卡片配色主题，前端 ProjectRecord.coverTone */
    private String coverTone;
    /** 卡片角标文字，前端 ProjectRecord.coverCode */
    private String coverCode;
    /** 卡片大标题，前端 ProjectRecord.coverTitle */
    private String coverTitle;
    /** draft | published | unpublished | archived */
    private String status;
    private JsonNode draftDocument;
    private Long draftRevision;
    private String publicSlug;
    private String publishedVersionId;
    private JsonNode publishSettings;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}

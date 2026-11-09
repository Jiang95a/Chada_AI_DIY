package com.campus.server.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表 templates。document 存的是前端 DocPlan/Doc 结构（见 web/src/data/templates.ts）。
 */
@Data
public class TemplateEntity {
    private String id;
    private String code;
    private String name;
    /** recruitment | registration | vote | checkin */
    private String category;
    /**
     * 活动大类：wellbeing（心理）/ career（生涯）/ general（通用）。
     * 这个字段能区分「心理知识问答」和「生涯知识测试」——两者 category 都是 vote，
     * 但一个属于心理、一个属于生涯，前端分栏必须靠它。
     */
    private String activityKind;
    private String description;
    private String coverUrl;
    private JsonNode document;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

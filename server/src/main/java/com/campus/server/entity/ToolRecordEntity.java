package com.campus.server.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表 tool_records。参与者在「心晴工具箱」里留下的记录：能量牌局、三栏。
 *
 * <p>为什么要有这张表：这些记录原先只存在浏览器 localStorage，换设备就没了，
 * 回看智能体也就只能分析活动表单，看不到「他在工具里说了什么」。
 * 落库之后它们和轨迹一样成为回看的输入（InsightFacts.events 里 source="tool"）。
 *
 * <p>为什么 tags 存 JSON 而不是关联表：标签永远跟着记录整条读，
 * 没有「按标签反查所有记录」的需求；拆表只会让每次加字段都要迁移。
 *
 * <p>注意 {@code created_at_ms} 是**客户端传来的毫秒时间戳**，服务端原样保存。
 * 用它排序而不是用 created_at，是因为用户的记录时间以他的设备为准，
 * 服务器时钟偏移会把这个人的时间线揉乱。
 */
@Data
public class ToolRecordEntity {
    private String id;
    private String userId;
    /** energy=能量牌局 / thought=三栏 */
    private String type;
    private String title;
    private String summary;
    private JsonNode tags;
    private Long createdAtMs;
    private LocalDateTime createdAt;
}

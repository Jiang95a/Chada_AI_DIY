package com.campus.server.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表 participant_traces。参与者在某一场活动里逐步留下的个人轨迹。
 *
 * <p>为什么整条存 JSON 而不是拆字段：轨迹的结构还在演进（前端一期是关键词规则引擎，
 * 三期换成 agent 生成），拆成 tags / notes / session_context 三张表会让每次加字段
 * 都要迁移一次。而读取永远是「整条取出来给前端」，没有按标签检索的需求。
 *
 * <p>注意 updated_at_ms 是**前端传来的毫秒时间戳**，不是本表的 updated_at。
 * 前端拿它判断「远端和本地哪个更新」（web/src/stores/trace.ts:181），
 * 所以服务端必须原样存回，不能用自己的时钟覆盖。
 */
@Data
public class ParticipantTraceEntity {
    private String userId;
    /** projects.id，由 slug 反查得到，不是 slug 本身。 */
    private String projectId;
    /** mental | career */
    private String domain;
    private String semester;
    /** 冗余自 payload.summary，供列表页只查这几列时使用。 */
    private String summary;
    /** 完整 ParticipantTrace。 */
    private JsonNode payload;
    private Long updatedAtMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

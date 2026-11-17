package com.campus.server.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表 insight_runs。一次「让 AI 回看」的可复现记录。
 *
 * <p>产品文档要求记录 insightId / model / promptVersion / 输入哈希——
 * 没有这四样，用户说「上次那个结论不对」时谁也复现不出来。
 *
 * <p>它同时承担 24 小时缓存：同一个用户、同一份 facts 哈希，直接回放上次的 response_json，
 * 不再重复调用模型（省钱，也让「连点两下」不会产生两段不同的话）。
 *
 * <p>{@code status} 把四种结局都落库：ok / insufficient_data / crisis / analysis_error。
 * 数据不足和危机这两种**不调用模型**，但也要留痕——否则没法证明"该拦的确实拦了"。
 */
@Data
public class InsightRunEntity {
    private String id;
    private String userId;
    /** 输入事实的 sha256。 */
    private String factsHash;
    private Integer days;
    private String model;
    private String promptVersion;
    private String status;
    /** 成功时的完整响应，缓存命中直接回放。 */
    private JsonNode responseJson;
    private LocalDateTime createdAt;
}

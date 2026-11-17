package com.campus.server.web;

import com.campus.server.common.ApiResponse;
import com.campus.server.dto.Dtos;
import com.campus.server.service.InsightService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回看数据聚合。鉴权由 AuthInterceptor 统一处理；这里不接收 userId。
 *
 * <p>该接口只返回结构化事实，不调用模型、不生成图片、不做心理判断。
 * 后续智能体分析应消费这份 facts，而不是直接访问数据库。
 */
@RestController
@RequestMapping("/insights")
public class InsightController {

    private final InsightService insightService;

    public InsightController(InsightService insightService) {
        this.insightService = insightService;
    }

    /** 默认最近 30 天；最大 365 天。 */
    @GetMapping("/facts")
    public ApiResponse.Envelope<Dtos.InsightFactsDto> facts(
            @RequestParam(value = "days", required = false) Integer days) {
        return ApiResponse.ok(insightService.facts(days));
    }
}

package com.campus.server.web;

import com.campus.server.common.ApiResponse;
import com.campus.server.dto.Dtos;
import com.campus.server.service.InsightAnalysisService;
import com.campus.server.service.InsightService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回看：数据聚合 + 智能体分析。鉴权由 AuthInterceptor 统一处理；这里不接收 userId。
 *
 * <p><b>两个接口的职责分得很开</b>：
 * <ul>
 *   <li>{@code GET /insights/facts} 只返回结构化事实，不调模型、不做心理判断；</li>
 *   <li>{@code POST /insights/analyze} 基于同一份 facts 生成回看——
 *       事实由服务端按 token 重新取，<b>不信任请求体里的任何计数</b>。</li>
 * </ul>
 *
 * <p>返回 200 还是 4xx：数据不足、危机、模型失败都算「正常业务结局」，
 * 一律 200 + {@code status} 字段区分——前端要按 status 渲染不同界面，
 * 用 HTTP 状态码表达会让「危机」这类必须被展示的结果变成错误提示。
 * 只有未登录（401）、限流（429）才走错误通道。
 */
@RestController
@RequestMapping("/insights")
public class InsightController {

    private final InsightService insightService;
    private final InsightAnalysisService analysisService;

    public InsightController(InsightService insightService,
                             InsightAnalysisService analysisService) {
        this.insightService = insightService;
        this.analysisService = analysisService;
    }

    /** 默认最近 30 天；最大 365 天。 */
    @GetMapping("/facts")
    public ApiResponse.Envelope<Dtos.InsightFactsDto> facts(
            @RequestParam(value = "days", required = false) Integer days) {
        return ApiResponse.ok(insightService.facts(days));
    }

    /**
     * 让 AI 回看这段时间。
     *
     * <p>请求体只影响「多少天、能不能引用原话」，其余一律由服务端决定。
     * 四种结局都在响应体的 {@code status} 里：ok / insufficient_data / crisis / analysis_error。
     */
    @PostMapping("/analyze")
    public ApiResponse.Envelope<Dtos.InsightAnalyzeResponseDto> analyze(
            @RequestBody(required = false) Dtos.InsightAnalyzeRequest request) {
        return ApiResponse.ok(analysisService.analyze(request));
    }
}

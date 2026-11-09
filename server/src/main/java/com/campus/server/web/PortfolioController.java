package com.campus.server.web;

import com.campus.server.common.ApiResponse;
import com.campus.server.dto.Dtos;
import com.campus.server.service.PortfolioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人成长档案。只有当前登录用户自己的那一份，不存在「看别人的档案」这种接口。
 */
@RestController
@RequestMapping("/me")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    /** 我的参与轨迹：参加过的心理活动、生涯活动，按时间段聚合。 */
    @GetMapping("/portfolio")
    public ApiResponse.Envelope<Dtos.PortfolioDto> portfolio() {
        return ApiResponse.ok(portfolioService.ofCurrentUser());
    }
}

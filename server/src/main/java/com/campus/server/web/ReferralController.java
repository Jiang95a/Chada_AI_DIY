package com.campus.server.web;

import com.campus.server.common.ApiResponse;
import com.campus.server.dto.Dtos;
import com.campus.server.service.ReferralService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 转介工单。
 *
 * <p>两个入口分得很清楚：
 * <ul>
 *   <li>{@code POST /public/activities/{slug}/referrals} —— 参与者提交，<b>无需登录</b>；</li>
 *   <li>{@code GET /referrals}、{@code POST /referrals/{id}/status} —— 组织者处理，需登录且只能看自己的。</li>
 * </ul>
 */
@RestController
public class ReferralController {

    private final ReferralService referralService;

    public ReferralController(ReferralService referralService) {
        this.referralService = referralService;
    }

    /** 组织者待办列表。超过 24 小时未处理的带出 pendingHours，前端标红。 */
    @GetMapping("/referrals")
    public ApiResponse.Envelope<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(referralService.listForOwner(status, limit));
    }

    /** 组织者标记状态：contacted（已联系）/ closed（已转介 / 已处理完毕）。 */
    @PostMapping("/referrals/{id}/status")
    public ApiResponse.Envelope<Dtos.ReferralUpdateDto> updateStatus(
            @PathVariable String id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(referralService.updateStatus(id, body == null ? null : body.get("status")));
    }
}

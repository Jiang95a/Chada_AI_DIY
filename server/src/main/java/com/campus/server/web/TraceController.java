package com.campus.server.web;

import com.campus.server.common.ApiResponse;
import com.campus.server.dto.Dtos;
import com.campus.server.service.TraceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 参与轨迹。契约见 {@code web/src/services/traces.ts} 的文件头注释——
 * 前端先写好这两个接口按「后端未就绪时静默降级」处理，所以路径和形状必须严格对齐，
 * 否则前端不会报错，只会一直显示「只存在这台设备」，很难察觉。
 *
 * <p>路径里的 {@code {activityId}} 实际是活动的 public_slug（见
 * {@code web/src/components/public/DynamicParticipation.vue:33}
 * {@code const activityId = computed(() => props.slug)}），由服务端反查 UUID。
 *
 * <p>全部接口都要求登录：{@code AuthInterceptor} 的 PUBLIC_PREFIXES 不含 /traces。
 * 未登录返回 401，前端同样走降级分支。
 */
@RestController
@RequestMapping("/traces")
public class TraceController {

    private final TraceService traceService;

    public TraceController(TraceService traceService) {
        this.traceService = traceService;
    }

    /**
     * 取某个活动下我自己的轨迹。
     *
     * <p>没有记录时返回 {@code data: null} 而不是 404——这是前端契约要求的：
     * {@code traces.ts:17} 的 unwrapTrace 把 null 当作「远端没有，用本地的」，
     * 而 404 会被当成「后端不支持」，两条分支的 UI 文案不一样。
     */
    @GetMapping
    public ApiResponse.Envelope<Dtos.ParticipantTraceDto> get(@RequestParam("activityId") String activityId) {
        return ApiResponse.ok(traceService.get(activityId));
    }

    /** 整条覆盖保存。前端每 1.4 秒防抖同步一次（stores/trace.ts:151）。 */
    @PutMapping("/{activityId}")
    public ApiResponse.Envelope<Dtos.ParticipantTraceDto> save(@PathVariable("activityId") String activityId,
                                                              @Valid @RequestBody Dtos.SaveTraceRequest request) {
        return ApiResponse.ok(traceService.save(activityId, request));
    }

    /** 「我的轨迹」页：跨活动列出全部轨迹，按更新时间倒序。 */
    @GetMapping("/list")
    public ApiResponse.Envelope<List<Dtos.TraceListItemDto>> list(
            @RequestParam(value = "limit", required = false) Integer limit) {
        return ApiResponse.ok(traceService.list(limit));
    }

    /** 删除某活动下的轨迹。删不存在的也算成功。 */
    @DeleteMapping("/{activityId}")
    public ApiResponse.Envelope<Void> delete(@PathVariable("activityId") String activityId) {
        traceService.delete(activityId);
        return ApiResponse.ok(null);
    }
}

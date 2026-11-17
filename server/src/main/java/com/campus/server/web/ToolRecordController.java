package com.campus.server.web;

import com.campus.server.common.ApiResponse;
import com.campus.server.dto.Dtos;
import com.campus.server.service.ToolRecordService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工具记录（能量牌局 / 三栏）。
 *
 * <p>路径是 {@code /api/v1/tools/records}，三个接口都要求登录：
 * {@code AuthInterceptor} 的放行前缀里没有 /tools，未登录会直接 401。
 * 前端据此降级为「只存本机」——这是施工需求 2.1 明确要求的：
 * <b>未登录保存不能被 401 阻断本地记录</b>。
 *
 * <p>这几个接口不接收 userId：一律从 token 取（见 {@link ToolRecordService}）。
 */
@RestController
@RequestMapping("/tools/records")
public class ToolRecordController {

    private final ToolRecordService toolRecordService;

    public ToolRecordController(ToolRecordService toolRecordService) {
        this.toolRecordService = toolRecordService;
    }

    @PostMapping
    public ApiResponse.Envelope<Dtos.ToolRecordDto> create(
            @RequestBody Dtos.ToolRecordCreateRequest request) {
        return ApiResponse.ok(toolRecordService.create(request));
    }

    /** 默认最近 30 天；最大 365 天。 */
    @GetMapping
    public ApiResponse.Envelope<List<Dtos.ToolRecordDto>> list(
            @RequestParam(value = "days", required = false) Integer days) {
        return ApiResponse.ok(toolRecordService.list(days));
    }

    @DeleteMapping("/{id}")
    public ApiResponse.Envelope<String> delete(@PathVariable("id") String id) {
        toolRecordService.delete(id);
        return ApiResponse.ok(id);
    }
}

package com.campus.server.web;

import com.campus.server.common.ApiResponse;
import com.campus.server.dto.Dtos;
import com.campus.server.service.SubmissionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 组织者查看某个项目的报名提交列表。
 *
 * <p>列表走 {@link SubmissionService#listDetailed}：多带隐私档位与匿名编号。
 * 匿名记录不返回任何可回指到人的字段（submitterHash 不出网）。
 */
@RestController
@RequestMapping("/projects/{projectId}/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @GetMapping
    public ApiResponse.PagedEnvelope<java.util.List<Dtos.SubmissionDetailDto>> list(
            @PathVariable String projectId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Dtos.PagedResultHolder<Dtos.SubmissionDetailDto> result =
                submissionService.listDetailed(projectId, status, page, pageSize);
        return ApiResponse.page(result.data(), result.meta().total(),
                result.meta().page(), result.meta().pageSize());
    }
}

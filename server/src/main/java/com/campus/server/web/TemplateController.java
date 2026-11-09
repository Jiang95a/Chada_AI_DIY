package com.campus.server.web;

import com.campus.server.common.ApiResponse;
import com.campus.server.dto.Dtos;
import com.campus.server.service.TemplateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 模板列表，对应 web/src/components/editor/TemplatePanel.vue 与 CreateModal.vue。 */
@RestController
@RequestMapping("/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public ApiResponse.Envelope<List<Dtos.TemplateDto>> list(
            @RequestParam(required = false) String category,
            /** wellbeing | career | general；不传返回全部。 */
            @RequestParam(required = false) String kind) {
        return ApiResponse.ok(templateService.list(category, kind));
    }

    @GetMapping("/{id}")
    public ApiResponse.Envelope<Dtos.TemplateDto> get(@PathVariable String id) {
        return ApiResponse.ok(templateService.get(id));
    }
}

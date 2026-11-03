package com.campus.server.service;

import com.campus.server.common.BizException;
import com.campus.server.dto.Dtos;
import com.campus.server.entity.MiscEntities.ExportJobEntity;
import com.campus.server.entity.FormSubmissionEntity;
import com.campus.server.mapper.MiscMapper;
import com.campus.server.support.Ids;
import com.campus.server.support.JsonUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 报名数据导出。
 *
 * <p>V1 走同步生成：POST 时立即把 CSV 生成到本地目录，状态直接 succeeded。
 * 之所以还保留 export_jobs 表的 job 语义，是为了以后换 OSS + 异步不用改接口形状。
 */
@Service
public class ExportService {

    private final MiscMapper miscMapper;
    private final SubmissionService submissionService;
    private final FileStorage storage;
    private final com.campus.server.config.AppProperties props;
    private final ProjectService projectService;

    public ExportService(MiscMapper miscMapper, SubmissionService submissionService,
                         FileStorage storage, com.campus.server.config.AppProperties props,
                         ProjectService projectService) {
        this.miscMapper = miscMapper;
        this.submissionService = submissionService;
        this.storage = storage;
        this.props = props;
        this.projectService = projectService;
    }

    @Transactional
    public Dtos.ExportJobDto create(String projectId) {
        // 隐私设置里可能关了导出；先拦，免得"导出一份不该存在的表"
        submissionService.assertExportAllowed(projectId);
        List<FormSubmissionEntity> rows = submissionService.allForExport(projectId);

        ExportJobEntity job = new ExportJobEntity();
        job.setId(Ids.uuid());
        job.setProjectId(projectId);
        job.setRequestedBy(com.campus.server.security.CurrentUser.require().userId());
        job.setStatus("running");
        ObjectNode filters = JsonUtil.mapper().createObjectNode();
        filters.put("count", rows.size());
        job.setFilters(filters);
        job.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(24));
        miscMapper.insertExportJob(job);

        try {
            String objectKey = "exports/" + projectId + "/" + job.getId() + ".csv";
            storage.write(objectKey, toCsv(rows).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            miscMapper.updateExportJob(job.getId(), "succeeded", objectKey, null,
                    LocalDateTime.now(ZoneOffset.UTC));
        } catch (Exception ex) {
            miscMapper.updateExportJob(job.getId(), "failed", null, ex.getMessage(),
                    LocalDateTime.now(ZoneOffset.UTC));
        }
        return toDto(miscMapper.findExportJob(job.getId()));
    }

    public Dtos.ExportJobDto get(String exportId) {
        ExportJobEntity job = miscMapper.findExportJob(exportId);
        if (job == null) {
            throw BizException.notFound("导出任务不存在");
        }
        requireOwner(job);
        return toDto(job);
    }

    public List<Dtos.ExportJobDto> list(String projectId) {
        projectService.loadOwned(projectId);
        String userId = com.campus.server.security.CurrentUser.require().userId();
        return miscMapper.listExportJobs(projectId, userId).stream().map(this::toDto).toList();
    }

    public String downloadUrl(String exportId) {
        ExportJobEntity job = miscMapper.findExportJob(exportId);
        if (job == null) {
            throw BizException.notFound("导出任务不存在");
        }
        if (job.getObjectKey() == null) {
            throw BizException.notFound("导出文件不存在或未生成完成");
        }
        // 导出的是报名者的个人信息，不能像图片那样匿名可读：
        // 先确认当前登录用户就是发起导出的人，再返回带一次性凭证的下载地址。
        String userId = com.campus.server.security.CurrentUser.require().userId();
        if (!userId.equals(job.getRequestedBy())) {
            throw BizException.forbidden("这个导出文件不属于你");
        }
        return storage.publicUrl(job.getObjectKey()) + "?t=" + Ids.sha256(job.getId() + ":" + userId);
    }

    private String toCsv(List<FormSubmissionEntity> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // 让 Excel 正确识别 UTF-8
        sb.append("提交ID,时间,页面,表单组件,状态,隐私档位,匿名编号,是否同意,答案\n");
        for (FormSubmissionEntity row : rows) {
            sb.append(csv(row.getId())).append(',')
              .append(csv(Ids.iso(row.getSubmittedAt()))).append(',')
              .append(csv(row.getPageId())).append(',')
              .append(csv(row.getFormComponentId())).append(',')
              .append(csv(row.getStatus())).append(',')
              .append(csv(row.getPrivacyModeSnapshot())).append(',')
              // 匿名记录导出时给匿名编号，方便现场核对，但回指不到人
              .append(csv(row.isAnonymous() ? row.getAnonymousToken() : "")).append(',')
              .append(csv(row.getConsentId() != null ? "已同意" : "")).append(',')
              .append(csv(row.getAnswers() == null ? "" : row.getAnswers().toString()))
              .append('\n');
        }
        return sb.toString();
    }

    private String csv(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.replace("\r", " ").replace("\n", " ");
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private Dtos.ExportJobDto toDto(ExportJobEntity job) {
        return new Dtos.ExportJobDto(job.getId(), job.getProjectId(), job.getStatus(),
                job.getObjectKey(), job.getErrorMessage(), Ids.iso(job.getCreatedAt()),
                Ids.iso(job.getFinishedAt()));
    }

    private void requireOwner(ExportJobEntity job) {
        String userId = com.campus.server.security.CurrentUser.require().userId();
        if (!userId.equals(job.getRequestedBy())) {
            throw BizException.forbidden("这个导出任务不属于你");
        }
    }
}

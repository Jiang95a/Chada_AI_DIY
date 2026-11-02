package com.campus.server.service;

import com.campus.server.common.BizException;
import com.campus.server.dto.Dtos;
import com.campus.server.entity.AiJobEntity;
import com.campus.server.entity.AiProposalEntity;
import com.campus.server.entity.ProjectEntity;
import com.campus.server.mapper.AiMapper;
import com.campus.server.security.CurrentUser;
import com.campus.server.support.Ids;
import com.campus.server.support.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * AI 相关能力。
 *
 * <p><b>分工</b>：真正的模型编排在 {@code agent/}（FastAPI，8000 端口），
 * 后端只负责 (1) 转调 agent 的 POST /chat，(2) 把生成的页面落成 ai_proposals 供人工「采用」。
 *
 * <p><b>降级是硬要求</b>：agent 不通或没配 Key 时，这里抛 503 AGENT_UNAVAILABLE，
 * 但绝不把异常抛成 500 把前端页面打挂 —— 前端会退到本地模板（{@code web/src/services/planner.ts}）。
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final AiMapper aiMapper;
    private final RestClient agentClient;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private DocumentRepair documentRepair;

    @Autowired
    private com.campus.server.common.PageDocumentValidator documentValidator;

    public AiService(AiMapper aiMapper, RestClient agentRestClient) {
        this.aiMapper = aiMapper;
        this.agentClient = agentRestClient;
    }

    // ================================================================ 转调 agent

    /** 透传一次 /chat。前端 AiPanel 也走这条。 */
    public JsonNode chat(String message, String threadId, String systemExtra) {
        if (message == null || message.isBlank()) {
            throw BizException.invalid("message 不能为空");
        }
        ObjectNode payload = JsonUtil.mapper().createObjectNode();
        payload.put("message", message);
        if (threadId != null && !threadId.isBlank()) {
            payload.put("thread_id", threadId);
        }
        if (systemExtra != null && !systemExtra.isBlank()) {
            payload.put("system_extra", systemExtra);
        }

        try {
            JsonNode response = agentClient.post()
                    .uri("/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(JsonUtil.toJson(payload))
                    .retrieve()
                    .body(JsonNode.class);
            return response == null ? JsonUtil.mapper().createObjectNode() : response;
        } catch (RestClientException ex) {
            log.warn("调用 agent /chat 失败: {}", ex.getMessage());
            throw new com.campus.server.common.BizException(
                    com.campus.server.common.ErrorCode.AGENT_UNAVAILABLE,
                    "智能体暂时不可用，请先用手动方式搭建页面");
        }
    }

    /** 探活：GET {agent}/health，失败返回 false 而不抛异常。 */
    public boolean agentAlive() {
        try {
            JsonNode body = agentClient.get().uri("/health").retrieve().body(JsonNode.class);
            return body != null && body.path("ok").asBoolean(false);
        } catch (Exception ex) {
            return false;
        }
    }

    /** 海报解析统一经业务后端转发，前端不再直连 8000。 */
    public JsonNode extractPoster(String dataUrl, String imageUrl) {
        try {
            JsonNode response;
            if (imageUrl != null && !imageUrl.isBlank()) {
                response = agentClient.post()
                        .uri(uriBuilder -> uriBuilder.path("/poster/extract")
                                .queryParam("url", imageUrl)
                                .build())
                        .retrieve()
                        .body(JsonNode.class);
            } else {
                if (dataUrl == null || dataUrl.isBlank()) {
                    throw BizException.invalid("dataUrl 或 url 至少提供一个");
                }
                ObjectNode payload = JsonUtil.mapper().createObjectNode();
                payload.put("dataUrl", dataUrl);
                response = agentClient.post()
                        .uri("/poster/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(JsonUtil.toJson(payload))
                        .retrieve()
                        .body(JsonNode.class);
            }
            return response == null ? JsonUtil.mapper().createObjectNode() : response;
        } catch (BizException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("调用 agent /poster/extract 失败: {}", ex.getMessage());
            throw new com.campus.server.common.BizException(
                    com.campus.server.common.ErrorCode.AGENT_UNAVAILABLE,
                    "海报识别暂时不可用，请手动填写活动信息");
        }
    }

    // ================================================================ 生成提案

    /**
     * 让 agent 按提示词生成一份 PageDocument 候选稿，落成 ai_proposals。
     * <b>不直接改项目草稿</b>——必须由用户在编辑器里点「采用」才会生效（人在环）。
     */
    @Transactional
    public Dtos.AiProposalDto generateFromPrompt(String projectId, String prompt,
                                                 long baseRevision, String clientRequestId) {
        String userId = CurrentUser.require().userId();
        ProjectEntity project = projectService.loadOwned(projectId);

        // 同一次点击重发（前端连点、网络重试）直接返回原来那条提案
        if (clientRequestId != null && !clientRequestId.isBlank()) {
            AiJobEntity done = aiMapper.findLatestJobByClientRequest(userId, clientRequestId);
            if (done != null && "succeeded".equals(done.getStatus())) {
                AiProposalEntity existing = aiMapper.findProposalByJobId(done.getId());
                if (existing != null) {
                    return toProposalDto(existing);
                }
            }
        }
        if (prompt == null || prompt.isBlank()) {
            throw BizException.invalid("prompt 不能为空");
        }

        AiJobEntity job = new AiJobEntity();
        job.setId(Ids.uuid());
        job.setProjectId(projectId);
        job.setUserId(userId);
        job.setKind("generate_from_prompt");
        job.setStatus("running");
        job.setClientRequestId(clientRequestId == null || clientRequestId.isBlank() ? null : clientRequestId);
        ObjectNode input = JsonUtil.mapper().createObjectNode();
        input.put("prompt", prompt);
        job.setInputPayload(input);
        job.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
        aiMapper.insertJob(job);

        JsonNode document;
        DocumentRepair.RepairResult repaired;
        List<String> repairNotes;
        try {
            JsonNode reply = chat(prompt, null, PLAN_SYSTEM_EXTRA);
            String text = reply.path("reply").asText("");
            document = extractJson(text);
            if (document == null) {
                markFailed(job.getId(), "FORMAT", "模型没有返回可解析的 JSON");
                throw new com.campus.server.common.BizException(
                        com.campus.server.common.ErrorCode.AGENT_UNAVAILABLE,
                        "智能体这次没给出可用的结构，请重试或改用手动搭建");
            }
            // agent 产出的是「DocPlan」：页面和组件都没有 id，也没有 slug/theme/meta 兜底字段。
            // 原样存进去的话，用户点「采用」会被 schema 校验拦成 422 —— 所以入库前先修成完整 PageDocument。
            repaired = documentRepair.repair(document, project == null ? null : project.getTitle(),
                    project == null ? null : project.getPublicSlug());
            if (repaired == null) {
                markFailed(job.getId(), "DOCUMENT", "智能体返回的页面结构里没有任何可用页面");
                throw new com.campus.server.common.BizException(
                        com.campus.server.common.ErrorCode.AGENT_UNAVAILABLE,
                        "智能体这次没给出可用的页面结构，请重试或改用手动搭建");
            }
            document = repaired.document();
            repairNotes = repaired.notes();
            if (!repairNotes.isEmpty()) {
                log.info("AI 候选稿已自动修复 {} 处：{}", repairNotes.size(), repairNotes);
            }
            // 修完还必须过一遍 schema —— 修复器不吞错，修不好就在这里变成 503，
            // 而不是存进 ai_proposals 等到 apply 时才 422（那是队友反馈的原始问题）。
            documentValidator.validateOrThrow(document);
        } catch (com.campus.server.common.BizException ex) {
            // 格式错误上面已经记过了，这里统一在 catch 里留痕
            if (ex.getCode() == com.campus.server.common.ErrorCode.AGENT_UNAVAILABLE) {
                markFailed(job.getId(), com.campus.server.common.ErrorCode.AGENT_UNAVAILABLE.name(),
                        ex.getMessage());
            }
            throw ex;
        } catch (Exception ex) {
            markFailed(job.getId(), "DOCUMENT", "候选稿校验未通过: " + ex.getMessage());
            log.warn("AI 候选稿修复后仍无法通过校验: {}", ex.getMessage());
            throw new com.campus.server.common.BizException(
                    com.campus.server.common.ErrorCode.AGENT_UNAVAILABLE,
                    "智能体返回的页面结构不完整，已尝试自动修复但仍不可用，请重试或改用手动搭建");
        }

        aiMapper.updateJobResult(job.getId(), "succeeded", JsonUtil.toJson(document),
                null, null, LocalDateTime.now(ZoneOffset.UTC), LocalDateTime.now(ZoneOffset.UTC));

        ObjectNode target = JsonUtil.mapper().createObjectNode();
        target.put("kind", "document");

        AiProposalEntity proposal = new AiProposalEntity();
        proposal.setId(Ids.uuid());
        proposal.setJobId(job.getId());
        proposal.setProjectId(projectId);
        proposal.setBaseRevision(baseRevision);
        proposal.setTarget(target);
        // summary 直接告诉用户「这次会改动什么」，前端不需要自己也复制一份 diff 逻辑
        String summary = documentRepair.diffSummary(project == null ? null : project.getDraftDocument(), document);
        proposal.setSummary(repairNotes.isEmpty() ? summary : summary + "（服务端已自动补齐 " + repairNotes.size() + " 处结构）");
        proposal.setCandidateDocument(document);
        // operations 存「采用后怎么撤回」——前端撤销时按它回滚，而不是让用户自己找历史版本
        proposal.setOperations(buildUndoOperations(project, document, baseRevision));
        proposal.setRationale(repairNotes.isEmpty()
                ? JsonUtil.mapper().createArrayNode()
                : JsonUtil.mapper().valueToTree(repairNotes));
        proposal.setRisk("medium");
        proposal.setStatus("proposed");
        aiMapper.insertProposal(proposal);

        AiProposalEntity saved = aiMapper.findProposal(proposal.getId());
        if (saved == null) {
            // 理论上不可能（insert 刚成功），但宁可报明确错误也不要把 NPE 抛给前端
            throw new com.campus.server.common.BizException(
                    com.campus.server.common.ErrorCode.INTERNAL_ERROR,
                    "提案已生成但读取失败，请刷新项目后重试");
        }
        return toProposalDto(saved);
    }

    public Dtos.AiProposalDto getProposal(String proposalId) {
        AiProposalEntity proposal = aiMapper.findProposal(proposalId);
        if (proposal == null) {
            throw BizException.notFound("提案不存在");
        }
        projectService.loadOwned(proposal.getProjectId());
        return toProposalDto(proposal);
    }

    /**
     * 采用提案：把 candidateDocument 写进项目草稿。
     *
     * <p>两个防呆：
     * <ol>
     *   <li>baseRevision 与服务端当前 draft_revision 不一致 → 409（提案已过期，status 改成 stale）
     *   <li>候选人文档先过 JSON Schema 校验，不过就 422
     * </ol>
     */
    @Transactional
    public Dtos.DraftDto applyProposal(String proposalId, long baseRevision) {
        AiProposalEntity proposal = aiMapper.findProposal(proposalId);
        if (proposal == null) {
            throw BizException.notFound("提案不存在");
        }
        if (!"proposed".equals(proposal.getStatus())) {
            throw new com.campus.server.common.BizException(
                    com.campus.server.common.ErrorCode.REVISION_CONFLICT,
                    "这条提案已经" + ("applied".equals(proposal.getStatus()) ? "被采用过" : "失效") + "了");
        }

        ProjectEntity project = projectService.loadOwned(proposal.getProjectId());
        if (project.getDraftRevision() != baseRevision) {
            aiMapper.markStale(proposalId);
            throw new com.campus.server.common.BizException(
                    com.campus.server.common.ErrorCode.REVISION_CONFLICT,
                    "文档已被修改，当前服务端版本是 " + project.getDraftRevision() + "，请刷新后再采用");
        }

        JsonNode candidate = proposal.getCandidateDocument();
        if (candidate == null || !candidate.isObject()) {
            throw BizException.invalid("提案内容为空");
        }

        // 兜底再修一次：老提案是修复器上线前生成的，里面仍是缺 id 的 DocPlan。
        // 这一步保证「库里历史遗留的脏提案」也能被采用，而不是一路 422 把用户堵死。
        DocumentRepair.RepairResult repaired = documentRepair.repair(candidate,
                project.getTitle(), project.getPublicSlug());
        if (repaired == null) {
            throw BizException.invalid("提案里的页面结构已经损坏，无法采用，请重新生成");
        }
        candidate = repaired.document();
        try {
            documentValidator.validateOrThrow(candidate);
        } catch (com.campus.server.common.BizException ex) {
            log.warn("提案 {} 修复后仍不合法: {}", proposalId, ex.getMessage(), ex);
            throw new com.campus.server.common.BizException(
                    com.campus.server.common.ErrorCode.PAGE_DOCUMENT_INVALID,
                    "提案内容不合法，无法应用：" + ex.getMessage());
        }
        if (!repaired.notes().isEmpty()) {
            log.info("采用提案 {} 时补修 {} 处：{}", proposalId, repaired.notes().size(), repaired.notes());
        }

        Dtos.SaveDraftRequest save = new Dtos.SaveDraftRequest(project.getDraftRevision(), candidate);
        projectService.saveDraft(project.getId(), save);

        long newRevision = project.getDraftRevision() + 1;
        aiMapper.markApplied(proposalId, newRevision, CurrentUser.require().userId());

        return new Dtos.DraftDto(newRevision, candidate, Ids.nowIso());
    }

    /**
     * 生成「怎么撤销这次采用」的说明，存进 {@code ai_proposals.operations}。
     *
     * <p>撤销不靠让用户自己翻历史版本，而是记下「恢复到哪一版 + 旧文档本体」，
     * 前端拿着这段结构去 {@code PUT /draft} 就能一步撤回。
     *
     * <p><b>形状必须是数组</b>：{@code ai_proposals.operations} 上有
     * {@code CHECK (json_type(operations) = 'ARRAY')}，写成对象会直接违反约束报 500。
     * 数组元素是「操作」，以后要加别的操作（例如只回滚某一页）往数组里追加即可。
     *
     * <pre>
     * [ { "op": "restore-draft", "fromRevision": 7, "toRevision": 8,
     *     "pageCountBefore": 3, "pageCountAfter": 2, "document": {...} } ]
     * </pre>
     */
    private JsonNode buildUndoOperations(ProjectEntity project, JsonNode candidate, long baseRevision) {
        ArrayNode operations = JsonUtil.mapper().createArrayNode();
        ObjectNode undo = JsonUtil.mapper().createObjectNode();
        undo.put("op", "restore-draft");
        undo.put("fromRevision", baseRevision);
        undo.put("toRevision", baseRevision + 1);
        undo.put("pageCountBefore", countPages(project == null ? null : project.getDraftDocument()));
        undo.put("pageCountAfter", countPages(candidate));
        undo.put("note", "撤回时把草稿恢复到 revision=" + baseRevision + " 即可，下面的 document 就是那一版的内容");
        if (project != null && project.getDraftDocument() != null && !project.getDraftDocument().isNull()) {
            undo.set("document", project.getDraftDocument());
        }
        operations.add(undo);
        return operations;
    }

    private int countPages(JsonNode document) {
        if (document == null || !(document.get("pages") instanceof com.fasterxml.jackson.databind.node.ArrayNode pages)) {
            return 0;
        }
        return pages.size();
    }

    public Dtos.AiProposalDto toProposalDto(AiProposalEntity proposal) {
        return new Dtos.AiProposalDto(proposal.getId(), proposal.getJobId(), proposal.getProjectId(),
                proposal.getBaseRevision(), proposal.getTarget(), proposal.getSummary(),
                proposal.getCandidateDocument(), proposal.getOperations(), proposal.getRationale(),
                proposal.getRisk(), proposal.getStatus());
    }

    /** 给 /health 用。 */
    public Map<String, Object> status() {
        return Map.of("agentAlive", agentAlive());
    }

    // ================================================================ 内部工具

    private volatile String lastErrorCode;

    private void markFailed(String jobId, String errorCode, String message) {
        this.lastErrorCode = errorCode;
        aiMapper.updateJobResult(jobId, "failed", null, errorCode, message,
                LocalDateTime.now(ZoneOffset.UTC), LocalDateTime.now(ZoneOffset.UTC));
    }

    /** 规划器提示词：与前端 {@code planner.ts} 的 DocPlan 结构保持一致。 */
    static final String PLAN_SYSTEM_EXTRA =
            "你是校园活动页面规划器。只输出一个 JSON 对象，不要输出解释、不要在 JSON 外写文字、不要用代码块包裹。"
            + "结构：{\"intent\":\"\",\"reason\":\"\",\"title\":\"\","
            + "\"theme\":{\"primary\":\"#rrggbb\",\"accent\":\"#rrggbb\",\"bg\":\"#rrggbb\","
            + "\"text\":\"#rrggbb\",\"radius\":12,\"fontScale\":1,\"spacing\":12},"
            + "\"pages\":[{\"name\":\"首页\",\"role\":\"home\",\"blocks\":[{\"type\":\"banner\",\"props\":{}}]}]}。"
            + "pages 里至少有一页 role=home。block.type 只能是："
            + "nav, banner, heading, text, image, infoCard, button, form, radio, checkbox, qrcode, divider, countdown。";

    /**
     * 从模型回复里抠出第一个完整 JSON 对象（允许前后有解释文字或 ``` 包裹）。
     * 与前端 {@code extractJson} 逻辑一致，保证两端对同一段回复的解析结果相同。
     */
    static JsonNode extractJson(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String cleaned = text.replaceAll("(?i)```json", "```").replace("```", "");
        int start = cleaned.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    try {
                        return JsonUtil.mapper().readTree(cleaned.substring(start, i + 1));
                    } catch (Exception ex) {
                        return null;
                    }
                }
            }
        }
        return null;
    }
}

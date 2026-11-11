package com.campus.server.service;

import com.campus.server.common.BizException;
import com.campus.server.common.ErrorCode;
import com.campus.server.config.AppProperties;
import com.campus.server.dto.Dtos;
import com.campus.server.entity.AiJobEntity;
import com.campus.server.entity.ProjectEntity;
import com.campus.server.mapper.AiMapper;
import com.campus.server.mapper.ProjectMapper;
import com.campus.server.security.CurrentUser;
import com.campus.server.support.Ids;
import com.campus.server.support.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;

/**
 * AI 海报底图生成。
 *
 * <p><b>链路</b>：前端 → 本服务 → agent（/poster/generate）→ 图像模型。
 * 浏览器任何时候都不直连 agent，密钥也只在 .env 里。
 *
 * <p><b>为什么必须落盘</b>：上游图像服务给的 url 通常几小时就失效。
 * 如果直接把上游地址透传给前端，用户第二天打开页面会看到裂图，
 * 而这时图已经付过费了。所以拿到图就立刻写进 {@link FileStorage}，
 * 返回本服务自己的 /files 地址（该路径在鉴权白名单里，公开页也能显示）。
 *
 * <p><b>幂等</b>：同一次点击重发（前端连点、网络重试）带同一个 clientRequestId，
 * 这里直接返回上次的结果，不会二次付费。见 {@code ai_jobs.client_request_id}。
 *
 * <p><b>降级是硬要求</b>：没配图像模型时 agent 返回 503，这里翻译成
 * {@link ErrorCode#IMAGE_GENERATION_UNAVAILABLE}，前端提示改用上传海报；
 * 绝不假装生成成功，也绝不返回 placeholder 图。
 */
@Service
public class PosterService {

    private static final Logger log = LoggerFactory.getLogger(PosterService.class);

    /** 允许的图片 MIME，和 AssetController 的白名单保持一致。 */
    private static final java.util.Set<String> ALLOWED_MIME =
            java.util.Set.of("image/png", "image/jpeg", "image/webp");

    private final AiMapper aiMapper;
    private final FileStorage storage;
    private final AppProperties props;
    private final RestClient agentClient;
    private final ProjectMapper projectMapper;

    public PosterService(AiMapper aiMapper, FileStorage storage, AppProperties props,
                         RestClient agentRestClient, ProjectMapper projectMapper) {
        this.aiMapper = aiMapper;
        this.storage = storage;
        this.props = props;
        this.agentClient = agentRestClient;
        this.projectMapper = projectMapper;
    }

    /**
     * 生成一张海报底图并落盘。
     *
     * @param projectId       所属项目。当前前端不传也能用，但 ai_jobs.project_id 是 NOT NULL
     *                        且有外键指向 projects，所以不传时退化成「用户最近的项目」；
     *                        连一个项目都没有才会明确报错
     * @param prompt          画面描述（由前端根据公开的活动字段拼出来）
     * @param aspectRatio     3:4 / 4:3 / 1:1 / 16:9
     * @param style           版式风格标识
     * @param clientRequestId 幂等键，可为空（为空就不做幂等，每次都是新的一次付费调用）
     */
    public Dtos.PosterGenerateResponse generate(String projectId, String prompt, String aspectRatio,
                                                String style, String clientRequestId) {
        String userId = CurrentUser.require().userId();
        String requestId = clientRequestId == null || clientRequestId.isBlank() ? null : clientRequestId;
        String jobProjectId = resolveProjectId(projectId);

        // 同一次点击重发：直接返回上次落盘的那张图和同一个 jobId
        if (requestId != null) {
            AiJobEntity done = aiMapper.findLatestJobByClientRequest(userId, requestId);
            if (done != null && "succeeded".equals(done.getStatus()) && done.getResultPayload() != null) {
                JsonNode cached = done.getResultPayload();
                String url = cached.path("url").asText("");
                if (!url.isBlank()) {
                    return new Dtos.PosterGenerateResponse(done.getId(), url,
                            cached.path("prompt").asText(prompt),
                            cached.path("model").asText(""),
                            cached.path("aspectRatio").asText("3:4"),
                            cached.path("latencyMs").asInt(0), true);
                }
            }
        }

        AiJobEntity job = new AiJobEntity();
        job.setId(Ids.uuid());
        job.setProjectId(jobProjectId);
        job.setUserId(userId);
        job.setKind("poster_generate");
        job.setStatus("running");
        job.setClientRequestId(requestId);
        ObjectNode input = JsonUtil.mapper().createObjectNode();
        input.put("prompt", prompt);
        input.put("aspectRatio", aspectRatio == null ? "3:4" : aspectRatio);
        input.put("style", style == null ? "warm-editorial-campus" : style);
        job.setInputPayload(input);
        job.setModelProvider("agent");
        job.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
        aiMapper.insertJob(job);

        JsonNode reply;
        try {
            ObjectNode payload = JsonUtil.mapper().createObjectNode();
            payload.put("prompt", prompt);
            payload.put("aspectRatio", aspectRatio == null || aspectRatio.isBlank() ? "3:4" : aspectRatio);
            payload.put("style", style == null || style.isBlank() ? "warm-editorial-campus" : style);

            reply = agentClient.post()
                    .uri("/poster/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(JsonUtil.toJson(payload))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            // agent 按契约返回 503/422/502，这里要原样翻译成同样的语义，
            // 否则前端拿到的永远是笼统的「不可用」，分不清该改提示词还是该放弃
            String body = ex.getResponseBodyAsString();
            String message = extractMessage(body);
            int status = ex.getStatusCode().value();
            markFailed(job.getId(), status == 422 ? "PROMPT_INVALID" : "IMAGE_FAILED", message);
            log.warn("agent /poster/generate 返回 {}: {}", status, message);
            if (status == 503) {
                throw new BizException(ErrorCode.IMAGE_GENERATION_UNAVAILABLE, message);
            }
            if (status == 422) {
                throw new BizException(ErrorCode.IMAGE_PROMPT_INVALID, message);
            }
            throw new BizException(ErrorCode.IMAGE_GENERATION_FAILED, message);
        } catch (RestClientException ex) {
            markFailed(job.getId(), "AGENT_UNAVAILABLE", ex.getMessage());
            log.warn("调用 agent /poster/generate 失败: {}", ex.getMessage());
            throw new BizException(ErrorCode.IMAGE_GENERATION_UNAVAILABLE,
                    "海报生成服务暂时不可用，请改用上传已有海报");
        }

        String b64 = reply == null ? "" : reply.path("b64").asText("");
        String upstreamUrl = reply == null ? "" : reply.path("url").asText("");
        if (b64.isBlank()) {
            // 没能拿到图片二进制（上游给了 url 但下载失败）。此时绝不能返回上游地址假装成功，
            // 那个地址几小时后就会失效。
            markFailed(job.getId(), "IMAGE_EMPTY", "图像服务没有返回可保存的图片数据");
            throw new BizException(ErrorCode.IMAGE_GENERATION_FAILED,
                    "海报图片没能保存到服务器，请稍后重试或改用上传已有海报");
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException ex) {
            markFailed(job.getId(), "IMAGE_DECODE", "图像数据不是合法的 base64");
            throw new BizException(ErrorCode.IMAGE_GENERATION_FAILED, "海报图片数据损坏，请重试");
        }

        String ext = sniffExtension(bytes);
        String objectKey = "assets/" + userId + "/poster-" + job.getId() + ext;
        storage.write(objectKey, bytes);
        String publicUrl = storage.publicUrl(objectKey);

        String model = reply.path("model").asText("");
        String finalPrompt = reply.path("prompt").asText(prompt);
        String ratio = reply.path("aspectRatio").asText("3:4");
        int latencyMs = reply.path("latencyMs").asInt(0);

        ObjectNode result = JsonUtil.mapper().createObjectNode();
        result.put("url", publicUrl);
        result.put("objectKey", objectKey);
        result.put("prompt", finalPrompt);
        result.put("model", model);
        result.put("aspectRatio", ratio);
        result.put("latencyMs", latencyMs);
        result.put("sizeBytes", bytes.length);
        if (!upstreamUrl.isBlank()) {
            // 留痕：上游原始地址只给运营排查用，绝不会出现在响应体里
            result.put("upstreamUrl", upstreamUrl);
        }
        aiMapper.updateJobResult(job.getId(), "succeeded", JsonUtil.toJson(result), null, null,
                LocalDateTime.now(ZoneOffset.UTC), LocalDateTime.now(ZoneOffset.UTC));

        log.info("海报生成成功 job={} user={} model={} bytes={}", job.getId(), userId, model, bytes.length);
        return new Dtos.PosterGenerateResponse(job.getId(), publicUrl, finalPrompt, model, ratio,
                latencyMs, false);
    }

    /** 从 agent 的错误响应体里抠出人话，抠不到就用原文截断。 */
    private static String extractMessage(String body) {
        if (body == null || body.isBlank()) {
            return "海报生成失败";
        }
        try {
            JsonNode node = JsonUtil.mapper().readTree(body);
            String message = node.path("detail").path("message").asText("");
            if (!message.isBlank()) {
                return message;
            }
            message = node.path("detail").asText("");
            if (!message.isBlank()) {
                return message;
            }
        } catch (Exception ignored) {
            // 上游返回的不是 JSON，落到下面的原文截断
        }
        return body.length() > 300 ? body.substring(0, 300) : body;
    }

    /**
     * 按文件头判断扩展名，而不是相信上游说的 MIME。
     *
     * <p>上游有时会在响应里写 application/octet-stream，
     * 而浏览器靠扩展名决定能不能直接渲染，所以按魔数来判断。
     */
    private static String sniffExtension(byte[] bytes) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G') {
            return ".png";
        }
        if (bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8
                && bytes[2] == (byte) 0xFF) {
            return ".jpg";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return ".webp";
        }
        // 认不出来就当 png 落盘：多数图像模型默认出 png，
        // 即使判断错了，AssetController#read 也会按数据库里的 MIME 兜底为 octet-stream
        return ".png";
    }

    private void markFailed(String jobId, String errorCode, String message) {
        aiMapper.updateJobResult(jobId, "failed", null, errorCode, message,
                LocalDateTime.now(ZoneOffset.UTC), LocalDateTime.now(ZoneOffset.UTC));
    }

    /**
     * 决定这条 ai_jobs 记在哪个项目下。
     *
     * <p>为什么不肯把 project_id 做成可空：那要改 ai_jobs 的 NOT NULL 和外键，
     * 而 ai_proposals 与页面生成整条链路都压在这张表上，为一个字段放开全库约束不划算。
     * 折中方案是：调用方给了项目就校验归属；没给就退化成「用户最近的项目」。
     * 一个项目都没有的新用户才会被明确拒绝——这种情况本来也该先建项目。
     */
    private String resolveProjectId(String projectId) {
        if (projectId != null && !projectId.isBlank()) {
            ProjectEntity project = projectMapper.findById(projectId);
            if (project == null) {
                throw BizException.notFound("项目不存在");
            }
            if (!CurrentUser.require().userId().equals(project.getOwnerId())) {
                throw new BizException(ErrorCode.FORBIDDEN, "只能给自己的工作台生成海报");
            }
            return project.getId();
        }
        ProjectEntity latest = projectMapper.findLatestByOwner(CurrentUser.require().userId());
        if (latest == null) {
            throw BizException.invalid("请先创建一个活动项目，再生成海报");
        }
        return latest.getId();
    }
}

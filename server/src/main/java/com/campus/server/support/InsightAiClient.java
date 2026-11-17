package com.campus.server.support;

import com.campus.server.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 回看智能体的模型客户端：走 OpenAI 兼容的 {@code POST {base-url}/chat/completions}。
 *
 * <p><b>只做一件事</b>：把两段提示词发出去，把模型的文本拿回来。
 * 解析 JSON、校验证据、过滤诊断词都在 {@code InsightAnalysisService} 里做——
 * 这一层的职责越窄，出错时越容易判断是「模型没说人话」还是「我们没校验好」。
 *
 * <p><b>安全</b>：key 只在请求头里出现，不写日志、不进异常消息。
 * 失败时抛 {@link AiCallException}，消息里只有状态码和上游返回的简短 error.message。
 *
 * <p><b>为什么不用 RestClient</b>：需要给单次调用设超时。
 * RestClient 的超时要落到 request factory 上，会牵连全局配置；
 * 这里直接用 JDK HttpClient，超时就是这一次调用的属性。
 */
@Component
public class InsightAiClient {

    private static final Logger log = LoggerFactory.getLogger(InsightAiClient.class);
    private static final int MAX_ERROR_TEXT = 200;

    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient http;

    public InsightAiClient(AppProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** 模型是否配置齐全。没配时调用方直接返回 analysis_error，不发起请求。 */
    public boolean ready() {
        return props.getAi().ready();
    }

    public String modelName() {
        return props.getAi().getModel() == null ? "" : props.getAi().getModel();
    }

    /**
     * 发一次对话请求，返回模型给的原始文本。
     *
     * @throws AiCallException 网络失败、非 2xx、或返回结构里拿不到 content
     */
    public String complete(String systemPrompt, String userPrompt) {
        AppProperties.Ai ai = props.getAi();
        if (!ai.ready()) {
            throw new AiCallException("模型未配置");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", ai.getModel());
        // 回看要求稳定复述而非发挥；温度低一点，同一份 facts 两次结果才不至于南辕北辙
        body.put("temperature", 0.3);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

        HttpRequest request;
        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
            request = HttpRequest.newBuilder()
                    .uri(URI.create(stripTrailingSlash(ai.getBaseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofMillis(Math.max(5000, ai.getTimeoutMs())))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + ai.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
        } catch (Exception ex) {
            throw new AiCallException("构造模型请求失败：" + ex.getClass().getSimpleName());
        }

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new AiCallException("模型响应超时");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiCallException("模型调用被中断");
        } catch (Exception ex) {
            throw new AiCallException("连不上模型服务：" + ex.getClass().getSimpleName());
        }

        if (response.statusCode() / 100 != 2) {
            String hint = shortError(response.body());
            log.warn("回看模型返回 {}：{}", response.statusCode(), hint);
            throw new AiCallException("模型返回 " + response.statusCode() + (hint.isEmpty() ? "" : "：" + hint));
        }

        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new AiCallException("模型没有返回内容");
            }
            return content.asText();
        } catch (AiCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiCallException("模型返回的不是合法 JSON 响应");
        }
    }

    /**
     * 从上游错误体里抠一句人话。
     *
     * <p>只取最多 200 字，并且不打印请求体——
     * 请求体里有用户的原话（summary/change），进日志就等于把隐私写到了磁盘上。
     */
    private String shortError(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String message = root.path("error").path("message").asText("");
            if (message.isBlank()) {
                message = root.path("message").asText("");
            }
            if (!message.isBlank()) {
                return trimTo(message, MAX_ERROR_TEXT);
            }
        } catch (Exception ignored) {
            // 不是 JSON 就退回原始文本，同样截断
        }
        return trimTo(body, MAX_ERROR_TEXT);
    }

    private static String trimTo(String value, int max) {
        String text = value.replaceAll("\\s+", " ").trim();
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private static String stripTrailingSlash(String url) {
        String value = url == null ? "" : url.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /** 模型调用失败。消息里不含 key、不含用户原文。 */
    public static class AiCallException extends RuntimeException {
        public AiCallException(String message) {
            super(message);
        }
    }
}

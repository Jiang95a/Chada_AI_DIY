package com.campus.server.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 统一错误码。与 docs/api/openapi.yaml 的 Error.code 枚举一一对应。
 *
 * <p>前端按 code 分流（401 跳登录、409 提示冲突重试），不要按 message 匹配。
 */
@Getter
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "参数不合法"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "未登录或登录已过期"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "没有权限执行该操作"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),
    REVISION_CONFLICT(HttpStatus.CONFLICT, "文档版本冲突，请先拉取最新版本"),
    SLUG_CONFLICT(HttpStatus.CONFLICT, "该访问路径已被占用"),
    SUBMISSION_CLOSED(HttpStatus.CONFLICT, "活动已关闭，不再接受提交"),
    DUPLICATE_SUBMISSION(HttpStatus.CONFLICT, "你已经提交过了"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "文件超过大小限制"),
    PAGE_DOCUMENT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "页面文档结构不合法"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "操作过于频繁，请稍后再试"),
    AGENT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "智能体服务暂不可用，请先手动创建或使用模板"),
    /**
     * 图像生成能力没配（IMAGE_MODEL / IMAGE_API_KEY 为空）或被关闭。
     *
     * <p>刻意和 {@link #AGENT_UNAVAILABLE} 分开：前端对这两种情况的提示不一样——
     * 智能体挂了值得提醒「稍后重试」，而没配图像模型是长期状态，只能引导用户改用上传海报，
     * 不然用户会一直点重试。见 docs/poster-generation-api.md 的错误表。
     */
    IMAGE_GENERATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "暂未接入 AI 海报生成，请改用上传已有海报"),
    /**
     * 提示词不合规（太短、太长、带手机号/学号等个人信息）。
     *
     * <p>不能复用 {@link #INVALID_INPUT}：那个是 400，表示「请求根本没法处理」；
     * 这里是 422，表示「请求能处理，但内容我们不接受」，前端据此引导用户改提示词而不是刷新重来。
     * 也不能复用 {@link #PAGE_DOCUMENT_INVALID}：那是页面文档的错，前端会去弹编辑器。
     */
    IMAGE_PROMPT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "画面描述不合规，请换个说法"),
    /** 上游图像服务出错（限流、超时、返回格式不对）。可重试，但不能自动重试——每次都是一次付费调用。 */
    IMAGE_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "AI 海报生成失败，请稍后重试或改用上传已有海报"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");

    private final HttpStatus status;
    private final String defaultMessage;

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}

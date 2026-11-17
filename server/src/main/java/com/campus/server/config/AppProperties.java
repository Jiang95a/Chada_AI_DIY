package com.campus.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 里 app.* 的配置。全部可通过环境变量覆盖（见 docs/.env.example）。
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String webOrigin = "http://localhost:5173";
    /** 公开活动页的地址前缀，用于拼 publicUrl（指向前端，不是后端）。 */
    private String publicBaseUrl = "http://localhost:5173/p";
    /**
     * 后端自身的对外基地址，用于拼上传文件的绝对 URL。
     * 前端在 5173 时要访问 8080 上的文件，返回相对路径会 404，所以必须是绝对地址。
     */
    private String apiBaseUrl = "http://127.0.0.1:8080";
    private String agentBaseUrl = "http://127.0.0.1:8000";
    private int agentTimeoutSeconds = 90;
    /**
     * AI 海报生成的读取超时。
     *
     * <p>比 {@link #agentTimeoutSeconds} 长得多：图像模型普遍要 30~120 秒才出图，
     * 用 90 秒会「快的刚好够、慢的必超时」，用户白等，而上游往往已经计费。
     */
    private int imageTimeoutSeconds = 180;
    private Jwt jwt = new Jwt();
    private Upload upload = new Upload();
    private Ai ai = new Ai();

    @Data
    public static class Jwt {
        /**
         * JWT 签名密钥，至少 32 字节。
         *
         * <p><b>刻意不给默认值。</b>这里原本写死了一个开发密钥，等于把「签发合法 token 的能力」
         * 公开在仓库里 —— 任何人 clone 下来都能伪造任意用户的 token。而且由于它兜住了 null，
         * {@link com.campus.server.security.JwtService} 的长度校验也永远不会触发，
         * 运维漏配环境变量时服务照常启动，问题要到出事才被发现。
         *
         * <p>本地开发请在 {@code application-dev.yml}（dev profile）里提供，
         * 生产必须通过环境变量 {@code JWT_SECRET} 提供。
         */
        private String secret;
        private long accessTtlMinutes = 15;
        private long refreshTtlDays = 7;
    }

    @Data
    public static class Upload {
        private int maxMb = 10;
        private String dir = "./uploads";
    }

    /**
     * 回看智能体用的模型配置（施工需求 2.3）。
     *
     * <p><b>为什么不复用 agent 服务</b>：回看这件事要求「严格 JSON + 输出校验 +
     * 失败重试一次 + 按 facts 哈希缓存」，校验逻辑必须和落库在同一个事务边界里；
     * 放到 agent 那边就得再走一层网络，失败时也说不清是模型错还是链路错。
     * 所以后端直连 OpenAI 兼容的 /chat/completions。
     *
     * <p><b>为什么必须有 key 才能用</b>：{@link #ready()} 为 false 时
     * {@code /insights/analyze} 会返回 {@code analysis_error} 并说明「没配模型」，
     * 绝不假装分析成功，也绝不返回编造的结论。
     *
     * <p>安全：这几个值只从环境变量读，不写进前端、不进日志、不进 git。
     */
    @Data
    public static class Ai {
        /** 供应商标识，仅用于日志与复现（openai / deepseek / qwen / custom）。 */
        private String provider = "";
        /** OpenAI 兼容的 base url，例如 https://api.deepseek.com/v1。 */
        private String baseUrl = "";
        private String apiKey = "";
        /** 模型名，例如 deepseek-chat。 */
        private String model = "";
        /** 回看要生成一段结构化文本，60 秒足够；超时就返回 analysis_error 让用户重试。 */
        private int timeoutMs = 60000;

        public boolean ready() {
            return apiKey != null && !apiKey.isBlank()
                    && baseUrl != null && !baseUrl.isBlank()
                    && model != null && !model.isBlank();
        }

        /** 给 /health 和日志看的安全摘要：不回显 key，只说明配了没有。 */
        public String safeSummary() {
            return "provider=" + (provider == null || provider.isBlank() ? "(未配置)" : provider)
                    + " model=" + (model == null || model.isBlank() ? "(未配置)" : model)
                    + " key=" + (apiKey != null && !apiKey.isBlank() ? "已配置" : "未配置");
        }
    }
}

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

    @Data
    public static class Jwt {
        private String secret = "campus-diy-dev-secret-please-change-this-32bytes";
        private long accessTtlMinutes = 15;
        private long refreshTtlDays = 7;
    }

    @Data
    public static class Upload {
        private int maxMb = 10;
        private String dir = "./uploads";
    }
}

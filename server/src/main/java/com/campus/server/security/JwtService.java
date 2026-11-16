package com.campus.server.security;

import com.campus.server.common.BizException;
import com.campus.server.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与解析。
 *
 * <p>约定（docs/.env.example）：
 * Access 15 分钟、Refresh 7 天；Refresh 同时落库到 auth_sessions（存 sha256，不存明文）。
 */
@Component
public class JwtService {

    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final AppProperties props;
    private final SecretKey key;

    public JwtService(AppProperties props) {
        this.props = props;
        String secretValue = props.getJwt().getSecret();
        // 先判空再判长度：没有默认值之后，漏配时拿到的是 null 而不是空串，
        // 直接 .getBytes() 会抛 NPE，报错信息对运维毫无帮助。这里换成一句
        // 「缺哪个变量、本地怎么绕过」的提示。
        if (secretValue == null || secretValue.isBlank()) {
            throw new IllegalStateException(
                    "缺少 JWT_SECRET：JWT 签名密钥没有配置，服务无法启动。"
                            + "生产请设置环境变量 JWT_SECRET（至少 32 字节）；"
                            + "本地开发请用 dev profile 启动（application-dev.yml 里有开发用密钥）。");
        }
        byte[] secret = secretValue.getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT_SECRET 至少 32 字节，当前 " + secret.length + " 字节");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    /**
     * accessToken 的 jti 必须绑定到 session id，拦截器才能靠它回查会话是否已吊销。
     * 之前这里发的是随机 UUID，和 auth_sessions 毫无关系，导致「登出后 token 依旧可用」。
     */
    public Issued issueAccess(String userId, String email, String sessionId) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.getJwt().getAccessTtlMinutes(), ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .subject(userId)
                .id(sessionId)
                .claim("email", email)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
        return new Issued(token, exp);
    }

    public Issued issueRefresh(String userId, String sessionId) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.getJwt().getRefreshTtlDays(), ChronoUnit.DAYS);
        String token = Jwts.builder()
                .subject(userId)
                .id(sessionId)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
        return new Issued(token, exp);
    }

    /** 解析并校验签名与类型；失败统一抛 401。 */
    public Claims parse(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
                throw BizException.unauthenticated("令牌类型不正确");
            }
            return claims;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.unauthenticated("令牌无效或已过期");
        }
    }

    public long refreshTtlSeconds() {
        return props.getJwt().getRefreshTtlDays() * 24 * 3600;
    }

    public record Issued(String token, Instant expiresAt) {
    }
}

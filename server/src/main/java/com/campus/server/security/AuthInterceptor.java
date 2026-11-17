package com.campus.server.security;

import com.campus.server.common.BizException;
import com.campus.server.entity.AuthSessionEntity;
import com.campus.server.mapper.AuthSessionMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 鉴权拦截器。
 *
 * <p>规则与 openapi.yaml 对齐：
 * <ul>
 *   <li>{@code /public/**}、{@code /health}、{@code /auth/register|login|refresh} 放行
 *   <li>其余接口都要 {@code Authorization: Bearer <accessToken>}
 *   <li>需要当前用户的 Controller 方法只要调 {@link CurrentUser#require()} 即可
 * </ul>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String[] PUBLIC_PREFIXES = {
            "/health",
            "/ping",
            "/auth/register",
            "/auth/login",
            "/auth/refresh",
            "/public/",
            "/files/"
    };

    private final JwtService jwtService;
    private final AuthSessionMapper sessionMapper;

    public AuthInterceptor(JwtService jwtService, AuthSessionMapper sessionMapper) {
        this.jwtService = jwtService;
        this.sessionMapper = sessionMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (isPublic(path)) {
            // 公开接口允许匿名，但如果浏览器带了登录 token，仍然要认出当前用户。
            // 例如公开报名页：登录用户提交后，这条表单才能计入他的个人回看数据。
            try {
                authenticate(request);
            } catch (RuntimeException ignored) {
                CurrentUser.clear();
            }
            return true;
        }

        authenticate(request);
        return true;
    }

    private void authenticate(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw BizException.unauthenticated("缺少 Authorization 头");
        }
        String token = header.substring(7).trim();
        if (token.isEmpty()) {
            throw BizException.unauthenticated("令牌为空");
        }

        Claims claims = jwtService.parse(token, JwtService.TYPE_ACCESS);

        // 只验签是不够的：登出 / 刷新轮换之后会话已经 revoke，但旧 accessToken 签名依然有效。
        // 这里必须回查会话状态，否则「登出后还能继续调接口」。
        String sessionId = claims.getId();
        if (sessionId == null) {
            throw BizException.unauthenticated("令牌缺少会话标识，请重新登录");
        }
        AuthSessionEntity session = sessionMapper.findById(sessionId);
        if (session == null || session.getRevokedAt() != null) {
            throw BizException.unauthenticated("登录已失效，请重新登录");
        }
        if (session.getExpiresAt() != null
                && session.getExpiresAt().isBefore(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))) {
            throw BizException.unauthenticated("登录已过期，请重新登录");
        }

        CurrentUser current = new CurrentUser(claims.getSubject(), claims.get("email", String.class));
        CurrentUser.set(current);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        CurrentUser.clear();
    }

    private boolean isPublic(String path) {
        return Arrays.stream(PUBLIC_PREFIXES).anyMatch(path::startsWith);
    }
}

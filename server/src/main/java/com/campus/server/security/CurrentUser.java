package com.campus.server.security;

/**
 * 已登录用户的最小上下文。放在 ThreadLocal 里，由拦截器填充、请求结束清理。
 */
public record CurrentUser(String userId, String email) {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static CurrentUser require() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw com.campus.server.common.BizException.unauthenticated("请先登录");
        }
        return user;
    }

    /**
     * 登录了就返回 userId，没登录返回 null。
     *
     * <p>给公开接口用：公开活动里参与者在匿名档下同样可以提交转介请求，
     * 这时不该强制登录，但登录了就顺手把归属记上。
     */
    public static String optionalUserId() {
        CurrentUser user = HOLDER.get();
        return user == null ? null : user.userId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}

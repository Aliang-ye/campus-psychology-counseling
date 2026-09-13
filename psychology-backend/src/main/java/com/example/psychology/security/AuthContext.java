package com.example.psychology.security;

public final class AuthContext {
    private static final ThreadLocal<AuthUser> CURRENT = new ThreadLocal<>();

    private AuthContext() {}

    public static void set(AuthUser user) {
        CURRENT.set(user);
    }

    public static AuthUser get() {
        return CURRENT.get();
    }

    public static AuthUser require() {
        AuthUser user = CURRENT.get();
        if (user == null) {
            throw new UnauthorizedException("请先登录");
        }
        return user;
    }

    public static void clear() {
        CURRENT.remove();
    }
}

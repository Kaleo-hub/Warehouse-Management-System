package com.myproject.wmsserver.common.context;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录用户，用 ThreadLocal 存放。
 * 一次请求一个线程，所以 Service 里可以直接取，不用层层传参。
 */
public final class UserContext {

    private UserContext() {
    }

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Long getUserId() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.getUserId();
    }

    public static String getRole() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.getRole();
    }

    @Data
    @AllArgsConstructor
    public static class CurrentUser {
        private Long userId;
        private String username;
        private String role;
    }
}

package com.slothrag.common.logging;

import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

/**
 * 日志诊断上下文：把 sessionId 挂到当前线程 MDC，使同一会话的所有日志行带上会话 ID。
 * <p>
 * 会话链路是 SSE 异步流，回调线程无法继承主线程 MDC：
 * 入口处 open() 设置并返回快照，异步回调线程内用 with(快照) 恢复，随 try-with-resources 自动清理。
 */
public final class LoggingContext {

    public static final String SESSION_ID = "sessionId";

    private LoggingContext() {
    }

    /** 设置当前线程的 sessionId 并返回快照，供异步线程恢复 */
    public static Map<String, String> open(String sessionId) {
        if (sessionId != null) {
            MDC.put(SESSION_ID, shorten(sessionId, 24));
        }
        return snapshot();
    }

    /** close() 不抛受检异常，可直接用于 try-with-resources */
    @FunctionalInterface
    public interface LoggingScope extends AutoCloseable {
        @Override
        void close();
    }

    /** 在异步回调线程中恢复入口线程的上下文，配合 try-with-resources 自动清理 */
    public static LoggingScope with(Map<String, String> snapshot) {
        restore(snapshot);
        return LoggingContext::clear;
    }

    public static void clear() {
        MDC.remove(SESSION_ID);
    }

    public static Map<String, String> snapshot() {
        Map<String, String> copy = MDC.getCopyOfContextMap();
        return copy != null ? copy : new HashMap<>();
    }

    public static void restore(Map<String, String> snapshot) {
        if (snapshot != null && !snapshot.isEmpty()) {
            MDC.setContextMap(snapshot);
        }
    }

    private static String shorten(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

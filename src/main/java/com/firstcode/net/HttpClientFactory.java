package com.firstcode.net;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 共享 {@link HttpClient} 实例工厂。
 *
 * <p>Connect timeout 设为 10s,request timeout 由调用方使用
 * {@link HttpClient#send(java.net.http.HttpRequest, java.net.http.BodyHandlers)} 配合
 * {@link #requestTimeout()} 控制(60s,仅在调用方显式按字节流读取时生效)。
 *
 * <p>重定向被禁用,避免重定向过程中丢失 Authorization 等头部。
 */
public final class HttpClientFactory {

    private static final HttpClient SHARED;

    static {
        SHARED = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private HttpClientFactory() {
    }

    public static HttpClient create() {
        return SHARED;
    }

    public static Duration requestTimeout() {
        return Duration.ofSeconds(60);
    }
}

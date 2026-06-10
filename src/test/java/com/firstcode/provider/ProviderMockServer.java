package com.firstcode.provider;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 简单的本地 mock HTTP server,用于 Provider 单元测试。 */
final class ProviderMockServer implements AutoCloseable {

    private final HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger(0);

    ProviderMockServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    int port() {
        return server.getAddress().getPort();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    int requestCount() {
        return requestCount.get();
    }

    /** 注册一个 SSE 端点,/v1/messages 或 /chat/completions 都行。 */
    void sseEndpoint(String path, int status, List<String> sseEvents) {
        server.createContext(path, new SseHandler(status, sseEvents));
    }

    /** 注册一个 JSON 错误端点(非流式)。 */
    void jsonError(String path, int status, String body) {
        server.createContext(path, new JsonErrorHandler(status, body));
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private class SseHandler implements HttpHandler {
        private final int status;
        private final List<String> events;

        SseHandler(int status, List<String> events) {
            this.status = status;
            this.events = events;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();
            byte[] body = String.join("\n\n", events).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().add("Connection", "close");
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                    os.flush();
                }
            } else {
                exchange.close();
            }
        }
    }

    private class JsonErrorHandler implements HttpHandler {
        private final int status;
        private final String body;

        JsonErrorHandler(int status, String body) {
            this.status = status;
            this.body = body;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}

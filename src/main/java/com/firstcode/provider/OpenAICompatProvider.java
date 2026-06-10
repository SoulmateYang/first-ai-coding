package com.firstcode.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.firstcode.io.Sanitizer;
import com.firstcode.net.HttpClientFactory;
import com.firstcode.net.SseChunk;
import com.firstcode.net.SseParser;
import com.firstcode.session.Message;
import com.firstcode.session.Role;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * OpenAI Chat Completions 流式协议的兼容实现。
 *
 * <p>通过 {@code base_url} + {@code model} 区分不同厂商,以下后端均走本实现:
 * OpenAI 官方、DeepSeek、通义千问 Qwen、智谱 GLM 等。
 *
 * <p>注意:本实现忽略 OpenAI 端的 thinking 字段(spec F10);若上游返回 thinking,
 * 也会被解析但仅作为普通 content 的一部分。
 */
public final class OpenAICompatProvider implements Provider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DONE_MARKER = "[DONE]";

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient http;
    private final SseParser parser = new SseParser();

    public OpenAICompatProvider(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.http = HttpClientFactory.create();
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public void stream(List<Message> messages, StreamRequest request, Consumer<StreamEvent> sink) {
        try {
            doStream(messages, request, sink);
        } catch (ProviderException ex) {
            sink.accept(new StreamEvent.Error(ex));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            sink.accept(new StreamEvent.Error(new ProviderException("interrupted", ex)));
        } catch (Exception ex) {
            sink.accept(new StreamEvent.Error(new ProviderException(
                    Sanitizer.scrub(ex.getMessage() == null ? ex.toString() : ex.getMessage()), ex)));
        }
    }

    private void doStream(List<Message> messages, StreamRequest request, Consumer<StreamEvent> sink)
            throws IOException, InterruptedException {
        String body = buildRequestBody(messages, request);
        URI uri = URI.create(baseUrl + "/chat/completions");
        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("authorization", "Bearer " + apiKey)
                .header("content-type", "application/json")
                .timeout(HttpClientFactory.requestTimeout())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() / 100 != 2) {
            String errBody;
            try (InputStream in = resp.body()) {
                errBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            throw new ProviderException(
                    "openai HTTP " + resp.statusCode() + ": " + Sanitizer.scrub(errBody));
        }

        parser.reset();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                List<SseChunk> chunks = parser.feed(line + "\n");
                for (SseChunk c : chunks) {
                    handleChunk(c, sink);
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException();
                    }
                }
            }
            for (SseChunk c : parser.flush()) {
                handleChunk(c, sink);
            }
        }
    }

    private void handleChunk(SseChunk chunk, Consumer<StreamEvent> sink) {
        if (chunk.data() == null || chunk.data().isEmpty()) {
            return;
        }
        if (DONE_MARKER.equals(chunk.data().trim())) {
            sink.accept(StreamEvent.Done.INSTANCE);
            return;
        }
        try {
            JsonNode root = MAPPER.readTree(chunk.data());
            if (root.has("error")) {
                sink.accept(new StreamEvent.Error(new ProviderException(
                        "upstream error: " + Sanitizer.scrub(root.path("error").toString()))));
                return;
            }
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).path("delta");
                String content = delta.path("content").asText(null);
                if (content != null && !content.isEmpty()) {
                    sink.accept(new StreamEvent.Delta(content));
                }
                JsonNode reasoning = delta.path("reasoning_content");
                if (reasoning.isTextual() && !reasoning.asText().isEmpty()) {
                    // 透传 thinking(若有)
                    sink.accept(new StreamEvent.Thinking(reasoning.asText()));
                }
            }
        } catch (Exception ex) {
            sink.accept(new StreamEvent.Error(new ProviderException(
                    "failed to parse SSE chunk: " + ex.getMessage(), ex)));
        }
    }

    private String buildRequestBody(List<Message> messages, StreamRequest request) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("model", request.model() == null ? model : request.model());
            root.put("stream", true);

            ArrayNode arr = MAPPER.createArrayNode();
            if (request.systemPrompt() != null && !request.systemPrompt().isEmpty()) {
                ObjectNode sys = MAPPER.createObjectNode();
                sys.put("role", "system");
                sys.put("content", request.systemPrompt());
                arr.add(sys);
            }
            for (Message m : messages) {
                ObjectNode msg = MAPPER.createObjectNode();
                msg.put("role", m.role() == Role.USER ? "user" : "assistant");
                msg.put("content", m.content());
                arr.add(msg);
            }
            root.set("messages", arr);
            return MAPPER.writeValueAsString(root);
        } catch (Exception ex) {
            throw new ProviderException("failed to build request body", ex);
        }
    }
}

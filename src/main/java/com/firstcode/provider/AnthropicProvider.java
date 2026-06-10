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
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * Anthropic Messages API 适配器。
 *
 * <p>URL: {@code POST <baseUrl>/v1/messages}
 * <p>Headers: {@code x-api-key}, {@code anthropic-version}
 * <p>流式响应:处理 {@code content_block_delta}(text_delta / thinking_delta)与
 * {@code message_stop}。
 */
public final class AnthropicProvider implements Provider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient http;
    private final SseParser parser = new SseParser();

    public AnthropicProvider(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.http = HttpClientFactory.create();
    }

    @Override
    public String name() {
        return "anthropic";
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
        URI uri = URI.create(baseUrl + "/v1/messages");
        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
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
                    "anthropic HTTP " + resp.statusCode() + ": " + Sanitizer.scrub(errBody));
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
        String event = chunk.event();
        try {
            if ("message_stop".equals(event)) {
                sink.accept(StreamEvent.Done.INSTANCE);
                return;
            }
            if ("error".equals(event)) {
                sink.accept(new StreamEvent.Error(new ProviderException(
                        "anthropic stream error: " + Sanitizer.scrub(chunk.data()))));
                return;
            }
            if ("content_block_delta".equals(event)) {
                JsonNode root = MAPPER.readTree(chunk.data());
                JsonNode delta = root.path("delta");
                String type = delta.path("type").asText("");
                if ("text_delta".equals(type)) {
                    String text = delta.path("text").asText("");
                    if (!text.isEmpty()) {
                        sink.accept(new StreamEvent.Delta(text));
                    }
                } else if ("thinking_delta".equals(type)) {
                    String text = delta.path("thinking").asText("");
                    if (!text.isEmpty()) {
                        sink.accept(new StreamEvent.Thinking(text));
                    }
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
            root.put("max_tokens", 8192);

            if (request.systemPrompt() != null && !request.systemPrompt().isEmpty()) {
                root.put("system", request.systemPrompt());
            }
            if (request.thinkingEnabled()) {
                ObjectNode thinking = MAPPER.createObjectNode();
                thinking.put("type", "enabled");
                thinking.put("budget_tokens", 2048);
                root.set("thinking", thinking);
            }

            ArrayNode arr = MAPPER.createArrayNode();
            for (Message m : messages) {
                ObjectNode msg = MAPPER.createObjectNode();
                msg.put("role", m.role() == Role.USER ? "user" : "assistant");
                if (m.thinking() != null && !m.thinking().isEmpty()) {
                    ArrayNode content = MAPPER.createArrayNode();
                    ObjectNode thinkingBlock = MAPPER.createObjectNode();
                    thinkingBlock.put("type", "thinking");
                    thinkingBlock.put("thinking", m.thinking());
                    content.add(thinkingBlock);
                    ObjectNode textBlock = MAPPER.createObjectNode();
                    textBlock.put("type", "text");
                    textBlock.put("text", m.content());
                    content.add(textBlock);
                    msg.set("content", content);
                } else {
                    msg.put("content", m.content());
                }
                arr.add(msg);
            }
            root.set("messages", arr);
            return MAPPER.writeValueAsString(root);
        } catch (Exception ex) {
            throw new ProviderException("failed to build request body", ex);
        }
    }
}

package com.firstcode.provider;

import com.firstcode.session.Message;
import com.firstcode.session.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicProviderTest {

    private ProviderMockServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new ProviderMockServer();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void parsesCompleteStreamWithTextAndThinking() {
        server.sseEndpoint("/v1/messages", 200, List.of(
                "event: message_start\ndata: {\"type\":\"message_start\"}",
                "event: content_block_start\ndata: {\"type\":\"content_block_start\",\"content_block\":{\"type\":\"thinking\"}}",
                "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"Let me think.\"}}",
                "event: content_block_start\ndata: {\"type\":\"content_block_start\",\"content_block\":{\"type\":\"text\"}}",
                "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello \"}}",
                "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"world!\"}}",
                "event: message_stop\ndata: {\"type\":\"message_stop\"}"
        ));

        AnthropicProvider provider = new AnthropicProvider(
                server.baseUrl(), "sk-ant-test", "claude-test");

        List<StreamEvent> events = new ArrayList<>();
        provider.stream(
                List.of(Message.user("hi")),
                StreamRequest.of("claude-test", true),
                events::add);

        assertThat(events).hasSize(4);
        assertThat(events.get(0)).isInstanceOf(StreamEvent.Thinking.class);
        assertThat(((StreamEvent.Thinking) events.get(0)).text).isEqualTo("Let me think.");
        assertThat(events.get(1)).isInstanceOf(StreamEvent.Delta.class);
        assertThat(((StreamEvent.Delta) events.get(1)).text).isEqualTo("Hello ");
        assertThat(events.get(2)).isInstanceOf(StreamEvent.Delta.class);
        assertThat(((StreamEvent.Delta) events.get(2)).text).isEqualTo("world!");
        assertThat(events.get(3)).isInstanceOf(StreamEvent.Done.class);
    }

    @Test
    void parsesStreamWithoutThinking() {
        server.sseEndpoint("/v1/messages", 200, List.of(
                "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"ok\"}}",
                "event: message_stop\ndata: {\"type\":\"message_stop\"}"
        ));

        AnthropicProvider provider = new AnthropicProvider(
                server.baseUrl(), "sk-ant-test", "claude-test");

        List<StreamEvent> events = new ArrayList<>();
        provider.stream(List.of(Message.user("hi")), StreamRequest.of("claude-test", false), events::add);

        assertThat(events).hasSize(2);
        assertThat(((StreamEvent.Delta) events.get(0)).text).isEqualTo("ok");
        assertThat(events.get(1)).isInstanceOf(StreamEvent.Done.class);
    }

    @Test
    void http401SurfacesAsErrorEvent() {
        server.jsonError("/v1/messages", 401, "{\"error\":\"invalid api key sk-abcdefghij1234\"}");

        AnthropicProvider provider = new AnthropicProvider(
                server.baseUrl(), "sk-ant-test", "claude-test");

        List<StreamEvent> events = new ArrayList<>();
        provider.stream(List.of(Message.user("hi")), StreamRequest.of("claude-test"), events::add);

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(StreamEvent.Error.class);
        StreamEvent.Error err = (StreamEvent.Error) events.get(0);
        assertThat(err.cause).isInstanceOf(ProviderException.class);
        assertThat(err.cause.getMessage()).contains("401").doesNotContain("sk-abcdefghij1234");
    }

    @Test
    void messageHistoryIsSent() {
        server.sseEndpoint("/v1/messages", 200, List.of(
                "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"ack\"}}",
                "event: message_stop\ndata: {\"type\":\"message_stop\"}"
        ));

        AnthropicProvider provider = new AnthropicProvider(
                server.baseUrl(), "sk-ant-test", "claude-test");

        List<Message> history = List.of(
                Message.user("first"),
                Message.assistant("first reply"),
                Message.user("second"));
        List<StreamEvent> events = new ArrayList<>();
        provider.stream(history, StreamRequest.of("claude-test"), events::add);

        assertThat(events.get(events.size() - 1)).isInstanceOf(StreamEvent.Done.class);
    }

    @Test
    void preservesRole() {
        AnthropicProvider provider = new AnthropicProvider(
                "http://127.0.0.1:1", "sk-ant-test", "claude-test");
        assertThat(provider.name()).isEqualTo("anthropic");
    }

    @Test
    void thinkingEnabledRequestGoesThroughWithoutError() {
        server.sseEndpoint("/v1/messages", 200, List.of(
                "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"ok\"}}",
                "event: message_stop\ndata: {\"type\":\"message_stop\"}"
        ));

        AnthropicProvider provider = new AnthropicProvider(
                server.baseUrl(), "sk-ant-test", "claude-test");

        List<StreamEvent> events = new ArrayList<>();
        provider.stream(List.of(Message.user("hi")),
                StreamRequest.of("claude-test", true), events::add);

        assertThat(events.get(events.size() - 1)).isInstanceOf(StreamEvent.Done.class);
    }

    @Test
    void assistantMessageWithThinkingRendersBothBlocks() {
        server.sseEndpoint("/v1/messages", 200, List.of(
                "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"reply\"}}",
                "event: message_stop\ndata: {\"type\":\"message_stop\"}"
        ));

        AnthropicProvider provider = new AnthropicProvider(
                server.baseUrl(), "sk-ant-test", "claude-test");

        List<Message> history = List.of(
                Message.user("u"),
                Message.assistant("prior", "prior thinking"));
        List<StreamEvent> events = new ArrayList<>();
        provider.stream(history, StreamRequest.of("claude-test"), events::add);

        assertThat(events.get(events.size() - 1)).isInstanceOf(StreamEvent.Done.class);
    }

    @Test
    void malformedChunkSurfacesAsErrorEvent() {
        server.sseEndpoint("/v1/messages", 200, List.of(
                "event: content_block_delta\ndata: not-json{",
                "event: message_stop\ndata: {\"type\":\"message_stop\"}"
        ));

        AnthropicProvider provider = new AnthropicProvider(
                server.baseUrl(), "sk-ant-test", "claude-test");

        List<StreamEvent> events = new ArrayList<>();
        provider.stream(List.of(Message.user("hi")), StreamRequest.of("claude-test"), events::add);

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(StreamEvent.Error.class);
        assertThat(((StreamEvent.Error) events.get(0)).cause.getMessage())
                .contains("failed to parse SSE chunk");
        assertThat(events.get(1)).isInstanceOf(StreamEvent.Done.class);
    }

    @Test
    void midstreamErrorEventSurfacesAsError() {
        server.sseEndpoint("/v1/messages", 200, List.of(
                "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"ok\"}}",
                "event: error\ndata: {\"type\":\"error\",\"error\":{\"message\":\"upstream bad\"}}",
                "event: message_stop\ndata: {\"type\":\"message_stop\"}"
        ));

        AnthropicProvider provider = new AnthropicProvider(
                server.baseUrl(), "sk-ant-test", "claude-test");

        List<StreamEvent> events = new ArrayList<>();
        provider.stream(List.of(Message.user("hi")), StreamRequest.of("claude-test"), events::add);

        assertThat(events).hasSize(3);
        assertThat(((StreamEvent.Delta) events.get(0)).text).isEqualTo("ok");
        assertThat(events.get(1)).isInstanceOf(StreamEvent.Error.class);
        assertThat(((StreamEvent.Error) events.get(1)).cause.getMessage())
                .contains("anthropic stream error");
    }
}

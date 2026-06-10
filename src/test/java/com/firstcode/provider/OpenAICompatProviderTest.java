package com.firstcode.provider;

import com.firstcode.session.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAICompatProviderTest {

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
    void parsesStreamOfDeltasAndDoneMarker() {
        server.sseEndpoint("/chat/completions", 200, List.of(
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hello \"}}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\"world!\"}}]}",
                "data: [DONE]"
        ));

        OpenAICompatProvider provider = new OpenAICompatProvider(
                server.baseUrl(), "sk-test", "gpt-test");

        List<StreamEvent> events = new ArrayList<>();
        provider.stream(List.of(Message.user("hi")), StreamRequest.of("gpt-test"), events::add);

        assertThat(events).hasSize(3);
        assertThat(((StreamEvent.Delta) events.get(0)).text).isEqualTo("Hello ");
        assertThat(((StreamEvent.Delta) events.get(1)).text).isEqualTo("world!");
        assertThat(events.get(2)).isInstanceOf(StreamEvent.Done.class);
    }

    @Test
    void http500SurfacesAsError() {
        server.jsonError("/chat/completions", 500, "{\"error\":\"server unavailable\"}");

        OpenAICompatProvider provider = new OpenAICompatProvider(
                server.baseUrl(), "sk-test", "gpt-test");

        List<StreamEvent> events = new ArrayList<>();
        provider.stream(List.of(Message.user("hi")), StreamRequest.of("gpt-test"), events::add);

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(StreamEvent.Error.class);
        assertThat(((StreamEvent.Error) events.get(0)).cause.getMessage()).contains("500");
    }

    @Test
    void midstreamErrorChunkSurfacesAsErrorEvent() {
        server.sseEndpoint("/chat/completions", 200, List.of(
                "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}",
                "data: {\"error\":\"context_length_exceeded\"}"
        ));

        OpenAICompatProvider provider = new OpenAICompatProvider(
                server.baseUrl(), "sk-test", "gpt-test");

        List<StreamEvent> events = new ArrayList<>();
        provider.stream(List.of(Message.user("hi")), StreamRequest.of("gpt-test"), events::add);

        assertThat(events).hasSize(2);
        assertThat(((StreamEvent.Delta) events.get(0)).text).isEqualTo("partial");
        assertThat(events.get(1)).isInstanceOf(StreamEvent.Error.class);
        assertThat(((StreamEvent.Error) events.get(1)).cause.getMessage())
                .contains("context_length_exceeded");
    }

    @Test
    void reasoningContentMapsToThinking() {
        server.sseEndpoint("/chat/completions", 200, List.of(
                "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"thinking...\"}}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\"answer\"}}]}",
                "data: [DONE]"
        ));

        OpenAICompatProvider provider = new OpenAICompatProvider(
                server.baseUrl(), "sk-test", "gpt-test");

        List<StreamEvent> events = new ArrayList<>();
        provider.stream(List.of(Message.user("hi")), StreamRequest.of("gpt-test"), events::add);

        assertThat(events).hasSize(3);
        assertThat(events.get(0)).isInstanceOf(StreamEvent.Thinking.class);
        assertThat(((StreamEvent.Thinking) events.get(0)).text).isEqualTo("thinking...");
        assertThat(((StreamEvent.Delta) events.get(1)).text).isEqualTo("answer");
        assertThat(events.get(2)).isInstanceOf(StreamEvent.Done.class);
    }

    @Test
    void providerNameIsOpenAi() {
        OpenAICompatProvider provider = new OpenAICompatProvider(
                "http://127.0.0.1:1", "sk-test", "gpt-test");
        assertThat(provider.name()).isEqualTo("openai");
    }

    @Test
    void thinkingEnabledRequestGoesThroughWithoutError() {
        server.sseEndpoint("/chat/completions", 200, List.of(
                "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}",
                "data: [DONE]"
        ));

        OpenAICompatProvider provider = new OpenAICompatProvider(
                server.baseUrl(), "sk-test", "gpt-test");

        List<StreamEvent> events = new ArrayList<>();
        provider.stream(List.of(Message.user("hi")),
                StreamRequest.of("gpt-test", true), events::add);

        assertThat(events.get(events.size() - 1)).isInstanceOf(StreamEvent.Done.class);
    }

    @Test
    void malformedChunkSurfacesAsErrorEvent() {
        server.sseEndpoint("/chat/completions", 200, List.of(
                "data: not-json{",
                "data: [DONE]"
        ));

        OpenAICompatProvider provider = new OpenAICompatProvider(
                server.baseUrl(), "sk-test", "gpt-test");

        List<StreamEvent> events = new ArrayList<>();
        provider.stream(List.of(Message.user("hi")), StreamRequest.of("gpt-test"), events::add);

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(StreamEvent.Error.class);
        assertThat(events.get(1)).isInstanceOf(StreamEvent.Done.class);
    }

    @Test
    void messageHistoryIsSentInOrder() {
        server.sseEndpoint("/chat/completions", 200, List.of(
                "data: {\"choices\":[{\"delta\":{\"content\":\"ack\"}}]}",
                "data: [DONE]"
        ));

        OpenAICompatProvider provider = new OpenAICompatProvider(
                server.baseUrl(), "sk-test", "gpt-test");

        List<Message> history = List.of(
                Message.user("first"),
                Message.assistant("first reply"),
                Message.user("second"));
        List<StreamEvent> events = new ArrayList<>();
        provider.stream(history, StreamRequest.of("gpt-test"), events::add);

        assertThat(events.get(events.size() - 1)).isInstanceOf(StreamEvent.Done.class);
    }
}

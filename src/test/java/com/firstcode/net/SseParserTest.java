package com.firstcode.net;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SseParserTest {

    @Test
    void parsesSingleEvent() {
        SseParser p = new SseParser();
        List<SseChunk> chunks = p.feed("event: message_start\ndata: {\"type\":\"message_start\"}\n\n");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).event()).isEqualTo("message_start");
        assertThat(chunks.get(0).data()).isEqualTo("{\"type\":\"message_start\"}");
    }

    @Test
    void handlesMissingEventField() {
        SseParser p = new SseParser();
        List<SseChunk> chunks = p.feed("data: [DONE]\n\n");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).event()).isNull();
        assertThat(chunks.get(0).data()).isEqualTo("[DONE]");
    }

    @Test
    void joinsMultilineData() {
        SseParser p = new SseParser();
        List<SseChunk> chunks = p.feed("data: line1\ndata: line2\ndata: line3\n\n");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).data()).isEqualTo("line1\nline2\nline3");
    }

    @Test
    void parsesMultipleEventsAcrossFeeds() {
        SseParser p = new SseParser();
        List<SseChunk> a = p.feed("event: a\ndata: 1\n\nevent: b\ndata: 2\n");
        List<SseChunk> b = p.feed("\nevent: c\ndata: 3\n\n");
        assertThat(a).hasSize(1);
        assertThat(a.get(0).data()).isEqualTo("1");
        assertThat(b).hasSize(2);
        assertThat(b.get(0).data()).isEqualTo("2");
        assertThat(b.get(1).data()).isEqualTo("3");
    }

    @Test
    void flushEmitsTrailingEventWithoutEmptyLine() {
        SseParser p = new SseParser();
        p.feed("event: a\ndata: 1\n\nevent: b\ndata: 2\n");
        List<SseChunk> tail = p.flush();
        assertThat(tail).hasSize(1);
        assertThat(tail.get(0).event()).isEqualTo("b");
        assertThat(tail.get(0).data()).isEqualTo("2");
    }

    @Test
    void flushOnEmptyBufferReturnsEmpty() {
        SseParser p = new SseParser();
        assertThat(p.flush()).isEmpty();
        p.feed("event: a\ndata: 1\n\n");
        assertThat(p.flush()).isEmpty();
    }

    @Test
    void ignoresCommentLines() {
        SseParser p = new SseParser();
        List<SseChunk> chunks = p.feed(": this is a comment\ndata: real\n\n");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).data()).isEqualTo("real");
    }

    @Test
    void handlesCrlfLineEndings() {
        SseParser p = new SseParser();
        List<SseChunk> chunks = p.feed("data: hello\r\n\r\n");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).data()).isEqualTo("hello");
    }

    @Test
    void preservesUtf8Content() {
        SseParser p = new SseParser();
        List<SseChunk> chunks = p.feed("data: 你好世界\n\n");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).data()).isEqualTo("你好世界");
    }

    @Test
    void treatsEmptyDataAfterColonAsEmptyString() {
        SseParser p = new SseParser();
        List<SseChunk> chunks = p.feed("event: ping\ndata:\n\n");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).event()).isEqualTo("ping");
        assertThat(chunks.get(0).data()).isEqualTo("");
    }

    @Test
    void resetsBetweenSessions() {
        SseParser p = new SseParser();
        p.feed("event: a\ndata: 1\n\n");
        p.reset();
        List<SseChunk> chunks = p.feed("event: b\ndata: 2\n\n");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).event()).isEqualTo("b");
    }

    @Test
    void handlesBlankLinesWithoutPendingData() {
        SseParser p = new SseParser();
        List<SseChunk> chunks = p.feed("\n\n\ndata: only\n\n");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).data()).isEqualTo("only");
    }
}

package com.firstcode.net;

/**
 * 一条解析后的 SSE 事件。
 *
 * @param event event 字段(可空,典型如 {@code message_start} / {@code content_block_delta})
 * @param data  data 字段(多行已用 {@code \n} 拼接)
 */
public record SseChunk(String event, String data) {
}

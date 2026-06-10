package com.firstcode.net;

import java.util.ArrayList;
import java.util.List;

/**
 * SSE(Server-Sent Events)行式状态机。
 *
 * <p>支持:
 * <ul>
 *   <li>{@code event: <name>} 记录事件类型</li>
 *   <li>{@code data: <text>}  追加到当前事件的 data(多行用 {@code \n} 拼接)</li>
 *   <li>空行 → 完成一个事件,产出 {@link SseChunk}</li>
 *   <li>{@code id:} / {@code retry:} / {@code :} 注释行 忽略</li>
 * </ul>
 *
 * <p>典型用法:从 HTTP {@code InputStream} 逐行读出,每次调 {@link #feed(String)} 喂入,
 * 产出的 {@link SseChunk} 列表可逐条处理。HTTP 流结束时(读不到更多行)调 {@link #flush()}
 * 取出缓冲区中尚未由空行结束的尾事件(SSE 协议允许流的最后一个事件不带结尾空行)。
 */
public final class SseParser {

    private String currentEvent;
    private final StringBuilder currentData = new StringBuilder();
    private boolean hasData;

    /** 重置内部状态。 */
    public void reset() {
        currentEvent = null;
        currentData.setLength(0);
        hasData = false;
    }

    /**
     * 取出缓冲区中残留的尾事件并重置。
     *
     * <p>HTTP SSE 流在服务器侧允许以「事件 + 换行」结尾,无末尾空行。
     * 读流结束时调用本方法,把尚未派发的最后一个事件返回出来。
     *
     * @return 若缓冲区里有 event 或 data 字段,返回单元素列表;否则空列表
     */
    public List<SseChunk> flush() {
        List<SseChunk> out = new ArrayList<>();
        if (hasData || currentEvent != null) {
            out.add(new SseChunk(currentEvent,
                    currentData.length() == 0 ? "" : currentData.toString()));
        }
        currentEvent = null;
        currentData.setLength(0);
        hasData = false;
        return out;
    }

    /**
     * 喂入一段文本(可能包含多行),返回该段中完成的事件列表。
     *
     * <p>注意:chunk 末尾没有换行符的最后一行不视为「空行」,仅处理内容而不完成事件。
     */
    public List<SseChunk> feed(String chunk) {
        List<SseChunk> out = new ArrayList<>();
        if (chunk == null || chunk.isEmpty()) {
            return out;
        }
        String normalized = chunk.replace("\r\n", "\n").replace('\r', '\n');
        int len = normalized.length();
        int lineStart = 0;
        for (int i = 0; i < len; i++) {
            if (normalized.charAt(i) != '\n') {
                continue;
            }
            handleLine(normalized, lineStart, i, true, out);
            lineStart = i + 1;
        }
        if (lineStart < len) {
            // 末行无换行终止符,仅 processLine,不算「空行」
            handleLine(normalized, lineStart, len, false, out);
        }
        return out;
    }

    private void handleLine(String src, int start, int end, boolean terminated,
                            List<SseChunk> out) {
        if (end > start && src.charAt(end - 1) == '\r') {
            end--;
        }
        if (end == start) {
            if (terminated && (hasData || currentEvent != null)) {
                out.add(new SseChunk(currentEvent,
                        currentData.length() == 0 ? "" : currentData.toString()));
            }
            currentEvent = null;
            currentData.setLength(0);
            hasData = false;
        } else {
            processLine(src.substring(start, end));
        }
    }

    private void processLine(String line) {
        if (line.isEmpty()) {
            return;
        }
        char c = line.charAt(0);
        if (c == ':') {
            return; // comment
        }
        int colon = line.indexOf(':');
        String field;
        String value;
        if (colon < 0) {
            field = line;
            value = "";
        } else {
            field = line.substring(0, colon);
            value = line.substring(colon + 1);
        }
        if (value.length() > 0 && value.charAt(0) == ' ') {
            value = value.substring(1);
        }
        switch (field) {
            case "event" -> currentEvent = value;
            case "data" -> {
                if (hasData) {
                    currentData.append('\n');
                }
                currentData.append(value);
                hasData = true;
            }
            case "id", "retry" -> {
                // ignore
            }
            default -> {
                // ignore unknown fields
            }
        }
    }
}

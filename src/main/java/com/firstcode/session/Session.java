package com.firstcode.session;

import java.util.Collections;
import java.util.List;

/**
 * 不可变会话,持有当前对话的全部消息。修改操作返回新 Session,符合不可变数据模型。
 *
 * <p>持久化由 {@link SessionIO} 负责。
 */
public final class Session {

    private final List<Message> messages;

    private Session(List<Message> messages) {
        this.messages = List.copyOf(messages);
    }

    public static Session empty() {
        return new Session(List.of());
    }

    public static Session from(List<Message> messages) {
        return new Session(messages);
    }

    public Session appendUserMessage(String content) {
        return new Session(append(Message.user(content)));
    }

    public Session appendAssistantMessage(String content, String thinking) {
        Message msg = (thinking == null || thinking.isEmpty())
                ? Message.assistant(content)
                : Message.assistant(content, thinking);
        return new Session(append(msg));
    }

    private List<Message> append(Message msg) {
        java.util.ArrayList<Message> next = new java.util.ArrayList<>(messages.size() + 1);
        next.addAll(messages);
        next.add(msg);
        return next;
    }

    public Session clear() {
        return empty();
    }

    public int size() {
        return messages.size();
    }

    public List<Message> messages() {
        return Collections.unmodifiableList(messages);
    }
}

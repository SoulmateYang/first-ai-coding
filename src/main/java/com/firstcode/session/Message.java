package com.firstcode.session;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 内部统一消息模型,作为业务层与 Provider 之间的归一化结构。
 *
 * <p><code>thinking</code> 仅在 extended thinking 启用时存在,Anthropic 协议会原样回传;OpenAI 协议忽略。
 *
 * <p>作为 Jackson 反序列化目标,字段命名保持 role / content / thinking 与 session.json 中一致。
 */
public record Message(Role role, String content, String thinking) {

    public static Message user(String content) {
        return new Message(Role.USER, content, null);
    }

    public static Message assistant(String content) {
        return new Message(Role.ASSISTANT, content, null);
    }

    public static Message assistant(String content, String thinking) {
        return new Message(Role.ASSISTANT, content, thinking);
    }

    @JsonCreator
    public Message(@JsonProperty("role") Role role,
                   @JsonProperty("content") String content,
                   @JsonProperty("thinking") String thinking) {
        this.role = role;
        this.content = content == null ? "" : content;
        this.thinking = thinking;
    }
}

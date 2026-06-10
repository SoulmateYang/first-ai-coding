package com.firstcode.provider;

import com.firstcode.session.Message;

import java.util.List;
import java.util.function.Consumer;

/**
 * LLM 后端抽象。
 *
 * <p>实现负责:
 * <ul>
 *   <li>把 {@link Message} 列表转为自家协议请求</li>
 *   <li>解析 SSE 事件流</li>
 *   <li>逐 chunk 通过 {@code sink} 回调 {@link StreamEvent}</li>
 * </ul>
 *
 * <p>实现不应处理 I/O、终端打印、配置解析;新增后端只需实现本接口。
 */
public interface Provider {

    /** 显示名,如 "anthropic" / "openai"。 */
    String name();

    /**
     * 发起一次流式对话请求,逐事件推送到 sink。
     * 异常结束必须以 {@link StreamEvent.Error} 推送,不要直接抛。
     */
    void stream(List<Message> messages, StreamRequest request, Consumer<StreamEvent> sink);
}

package com.firstcode.provider;

/**
 * 一次流式请求的参数。
 *
 * @param model           模型名(冗余,允许覆盖 Config.model)
 * @param thinkingEnabled 是否启用 extended thinking(仅 Anthropic 生效)
 * @param systemPrompt    可选 system prompt(本阶段未在配置中暴露)
 */
public record StreamRequest(
        String model,
        boolean thinkingEnabled,
        String systemPrompt) {

    public static StreamRequest of(String model) {
        return new StreamRequest(model, false, null);
    }

    public static StreamRequest of(String model, boolean thinkingEnabled) {
        return new StreamRequest(model, thinkingEnabled, null);
    }
}

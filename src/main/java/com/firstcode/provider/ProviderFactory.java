package com.firstcode.provider;

import com.firstcode.config.Config;
import com.firstcode.config.ConfigException;

/** 根据 {@link Config#protocol()} 返回对应 Provider 实例。 */
public final class ProviderFactory {

    private ProviderFactory() {
    }

    public static Provider fromConfig(Config config) {
        return switch (config.protocol()) {
            case ANTHROPIC -> new AnthropicProvider(
                    config.baseUrl(), config.apiKey(), config.model());
            case OPENAI -> new OpenAICompatProvider(
                    config.baseUrl(), config.apiKey(), config.model());
        };
    }
}

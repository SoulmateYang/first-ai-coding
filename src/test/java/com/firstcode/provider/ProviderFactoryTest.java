package com.firstcode.provider;

import com.firstcode.config.Config;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderFactoryTest {

    @Test
    void anthropicConfigProducesAnthropicProvider() {
        Config config = new Config("anthropic", "claude-test",
                "https://api.anthropic.com", "sk-ant-test", null);
        Provider provider = ProviderFactory.fromConfig(config);
        assertThat(provider).isInstanceOf(AnthropicProvider.class);
        assertThat(provider.name()).isEqualTo("anthropic");
    }

    @Test
    void openaiConfigProducesOpenAICompatProvider() {
        Config config = new Config("openai", "gpt-test",
                "https://api.openai.com/v1", "sk-test", null);
        Provider provider = ProviderFactory.fromConfig(config);
        assertThat(provider).isInstanceOf(OpenAICompatProvider.class);
        assertThat(provider.name()).isEqualTo("openai");
    }
}

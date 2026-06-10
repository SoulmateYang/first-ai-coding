package com.firstcode.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigTest {

    private String originalHome;
    private Path fakeHome;

    @BeforeEach
    void redirectHome(@TempDir Path tmp) throws Exception {
        originalHome = System.getProperty("user.home");
        fakeHome = tmp.resolve("home");
        Files.createDirectories(fakeHome);
        Files.createDirectories(fakeHome.resolve(".firstcode"));
        System.setProperty("user.home", fakeHome.toString());
    }

    @AfterEach
    void restoreHome() {
        if (originalHome == null) {
            System.clearProperty("user.home");
        } else {
            System.setProperty("user.home", originalHome);
        }
    }

    @Test
    void missingConfigThrowsWithExample() {
        assertThatThrownBy(Config::loadFromUserHome)
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("protocol: anthropic")
                .hasMessageContaining("model:")
                .hasMessageContaining("api_key:");
    }

    @Test
    void missingProtocolField() throws Exception {
        Files.writeString(fakeHome.resolve(".firstcode/config.yaml"), """
                model: m
                base_url: https://api.example.com
                api_key: sk-xxx
                """);
        assertThatThrownBy(Config::loadFromUserHome)
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("protocol");
    }

    @Test
    void missingModelField() throws Exception {
        Files.writeString(fakeHome.resolve(".firstcode/config.yaml"), """
                protocol: anthropic
                base_url: https://api.example.com
                api_key: sk-xxx
                """);
        assertThatThrownBy(Config::loadFromUserHome)
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("model");
    }

    @Test
    void missingApiKeyField() throws Exception {
        Files.writeString(fakeHome.resolve(".firstcode/config.yaml"), """
                protocol: anthropic
                model: claude-3
                base_url: https://api.example.com
                """);
        assertThatThrownBy(Config::loadFromUserHome)
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("api_key");
    }

    @Test
    void invalidProtocol() throws Exception {
        Files.writeString(fakeHome.resolve(".firstcode/config.yaml"), """
                protocol: foo
                model: m
                base_url: https://api.example.com
                api_key: sk-xxx
                """);
        assertThatThrownBy(Config::loadFromUserHome)
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("protocol");
    }

    @Test
    void invalidThinkingValue() throws Exception {
        Files.writeString(fakeHome.resolve(".firstcode/config.yaml"), """
                protocol: anthropic
                model: m
                base_url: https://api.example.com
                api_key: sk-xxx
                thinking: maybe
                """);
        assertThatThrownBy(Config::loadFromUserHome)
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("thinking");
    }

    @Test
    void validConfigWithoutThinking() throws Exception {
        Files.writeString(fakeHome.resolve(".firstcode/config.yaml"), """
                protocol: anthropic
                model: claude-3-5-sonnet
                base_url: https://api.anthropic.com
                api_key: sk-ant-xxxx
                """);
        Config c = Config.loadFromUserHome();
        assertThat(c.protocol()).isEqualTo(Protocol.ANTHROPIC);
        assertThat(c.model()).isEqualTo("claude-3-5-sonnet");
        assertThat(c.apiKey()).isEqualTo("sk-ant-xxxx");
        assertThat(c.thinking()).isEqualTo(ThinkingMode.DISABLED);
    }

    @Test
    void validConfigWithThinkingEnabled() throws Exception {
        Files.writeString(fakeHome.resolve(".firstcode/config.yaml"), """
                protocol: anthropic
                model: claude-3-5-sonnet
                base_url: https://api.anthropic.com
                api_key: sk-ant-xxxx
                thinking: enabled
                """);
        Config c = Config.loadFromUserHome();
        assertThat(c.thinking()).isEqualTo(ThinkingMode.ENABLED);
    }

    @Test
    void validOpenAiConfig() throws Exception {
        Files.writeString(fakeHome.resolve(".firstcode/config.yaml"), """
                protocol: openai
                model: gpt-4o-mini
                base_url: https://api.openai.com/v1
                api_key: sk-abcdefgh
                """);
        Config c = Config.loadFromUserHome();
        assertThat(c.protocol()).isEqualTo(Protocol.OPENAI);
    }

    @Test
    void errorMessageDoesNotLeakApiKey() throws Exception {
        String secret = "sk-supersecret-1234567890";
        Files.writeString(fakeHome.resolve(".firstcode/config.yaml"), """
                protocol: anthropic
                model: m
                base_url: not-a-url
                api_key: %s
                """.formatted(secret));
        try {
            Config.loadFromUserHome();
        } catch (ConfigException ex) {
            assertThat(ex.getMessage()).doesNotContain(secret);
        }
    }

    @Test
    void invalidBaseUrlRejected() throws Exception {
        Files.writeString(fakeHome.resolve(".firstcode/config.yaml"), """
                protocol: anthropic
                model: m
                base_url: not-a-url
                api_key: sk-xxx
                """);
        assertThatThrownBy(Config::loadFromUserHome)
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("base_url");
    }
}

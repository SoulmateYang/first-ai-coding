package com.firstcode.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 启动期从 {@code ~/.firstcode/config.yaml} 加载的 LLM 配置。
 *
 * <p>字段全部必填,除 {@code thinking}(默认 {@link ThinkingMode#DISABLED})。
 */
public record Config(
        Protocol protocol,
        String model,
        String baseUrl,
        String apiKey,
        ThinkingMode thinking) {

    @JsonCreator
    public Config(
            @JsonProperty("protocol") String protocolStr,
            @JsonProperty("model") String model,
            @JsonProperty("base_url") String baseUrl,
            @JsonProperty("api_key") String apiKey,
            @JsonProperty("thinking") String thinkingStr) {
        this(
                Protocol.fromWireName(protocolStr),
                requireNonBlank(model, "model"),
                requireValidUrl(requireNonBlank(baseUrl, "base_url"), "base_url"),
                requireNonBlank(apiKey, "api_key"),
                ThinkingMode.fromWireName(thinkingStr));
    }

    /** YAML 友好视图(供 Jackson 序列化使用)。 */
    @JsonProperty("protocol")
    public String protocolWire() {
        return protocol.wireName();
    }

    @JsonIgnore
    public Path sessionPath() {
        String home = System.getProperty("user.home");
        return Path.of(home, ".firstcode", "session.json");
    }

    public static Config loadFromUserHome() {
        Path path = configPath();
        if (Files.notExists(path)) {
            throw new ConfigException(
                    "config file not found at " + path + "\n\n"
                            + "Create it with the following content:\n\n"
                            + loadExampleYaml());
        }
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            // 用一个中间 POJO 解析,再走带校验的 record 构造器
            YamlConfigView view = mapper.readValue(path.toFile(), YamlConfigView.class);
            return new Config(
                    view.protocol(),
                    view.model(),
                    view.baseUrl(),
                    view.apiKey(),
                    view.thinking());
        } catch (ConfigException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ConfigException("failed to read config at " + path + ": " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new ConfigException("invalid config at " + path + ": " + ex.getMessage(), ex);
        }
    }

    public static Path configPath() {
        String home = System.getProperty("user.home");
        return Path.of(home, ".firstcode", "config.yaml");
    }

    private static String loadExampleYaml() {
        try (InputStream in = Config.class.getResourceAsStream("/config.example.yaml")) {
            if (in == null) {
                return "(example resource missing)";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "(failed to load example: " + ex.getMessage() + ")";
        }
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ConfigException("missing required field: " + field);
        }
        return value;
    }

    private static String requireValidUrl(String value, String field) {
        try {
            URI uri = URI.create(value);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new ConfigException("invalid " + field + ": must be a full http(s) URL");
            }
        } catch (IllegalArgumentException ex) {
            throw new ConfigException("invalid " + field + ": " + ex.getMessage());
        }
        return value;
    }

    /** 简单 POJO,先解析再走 record 校验构造器,避免 Jackson 直接调 record 时的反射坑。 */
    public static final class YamlConfigView {
        private String protocol;
        private String model;
        private String base_url;
        private String api_key;
        private String thinking;

        public String protocol() { return protocol; }
        public String model() { return model; }
        public String baseUrl() { return base_url; }
        public String apiKey() { return api_key; }
        public String thinking() { return thinking; }

        public void setProtocol(String v) { this.protocol = v; }
        public void setModel(String v) { this.model = v; }
        public void setBase_url(String v) { this.base_url = v; }
        public void setApi_key(String v) { this.api_key = v; }
        public void setThinking(String v) { this.thinking = v; }
    }
}

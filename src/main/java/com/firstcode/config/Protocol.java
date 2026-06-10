package com.firstcode.config;

/** 后端协议枚举。 */
public enum Protocol {
    ANTHROPIC,
    OPENAI;

    /** 在 YAML 配置和日志中使用的字面量,小写。 */
    public String wireName() {
        return name().toLowerCase();
    }

    public static Protocol fromWireName(String s) {
        if (s == null) {
            throw new ConfigException("protocol is required (expected: anthropic | openai)");
        }
        return switch (s.trim().toLowerCase()) {
            case "anthropic" -> ANTHROPIC;
            case "openai" -> OPENAI;
            default -> throw new ConfigException(
                    "invalid protocol: '" + s + "' (expected: anthropic | openai)");
        };
    }
}

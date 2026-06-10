package com.firstcode.config;

/** Extended thinking 开关,仅 {@code protocol: anthropic} 生效。 */
public enum ThinkingMode {
    ENABLED,
    DISABLED;

    public static final ThinkingMode DEFAULT = DISABLED;

    public static ThinkingMode fromWireName(String s) {
        if (s == null || s.isBlank()) {
            return DEFAULT;
        }
        return switch (s.trim().toLowerCase()) {
            case "enabled" -> ENABLED;
            case "disabled" -> DISABLED;
            default -> throw new ConfigException(
                    "invalid thinking: '" + s + "' (expected: enabled | disabled)");
        };
    }
}

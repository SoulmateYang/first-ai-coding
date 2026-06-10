package com.firstcode.io;

import java.util.regex.Pattern;

/**
 * 脱敏工具:从字符串或异常链中擦除 API key 等敏感信息,避免泄漏到日志/终端。
 *
 * <p>覆盖三种模式:
 * <ul>
 *   <li>OpenAI 风格: <code>sk-...</code></li>
 *   <li>Anthropic 风格: <code>sk-ant-...</code></li>
 *   <li>YAML/JSON 字段: <code>api_key: "..."</code></li>
 * </ul>
 *
 * <p>所有匹配项统一替换为 <code>***</code>。
 */
public final class Sanitizer {

    private static final String MASK = "***";

    private static final Pattern OPENAI_KEY = Pattern.compile("sk-[A-Za-z0-9_-]{8,}");
    private static final Pattern ANTHROPIC_KEY = Pattern.compile("sk-ant-[A-Za-z0-9_-]{8,}");
    private static final Pattern API_KEY_FIELD = Pattern.compile(
            "(?i)api[_\\-]?key\\s*[:=]\\s*[\"']?[A-Za-z0-9_-]{8,}");

    private Sanitizer() {
    }

    /**
     * 对字符串执行脱敏,返回处理后的新串(null 输入返回 null)。
     */
    public static String scrub(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = OPENAI_KEY.matcher(input).replaceAll(MASK);
        result = ANTHROPIC_KEY.matcher(result).replaceAll(MASK);
        result = API_KEY_FIELD.matcher(result).replaceAll(MASK);
        return result;
    }

    /**
     * 递归擦除异常的 message 及其 cause chain 中所有敏感串,返回拼接后的安全描述。
     */
    public static String scrub(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 10) {
            if (depth > 0) {
                sb.append(" <- ");
            }
            String msg = current.getMessage();
            sb.append(current.getClass().getSimpleName());
            if (msg != null && !msg.isEmpty()) {
                sb.append(": ").append(scrub(msg));
            }
            current = current.getCause();
            depth++;
        }
        return sb.toString();
    }
}

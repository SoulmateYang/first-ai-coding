package com.firstcode;

/**
 * FirstCode 错误层级根。
 *
 * <p>所有可被 REPL 或启动期映射为可读错误、退出码的业务异常,均应继承本类。
 *
 * <p>具体子类:
 * <ul>
 *   <li>{@code ConfigException} — 配置加载/校验失败</li>
 *   <li>{@code ProviderException} — Provider 调用失败(网络/解析/上游)</li>
 *   <li>{@code SessionException} — 会话文件读写失败</li>
 *   <li>{@code ReplException} — 通用兜底</li>
 * </ul>
 */
public abstract class FirstCodeException extends RuntimeException {

    protected FirstCodeException(String message) {
        super(message);
    }

    protected FirstCodeException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 沿 cause chain 找到最深层(无 cause)的异常。
     * 若本异常本身没有 cause,返回自身。
     */
    public Throwable rootCause() {
        Throwable current = this;
        int guard = 0;
        while (current.getCause() != null && current.getCause() != current && guard++ < 10) {
            current = current.getCause();
        }
        return current;
    }
}

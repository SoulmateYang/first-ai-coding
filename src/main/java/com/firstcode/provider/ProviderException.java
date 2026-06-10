package com.firstcode.provider;

import com.firstcode.FirstCodeException;

/** Provider 调用失败(网络、协议解析、上游错误)时抛出。 */
public class ProviderException extends FirstCodeException {

    public ProviderException(String message) {
        super(message);
    }

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}

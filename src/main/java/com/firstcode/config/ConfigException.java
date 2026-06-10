package com.firstcode.config;

import com.firstcode.FirstCodeException;

/** 配置加载或字段校验失败时抛出。 */
public class ConfigException extends FirstCodeException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}

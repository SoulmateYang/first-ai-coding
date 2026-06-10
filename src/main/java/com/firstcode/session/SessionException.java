package com.firstcode.session;

import com.firstcode.FirstCodeException;

/** 会话文件读写失败时抛出。 */
public class SessionException extends FirstCodeException {

    public SessionException(String message) {
        super(message);
    }

    public SessionException(String message, Throwable cause) {
        super(message, cause);
    }
}

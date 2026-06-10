package com.firstcode.repl;

import com.firstcode.FirstCodeException;

/** REPL 主循环内部的兜底异常。 */
public class ReplException extends FirstCodeException {

    public ReplException(String message) {
        super(message);
    }

    public ReplException(String message, Throwable cause) {
        super(message, cause);
    }
}

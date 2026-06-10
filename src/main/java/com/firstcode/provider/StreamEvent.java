package com.firstcode.provider;

import com.firstcode.FirstCodeException;

/**
 * Provider → Repl 之间的流式事件回调载荷。
 *
 * <p>四种实现:
 * <ul>
 *   <li>{@link Delta} — 增量文本(可流式打印)</li>
 *   <li>{@link Thinking} — extended thinking 增量(Repl 决定如何呈现)</li>
 *   <li>{@link Done} — 流正常结束</li>
 *   <li>{@link Error} — 流异常结束(Repl 转为可读错误并继续 REPL)</li>
 * </ul>
 */
public abstract class StreamEvent {

    private StreamEvent() {
    }

    public static final class Delta extends StreamEvent {
        public final String text;

        public Delta(String text) {
            this.text = text == null ? "" : text;
        }
    }

    public static final class Thinking extends StreamEvent {
        public final String text;

        public Thinking(String text) {
            this.text = text == null ? "" : text;
        }
    }

    public static final class Done extends StreamEvent {
        public static final Done INSTANCE = new Done();

        private Done() {
        }
    }

    public static final class Error extends StreamEvent {
        public final FirstCodeException cause;

        public Error(FirstCodeException cause) {
            this.cause = cause;
        }
    }
}

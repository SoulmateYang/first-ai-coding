package com.firstcode.io;

/**
 * 测试用实现:把全部输出按类别收集到内存缓冲区,便于断言。
 *
 * <p>不真实写入 stdout/stderr。
 */
public final class BufferingOutput implements Output {

    private final StringBuilder text = new StringBuilder();
    private final StringBuilder errors = new StringBuilder();
    private final StringBuilder infos = new StringBuilder();
    private final StringBuilder prompts = new StringBuilder();
    private final Object lock = new Object();

    @Override
    public void print(String s) {
        synchronized (lock) {
            text.append(s);
        }
    }

    @Override
    public void println(String line) {
        synchronized (lock) {
            text.append(line).append('\n');
        }
    }

    @Override
    public void printPrompt() {
        synchronized (lock) {
            prompts.append("> ");
        }
    }

    @Override
    public void printInfo(String line) {
        synchronized (lock) {
            infos.append("INFO: ").append(line).append('\n');
        }
    }

    @Override
    public void printError(Throwable error) {
        synchronized (lock) {
            errors.append("ERROR: ").append(Sanitizer.scrub(error)).append('\n');
        }
    }

    public String text() {
        synchronized (lock) {
            return text.toString();
        }
    }

    public String errors() {
        synchronized (lock) {
            return errors.toString();
        }
    }

    public String infos() {
        synchronized (lock) {
            return infos.toString();
        }
    }

    public String prompts() {
        synchronized (lock) {
            return prompts.toString();
        }
    }

    /** 清空所有缓冲区。 */
    public void reset() {
        synchronized (lock) {
            text.setLength(0);
            errors.setLength(0);
            infos.setLength(0);
            prompts.setLength(0);
        }
    }
}

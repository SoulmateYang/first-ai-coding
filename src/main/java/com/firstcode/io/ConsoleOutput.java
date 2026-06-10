package com.firstcode.io;

import java.io.PrintWriter;

/**
 * 生产环境实现:把流式内容写到 stdout,把日志与错误写到 stderr。
 *
 * <p>所有写操作后立即 flush,确保流式输出在 Windows 控制台与 Unix 终端都按预期逐 token 显示。
 */
public final class ConsoleOutput implements Output {

    private static final String PROMPT = "> ";

    private final PrintWriter out;
    private final PrintWriter err;

    public ConsoleOutput() {
        this(new PrintWriter(System.out, true), new PrintWriter(System.err, true));
    }

    /** 测试或自定义输出流场景。 */
    public ConsoleOutput(PrintWriter out, PrintWriter err) {
        this.out = out;
        this.err = err;
    }

    @Override
    public void print(String text) {
        out.print(text);
        out.flush();
    }

    @Override
    public void println(String line) {
        out.println(line);
        out.flush();
    }

    @Override
    public void printPrompt() {
        out.print(PROMPT);
        out.flush();
    }

    @Override
    public void printInfo(String line) {
        err.println("INFO: " + line);
        err.flush();
    }

    @Override
    public void printError(Throwable error) {
        err.println("ERROR: " + Sanitizer.scrub(error));
        err.flush();
    }
}

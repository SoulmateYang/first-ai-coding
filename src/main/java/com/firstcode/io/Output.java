package com.firstcode.io;

/**
 * 终端输出抽象。业务层(Repl、Provider 等)只依赖此接口,不直接写 System.out / System.err。
 *
 * <p>实现类负责:
 * <ul>
 *   <li>流式回复走 <code>print(String)</code> 写到 stdout,必须立即 flush</li>
 *   <li>提示符、日志、错误走 <code>stderr</code>(printError / printInfo)</li>
 *   <li>错误信息在写出前经 <code>Sanitizer.scrub</code> 脱敏</li>
 * </ul>
 */
public interface Output {

    /**
     * 写入流式回复片段(无前置/后置换行,不会 flush 触发新行)。
     */
    void print(String text);

    /**
     * 写入一行(自动追加换行符)。
     */
    void println(String line);

    /**
     * 写入 REPL 提示符(默认 <code>&gt;&nbsp;</code>),不换行。
     */
    void printPrompt();

    /**
     * 写入一条 INFO 日志到 stderr。
     */
    void printInfo(String line);

    /**
     * 写入一条 ERROR 到 stderr(经脱敏)。
     */
    void printError(Throwable error);
}

package com.firstcode.repl;

import com.firstcode.config.Config;
import com.firstcode.config.ThinkingMode;
import com.firstcode.io.Output;
import com.firstcode.provider.Provider;
import com.firstcode.provider.StreamEvent;
import com.firstcode.provider.StreamRequest;
import com.firstcode.session.Message;
import com.firstcode.session.Role;
import com.firstcode.session.Session;
import com.firstcode.session.SessionIO;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * REPL 主循环。
 *
 * <p>流程:读行 → 解析 → 分派(命令 / 消息)。
 * <ul>
 *   <li>命令:执行内置行为,可能退出或保存</li>
 *   <li>消息:追加到 Session,调用 Provider.stream 拿到事件,逐 token 写出,
 *       流结束后原子写 session.json</li>
 * </ul>
 *
 * <p>运行期异常(Provider 网络/解析错误)经 {@link StreamEvent.Error} 走 Output.printError,
 * REPL 继续运行,不会因为一次失败对话而退出。
 */
public final class Repl {

    private final Config config;
    private final Provider provider;
    private final SessionIO sessionIO;
    private final Output output;
    private final CommandParser parser = new CommandParser();

    private Session session;
    private final Scanner in;

    public Repl(Config config, Provider provider, Session session, SessionIO sessionIO, Output output) {
        this(config, provider, session, sessionIO, output, new Scanner(System.in));
    }

    /** 测试用入口,可注入 Scanner。 */
    public Repl(Config config, Provider provider, Session session, SessionIO sessionIO,
                Output output, Scanner in) {
        this.config = config;
        this.provider = provider;
        this.session = session;
        this.sessionIO = sessionIO;
        this.output = output;
        this.in = in;
    }

    /** 返回当前 Session 快照(供测试断言)。 */
    public Session currentSession() {
        return session;
    }

    public void run() {
        output.printInfo(startupBanner());
        output.printPrompt();
        while (true) {
            String line;
            try {
                line = in.nextLine();
            } catch (NoSuchElementException eof) {
                output.println("");
                output.printInfo("bye");
                return;
            } catch (Exception ex) {
                output.printError(new ReplException("read error: " + ex.getMessage(), ex));
                return;
            }

            CommandParser.ParsedLine parsed = parser.parse(line);
            if (parsed.isEmpty()) {
                output.printPrompt();
                continue;
            }
            if (parsed.isCommand()) {
                Command cmd = parsed.command().orElseThrow();
                if (cmd instanceof Command.Exit) {
                    output.printInfo("bye");
                    return;
                } else if (cmd instanceof Command.ClearScreen) {
                    clearScreen();
                    output.printPrompt();
                    continue;
                } else if (cmd instanceof Command.ShowHistory) {
                    showHistory();
                    output.printPrompt();
                    continue;
                } else if (cmd instanceof Command.ResetSession) {
                    session = session.clear();
                    try {
                        sessionIO.save(session);
                    } catch (Exception ex) {
                        output.printError(new ReplException("failed to save cleared session", ex));
                    }
                    output.printInfo("session reset");
                    output.printPrompt();
                    continue;
                }
            }
            // 消息分支
            String text = parsed.message().orElseThrow();
            handleUserMessage(text);
            output.printPrompt();
        }
    }

    private void handleUserMessage(String text) {
        session = session.appendUserMessage(text);
        StringBuilder textBuf = new StringBuilder();
        StringBuilder thinkBuf = new StringBuilder();
        boolean[] doneReceived = {false};
        StreamRequest request = new StreamRequest(
                config.model(),
                config.thinking() == ThinkingMode.ENABLED,
                null);

        provider.stream(session.messages(), request, event -> {
            if (event instanceof StreamEvent.Delta d) {
                if (!d.text.isEmpty()) {
                    output.print(d.text);
                    textBuf.append(d.text);
                }
            } else if (event instanceof StreamEvent.Thinking t) {
                if (!t.text.isEmpty()) {
                    if (thinkBuf.length() == 0) {
                        output.print("[thinking]");
                    }
                    output.print(t.text);
                    thinkBuf.append(t.text);
                }
            } else if (event instanceof StreamEvent.Done) {
                doneReceived[0] = true;
                output.println("");
                String finalText = textBuf.toString();
                String finalThink = thinkBuf.length() == 0 ? null : thinkBuf.toString();
                session = session.appendAssistantMessage(finalText, finalThink);
                try {
                    sessionIO.save(session);
                } catch (Exception ex) {
                    output.printError(new ReplException("failed to save session", ex));
                }
            } else if (event instanceof StreamEvent.Error err) {
                output.printError(err.cause);
            }
        });

        if (!doneReceived[0]) {
            // 流在收到 Done 之前被中断(连接异常等),换行回 REPL
            output.println("");
        }
    }

    private void showHistory() {
        List<Message> msgs = session.messages();
        if (msgs.isEmpty()) {
            output.printInfo("(no history)");
            return;
        }
        int idx = 1;
        for (Message m : msgs) {
            String label = m.role() == Role.USER ? "user" : "assistant";
            output.println(String.format("%d. [%s] %s", idx++, label, m.content()));
        }
    }

    private void clearScreen() {
        // 不依赖 ANSI,打印若干空行模拟清屏
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append('\n');
        }
        output.print(sb.toString());
    }

    private String startupBanner() {
        return String.format("FirstCode ready: protocol=%s model=%s thinking=%s",
                config.protocol().wireName(),
                config.model(),
                config.thinking() == ThinkingMode.ENABLED ? "enabled" : "disabled")
                + " (loaded " + session.size() + " history messages)";
    }
}

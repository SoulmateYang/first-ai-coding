package com.firstcode.app;

import com.firstcode.config.Config;
import com.firstcode.config.ConfigException;
import com.firstcode.io.ConsoleOutput;
import com.firstcode.provider.Provider;
import com.firstcode.provider.ProviderFactory;
import com.firstcode.repl.Repl;
import com.firstcode.session.Session;
import com.firstcode.session.SessionException;
import com.firstcode.session.SessionIO;

/**
 * FirstCode 启动入口。
 *
 * <p>职责:
 * <ul>
 *   <li>加载 {@link Config}(失败:打印 YAML 示例 + 退出码 2)</li>
 *   <li>加载或创建 {@link Session}(失败:降级为空 Session + 提示)</li>
 *   <li>装配 {@link Provider}(失败:退出码 2)</li>
 *   <li>实例化 {@link Repl} 并阻塞运行</li>
 *   <li>顶层 catch:防止任何异常逃逸到堆栈</li>
 * </ul>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (ConfigException ex) {
            System.err.println("ERROR: " + ex.getMessage());
            System.exit(2);
        } catch (Throwable ex) {
            // 兜底,绝不暴露堆栈
            System.err.println("ERROR: unexpected " + ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
            System.exit(1);
        }
    }

    private static void run() {
        Config config = Config.loadFromUserHome();
        SessionIO sessionIO = new SessionIO(config.sessionPath());
        Session session;
        try {
            session = sessionIO.load();
        } catch (SessionException ex) {
            // 损坏文件 → 降级为空 Session,提示但不退出
            System.err.println("WARN: failed to load session, starting fresh: "
                    + ex.getMessage());
            session = Session.empty();
        }
        Provider provider = ProviderFactory.fromConfig(config);
        Repl repl = new Repl(config, provider, session, sessionIO, new ConsoleOutput());
        repl.run();
    }
}

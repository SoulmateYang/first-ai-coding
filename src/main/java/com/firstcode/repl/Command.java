package com.firstcode.repl;

/** REPL 内置命令基类(closed 集合)。 */
public abstract class Command {

    private Command() {
    }

    /** 退出 REPL(exit / quit)。 */
    public static final class Exit extends Command {
        public static final Exit INSTANCE = new Exit();
        private Exit() {}
    }

    /** 清屏(/clear)。 */
    public static final class ClearScreen extends Command {
        public static final ClearScreen INSTANCE = new ClearScreen();
        private ClearScreen() {}
    }

    /** 打印当前会话历史(/history)。 */
    public static final class ShowHistory extends Command {
        public static final ShowHistory INSTANCE = new ShowHistory();
        private ShowHistory() {}
    }

    /** 清空当前会话,开始新会话(/reset)。 */
    public static final class ResetSession extends Command {
        public static final ResetSession INSTANCE = new ResetSession();
        private ResetSession() {}
    }
}

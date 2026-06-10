package com.firstcode.repl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandParserTest {

    private final CommandParser parser = new CommandParser();

    @Test
    void emptyLineIsEmpty() {
        assertThat(parser.parse("").isEmpty()).isTrue();
        assertThat(parser.parse(null).isEmpty()).isTrue();
        assertThat(parser.parse("   ").isEmpty()).isTrue();
    }

    @Test
    void exitAndQuit() {
        assertThat(parser.parse("exit").command()).contains(Command.Exit.INSTANCE);
        assertThat(parser.parse("quit").command()).contains(Command.Exit.INSTANCE);
        assertThat(parser.parse("EXIT").command()).contains(Command.Exit.INSTANCE);
        assertThat(parser.parse("Quit").command()).contains(Command.Exit.INSTANCE);
    }

    @Test
    void clearCommand() {
        assertThat(parser.parse("/clear").command()).contains(Command.ClearScreen.INSTANCE);
        assertThat(parser.parse("  /clear  ").command()).contains(Command.ClearScreen.INSTANCE);
    }

    @Test
    void historyCommand() {
        assertThat(parser.parse("/history").command()).contains(Command.ShowHistory.INSTANCE);
    }

    @Test
    void resetCommand() {
        assertThat(parser.parse("/reset").command()).contains(Command.ResetSession.INSTANCE);
    }

    @Test
    void unknownCommandBecomesMessage() {
        // /unknown 不是已知命令 → 作为普通消息
        CommandParser.ParsedLine p = parser.parse("/unknown");
        assertThat(p.isMessage()).isTrue();
        assertThat(p.message()).contains("/unknown");
    }

    @Test
    void plainTextIsMessage() {
        assertThat(parser.parse("hello world").message()).contains("hello world");
    }

    @Test
    void messagePreservesContent() {
        assertThat(parser.parse("  hello  ").message()).contains("hello");
    }

    @Test
    void messageContainingSlash() {
        // 普通消息中含 / 不视为命令
        assertThat(parser.parse("a/b/c").message()).contains("a/b/c");
    }
}

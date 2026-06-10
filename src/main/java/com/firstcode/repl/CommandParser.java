package com.firstcode.repl;

import java.util.Optional;

/**
 * REPL 命令解析器。
 *
 * <p>输入分类:
 * <ul>
 *   <li>空 / 纯空白 → {@link ParsedLine.Empty}</li>
 *   <li>{@code exit} / {@code quit} → {@code Command.Exit}</li>
 *   <li>{@code /clear} → {@code Command.ClearScreen}</li>
 *   <li>{@code /history} → {@code Command.ShowHistory}</li>
 *   <li>{@code /reset} → {@code Command.ResetSession}</li>
 *   <li>其它 → {@link ParsedLine.Message},文本为去除首尾空白后的原文</li>
 * </ul>
 */
public final class CommandParser {

    public ParsedLine parse(String line) {
        if (line == null) {
            return ParsedLine.empty();
        }
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return ParsedLine.empty();
        }
        String lower = trimmed.toLowerCase();
        switch (lower) {
            case "exit", "quit" -> {
                return ParsedLine.ofCommand(Command.Exit.INSTANCE);
            }
            case "/clear" -> {
                return ParsedLine.ofCommand(Command.ClearScreen.INSTANCE);
            }
            case "/history" -> {
                return ParsedLine.ofCommand(Command.ShowHistory.INSTANCE);
            }
            case "/reset" -> {
                return ParsedLine.ofCommand(Command.ResetSession.INSTANCE);
            }
            default -> {
                return ParsedLine.message(trimmed);
            }
        }
    }

    /** 解析结果(sealed-style 三态)。 */
    public static abstract class ParsedLine {

        private ParsedLine() {
        }

        public static ParsedLine empty() {
            return Empty.INSTANCE;
        }

        public static ParsedLine ofCommand(Command cmd) {
            return new OfCommand(cmd);
        }

        public static ParsedLine message(String text) {
            return new Message(text);
        }

        public boolean isEmpty() {
            return this instanceof Empty;
        }

        public boolean isCommand() {
            return this instanceof OfCommand;
        }

        public boolean isMessage() {
            return this instanceof Message;
        }

        public Optional<Command> command() {
            return this instanceof OfCommand c ? Optional.of(c.value) : Optional.empty();
        }

        public Optional<String> message() {
            return this instanceof Message m ? Optional.of(m.text) : Optional.empty();
        }

        public static final class Empty extends ParsedLine {
            public static final Empty INSTANCE = new Empty();
            private Empty() {}
        }

        public static final class OfCommand extends ParsedLine {
            public final Command value;

            private OfCommand(Command value) {
                this.value = value;
            }
        }

        public static final class Message extends ParsedLine {
            public final String text;

            private Message(String text) {
                this.text = text;
            }
        }
    }
}

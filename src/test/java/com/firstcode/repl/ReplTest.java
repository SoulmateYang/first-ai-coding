package com.firstcode.repl;

import com.firstcode.config.Config;
import com.firstcode.io.BufferingOutput;
import com.firstcode.provider.Provider;
import com.firstcode.provider.ProviderException;
import com.firstcode.provider.StreamEvent;
import com.firstcode.provider.StreamRequest;
import com.firstcode.session.Message;
import com.firstcode.session.Role;
import com.firstcode.session.Session;
import com.firstcode.session.SessionIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repl 集成测试:用 mock Provider + 真实 SessionIO + 临时目录,覆盖:
 * <ul>
 *   <li>用户输入消息 → Provider 流式事件 → Session 追加 + 持久化</li>
 *   <li>命令(exit / /reset / /history)分支</li>
 *   <li>Provider 错误不导致 REPL 退出</li>
 *   <li>多轮对话</li>
 * </ul>
 */
class ReplTest {

    @Test
    void streamsResponseAndAppendsAssistantToSession(@TempDir Path tmp) {
        FakeProvider provider = FakeProvider.ofTurn(turn(
                new StreamEvent.Delta("hello "),
                new StreamEvent.Delta("world"),
                StreamEvent.Done.INSTANCE));
        BufferingOutput out = new BufferingOutput();
        Config config = newConfig();

        Repl repl = new Repl(config, provider, Session.empty(),
                new SessionIO(tmp.resolve("session.json")), out,
                new Scanner("hi\nexit\n"));
        repl.run();

        Session s = repl.currentSession();
        assertThat(s.size()).isEqualTo(2);
        assertThat(s.messages().get(0).role()).isEqualTo(Role.USER);
        assertThat(s.messages().get(0).content()).isEqualTo("hi");
        assertThat(s.messages().get(1).role()).isEqualTo(Role.ASSISTANT);
        assertThat(s.messages().get(1).content()).isEqualTo("hello world");

        assertThat(out.text()).contains("hello world");
        assertThat(provider.calls).hasSize(1);
        assertThat(provider.calls.get(0)).hasSize(1);
        assertThat(provider.calls.get(0).get(0).content()).isEqualTo("hi");
    }

    @Test
    void exitCommandTerminatesLoop(@TempDir Path tmp) {
        FakeProvider provider = FakeProvider.empty();
        BufferingOutput out = new BufferingOutput();
        Config config = newConfig();

        Repl repl = new Repl(config, provider, Session.empty(),
                new SessionIO(tmp.resolve("session.json")), out,
                new Scanner("exit\n"));
        repl.run();

        assertThat(out.infos()).contains("bye");
        assertThat(repl.currentSession().size()).isZero();
        assertThat(provider.calls).isEmpty();
    }

    @Test
    void resetCommandClearsSessionAndPersists(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("session.json");
        SessionIO io = new SessionIO(file);
        io.save(Session.from(List.of(
                Message.user("old user"),
                Message.assistant("old reply"))));

        Session loaded = io.load();
        assertThat(loaded.size()).isEqualTo(2);

        FakeProvider provider = FakeProvider.empty();
        BufferingOutput out = new BufferingOutput();
        Config config = newConfig();

        Repl repl = new Repl(config, provider, loaded, io, out,
                new Scanner("/reset\nexit\n"));
        repl.run();

        assertThat(repl.currentSession().size()).isZero();
        assertThat(io.load().size()).isZero();
        assertThat(out.infos()).contains("session reset");
    }

    @Test
    void providerErrorDoesNotCrashRepl(@TempDir Path tmp) {
        FakeProvider provider = FakeProvider.ofTurn(turn(
                new StreamEvent.Error(new ProviderException("upstream boom"))));
        BufferingOutput out = new BufferingOutput();
        Config config = newConfig();

        Repl repl = new Repl(config, provider, Session.empty(),
                new SessionIO(tmp.resolve("session.json")), out,
                new Scanner("hi\nexit\n"));
        repl.run();

        assertThat(out.errors()).contains("upstream boom");
        assertThat(out.infos()).contains("bye");
        assertThat(repl.currentSession().size())
                .as("error path should NOT add an assistant turn")
                .isEqualTo(1);
    }

    @Test
    void historyCommandPrintsAllMessages(@TempDir Path tmp) {
        FakeProvider provider = FakeProvider.empty();
        BufferingOutput out = new BufferingOutput();
        Config config = newConfig();

        Session start = Session.from(List.of(
                Message.user("first"),
                Message.assistant("first reply"),
                Message.user("second")));

        Repl repl = new Repl(config, provider, start,
                new SessionIO(tmp.resolve("session.json")), out,
                new Scanner("/history\nexit\n"));
        repl.run();

        assertThat(out.text()).contains("[user] first");
        assertThat(out.text()).contains("[assistant] first reply");
        assertThat(out.text()).contains("[user] second");
    }

    @Test
    void multiTurnAccumulatesHistory(@TempDir Path tmp) {
        FakeProvider provider = FakeProvider.ofTurns(turns(
                turn(new StreamEvent.Delta("a1"), StreamEvent.Done.INSTANCE),
                turn(new StreamEvent.Delta("a2"), StreamEvent.Done.INSTANCE)));
        BufferingOutput out = new BufferingOutput();
        Config config = newConfig();

        Repl repl = new Repl(config, provider, Session.empty(),
                new SessionIO(tmp.resolve("session.json")), out,
                new Scanner("q1\nq2\nexit\n"));
        repl.run();

        Session s = repl.currentSession();
        assertThat(s.size()).isEqualTo(4);
        assertThat(s.messages().get(0).content()).isEqualTo("q1");
        assertThat(s.messages().get(1).content()).isEqualTo("a1");
        assertThat(s.messages().get(2).content()).isEqualTo("q2");
        assertThat(s.messages().get(3).content()).isEqualTo("a2");
        assertThat(provider.calls).hasSize(2);
        assertThat(provider.calls.get(1)).hasSize(3);
    }

    @Test
    void extendedThinkingRendersAndPersists(@TempDir Path tmp) {
        FakeProvider provider = FakeProvider.ofTurn(turn(
                new StreamEvent.Thinking("hmm"),
                new StreamEvent.Thinking("..."),
                new StreamEvent.Delta("answer"),
                StreamEvent.Done.INSTANCE));
        BufferingOutput out = new BufferingOutput();
        Config config = new Config("anthropic", "claude-test",
                "http://localhost:1", "sk-test", "enabled");

        Repl repl = new Repl(config, provider, Session.empty(),
                new SessionIO(tmp.resolve("session.json")), out,
                new Scanner("think\nexit\n"));
        repl.run();

        assertThat(out.text()).contains("[thinking]hmm...");
        assertThat(out.text()).contains("answer");
        assertThat(repl.currentSession().messages().get(1).thinking())
                .isEqualTo("hmm...");
    }

    /**
     * 500 条历史下,「准备 stream 请求 + Provider 收到 events」全程耗时 < 500ms(AC36 拆分项)。
     * 用 FakeProvider 排除真实网络,只测 Repl → Session → Provider 调用链自身的吞吐。
     */
    @Test
    void largeHistoryStaysWithinPerfBudget() {
        Session s = Session.empty();
        for (int i = 0; i < 250; i++) {
            s = s.appendUserMessage("user question #" + i + " with some text " + i);
            s = s.appendAssistantMessage("assistant answer #" + i + " also with some text " + i, null);
        }
        assertThat(s.size()).isEqualTo(500);

        FakeProvider provider = FakeProvider.ofTurn(turn(
                new StreamEvent.Delta("ok"),
                StreamEvent.Done.INSTANCE));

        List<StreamEvent> events = new ArrayList<>();
        long t0 = System.nanoTime();
        provider.stream(s.messages(), StreamRequest.of("gpt-test", false), events::add);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        assertThat(events).hasSize(2);
        assertThat(elapsedMs).as("500 条历史下 stream 调用耗时(ms)").isLessThan(500);
    }

    private static Config newConfig() {
        return new Config("anthropic", "claude-test",
                "http://localhost:1", "sk-test", null);
    }

    private static List<StreamEvent> turn(StreamEvent... events) {
        return new ArrayList<>(List.of(events));
    }

    private static List<StreamEvent>[] turns(List<StreamEvent>... turns) {
        @SuppressWarnings("unchecked")
        List<StreamEvent>[] out = (List<StreamEvent>[]) new List[turns.length];
        for (int i = 0; i < turns.length; i++) {
            out[i] = turns[i];
        }
        return out;
    }

    /** 简单可控的 Provider:每次 stream() 从构造时给定的「轮次」列表里按顺序取下一组。 */
    private static final class FakeProvider implements Provider {
        private final List<List<StreamEvent>> turns;
        final List<List<Message>> calls = new ArrayList<>();
        private int cursor = 0;

        static FakeProvider empty() {
            return new FakeProvider(List.of());
        }

        static FakeProvider ofTurn(List<StreamEvent> firstTurn) {
            return new FakeProvider(List.of(firstTurn));
        }

        static FakeProvider ofTurns(List<StreamEvent>[] turns) {
            return new FakeProvider(new ArrayList<>(List.of(turns)));
        }

        private FakeProvider(List<List<StreamEvent>> turns) {
            this.turns = turns;
        }

        @Override
        public String name() {
            return "fake";
        }

        @Override
        public void stream(List<Message> messages, StreamRequest request,
                           Consumer<StreamEvent> sink) {
            calls.add(new ArrayList<>(messages));
            List<StreamEvent> events = cursor < turns.size() ? turns.get(cursor) : List.of();
            cursor++;
            for (StreamEvent e : events) {
                sink.accept(e);
            }
        }
    }
}

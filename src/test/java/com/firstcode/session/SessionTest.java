package com.firstcode.session;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTest {

    @Test
    void emptyHasNoMessages() {
        Session s = Session.empty();
        assertThat(s.size()).isZero();
        assertThat(s.messages()).isEmpty();
    }

    @Test
    void appendUserMessageReturnsNewSession() {
        Session s1 = Session.empty();
        Session s2 = s1.appendUserMessage("hi");
        assertThat(s1.size()).isZero();
        assertThat(s2.size()).isEqualTo(1);
        assertThat(s2.messages().get(0).role()).isEqualTo(Role.USER);
        assertThat(s2.messages().get(0).content()).isEqualTo("hi");
    }

    @Test
    void appendAssistantMessageWithoutThinking() {
        Session s = Session.empty().appendAssistantMessage("hello", null);
        assertThat(s.messages()).hasSize(1);
        assertThat(s.messages().get(0).role()).isEqualTo(Role.ASSISTANT);
        assertThat(s.messages().get(0).content()).isEqualTo("hello");
        assertThat(s.messages().get(0).thinking()).isNull();
    }

    @Test
    void appendAssistantMessageWithThinking() {
        Session s = Session.empty().appendAssistantMessage("answer", "thinking step");
        assertThat(s.messages().get(0).thinking()).isEqualTo("thinking step");
    }

    @Test
    void clearRemovesAllMessages() {
        Session s = Session.empty()
                .appendUserMessage("a")
                .appendAssistantMessage("b", null)
                .appendUserMessage("c")
                .clear();
        assertThat(s.size()).isZero();
    }

    @Test
    void messagesIsImmutable() {
        Session s = Session.empty().appendUserMessage("x");
        List<Message> msgs = s.messages();
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> msgs.add(Message.user("y")));
    }

    @Test
    void appendingIsImmutable() {
        Session s1 = Session.empty();
        Session s2 = s1.appendUserMessage("a");
        Session s3 = s2.appendUserMessage("b");
        assertThat(s1.size()).isZero();
        assertThat(s2.size()).isEqualTo(1);
        assertThat(s3.size()).isEqualTo(2);
    }

    @Test
    void fromReusesProvidedList() {
        Session s = Session.from(List.of(Message.user("a"), Message.assistant("b", null)));
        assertThat(s.size()).isEqualTo(2);
    }
}

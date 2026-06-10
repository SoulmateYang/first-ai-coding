package com.firstcode.io;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SanitizerTest {

    @Test
    void scrubsOpenAiKey() {
        String input = "Authorization: Bearer sk-abc1234567890xyz";
        String result = Sanitizer.scrub(input);
        assertThat(result).contains("***").doesNotContain("sk-abc1234567890xyz");
    }

    @Test
    void scrubsAnthropicKey() {
        String input = "x-api-key: sk-ant-abc1234567890xyz";
        String result = Sanitizer.scrub(input);
        assertThat(result).contains("***").doesNotContain("sk-ant-abc1234567890xyz");
    }

    @Test
    void scrubsApiKeyField() {
        String input = "config: api_key: \"abcdef1234567890\"";
        String result = Sanitizer.scrub(input);
        assertThat(result).contains("***").doesNotContain("abcdef1234567890");
    }

    @Test
    void scrubsMultipleOccurrences() {
        String input = "first sk-abcdefghij1234 and second sk-xyz0987654321";
        String result = Sanitizer.scrub(input);
        assertThat(result).doesNotContain("sk-abcdefghij1234").doesNotContain("sk-xyz0987654321");
    }

    @Test
    void leavesNonSensitiveText() {
        String input = "Hello world, this is a normal message about cats and dogs.";
        String result = Sanitizer.scrub(input);
        assertThat(result).isEqualTo(input);
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(Sanitizer.scrub((String) null)).isNull();
        assertThat(Sanitizer.scrub("")).isEqualTo("");
    }

    @Test
    void scrubsCauseChain() {
        Throwable inner = new RuntimeException("leaked sk-abcdefghij1234");
        Throwable outer = new IllegalStateException("wrapper", inner);
        String result = Sanitizer.scrub(outer);
        assertThat(result)
                .contains("IllegalStateException")
                .contains("RuntimeException")
                .contains("***")
                .doesNotContain("sk-abcdefghij1234");
    }

    @Test
    void handlesThrowableWithoutMessage() {
        Throwable t = new RuntimeException();
        String result = Sanitizer.scrub(t);
        assertThat(result).contains("RuntimeException");
    }
}

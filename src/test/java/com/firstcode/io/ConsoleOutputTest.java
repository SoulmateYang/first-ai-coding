package com.firstcode.io;

import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleOutputTest {

    /** 去掉平台行尾差异(Windows \r\n vs Unix \n)。 */
    private static String norm(String s) {
        return s.replace("\r\n", "\n");
    }

    @Test
    void printWritesToOutAndFlushes() {
        StringWriter outBuf = new StringWriter();
        StringWriter errBuf = new StringWriter();
        Output out = new ConsoleOutput(
                new PrintWriter(outBuf, true), new PrintWriter(errBuf, true));

        out.print("hello");

        assertThat(outBuf.toString()).isEqualTo("hello");
        assertThat(errBuf.toString()).isEmpty();
    }

    @Test
    void printlnAddsNewline() {
        StringWriter outBuf = new StringWriter();
        StringWriter errBuf = new StringWriter();
        Output out = new ConsoleOutput(
                new PrintWriter(outBuf, true), new PrintWriter(errBuf, true));

        out.println("line");

        assertThat(norm(outBuf.toString())).isEqualTo("line\n");
    }

    @Test
    void printPromptWritesStandardPrompt() {
        StringWriter outBuf = new StringWriter();
        StringWriter errBuf = new StringWriter();
        Output out = new ConsoleOutput(
                new PrintWriter(outBuf, true), new PrintWriter(errBuf, true));

        out.printPrompt();

        assertThat(outBuf.toString()).isEqualTo("> ");
    }

    @Test
    void printInfoWritesToErrWithPrefix() {
        StringWriter outBuf = new StringWriter();
        StringWriter errBuf = new StringWriter();
        Output out = new ConsoleOutput(
                new PrintWriter(outBuf, true), new PrintWriter(errBuf, true));

        out.printInfo("ready");

        assertThat(norm(errBuf.toString())).isEqualTo("INFO: ready\n");
        assertThat(outBuf.toString()).isEmpty();
    }

    @Test
    void printErrorWritesToErrAndScrubs() {
        StringWriter outBuf = new StringWriter();
        StringWriter errBuf = new StringWriter();
        Output out = new ConsoleOutput(
                new PrintWriter(outBuf, true), new PrintWriter(errBuf, true));

        out.printError(new RuntimeException("api_key=sk-abcdefghij1234"));

        String written = norm(errBuf.toString());
        assertThat(written).contains("ERROR:").contains("api_key=***");
        assertThat(written).doesNotContain("sk-abcdefghij1234");
    }
}

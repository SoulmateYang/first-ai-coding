package com.firstcode.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionIOTest {

    @Test
    void loadReturnsEmptyWhenFileMissing(@TempDir Path tmp) {
        SessionIO io = new SessionIO(tmp.resolve("session.json"));
        Session s = io.load();
        assertThat(s.size()).isZero();
    }

    @Test
    void saveAndLoadRoundTrip(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("session.json");
        SessionIO io = new SessionIO(file);
        Session s = Session.empty()
                .appendUserMessage("hello")
                .appendAssistantMessage("world", null);
        io.save(s);
        Session loaded = io.load();
        assertThat(loaded.size()).isEqualTo(2);
        assertThat(loaded.messages().get(0).content()).isEqualTo("hello");
        assertThat(loaded.messages().get(1).content()).isEqualTo("world");
    }

    @Test
    void saveLeavesNoTmpFileBehind(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("session.json");
        SessionIO io = new SessionIO(file);
        io.save(Session.empty().appendUserMessage("hi"));
        Path tmpFile = file.resolveSibling("session.json.tmp");
        assertThat(Files.exists(tmpFile)).isFalse();
        assertThat(Files.exists(file)).isTrue();
    }

    @Test
    void saveCreatesParentDirectory(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("nested/dir/session.json");
        SessionIO io = new SessionIO(file);
        io.save(Session.empty().appendUserMessage("hi"));
        assertThat(Files.exists(file)).isTrue();
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void saveSetsOwnerReadWriteOnUnix(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("session.json");
        SessionIO io = new SessionIO(file);
        io.save(Session.empty().appendUserMessage("hi"));
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
        assertThat(perms).containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void saveDoesNotThrowOnWindows(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("session.json");
        SessionIO io = new SessionIO(file);
        io.save(Session.empty().appendUserMessage("hi"));
        assertThat(Files.exists(file)).isTrue();
    }

    @Test
    void loadThrowsOnCorruptedFile(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("session.json");
        Files.writeString(file, "this is not valid json {");
        SessionIO io = new SessionIO(file);
        assertThatThrownBy(io::load)
                .isInstanceOf(SessionException.class);
    }

    @Test
    void defaultPathPointsToUserHome() {
        Path p = SessionIO.defaultPath();
        assertThat(p.toString()).contains(".firstcode").endsWith("session.json");
    }
}

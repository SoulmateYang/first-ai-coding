package com.firstcode.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 会话文件的读写工具。
 *
 * <p>写入采用「先写 .tmp,再 Files.move(ATOMIC_MOVE)」模式,避免半截 JSON;在 Win / Unix 上都由
 * {@link java.nio.file.StandardCopyOption#ATOMIC_MOVE} 保证原子性。
 *
 * <p>POSIX 权限(0600)仅在支持的文件系统上设置,Windows 自动跳过。
 */
public final class SessionIO {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final Set<PosixFilePermission> OWNER_RW =
            PosixFilePermissions.fromString("rw-------");

    private final Path file;

    public SessionIO(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public static Path defaultPath() {
        String home = System.getProperty("user.home");
        return Path.of(home, ".firstcode", "session.json");
    }

    /** 序列化 + 原子写入,目标文件父目录会自动创建。 */
    public void save(Session session) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
            MAPPER.writeValue(tmp.toFile(), session.messages());
            try {
                Files.move(tmp, file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            trySetOwnerReadWrite(file);
        } catch (IOException ex) {
            throw new SessionException("failed to save session to " + file, ex);
        }
    }

    /** 加载:文件不存在 → 空 Session;存在但解析失败 → 抛 SessionException。 */
    public Session load() {
        if (!Files.exists(file)) {
            return Session.empty();
        }
        try {
            List<Message> messages = MAPPER.readValue(
                    file.toFile(), new TypeReference<List<Message>>() {});
            return Session.from(messages);
        } catch (IOException ex) {
            throw new SessionException("failed to load session from " + file, ex);
        }
    }

    private void trySetOwnerReadWrite(Path target) {
        try {
            Set<PosixFilePermission> perms = EnumSet.copyOf(OWNER_RW);
            Files.setPosixFilePermissions(target, perms);
        } catch (UnsupportedOperationException unsupported) {
            // Windows / 非 POSIX 文件系统,跳过
        } catch (IOException ignored) {
            // 权限设置失败不阻塞主流程
        }
    }
}

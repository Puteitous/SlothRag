package com.slothrag.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slothrag.ai.llm.ChatMessage;
import com.slothrag.common.web.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 会话存储：JSONL 文件追加（每条消息一行 JSON），按日期分目录
 * <p>
 * 设计说明（参考 HippoBuddy SessionStorage 思路，简化掉异步刷盘与去重）：
 * - sessionId 格式 web-{13 位毫秒时间戳}_{随机后缀}，ID 内时间戳可直接推导日期目录，
 *   读/写/删无需外部映射即可定位文件；
 * - 旧版纯 UUID 会话无法解析日期，统一落 legacy/ 目录兼容；
 * - 顺序追加 = 稳定前缀，天然适配长上下文窗口与模型侧缓存命中；
 * - 对话频率低（每轮 2 条消息），同步写文件足够
 */
@Slf4j
@Service
public class SessionStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 会话 ID 前缀（含时间戳，参考 HippoBuddy web- 格式） */
    private static final String SESSION_ID_PREFIX = "web-";
    /** 旧版会话（纯 UUID，无法解析日期）统一存放目录 */
    private static final String LEGACY_DIR = "legacy";
    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Path sessionDir;

    public SessionStore(@Value("${kb.session-dir}") String sessionDir) {
        this.sessionDir = Path.of(sessionDir);
    }

    public String newSessionId() {
        return SESSION_ID_PREFIX + System.currentTimeMillis() + "_"
                + Integer.toHexString(ThreadLocalRandom.current().nextInt());
    }

    /** 会话文件路径：web- 前缀按 ID 内时间戳落日期子目录；旧版 UUID 落 legacy/ */
    private Path sessionFilePath(String sessionId) {
        return sessionDir.resolve(dateDirOf(sessionId)).resolve(sessionId + ".jsonl");
    }

    private String dateDirOf(String sessionId) {
        if (sessionId != null && sessionId.startsWith(SESSION_ID_PREFIX)) {
            String rest = sessionId.substring(SESSION_ID_PREFIX.length());
            int idx = rest.indexOf('_');
            String numeric = idx > 0 ? rest.substring(0, idx) : rest;
            if (numeric.length() >= 13) {
                try {
                    long millis = Long.parseLong(numeric.substring(0, 13));
                    return LocalDate.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
                            .format(DATE_DIR);
                } catch (NumberFormatException e) {
                    // 时间戳解析失败，走 legacy 兜底
                }
            }
        }
        return LEGACY_DIR;
    }

    /**
     * 删除会话文件（与 conversation 记录删除配套，不存在时静默）
     */
    public void deleteSessionFile(String sessionId) {
        Path file = sessionFilePath(sessionId);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("删除会话文件失败 sessionId={}", sessionId, e);
        }
    }

    /**
     * 追加一条消息到会话文件
     */
    public synchronized void appendMessage(String sessionId, ChatMessage message) {
        try {
            Path file = sessionFilePath(sessionId);
            Files.createDirectories(file.getParent());
            Files.writeString(file,
                    MAPPER.writeValueAsString(message) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("会话写入失败 sessionId={}", sessionId, e);
            throw new BizException("SESSION_WRITE_ERROR", "会话写入失败");
        }
    }

    /**
     * 扫描 sessions 目录（日期子目录 + legacy），返回所有存在的会话 id（按文件修改时间倒序）
     */
    public List<String> listSessionIds() {
        if (!Files.exists(sessionDir)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (var entries = Files.list(sessionDir)) {
            for (Path entry : entries.toList()) {
                if (Files.isDirectory(entry)) {
                    try (var inner = Files.list(entry)) {
                        inner.filter(p -> p.toString().endsWith(".jsonl")).forEach(files::add);
                    }
                } else if (entry.toString().endsWith(".jsonl")) {
                    files.add(entry);
                }
            }
        } catch (IOException e) {
            log.warn("扫描会话文件失败", e);
            return List.of();
        }
        return files.stream()
                .sorted((a, b) -> {
                    try {
                        return Long.compare(
                                Files.getLastModifiedTime(b).toMillis(),
                                Files.getLastModifiedTime(a).toMillis());
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .map(p -> p.getFileName().toString().replace(".jsonl", ""))
                .toList();
    }

    /**
     * 从 jsonl 文件中提取首条用户消息作为会话标题。
     * 取前 30 个字符，不足时截断。
     */
    public String extractTitle(String sessionId) {
        Path file = sessionFilePath(sessionId);
        if (!Files.exists(file)) {
            return null;
        }
        try (var lines = Files.lines(file)) {
            String title = lines
                    .filter(l -> !l.isBlank())
                    .map(l -> {
                        try {
                            return MAPPER.readValue(l, ChatMessage.class);
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .filter(m -> m != null && "user".equals(m.getRole()) && m.getContent() != null && !m.getContent().isBlank())
                    .findFirst()
                    .map(m -> m.getContent().trim())
                    .orElse(null);
            if (title == null) {
                return null;
            }
            return title.length() <= 30 ? title : title.substring(0, 30);
        } catch (IOException e) {
            log.warn("读取会话标题失败 sessionId={}", sessionId, e);
            return null;
        }
    }

    /**
     * 读取会话全部历史（按写入顺序）
     */
    public List<ChatMessage> loadMessages(String sessionId) {
        Path file = sessionFilePath(sessionId);
        if (!Files.exists(file)) {
            return List.of();
        }
        try (var lines = Files.lines(file)) {
            return lines.filter(l -> !l.isBlank())
                    .map(l -> {
                        try {
                            return MAPPER.readValue(l, ChatMessage.class);
                        } catch (IOException e) {
                            throw new RuntimeException("会话记录解析失败: " + e.getMessage(), e);
                        }
                    })
                    .toList();
        } catch (IOException e) {
            log.error("会话读取失败 sessionId={}", sessionId, e);
            throw new BizException("SESSION_READ_ERROR", "会话读取失败");
        }
    }
}

package com.ragbase.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragbase.ai.llm.ChatMessage;
import com.ragbase.common.web.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;

/**
 * 会话存储：JSONL 文件追加（每条消息一行 JSON）
 * <p>
 * 设计说明（参考 HippoBuddy SessionTranscript 思路，简化掉异步刷盘与去重）：
 * 顺序追加 = 稳定前缀，天然适配长上下文窗口与模型侧缓存命中；
 * 对话频率低（每轮 2 条消息），同步写文件足够
 */
@Slf4j
@Service
public class SessionStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path sessionDir;

    public SessionStore(@Value("${kb.storage-dir}") String storageDir) {
        this.sessionDir = Path.of(storageDir, "sessions");
    }

    public String newSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 追加一条消息到会话文件
     */
    public synchronized void appendMessage(String sessionId, ChatMessage message) {
        try {
            Files.createDirectories(sessionDir);
            Path file = sessionDir.resolve(sessionId + ".jsonl");
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
     * 读取会话全部历史（按写入顺序）
     */
    public List<ChatMessage> loadMessages(String sessionId) {
        Path file = sessionDir.resolve(sessionId + ".jsonl");
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

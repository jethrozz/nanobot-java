package org.nanobot.agent;

import lombok.extern.slf4j.Slf4j;
import org.nanobot.model.ChatMessage;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话管理器
 */
@Slf4j
@Component
public class SessionManager {

    private static final String SESSION_DIR = System.getProperty("user.home") + "/.nanobot/sessions";

    public SessionManager() {
        // 确保会话目录存在
        File dir = new File(SESSION_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * 获取会话历史
     *
     * @param sessionId    会话ID
     * @param maxMessages  最大消息数
     * @return List<ChatMessage>
     */
    public List<ChatMessage> getHistory(String sessionId, int maxMessages) {
        Path sessionFile = getSessionFile(sessionId);
        if (!Files.exists(sessionFile)) {
            return List.of();
        }

        List<ChatMessage> messages = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(sessionFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    ChatMessage msg = parseMessage(line);
                    if (msg != null) {
                        messages.add(msg);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse message: {}", line, e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to read session file: {}", sessionFile, e);
        }

        // 返回最近的 N 条消息
        if (messages.size() > maxMessages) {
            return messages.subList(messages.size() - maxMessages, messages.size());
        }
        return messages;
    }

    /**
     * 追加消息到会话
     *
     * @param sessionId 会话ID
     * @param message   消息
     */
    public void appendMessage(String sessionId, ChatMessage message) {
        Path sessionFile = getSessionFile(sessionId);

        try (BufferedWriter writer = Files.newBufferedWriter(
                sessionFile,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND)) {

            String line = formatMessage(message);
            writer.write(line);
            writer.newLine();

        } catch (IOException e) {
            log.error("Failed to write message to session file: {}", sessionFile, e);
        }
    }

    /**
     * 清除会话历史
     *
     * @param sessionId 会话ID
     */
    public void clearHistory(String sessionId) {
        Path sessionFile = getSessionFile(sessionId);
        try {
            Files.deleteIfExists(sessionFile);
        } catch (IOException e) {
            log.error("Failed to delete session file: {}", sessionFile, e);
        }
    }

    /**
     * 获取会话文件路径
     */
    private Path getSessionFile(String sessionId) {
        // 将 sessionId 中的特殊字符替换为下划线
        String safeSessionId = sessionId.replaceAll("[^a-zA-Z0-9:_-]", "_");
        return Paths.get(SESSION_DIR, safeSessionId + ".jsonl");
    }

    /**
     * 解析消息行
     */
    private ChatMessage parseMessage(String line) {
        // TODO: 实现完整的 JSON 解析
        return null;
    }

    /**
     * 格式化消息为行
     */
    private String formatMessage(ChatMessage message) {
        // TODO: 实现完整的 JSON 格式化
        return String.format("{\"role\":\"%s\",\"content\":\"%s\",\"timestamp\":%d}",
                message.getRole().getValue(),
                escapeJson(message.getContent()),
                message.getTimestamp());
    }

    /**
     * 转义 JSON 字符串
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

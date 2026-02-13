package org.nanobot.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.nanobot.config.AgentConfig;
import org.nanobot.model.ChatMessage;
import org.nanobot.model.ToolCall;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 会话管理器
 * 负责会话历史的持久化存储和检索
 * 使用 JSONL 格式存储，每行一条消息
 */
@Slf4j
@Component
public class SessionManager {

    private final String sessionDir;
    private final ObjectMapper objectMapper;

    public SessionManager(AgentConfig agentConfig) {
        this.sessionDir = agentConfig.getDefaultAgent().getWorkspace() + "/sessions";
        this.objectMapper = new ObjectMapper();

        // 确保会话目录存在
        File dir = new File(sessionDir);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                log.info("Created session directory: {}", sessionDir);
            }
        }
        log.debug("Session manager initialized with directory: {}", sessionDir);
    }

    /**
     * 获取会话历史
     *
     * @param sessionId   会话ID
     * @param maxMessages 最大消息数
     * @return List<ChatMessage> 按时间顺序排列的消息列表
     */
    public List<ChatMessage> getHistory(String sessionId, int maxMessages) {
        Path sessionFile = getSessionFile(sessionId);
        if (!Files.exists(sessionFile)) {
            log.debug("Session file not found: {}", sessionFile);
            return List.of();
        }

        List<ChatMessage> messages = new ArrayList<>();
        try (Stream<String> lines = Files.lines(sessionFile)) {
            lines.forEach(line -> {
                try {
                    ChatMessage msg = parseMessage(line);
                    if (msg != null) {
                        messages.add(msg);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse message line, skipping: {}", e.getMessage());
                }
            });
        } catch (IOException e) {
            log.error("Failed to read session file: {}", sessionFile, e);
            return List.of();
        }

        // 按时间戳排序
        messages.sort(Comparator.comparing(ChatMessage::getTimestamp));

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

        try {
            String json = formatMessage(message);
            Files.writeString(sessionFile, json + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.debug("Appended message to session: {}", sessionId);
        } catch (IOException e) {
            log.error("Failed to write message to session file: {}", sessionFile, e);
        }
    }

    /**
     * 清除会话历史
     *
     * @param sessionId 会话ID
     * @return boolean 是否成功删除
     */
    public boolean clearHistory(String sessionId) {
        Path sessionFile = getSessionFile(sessionId);
        try {
            boolean deleted = Files.deleteIfExists(sessionFile);
            if (deleted) {
                log.info("Cleared session history: {}", sessionId);
            }
            return deleted;
        } catch (IOException e) {
            log.error("Failed to delete session file: {}", sessionFile, e);
            return false;
        }
    }

    /**
     * 列出所有会话ID
     *
     * @return List<String> 会话ID列表
     */
    public List<String> listSessions() {
        File dir = new File(sessionDir);
        if (!dir.exists()) {
            return List.of();
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".jsonl"));
        if (files == null) {
            return List.of();
        }

        List<String> sessions = new ArrayList<>();
        for (File file : files) {
            String filename = file.getName();
            String sessionId = filename.substring(0, filename.length() - 6); // 移除 .jsonl
            sessions.add(sessionId);
        }
        return sessions;
    }

    /**
     * 检查会话是否存在
     *
     * @param sessionId 会话ID
     * @return boolean 是否存在
     */
    public boolean sessionExists(String sessionId) {
        return Files.exists(getSessionFile(sessionId));
    }

    /**
     * 获取会话文件路径
     *
     * @param sessionId 会话ID
     * @return Path 会话文件路径
     */
    private Path getSessionFile(String sessionId) {
        // 将 sessionId 中的特殊字符替换为下划线，防止路径遍历攻击
        // Windows 不允许文件名包含 : \ / * ? " < > |
        // 使用更严格的替换规则，仅保留字母数字和部分安全字符
        String safeSessionId = sessionId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return Paths.get(sessionDir, safeSessionId + ".jsonl");
    }

    /**
     * 解析消息行
     *
     * @param line JSON字符串
     * @return ChatMessage 解析后的消息，失败返回null
     */
    private ChatMessage parseMessage(String line) {
        try {
            JsonNode root = objectMapper.readTree(line);

            // 解析基本字段
            String roleStr = root.has("role") ? root.get("role").asText() : "user";
            ChatMessage.Role role = parseRole(roleStr);
            String content = root.has("content") ? root.get("content").asText() : "";
            Long timestamp = root.has("timestamp") ? root.get("timestamp").asLong() : System.currentTimeMillis();

            // 解析 tool calls (assistant 角色可能有)
            List<ToolCall> toolCalls = null;
            if (root.has("tool_calls") && root.get("tool_calls").isArray()) {
                toolCalls = new ArrayList<>();
                for (JsonNode toolCallNode : root.get("tool_calls")) {
                    toolCalls.add(parseToolCall(toolCallNode));
                }
            }

            // 解析 tool_call_id (tool 角色可能有)
            String toolCallId = root.has("tool_call_id") ? root.get("tool_call_id").asText() : null;

            // 构建消息
            ChatMessage.ChatMessageBuilder builder = ChatMessage.builder()
                    .role(role)
                    .content(content)
                    .timestamp(timestamp);

            if (toolCalls != null && !toolCalls.isEmpty()) {
                builder.toolCalls(toolCalls);
            }
            if (toolCallId != null) {
                builder.toolCallId(toolCallId);
            }

            return builder.build();

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON message: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析角色枚举
     */
    private ChatMessage.Role parseRole(String roleStr) {
        for (ChatMessage.Role role : ChatMessage.Role.values()) {
            if (role.getValue().equalsIgnoreCase(roleStr)) {
                return role;
            }
        }
        return ChatMessage.Role.USER; // 默认为用户角色
    }

    /**
     * 解析工具调用
     *
     * @param node JSON节点
     * @return ToolCall 工具调用对象
     */
    private ToolCall parseToolCall(JsonNode node) {
        // 兼容两种格式：带function包装和不带function包装的
        String arguments;
        String functionName;

        if (node.has("function")) {
            // 标准格式：{"id": "xxx", "type": "function", "function": {"name": "yyy", "arguments": "{}"}}
            JsonNode functionNode = node.get("function");
            functionName = functionNode.has("name") ? functionNode.get("name").asText() : null;
            arguments = functionNode.has("arguments") ? functionNode.get("arguments").asText() : null;
        } else {
            // 简化格式：{"id": "xxx", "functionName": "yyy", "arguments": "{}"}
            functionName = node.has("functionName") ? node.get("functionName").asText() : null;
            arguments = node.has("arguments") ? node.get("arguments").asText() : null;
        }

        return ToolCall.builder()
                .id(node.has("id") ? node.get("id").asText() : null)
                .type(node.has("type") ? node.get("type").asText() : "function")
                .functionName(functionName)
                .arguments(arguments)
                .build();
    }

    /**
     * 格式化消息为JSON字符串
     *
     * @param message 消息
     * @return String JSON字符串
     */
    private String formatMessage(ChatMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message to JSON", e);
            // 降级处理：使用简单格式
            return simpleFormat(message);
        }
    }

    /**
     * 简单格式化（当JSON序列化失败时降级使用）
     */
    private String simpleFormat(ChatMessage message) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"role\":\"").append(message.getRole().getValue()).append("\"");
        sb.append(",\"content\":\"").append(escapeJson(message.getContent())).append("\"");
        sb.append(",\"timestamp\":").append(message.getTimestamp());

        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            sb.append(",\"tool_calls\":[");
            for (int i = 0; i < message.getToolCalls().size(); i++) {
                if (i > 0) sb.append(",");
                ToolCall tc = message.getToolCalls().get(i);
                sb.append("{\"id\":\"").append(escapeJson(tc.getId())).append("\"");
                sb.append(",\"type\":\"").append(escapeJson(tc.getType())).append("\"");
                sb.append(",\"function\":{\"name\":\"").append(escapeJson(tc.getFunctionName())).append("\"");
                // 使用 Lombok 生成的 getArguments() 方法访问 arguments 字段
                String args = tc.getArguments();
                sb.append(",\"arguments\":\"").append(escapeJson(args)).append("\"}}");
            }
            sb.append("]");
        }

        if (message.getToolCallId() != null) {
            sb.append(",\"tool_call_id\":\"").append(message.getToolCallId()).append("\"");
        }

        sb.append("}");
        return sb.toString();
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

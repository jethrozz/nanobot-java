package org.nanobot.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nanobot.config.AgentConfig;
import org.nanobot.model.ChatMessage;
import org.nanobot.model.Message;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 上下文构建器
 * 负责组装系统提示词和对话历史
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextBuilder {

    private final AgentConfig agentConfig;
    private final SessionManager sessionManager;

    /**
     * 构建完整上下文
     *
     * @param message 用户消息
     * @return List<ChatMessage>
     */
    public List<ChatMessage> build(Message message) {
        List<ChatMessage> messages = new ArrayList<>();

        // 1. 系统提示词
        messages.add(buildSystemPrompt(message));

        // 2. 加载对话历史
        messages.addAll(loadHistory(message));

        // 3. 当前用户消息
        messages.add(ChatMessage.user(message.getContent()));

        return messages;
    }

    /**
     * 构建系统提示词
     */
    private ChatMessage buildSystemPrompt(Message message) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个 AI 助手，名字叫 Nanobot。\n\n");
        prompt.append(String.format("当前时间: %s\n\n", getCurrentTime()));
        prompt.append(String.format("工作目录: %s\n\n", getWorkspacePath()));

        // 添加可用工具信息
        prompt.append("你可以使用以下工具:\n");
        prompt.append("- read_file: 读取文件内容\n");
        prompt.append("- write_file: 写入文件内容\n");
        prompt.append("- exec: 执行 Shell 命令\n");
        prompt.append("- message: 发送消息到指定频道\n\n");

        prompt.append("请根据用户需求选择合适的工具来完成任务。");

        return ChatMessage.system(prompt.toString());
    }

    /**
     * 加载对话历史
     */
    private List<ChatMessage> loadHistory(Message message) {
        String sessionId = getSessionId(message);
        return sessionManager.getHistory(sessionId,
                agentConfig.getDefaultAgent().getMaxHistory());
    }

    /**
     * 获取会话 ID
     */
    private String getSessionId(Message message) {
        return String.format("%s:%s", message.getChannelType(), message.getUserId());
    }

    /**
     * 获取当前时间
     */
    private String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 获取工作区路径
     */
    private String getWorkspacePath() {
        return agentConfig.getDefaultAgent().getWorkspace();
    }
}

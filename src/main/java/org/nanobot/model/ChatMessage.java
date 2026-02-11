package org.nanobot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 聊天消息模型 (用于与 LLM 交互)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /**
     * 消息角色
     */
    private Role role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 工具调用列表 (assistant 角色时使用)
     */
    private List<ToolCall> toolCalls;

    /**
     * 工具调用ID (tool 角色时使用)
     */
    private String toolCallId;

    /**
     * 消息时间戳
     */
    private Long timestamp;

    /**
     * 消息角色枚举
     */
    public enum Role {
        /**
         * 系统消息
         */
        SYSTEM("system"),
        /**
         * 用户消息
         */
        USER("user"),
        /**
         * 助手消息
         */
        ASSISTANT("assistant"),
        /**
         * 工具消息
         */
        TOOL("tool");

        private final String value;

        Role(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 创建系统消息
     */
    public static ChatMessage system(String content) {
        return ChatMessage.builder()
                .role(Role.SYSTEM)
                .content(content)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建用户消息
     */
    public static ChatMessage user(String content) {
        return ChatMessage.builder()
                .role(Role.USER)
                .content(content)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建助手消息
     */
    public static ChatMessage assistant(String content) {
        return ChatMessage.builder()
                .role(Role.ASSISTANT)
                .content(content)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建助手消息（带工具调用）
     */
    public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
        return ChatMessage.builder()
                .role(Role.ASSISTANT)
                .content(content)
                .toolCalls(toolCalls)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建工具消息
     */
    public static ChatMessage tool(String toolCallId, String content) {
        return ChatMessage.builder()
                .role(Role.TOOL)
                .toolCallId(toolCallId)
                .content(content)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}

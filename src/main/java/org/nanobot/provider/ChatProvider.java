package org.nanobot.provider;

import org.nanobot.model.ChatMessage;
import org.nanobot.model.ToolCall;
import org.nanobot.tool.Tool;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Chat Provider 接口
 */
public interface ChatProvider {

    /**
     * 发送聊天请求
     *
     * @param messages 消息列表
     * @param tools    工具列表
     * @return ChatResponse
     */
    Mono<ChatResponse> chat(List<ChatMessage> messages, List<Tool> tools);

    /**
     * 流式聊天
     *
     * @param messages 消息列表
     * @param tools    工具列表
     * @return Flux<String>
     */
    Flux<String> chatStream(List<ChatMessage> messages, List<Tool> tools);

    /**
     * 聊天响应
     */
    class ChatResponse {
        private String content;
        private List<ToolCall> toolCalls;

        public ChatResponse(String content, List<ToolCall> toolCalls) {
            this.content = content;
            this.toolCalls = toolCalls;
        }

        public String getContent() {
            return content;
        }

        public List<ToolCall> getToolCalls() {
            return toolCalls;
        }

        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }

        /**
         * 创建文本响应
         */
        public static ChatResponse text(String content) {
            return new ChatResponse(content, null);
        }

        /**
         * 创建带工具调用的响应
         */
        public static ChatResponse withToolCalls(String content, List<ToolCall> toolCalls) {
            return new ChatResponse(content, toolCalls);
        }
    }
}

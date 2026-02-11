package org.nanobot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    /**
     * 工具调用ID
     */
    private String toolCallId;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 结果内容
     */
    private String content;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 创建成功结果
     */
    public static ToolResult success(String toolCallId, String content) {
        return ToolResult.builder()
                .toolCallId(toolCallId)
                .success(true)
                .content(content)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static ToolResult error(String toolCallId, Throwable e) {
        return ToolResult.builder()
                .toolCallId(toolCallId)
                .success(false)
                .error(e.getMessage())
                .build();
    }

    /**
     * 转换为 ChatMessage
     */
    public ChatMessage toMessage() {
        if (success) {
            return ChatMessage.tool(toolCallId, content);
        } else {
            return ChatMessage.tool(toolCallId, "Error: " + error);
        }
    }
}

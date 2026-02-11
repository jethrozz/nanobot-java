package org.nanobot.tool;

import org.nanobot.model.ToolCall;
import org.nanobot.model.ToolResult;
import reactor.core.publisher.Mono;

/**
 * 工具接口
 */
public interface Tool {

    /**
     * 工具名称（用于 LLM 调用）
     */
    String getName();

    /**
     * 工具描述
     */
    String getDescription();

    /**
     * 参数 Schema (JSON Schema 格式)
     */
    String getParameterSchema();

    /**
     * 执行工具
     *
     * @param call 工具调用
     * @return ToolResult
     */
    Mono<ToolResult> execute(ToolCall call);

    /**
     * 是否启用
     */
    default boolean isEnabled() {
        return true;
    }
}

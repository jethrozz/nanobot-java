package org.nanobot.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nanobot.model.ToolCall;
import org.nanobot.model.ToolResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 将自定义 Tool 接口适配为 Spring AI 的 ToolCallback
 * 基于 Spring AI 的 FunctionToolCallback 机制
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolCallbackAdapter {

    private final ApplicationContext applicationContext;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 将自定义 Tool 转换为 Spring AI ToolCallback
     * 使用 FunctionToolCallback 包装自定义工具
     *
     * @param tool 自定义工具
     * @return ToolCallback
     */
    public ToolCallback adaptToFunctionCallback(Tool tool) {
        // 使用 FunctionToolCallback.builder(name, function) 创建回调
        // function 接收 Map<String, Object> 类型的输入并返回 String
        return FunctionToolCallback.builder(tool.getName(), (Function<Map<String, Object>, String>) input -> {
            log.debug("Executing tool: {} with input: {}", tool.getName(), input);

            // 将输入（Map 或其他类型）转换为 JSON 字符串
            String argumentsJson;
            if (input == null) {
                argumentsJson = "{}";
            } else {
                argumentsJson = convertInputToJson(input);
            }

            // 创建 ToolCall 对象
            ToolCall toolCall = ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .functionName(tool.getName())
                    .arguments(argumentsJson)
                    .build();

            // 执行工具（同步等待结果）
            try {
                ToolResult result = tool.execute(toolCall).block();
                if (result != null && result.isSuccess()) {
                    log.debug("Tool {} executed successfully", tool.getName());
                    return result.getContent();
                } else {
                    String errorMsg = result != null ? result.getContent() : "Tool execution failed";
                    log.error("Tool {} execution failed: {}", tool.getName(), errorMsg);
                    return errorMsg;
                }
            } catch (Exception e) {
                log.error("Error executing tool: " + tool.getName(), e);
                return "Error: " + e.getMessage();
            }
        })
                .description(tool.getDescription())
                .inputType(Map.class)  // 使用 Map 作为通用输入类型
                .build();
    }

    /**
     * 将所有工具转换为 ToolCallback 列表
     *
     * @return ToolCallback 列表
     */
    public ToolCallback[] adaptAllTools() {
        return toolRegistry.getAllTools().stream()
                .map(this::adaptToFunctionCallback)
                .toArray(ToolCallback[]::new);
    }

    /**
     * 将 Spring AI 的输入对象转换为 JSON 字符串
     *
     * @param input 输入对象
     * @return JSON 字符串
     */
    private String convertInputToJson(Object input) {
        if (input == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert input to JSON", e);
            return "{}";
        }
    }
}

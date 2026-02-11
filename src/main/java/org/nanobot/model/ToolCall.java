package org.nanobot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具调用模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    /**
     * 工具调用ID
     */
    private String id;

    /**
     * 工具类型 (默认为 function)
     */
    private String type;

    /**
     * 函数名称
     */
    private String functionName;

    /**
     * 函数参数 (JSON 字符串)
     */
    private String arguments;

    /**
     * 获取指定参数
     */
    public String getArgument(String key) {
        // TODO: 解析 JSON 参数
        return null;
    }

    /**
     * 获取所有参数
     */
    public Map<String, Object> getArguments() {
        // TODO: 解析 JSON 参数
        return null;
    }
}

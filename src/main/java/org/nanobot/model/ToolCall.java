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
     *
     * @param key 参数键
     * @return String 参数值
     */
    public String getArgument(String key) {
        // 解析 JSON 参数
        return getArgumentAsMap().get(key);
    }

    /**
     * 获取所有参数
     *
     * @return Map<String, String> 参数映射
     */
    public Map<String, String> getArgumentAsMap() {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.core.type.TypeReference<Map<String, String>> typeRef =
                    new com.fasterxml.jackson.core.type.TypeReference<>() {};
            return mapper.readValue(arguments, typeRef);
        } catch (Exception e) {
            return Map.of();
        }
    }
}

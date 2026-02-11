package org.nanobot.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Provider 规范定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderSpec {

    /**
     * Provider 名称
     */
    private String name;

    /**
     * 匹配关键词
     */
    private List<String> keywords;

    /**
     * 环境变量名
     */
    private String envKey;

    /**
     * 模型名前缀
     */
    private String modelPrefix;

    /**
     * 是否为网关
     */
    private boolean isGateway;

    /**
     * API Key 前缀检测
     */
    private String detectByKeyPrefix;

    /**
     * 默认 Base URL
     */
    private String baseUrl;

    /**
     * 检查模型名是否匹配此 Provider
     */
    public boolean matchesModel(String model) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        String lowerModel = model.toLowerCase();
        return keywords.stream().anyMatch(keyword -> lowerModel.contains(keyword.toLowerCase()));
    }

    /**
     * 检查 API Key 是否匹配此 Provider
     */
    public boolean matchesApiKey(String apiKey) {
        if (detectByKeyPrefix == null || detectByKeyPrefix.isEmpty()) {
            return false;
        }
        return apiKey != null && apiKey.startsWith(detectByKeyPrefix);
    }
}

package org.nanobot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Provider 配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "nanobot.providers")
public class ProvidersConfig {

    /**
     * GLM 配置
     */
    private GLMConfig glm = new GLMConfig();

    /**
     * DeepSeek 配置
     */
    private DeepSeekConfig deepseek = new DeepSeekConfig();

    /**
     * Qwen 配置
     */
    private QwenConfig qwen = new QwenConfig();

    /**
     * Moonshot 配置
     */
    private MoonshotConfig moonshot = new MoonshotConfig();

    @Data
    public static class GLMConfig {
        private boolean enabled = true;
        private String apiKey;
        private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";
    }

    @Data
    public static class DeepSeekConfig {
        private boolean enabled = true;
        private String apiKey;
        private String baseUrl = "https://api.deepseek.com/v1";
    }

    @Data
    public static class QwenConfig {
        private boolean enabled = false;
        private String apiKey;
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    }

    @Data
    public static class MoonshotConfig {
        private boolean enabled = false;
        private String apiKey;
        private String baseUrl = "https://api.moonshot.cn/v1";
    }
}

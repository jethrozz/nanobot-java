package org.nanobot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "nanobot.agents")
public class AgentConfig {

    /**
     * 默认 Agent 配置
     */
    private DefaultAgentConfig defaultAgent = new DefaultAgentConfig();

    /**
     * 命名 Agent 配置
     */
    private Map<String, DefaultAgentConfig> agents = new HashMap<>();

    @Data
    public static class DefaultAgentConfig {
        /**
         * 模型名称
         */
        private String model = "glm-4-flash";

        /**
         * 工作区路径
         */
        private String workspace = System.getProperty("user.home") + "/.nanobot/workspace";

        /**
         * 最大迭代次数
         */
        private int maxIterations = 10;

        /**
         * 最大历史记录数
         */
        private int maxHistory = 50;

        /**
         * 温度参数
         */
        private double temperature = 0.7;

        /**
         * 系统提示词
         */
        private String systemPrompt;
    }
}

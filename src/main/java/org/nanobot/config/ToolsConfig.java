package org.nanobot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工具配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "nanobot.tools")
public class ToolsConfig {

    /**
     * 是否限制工具在工作区范围内
     */
    private boolean restrictToWorkspace = true;

    /**
     * 工作区路径
     */
    private String workspace = System.getProperty("user.home") + "/.nanobot/workspace";

    /**
     * 文件工具配置
     */
    private FileConfig file = new FileConfig();

    /**
     * Shell 工具配置
     */
    private ShellConfig shell = new ShellConfig();

    /**
     * Web 搜索配置
     */
    private WebSearchConfig webSearch = new WebSearchConfig();

    @Data
    public static class FileConfig {
        private boolean enabled = true;
        private int maxFileSize = 1024 * 1024; // 1MB
    }

    @Data
    public static class ShellConfig {
        private boolean enabled = true;
        private int timeout = 60;
        private List<String> blockedCommands = List.of("rm -rf", "format", "dd", "mkfs");
    }

    @Data
    public static class WebSearchConfig {
        private boolean enabled = false;
        private String provider = "brave";
        private String apiKey;
        private int maxResults = 5;
    }
}

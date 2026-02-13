package org.nanobot.tool.builtin;

import lombok.extern.slf4j.Slf4j;
import org.nanobot.config.ToolsConfig;
import org.nanobot.model.ToolCall;
import org.nanobot.model.ToolResult;
import org.nanobot.tool.Tool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件读取工具
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "nanobot.tools.file.enabled", havingValue = "true", matchIfMissing = true)
public class ReadFileTool implements Tool {

    private final ToolsConfig toolsConfig;

    public ReadFileTool(ToolsConfig toolsConfig) {
        this.toolsConfig = toolsConfig;
    }

    @Override
    public String getName() {
        return "read_file";
    }

    @Override
    public String getDescription() {
        return "读取文件内容。参数: path (文件路径)";
    }

    @Override
    public String getParameterSchema() {
        return """
                {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "文件路径"
                        }
                    },
                    "required": ["path"]
                }
                """;
    }

    @Override
    public Mono<ToolResult> execute(ToolCall call) {
        return Mono.fromCallable(() -> {
            String path = call.getArgument("path");
            log.info("Reading file: {}", path);

            // 工作区限制检查
            if (toolsConfig.isRestrictToWorkspace()) {
                validatePath(path);
            }

            File file = resolveFile(path);
            if (!file.exists()) {
                throw new IllegalArgumentException("File not found: " + path);
            }

            if (!file.isFile()) {
                throw new IllegalArgumentException("Not a file: " + path);
            }

            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);

            return ToolResult.success(call.getId(), content);
        })
                .onErrorResume(e -> Mono.just(ToolResult.error(call.getId(), e)));
    }

    /**
     * 验证路径是否在工作区内
     */
    private void validatePath(String userPath) {
        Path workspace = Paths.get(toolsConfig.getWorkspace()).toAbsolutePath().normalize();
        Path target = workspace.resolve(userPath).toAbsolutePath().normalize();

        if (!target.startsWith(workspace)) {
            throw new SecurityException("路径超出工作区范围: " + userPath);
        }
    }

    /**
     * 解析文件路径
     */
    private File resolveFile(String path) {
        File workspace = new File(toolsConfig.getWorkspace());
        File file = new File(path);
        if (file.isAbsolute()) {
            // 绝对路径 (支持 Windows 驱动器路径和 Unix 风格路径)
            return file;
        } else {
            // 相对路径，相对于工作区
            return new File(workspace, path);
        }
    }
}

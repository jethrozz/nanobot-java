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
import java.nio.file.StandardOpenOption;

/**
 * 文件写入工具
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "nanobot.tools.file.enabled", havingValue = "true", matchIfMissing = true)
public class WriteFileTool implements Tool {

    private final ToolsConfig toolsConfig;

    public WriteFileTool(ToolsConfig toolsConfig) {
        this.toolsConfig = toolsConfig;
    }

    @Override
    public String getName() {
        return "write_file";
    }

    @Override
    public String getDescription() {
        return "写入文件内容。参数: path (文件路径), content (文件内容)";
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
                        },
                        "content": {
                            "type": "string",
                            "description": "文件内容"
                        }
                    },
                    "required": ["path", "content"]
                }
                """;
    }

    @Override
    public Mono<ToolResult> execute(ToolCall call) {
        return Mono.fromCallable(() -> {
            String path = call.getArgument("path");
            String content = call.getArgument("content");

            log.info("Writing file: {}", path);

            // 工作区限制检查
            if (toolsConfig.isRestrictToWorkspace()) {
                validatePath(path);
            }

            File file = resolveFile(path);

            // 创建父目录
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 写入文件
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            return ToolResult.success(call.getId(), "文件已写入: " + path);
        })
                .onErrorResume(e -> Mono.just(ToolResult.error(call.getId(), e)));
    }

    private void validatePath(String userPath) {
        Path workspace = Paths.get(toolsConfig.getWorkspace()).toAbsolutePath().normalize();
        Path target = workspace.resolve(userPath).toAbsolutePath().normalize();

        if (!target.startsWith(workspace)) {
            throw new SecurityException("路径超出工作区范围: " + userPath);
        }
    }

    private File resolveFile(String path) {
        File workspace = new File(toolsConfig.getWorkspace());
        if (path.startsWith("/")) {
            return new File(path);
        } else {
            return new File(workspace, path);
        }
    }
}

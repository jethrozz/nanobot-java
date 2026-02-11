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
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Shell 命令执行工具
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "nanobot.tools.shell.enabled", havingValue = "true", matchIfMissing = true)
public class ExecTool implements Tool {

    private final ToolsConfig toolsConfig;

    public ExecTool(ToolsConfig toolsConfig) {
        this.toolsConfig = toolsConfig;
    }

    @Override
    public String getName() {
        return "exec";
    }

    @Override
    public String getDescription() {
        return "执行 Shell 命令。参数: command (要执行的命令)";
    }

    @Override
    public String getParameterSchema() {
        return """
                {
                    "type": "object",
                    "properties": {
                        "command": {
                            "type": "string",
                            "description": "要执行的 Shell 命令"
                        }
                    },
                    "required": ["command"]
                }
                """;
    }

    @Override
    public Mono<ToolResult> execute(ToolCall call) {
        return Mono.fromCallable(() -> {
            String command = call.getArgument("command");

            log.info("Executing command: {}", command);

            // 安全检查
            validateCommand(command);

            // 执行命令
            ProcessBuilder pb;
            if (isWindows()) {
                pb = new ProcessBuilder("cmd", "/c", command);
            } else {
                pb = new ProcessBuilder("bash", "-c", command);
            }

            // 设置工作目录
            File workDir = new File(toolsConfig.getWorkspace());
            if (workDir.exists()) {
                pb.directory(workDir);
            }

            Process process = pb.start();

            // 超时控制
            boolean finished = process.waitFor(toolsConfig.getShell().getTimeout(),
                    java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new TimeoutException("命令执行超时");
            }

            // 读取输出
            String output;
            if (process.exitValue() == 0) {
                output = new String(
                        process.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8
                );
            } else {
                output = new String(
                        process.getErrorStream().readAllBytes(),
                        StandardCharsets.UTF_8
                );
            }

            return ToolResult.success(call.getId(),
                    String.format("退出码: %d\n输出:\n%s", process.exitValue(), output));
        })
                .onErrorResume(e -> Mono.just(ToolResult.error(call.getId(), e)));
    }

    /**
     * 验证命令安全性
     */
    private void validateCommand(String command) {
        List<String> blockedCommands = toolsConfig.getShell().getBlockedCommands();
        for (String blocked : blockedCommands) {
            if (command.toLowerCase().contains(blocked.toLowerCase())) {
                throw new SecurityException("禁止的命令: " + blocked);
            }
        }
    }

    /**
     * 判断是否为 Windows 系统
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}

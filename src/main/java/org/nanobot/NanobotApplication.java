package org.nanobot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Nanobot-Java 主启动类
 * 超轻量级个人 AI 助手框架
 */
@SpringBootApplication
@ComponentScan(basePackages = "org.nanobot")
public class NanobotApplication {

    private static final String GATEWAY_MODE = "gateway";
    private static final String AGENT_MODE = "agent";
    private static final String DEFAULT_MODE = GATEWAY_MODE;

    public static void main(String[] args) {
        // 确定运行模式
        String mode = determineMode(args);

        if (AGENT_MODE.equals(mode)) {
            // Agent 模式：单次或交互式对话
            runAgentMode(args);
        } else {
            // Gateway 模式：启动网关服务
            runGatewayMode(args);
        }
    }

    /**
     * 确定运行模式
     */
    private static String determineMode(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--mode=")) {
                return arg.substring(7);
            }
        }
        return DEFAULT_MODE;
    }

    /**
     * Gateway 模式：启动 HTTP 服务
     */
    private static void runGatewayMode(String[] args) {
        SpringApplication app = new SpringApplication(NanobotApplication.class);
        app.run(args);
    }

    /**
     * Agent 模式：命令行对话
     */
    private static void runAgentMode(String[] args) {
        // TODO: 实现 Agent 模式
        System.out.println("Agent mode is under development...");
        System.exit(0);
    }
}

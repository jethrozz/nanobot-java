package org.nanobot.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    public ToolRegistry(List<Tool> toolBeans) {
        // 自动注册所有 Tool Bean
        for (Tool tool : toolBeans) {
            register(tool);
        }
        log.info("Registered {} tools: {}",
                tools.size(),
                tools.keySet().stream().sorted().toList());
    }

    /**
     * 注册工具
     */
    public void register(Tool tool) {
        if (tool.isEnabled()) {
            tools.put(tool.getName(), tool);
            log.debug("Registered tool: {}", tool.getName());
        }
    }

    /**
     * 获取工具
     */
    public Tool getTool(String name) {
        return tools.get(name);
    }

    /**
     * 获取所有工具
     */
    public List<Tool> getAllTools() {
        return List.copyOf(tools.values());
    }

    /**
     * 检查工具是否存在
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }
}

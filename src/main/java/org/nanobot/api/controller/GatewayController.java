package org.nanobot.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nanobot.channel.ChannelManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 网关控制器
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GatewayController {

    private final ChannelManager channelManager;

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("timestamp", System.currentTimeMillis());
        result.put("channels", channelManager.getAllChannels().size());
        return result;
    }

    /**
     * 获取频道状态
     */
    @GetMapping("/channels")
    public Map<String, Object> getChannels() {
        Map<String, Object> result = new HashMap<>();
        result.put("channels", channelManager.getAllChannels().stream()
                .map(c -> Map.of(
                        "type", c.getType(),
                        "id", c.getId(),
                        "enabled", c.isEnabled()
                ))
                .toList());
        return result;
    }
}

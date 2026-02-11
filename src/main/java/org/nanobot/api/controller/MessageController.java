package org.nanobot.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nanobot.agent.AgentLoop;
import org.nanobot.bus.MessageBus;
import org.nanobot.model.Message;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * 消息控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final AgentLoop agentLoop;
    private final MessageBus messageBus;

    /**
     * 发送消息（直接模式）
     */
    @PostMapping("/send")
    public Mono<Map<String, String>> sendMessage(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        String userId = request.getOrDefault("userId", "api-user");
        String channelType = request.getOrDefault("channelType", "api");

        log.info("Received message from API: {}", content);

        Message message = Message.builder()
                .id(UUID.randomUUID().toString())
                .channelType(channelType)
                .userId(userId)
                .content(content)
                .type(Message.MessageType.TEXT)
                .timestamp(java.time.Instant.now())
                .build();

        return agentLoop.process(message)
                .map(response -> Map.of(
                        "response", response,
                        "messageId", message.getId()
                ))
                .doOnSuccess(v -> log.info("Successfully processed message"));
    }

    /**
     * 发布消息到总线
     */
    @PostMapping("/publish")
    public Mono<Map<String, String>> publishMessage(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        String channelId = request.getOrDefault("channelId", "default");
        String channelType = request.getOrDefault("channelType", "api");
        String userId = request.getOrDefault("userId", "api-user");

        Message message = Message.builder()
                .id(UUID.randomUUID().toString())
                .channelId(channelId)
                .channelType(channelType)
                .userId(userId)
                .content(content)
                .type(Message.MessageType.TEXT)
                .timestamp(java.time.Instant.now())
                .build();

        return messageBus.publishInbound(message)
                .then(Mono.just(Map.of(
                        "status", "published",
                        "messageId", message.getId()
                )));
    }
}

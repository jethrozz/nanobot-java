package org.nanobot.channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nanobot.bus.MessageBus;
import org.nanobot.config.ChannelsConfig;
import org.nanobot.model.Message;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 频道管理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelManager {

    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    private final MessageBus messageBus;

    /**
     * 注册频道
     */
    public void register(Channel channel) {
        if (channel.isEnabled()) {
            channels.put(channel.getType() + ":" + channel.getId(), channel);
            log.info("Registered channel: {} ({})", channel.getType(), channel.getId());
        }
    }

    /**
     * 启动所有已注册频道
     */
    @EventListener(ApplicationReadyEvent.class)
    public Mono<Void> startAll() {
        return Flux.fromIterable(channels.values())
                .flatMap(channel -> {
                    log.info("Starting channel: {} ({})", channel.getType(), channel.getId());
                    return channel.start()
                            .doOnSuccess(v -> log.info("Channel started: {}", channel.getType()))
                            .doOnError(e -> log.error("Failed to start channel: {}", channel.getType(), e));
                })
                .then()
                .doOnSuccess(v -> log.info("All channels started successfully"));
    }

    /**
     * 停止所有频道
     */
    public Mono<Void> stopAll() {
        return Flux.fromIterable(channels.values())
                .flatMap(channel -> {
                    log.info("Stopping channel: {}", channel.getType());
                    return channel.stop()
                            .doOnError(e -> log.error("Failed to stop channel: {}", channel.getType(), e));
                })
                .then()
                .doOnSuccess(v -> log.info("All channels stopped"));
    }

    /**
     * 分发出站消息到对应频道
     */
    public void dispatchOutbound(Message message) {
        // 根据频道类型查找对应的频道
        Channel channel = channels.values().stream()
                .filter(c -> c.getType().equals(message.getChannelType()))
                .findFirst()
                .orElse(null);

        if (channel != null) {
            channel.sendMessage(message)
                    .doOnError(e -> log.error("Failed to send message via channel: {}", channel.getType(), e))
                    .subscribe();
        } else {
            log.warn("No channel found for type: {}", message.getChannelType());
        }
    }

    /**
     * 获取所有频道
     */
    public List<Channel> getAllChannels() {
        return List.copyOf(channels.values());
    }

    /**
     * 获取指定类型的频道
     */
    public Channel getChannel(String type) {
        return channels.values().stream()
                .filter(c -> c.getType().equals(type))
                .findFirst()
                .orElse(null);
    }
}

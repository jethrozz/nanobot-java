package org.nanobot.bus;

import lombok.extern.slf4j.Slf4j;
import org.nanobot.model.Message;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Reactor 的消息总线实现
 */
@Slf4j
@Component
public class ReactorMessageBus implements MessageBus {

    private final Sinks.Many<Message> inboundSink;
    private final Sinks.Many<Message> outboundSink;
    private final ConcurrentHashMap<String, Sinks.Many<Message>> channelSinks;

    public ReactorMessageBus() {
        // 使用多播策略，支持多个订阅者
        this.inboundSink = Sinks.many().multicast().onBackpressureBuffer();
        this.outboundSink = Sinks.many().multicast().onBackpressureBuffer();
        this.channelSinks = new ConcurrentHashMap<>();
    }

    @Override
    public Mono<Void> publishInbound(Message message) {
        return Mono.fromRunnable(() -> {
            Sinks.EmitResult result = inboundSink.tryEmitNext(message);
            if (result.isFailure()) {
                log.warn("Failed to emit inbound message: {}", result);
            }
            log.debug("Published inbound message: {}", message.getId());
        });
    }

    @Override
    public Mono<Void> publishOutbound(Message message) {
        return Mono.fromRunnable(() -> {
            Sinks.EmitResult result = outboundSink.tryEmitNext(message);
            if (result.isFailure()) {
                log.warn("Failed to emit outbound message: {}", result);
            }
            log.debug("Published outbound message: {}", message.getId());

            // 同时发布到频道特定的 sink
            if (message.getChannelId() != null) {
                Sinks.Many<Message> channelSink = channelSinks.computeIfAbsent(
                        message.getChannelId(),
                        k -> Sinks.many().multicast().onBackpressureBuffer()
                );
                channelSink.tryEmitNext(message);
            }
        });
    }

    @Override
    public Flux<Message> subscribeInbound() {
        return inboundSink.asFlux()
                .doOnNext(msg -> log.debug("Received inbound message: {}", msg.getId()));
    }

    @Override
    public Flux<Message> subscribeOutbound() {
        return outboundSink.asFlux()
                .doOnNext(msg -> log.debug("Received outbound message: {}", msg.getId()));
    }

    @Override
    public Flux<Message> subscribeChannel(String channelId) {
        return Flux.create(sink -> {
            Sinks.Many<Message> channelSink = channelSinks.computeIfAbsent(
                    channelId,
                    k -> Sinks.many().multicast().onBackpressureBuffer()
            );

            channelSink.asFlux().subscribe(sink::next);
        });
    }

    @Override
    public Flux<Message> subscribeChannelType(String channelType) {
        return outboundSink.asFlux()
                .filter(msg -> channelType.equals(msg.getChannelType()));
    }

    /**
     * 获取入站消息数量
     */
    public int getInboundBufferSize() {
        return inboundSink.asFlux()
                .collectList()
                .map(List::size)
                .blockOptional()
                .orElse(0);
    }
}

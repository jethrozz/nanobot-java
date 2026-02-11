package org.nanobot.bus;

import org.nanobot.model.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 消息总线接口
 */
public interface MessageBus {

    /**
     * 发布入站消息（Channel → Agent）
     *
     * @param message 消息
     * @return Mono<Void>
     */
    Mono<Void> publishInbound(Message message);

    /**
     * 发布出站消息（Agent → Channel）
     *
     * @param message 消息
     * @return Mono<Void>
     */
    Mono<Void> publishOutbound(Message message);

    /**
     * 订阅入站消息
     *
     * @return Flux<Message>
     */
    Flux<Message> subscribeInbound();

    /**
     * 订阅出站消息
     *
     * @return Flux<Message>
     */
    Flux<Message> subscribeOutbound();

    /**
     * 订阅特定频道的消息
     *
     * @param channelId 频道ID
     * @return Flux<Message>
     */
    Flux<Message> subscribeChannel(String channelId);

    /**
     * 订阅特定类型的频道消息
     *
     * @param channelType 频道类型
     * @return Flux<Message>
     */
    Flux<Message> subscribeChannelType(String channelType);
}

package org.nanobot.channel;

import org.nanobot.model.Message;
import reactor.core.publisher.Mono;

/**
 * 频道接口
 */
public interface Channel {

    /**
     * 频道类型
     *
     * @return String
     */
    String getType();

    /**
     * 频道ID
     *
     * @return String
     */
    String getId();

    /**
     * 启动频道
     *
     * @return Mono<Void>
     */
    Mono<Void> start();

    /**
     * 停止频道
     *
     * @return Mono<Void>
     */
    Mono<Void> stop();

    /**
     * 发送消息
     *
     * @param message 消息
     * @return Mono<Void>
     */
    Mono<Void> sendMessage(Message message);

    /**
     * 是否已启用
     *
     * @return boolean
     */
    boolean isEnabled();
}

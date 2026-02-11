package org.nanobot.model;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * 统一消息模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    /**
     * 消息唯一ID
     */
    private String id;

    /**
     * 频道ID
     */
    private String channelId;

    /**
     * 频道类型 (feishu/wecom/qq/dingtalk/telegram...)
     */
    private String channelType;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息类型
     */
    private MessageType type;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    /**
     * 时间戳
     */
    private Instant timestamp;

    /**
     * 消息类型枚举
     */
    public enum MessageType {
        /**
         * 文本消息
         */
        TEXT,
        /**
         * 图片消息
         */
        IMAGE,
        /**
         * 文件消息
         */
        FILE,
        /**
         * 命令消息
         */
        COMMAND,
        /**
         * 系统消息
         */
        SYSTEM
    }

    /**
     * 创建文本消息
     */
    public static Message text(String content) {
        return Message.builder()
                .content(content)
                .type(MessageType.TEXT)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * 创建系统消息
     */
    public static Message system(String content) {
        return Message.builder()
                .content(content)
                .type(MessageType.SYSTEM)
                .timestamp(Instant.now())
                .build();
    }
}

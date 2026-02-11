package org.nanobot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 频道配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "nanobot.channels")
public class ChannelsConfig {

    /**
     * 飞书配置
     */
    private FeishuConfig feishu = new FeishuConfig();

    /**
     * 企业微信配置
     */
    private WecomConfig wecom = new WecomConfig();

    /**
     * QQ 配置
     */
    private QQConfig qq = new QQConfig();

    /**
     * 钉钉配置
     */
    private DingTalkConfig dingtalk = new DingTalkConfig();

    /**
     * Telegram 配置
     */
    private TelegramConfig telegram = new TelegramConfig();

    @Data
    public static class FeishuConfig {
        private boolean enabled = false;
        private String appId;
        private String appSecret;
        private String encryptKey;
        private String verificationToken;
        private List<String> authorizedUsers = new ArrayList<>();
    }

    @Data
    public static class WecomConfig {
        private boolean enabled = false;
        private String corpId;
        private String agentId;
        private String secret;
        private String token;
        private List<String> authorizedUsers = new ArrayList<>();
    }

    @Data
    public static class QQConfig {
        private boolean enabled = false;
        private String appId;
        private String token;
        private String sandbox; // 是否为沙箱环境
        private List<String> authorizedUsers = new ArrayList<>();
    }

    @Data
    public static class DingTalkConfig {
        private boolean enabled = false;
        private String appId;
        private String appSecret;
        private List<String> authorizedUsers = new ArrayList<>();
    }

    @Data
    public static class TelegramConfig {
        private boolean enabled = false;
        private String botToken;
        private List<Long> allowFrom = new ArrayList<>();
    }
}

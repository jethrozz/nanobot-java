package org.nanobot.channel.wecom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.nanobot.bus.MessageBus;
import org.nanobot.channel.Channel;
import org.nanobot.config.ChannelsConfig.WecomConfig;
import org.nanobot.model.Message;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 企业微信频道实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "nanobot.channels.wecom.enabled", havingValue = "true")
public class WecomChannel implements Channel {

    private final WecomConfig config;
    private final MessageBus messageBus;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private String accessToken;
    private Instant tokenExpireTime;

    @Override
    public String getType() {
        return "wecom";
    }

    @Override
    public String getId() {
        return "wecom-" + config.getCorpId() + "-" + config.getAgentId();
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public Mono<Void> start() {
        return Mono.fromRunnable(() -> {
            log.info("Starting WeCom channel for corp: {}, agent: {}", config.getCorpId(), config.getAgentId());

            // 获取 access_token
            refreshAccessToken();

            // TODO: 启动回调服务
            log.info("WeCom channel started");
        });
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(() -> {
            log.info("Stopping WeCom channel");
            // TODO: 清理资源
        });
    }

    @Override
    public Mono<Void> sendMessage(Message message) {
        return Mono.fromCallable(() -> {
            try {
                sendWecomMessage(message.getUserId(), message.getContent());
                return null;
            } catch (Exception e) {
                log.error("Failed to send WeCom message", e);
                throw e;
            }
        });
    }

    /**
     * 刷新 access_token
     */
    private void refreshAccessToken() {
        try {
            String url = String.format(
                    "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=%s&corpsecret=%s",
                    config.getCorpId(),
                    config.getSecret()
            );

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JsonNode jsonNode = objectMapper.readTree(responseBody);

                    if (jsonNode.has("errcode") && jsonNode.get("errcode").asInt() == 0) {
                        this.accessToken = jsonNode.get("access_token").asText();
                        int expire = jsonNode.get("expires_in").asInt();
                        this.tokenExpireTime = Instant.now().plusSeconds(expire - 300);

                        log.info("Successfully obtained WeCom access_token");
                    } else {
                        log.error("Failed to get access_token: {}", responseBody);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error refreshing access token", e);
        }
    }

    /**
     * 发送企业微信消息
     */
    private void sendWecomMessage(String userId, String content) throws IOException {
        if (accessToken == null || tokenExpireTime == null || Instant.now().isAfter(tokenExpireTime)) {
            refreshAccessToken();
        }

        Map<String, Object> messageBody = new HashMap<>();
        messageBody.put("touser", userId);
        messageBody.put("msgtype", "text");
        messageBody.put("agentid", Integer.parseInt(config.getAgentId()));
        messageBody.put("text", Map.of("content", content));

        String jsonBody = objectMapper.writeValueAsString(messageBody);

        String url = "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=" + accessToken;

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, okhttp3.MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                log.info("Successfully sent WeCom message: {}", response.body().string());
            } else {
                log.error("Failed to send WeCom message: {}", response.code());
            }
        }
    }

    /**
     * 处理接收到的企业微信消息
     */
    public void handleIncomingMessage(JsonNode message) {
        try {
            String toUserName = message.get("ToUserName").asText();
            String agentId = config.getAgentId();

            // 确认消息是发送给当前应用的
            if (!toUserName.equals(agentId)) {
                return;
            }

            String userId = message.get("FromUserName").asText();
            String content = message.get("Content").asText();
            String msgType = message.get("MsgType").asText();

            if ("text".equals(msgType)) {
                Message msg = Message.builder()
                        .id(UUID.randomUUID().toString())
                        .channelType(getType())
                        .userId(userId)
                        .content(content)
                        .type(Message.MessageType.TEXT)
                        .timestamp(Instant.now())
                        .build();

                // 权限检查
                if (!isAuthorized(msg)) {
                    log.warn("Unauthorized user: {}", userId);
                    return;
                }

                // 发布到消息总线
                messageBus.publishInbound(msg).subscribe();
            }
        } catch (Exception e) {
            log.error("Error handling incoming WeCom message", e);
        }
    }

    /**
     * 检查用户是否已授权
     */
    private boolean isAuthorized(Message message) {
        return config.getAuthorizedUsers().isEmpty() ||
                config.getAuthorizedUsers().contains(message.getUserId());
    }
}

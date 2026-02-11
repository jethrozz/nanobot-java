package org.nanobot.channel.qq;

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
import org.nanobot.config.ChannelsConfig.QQConfig;
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
 * QQ 频道实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "nanobot.channels.qq.enabled", havingValue = "true")
public class QQChannel implements Channel {

    private final QQConfig config;
    private final MessageBus messageBus;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final String API_BASE = "https://api.sgroup.qq.com";

    @Override
    public String getType() {
        return "qq";
    }

    @Override
    public String getId() {
        return "qq-" + config.getAppId();
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public Mono<Void> start() {
        return Mono.fromRunnable(() -> {
            log.info("Starting QQ channel for app: {}", config.getAppId());

            // TODO: 启动 WebSocket 连接或设置回调服务
            log.info("QQ channel started");
        });
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(() -> {
            log.info("Stopping QQ channel");
            // TODO: 清理资源
        });
    }

    @Override
    public Mono<Void> sendMessage(Message message) {
        return Mono.fromCallable(() -> {
            try {
                sendQQMessage(message.getUserId(), message.getContent());
                return null;
            } catch (Exception e) {
                log.error("Failed to send QQ message", e);
                throw e;
            }
        });
    }

    /**
     * 发送 QQ 消息
     */
    private void sendQQMessage(String openId, String content) throws IOException {
        Map<String, Object> messageBody = new HashMap<>();
        messageBody.put("openid", openId);
        messageBody.put("msgtype", "text");
        messageBody.put("content", Map.of("text", content));

        String jsonBody = objectMapper.writeValueAsString(messageBody);

        String url = API_BASE + "/channel/v2/messages";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bot " + config.getAppId() + "." + config.getToken())
                .post(RequestBody.create(jsonBody, okhttp3.MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                log.info("Successfully sent QQ message: {}", response.body().string());
            } else {
                log.error("Failed to send QQ message: {}", response.code());
            }
        }
    }

    /**
     * 处理接收到的 QQ 消息
     */
    public void handleIncomingMessage(JsonNode event) {
        try {
            String eventType = event.get("type").asText();

            if ("MESSAGE".equals(eventType)) {
                String msgType = event.get("message_type").asText();

                if ("text".equals(msgType)) {
                    String openId = event.get("author").get("union_openid").asText();
                    String content = event.get("content").asText();

                    Message msg = Message.builder()
                            .id(UUID.randomUUID().toString())
                            .channelType(getType())
                            .userId(openId)
                            .content(content)
                            .type(Message.MessageType.TEXT)
                            .timestamp(Instant.now())
                            .build();

                    // 权限检查
                    if (!isAuthorized(msg)) {
                        log.warn("Unauthorized user: {}", openId);
                        return;
                    }

                    // 发布到消息总线
                    messageBus.publishInbound(msg).subscribe();
                }
            }
        } catch (Exception e) {
            log.error("Error handling incoming QQ message", e);
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

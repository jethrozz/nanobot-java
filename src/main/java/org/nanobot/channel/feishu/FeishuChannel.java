package org.nanobot.channel.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nanobot.config.ChannelsConfig.FeishuConfig;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.nanobot.bus.MessageBus;
import org.nanobot.channel.Channel;
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
 * 飞书频道实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "nanobot.channels.feishu.enabled", havingValue = "true")
public class FeishuChannel implements Channel {

    private final FeishuConfig config;
    private final MessageBus messageBus;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private String tenantAccessToken;
    private Instant tokenExpireTime;

    @Override
    public String getType() {
        return "feishu";
    }

    @Override
    public String getId() {
        return "feishu-" + config.getAppId();
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public Mono<Void> start() {
        return Mono.fromRunnable(() -> {
            log.info("Starting Feishu channel for app: {}", config.getAppId());

            // 获取 tenant_access_token
            refreshTenantToken();

            // TODO: 启动 WebSocket 长连接或启动轮询服务
            log.info("Feishu channel started");
        });
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(() -> {
            log.info("Stopping Feishu channel");
            // TODO: 清理资源
        });
    }

    @Override
    public Mono<Void> sendMessage(Message message) {
        return Mono.fromCallable(() -> {
            try {
                // 发送消息到飞书
                sendFeishuMessage(message.getUserId(), message.getContent());
                return null;
            } catch (Exception e) {
                log.error("Failed to send Feishu message", e);
                throw e;
            }
        });
    }

    /**
     * 刷新 tenant_access_token
     */
    private void refreshTenantToken() {
        try {
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("app_id", config.getAppId());
            requestBody.put("app_secret", config.getAppSecret());

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            Request request = new Request.Builder()
                    .url("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")
                    .post(RequestBody.create(jsonBody, okhttp3.MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JsonNode jsonNode = objectMapper.readTree(responseBody);

                    if (jsonNode.has("code") && jsonNode.get("code").asInt() == 0) {
                        this.tenantAccessToken = jsonNode.get("tenant_access_token").asText();
                        int expire = jsonNode.get("expire").asInt();
                        this.tokenExpireTime = Instant.now().plusSeconds(expire - 300); // 提前5分钟刷新

                        log.info("Successfully obtained Feishu tenant_access_token");
                    } else {
                        log.error("Failed to get tenant_access_token: {}", responseBody);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error refreshing tenant token", e);
        }
    }

    /**
     * 发送飞书消息
     */
    private void sendFeishuMessage(String receiveId, String content) throws IOException {
        if (tenantAccessToken == null || tokenExpireTime == null || Instant.now().isAfter(tokenExpireTime)) {
            refreshTenantToken();
        }

        Map<String, Object> messageBody = new HashMap<>();
        messageBody.put("msg_type", "text");
        messageBody.put("receive_id_type", "open_id");
        messageBody.put("receive_id", receiveId);
        messageBody.put("content", objectMapper.writeValueAsString(Map.of("text", content)));

        String jsonBody = objectMapper.writeValueAsString(messageBody);

        Request request = new Request.Builder()
                .url("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id")
                .addHeader("Authorization", "Bearer " + tenantAccessToken)
                .post(RequestBody.create(jsonBody, okhttp3.MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                log.info("Successfully sent Feishu message: {}", response.body().string());
            } else {
                log.error("Failed to send Feishu message: {}", response.code());
            }
        }
    }

    /**
     * 处理接收到的飞书消息
     */
    public void handleIncomingMessage(JsonNode event) {
        try {
            String eventType = event.get("type").asText();

            if ("im.message.receive_v1".equals(eventType)) {
                JsonNode message = event.get("message");
                String chatId = message.get("chat_id").asText();
                String messageId = message.get("message_id").asText();

                // 获取消息内容
                String content = getMessageContent(message);

                // 创建内部消息
                Message msg = Message.builder()
                        .id(UUID.randomUUID().toString())
                        .channelType(getType())
                        .userId(chatId)  // 使用 chat_id 作为 user_id
                        .content(content)
                        .type(Message.MessageType.TEXT)
                        .timestamp(Instant.now())
                        .build();

                // 权限检查
                if (!isAuthorized(msg)) {
                    log.warn("Unauthorized user: {}", msg.getUserId());
                    return;
                }

                // 发布到消息总线
                messageBus.publishInbound(msg).subscribe();
            }
        } catch (Exception e) {
            log.error("Error handling incoming Feishu message", e);
        }
    }

    /**
     * 获取消息内容
     */
    private String getMessageContent(JsonNode message) throws IOException {
        String messageId = message.get("message_id").asText();

        Request request = new Request.Builder()
                .url("https://open.feishu.cn/open-apis/im/v1/messages/" + messageId)
                .addHeader("Authorization", "Bearer " + tenantAccessToken)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.body().string());
                if (jsonNode.has("code") && jsonNode.get("code").asInt() == 0) {
                    JsonNode data = jsonNode.get("data");
                    String content = data.get("body").get("content").asText();

                    // 解析 content JSON
                    JsonNode contentJson = objectMapper.readTree(content);
                    if (contentJson.has("text")) {
                        return contentJson.get("text").asText();
                    }
                }
            }
        }

        return "";
    }

    /**
     * 检查用户是否已授权
     */
    private boolean isAuthorized(Message message) {
        // 空列表表示允许所有用户
        return config.getAuthorizedUsers().isEmpty() ||
                config.getAuthorizedUsers().contains(message.getUserId());
    }
}

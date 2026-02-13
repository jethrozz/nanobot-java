package org.nanobot.channel.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.enums.MsgTypeEnum;
import com.lark.oapi.service.im.v1.enums.ReceiveIdTypeEnum;
import com.lark.oapi.service.im.v1.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nanobot.bus.MessageBus;
import org.nanobot.channel.Channel;
import org.nanobot.config.ChannelsConfig.FeishuConfig;
import org.nanobot.model.Message;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;

/**
 * Feishu Channel using Official SDK with WebSocket Long Connection
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "nanobot.channels.feishu.enabled", havingValue = "true")
public class FeishuChannel implements Channel {

    private final FeishuConfig feishuConfig;
    private final MessageBus messageBus;
    private final ObjectMapper objectMapper;

    private com.lark.oapi.ws.Client wsClient;  // WebSocket client for receiving events
    private com.lark.oapi.Client apiClient; // HTTP client for sending messages

    @Override
    public String getType() {
        return "feishu";
    }

    @Override
    public String getId() {
        return "feishu-" + feishuConfig.getAppId();
    }

    @Override
    public boolean isEnabled() {
        return feishuConfig.isEnabled();
    }

    @Override
    public Mono<Void> start() {
        return Mono.fromRunnable(() -> {
            try {
                log.info("Starting Feishu channel for app: {}", feishuConfig.getAppId());

                // Initialize HTTP API client for sending messages
                apiClient = com.lark.oapi.Client.newBuilder(feishuConfig.getAppId(), feishuConfig.getAppSecret())
                        .build();

                // Create Event Dispatcher with P2MessageReceiveV1 handler
                EventDispatcher eventDispatcher = EventDispatcher.newBuilder("", "")  // Empty strings for WebSocket mode
                        .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                            @Override
                            public void handle(P2MessageReceiveV1 event) throws Exception {
                                log.debug("Received P2MessageReceiveV1 event");
                                handleIncomingMessage(event);
                            }
                        })
                        .build();

                // Initialize WebSocket client with event handler
                wsClient = new com.lark.oapi.ws.Client.Builder(feishuConfig.getAppId(), feishuConfig.getAppSecret())
                        .eventHandler(eventDispatcher)
                        .build();

                // Start WebSocket connection
                wsClient.start();

                log.info("Feishu channel started with WebSocket connection");
            } catch (Exception e) {
                log.error("Failed to start Feishu channel", e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(() -> {
            log.info("Stopping Feishu channel");
            wsClient = null;
            apiClient = null;
        });
    }

    @PreDestroy
    public void destroy() {
        stop().subscribe();
    }

    @Override
    public Mono<Void> sendMessage(Message message) {
        return Mono.fromCallable(() -> {
            try {
                sendFeishuMessage(message.getUserId(), message.getContent());
                return null;
            } catch (Exception e) {
                log.error("Failed to send Feishu message", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Send Feishu message using SDK
     */
    private void sendFeishuMessage(String receiveId, String content) throws Exception {
        if (apiClient == null) {
            log.warn("API Client not initialized");
            return;
        }

        // Build text message content as JSON string
        String messageContent = "{\"text\":\"" + content.replace("\"", "\\\"") + "\"}";

        // Create message request using SDK builder
        CreateMessageReq req = CreateMessageReq.newBuilder()
                .receiveIdType(ReceiveIdTypeEnum.OPEN_ID.getValue())
                .createMessageReqBody(CreateMessageReqBody.newBuilder()
                        .receiveId(receiveId)
                        .msgType(MsgTypeEnum.MSG_TYPE_TEXT.getValue())
                        .content(messageContent)
                        .build())
                .build();

        // Send message via SDK
        CreateMessageResp resp = apiClient.im().message().create(req);

        if (resp.getCode() == 0) {
            log.info("Successfully sent Feishu message, requestId: {}", resp.getRequestId());
        } else {
            log.error("Failed to send Feishu message: code={}, msg={}",
                    resp.getCode(), resp.getMsg());
        }
    }

    /**
     * Handle incoming P2MessageReceiveV1 event
     */
    private void handleIncomingMessage(P2MessageReceiveV1 event) {
        try {
            // Get event data
            P2MessageReceiveV1Data eventData = event.getEvent();
            if (eventData == null) {
                log.warn("Event data is null");
                return;
            }

            // Get sender info - EventSender has getSenderId() which returns UserId
            EventSender sender = eventData.getSender();
            String senderId = null;
            if (sender != null && sender.getSenderId() != null) {
                senderId = sender.getSenderId().getOpenId(); // Use open_id
            }

            // Get message info - EventMessage has chatId, messageId, content
            EventMessage messageData = eventData.getMessage();
            String chatId = null;
            String messageId = null;
            String content = "";

            if (messageData != null) {
                chatId = messageData.getChatId();
                messageId = messageData.getMessageId();
                content = extractTextContent(messageData);
            }

            // Create internal message
            Map<String, Object> metadata = new HashMap<>();
            if (chatId != null) {
                metadata.put("chat_id", chatId);
            }
            if (messageId != null) {
                metadata.put("message_id", messageId);
            }

            Message msg = Message.builder()
                    .id(UUID.randomUUID().toString())
                    .channelType(getType())
                    .userId(senderId != null ? senderId : chatId)
                    .content(content)
                    .type(Message.MessageType.TEXT)
                    .metadata(metadata)
                    .timestamp(Instant.now())
                    .build();

            // Authorization check
            if (!isAuthorized(msg)) {
                log.warn("Unauthorized user: {}", msg.getUserId());
                return;
            }

            // Publish to message bus
            messageBus.publishInbound(msg).subscribe();

        } catch (Exception e) {
            log.error("Error handling incoming Feishu message", e);
        }
    }

    /**
     * Extract text content from EventMessage
     */
    private String extractTextContent(EventMessage messageData) {
        if (messageData == null || messageData.getContent() == null) {
            return "";
        }
        try {
            JsonNode json = objectMapper.readTree(messageData.getContent());
            if (json.has("text")) {
                return json.get("text").asText();
            }
        } catch (Exception e) {
            log.debug("Failed to extract text content", e);
        }
        return "";
    }

    /**
     * Check if user is authorized
     */
    private boolean isAuthorized(Message message) {
        // Empty list means allow all users
        return feishuConfig.getAuthorizedUsers().isEmpty() ||
                feishuConfig.getAuthorizedUsers().contains(message.getUserId());
    }
}

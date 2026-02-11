package org.nanobot.tool.builtin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nanobot.bus.MessageBus;
import org.nanobot.model.Message;
import org.nanobot.model.ToolCall;
import org.nanobot.model.ToolResult;
import org.nanobot.tool.Tool;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 消息发送工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageTool implements Tool {

    private final MessageBus messageBus;

    @Override
    public String getName() {
        return "message";
    }

    @Override
    public String getDescription() {
        return "发送消息到指定频道。参数: channelId (频道ID), content (消息内容)";
    }

    @Override
    public String getParameterSchema() {
        return """
                {
                    "type": "object",
                    "properties": {
                        "channelId": {
                            "type": "string",
                            "description": "频道ID"
                        },
                        "content": {
                            "type": "string",
                            "description": "消息内容"
                        }
                    },
                    "required": ["channelId", "content"]
                }
                """;
    }

    @Override
    public Mono<ToolResult> execute(ToolCall call) {
        return Mono.fromCallable(() -> {
            String channelId = call.getArgument("channelId");
            String content = call.getArgument("content");

            log.info("Sending message to channel {}: {}", channelId, content);

            Message message = Message.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .channelId(channelId)
                    .content(content)
                    .type(Message.MessageType.TEXT)
                    .timestamp(java.time.Instant.now())
                    .build();

            messageBus.publishOutbound(message).subscribe();

            return ToolResult.success(call.getId(), "消息已发送");
        })
                .onErrorResume(e -> Mono.just(ToolResult.error(call.getId(), e)));
    }
}

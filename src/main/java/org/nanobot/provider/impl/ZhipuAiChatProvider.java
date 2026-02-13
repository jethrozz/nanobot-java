package org.nanobot.provider.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nanobot.model.ChatMessage;
import org.nanobot.model.ToolCall;
import org.nanobot.provider.ChatProvider;
import org.nanobot.tool.Tool;
import org.nanobot.tool.ToolCallbackAdapter;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Zhipu AI (GLM) Chat Provider 实现
 * 基于 Spring AI ZhipuAiChatModel
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZhipuAiChatProvider implements ChatProvider {

    private final ZhiPuAiChatModel chatModel;
    private final ToolCallbackAdapter toolCallbackAdapter;

    @Override
    public Mono<ChatResponse> chat(List<ChatMessage> messages, List<Tool> tools) {
        return Mono.fromCallable(() -> {
            log.debug("Calling Zhipu AI with {} messages, {} tools", messages.size(),
                    tools != null ? tools.size() : 0);

            // 转换消息格式
            List<Message> springMessages = convertMessages(messages);

            // 构建请求
            Prompt prompt;
            if (tools != null && !tools.isEmpty()) {
                // 带工具调用的请求
                ToolCallback[] toolCallbacks = toolCallbackAdapter.adaptAllTools();

                // 使用 ZhiPuAiChatOptions 配置工具
                ZhiPuAiChatOptions options = ZhiPuAiChatOptions.builder()
                        .toolCallbacks(toolCallbacks)
                        .build();

                prompt = new Prompt(springMessages, options);
                log.debug("Created prompt with {} tool callbacks", toolCallbacks.length);
            } else {
                // 普通请求
                prompt = new Prompt(springMessages);
            }

            // 调用 Spring AI
            org.springframework.ai.chat.model.ChatResponse springAiResponse = chatModel.call(prompt);

            // 转换响应
            return convertResponse(springAiResponse);
        });
    }

    @Override
    public Flux<String> chatStream(List<ChatMessage> messages, List<Tool> tools) {
        return Flux.defer(() -> {
            List<Message> springMessages = convertMessages(messages);
            Prompt prompt = new Prompt(springMessages);

            return chatModel.stream(prompt)
                    .map(chatResponse -> {
                        if (chatResponse.getResult() != null &&
                            chatResponse.getResult().getOutput() != null) {
                            // 使用 getText() 获取内容
                            return chatResponse.getResult().getOutput().getText();
                        }
                        return "";
                    })
                    .filter(content -> !content.isEmpty());
        });
    }

    /**
     * 转换消息格式
     */
    private List<Message> convertMessages(List<ChatMessage> messages) {
        List<Message> result = new ArrayList<>();

        for (ChatMessage msg : messages) {
            switch (msg.getRole()) {
                case SYSTEM:
                    result.add(new SystemMessage(msg.getContent()));
                    break;
                case USER:
                    result.add(new UserMessage(msg.getContent()));
                    break;
                case ASSISTANT:
                    // Assistant 消息可能包含工具调用
                    if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                        // 创建带工具调用的 AssistantMessage
                        AssistantMessage assistantMessage = convertAssistantMessage(msg);
                        result.add(assistantMessage);
                    } else {
                        // 普通 Assistant 消息
                        result.add(new AssistantMessage(msg.getContent()));
                    }
                    break;
                case TOOL:
                    // 工具结果消息 - 使用 UserMessage 传递工具结果
                    // Spring AI 使用 ToolResponseMessage 表示工具结果
                    String toolContent = msg.getContent();
                    String toolCallId = msg.getToolCallId();
                    if (toolCallId != null) {
                        // 创建工具响应消息
                        org.springframework.ai.chat.messages.ToolResponseMessage toolResponse =
                                new org.springframework.ai.chat.messages.ToolResponseMessage(
                                        List.of(new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse(
                                                toolCallId,
                                                toolContent,
                                                null  // toolName - 可以为 null
                                        ))
                                );
                        result.add(toolResponse);
                    } else {
                        // 回退到 UserMessage
                        String fallbackContent = String.format("[Tool Result]: %s", toolContent);
                        result.add(new UserMessage(fallbackContent));
                    }
                    break;
            }
        }

        return result;
    }

    /**
     * 转换 Assistant 消息（可能包含工具调用）
     */
    private AssistantMessage convertAssistantMessage(ChatMessage msg) {
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();

        if (msg.getToolCalls() != null) {
            for (ToolCall toolCall : msg.getToolCalls()) {
                // AssistantMessage.ToolCall 需要 4 个参数: id, type, name, arguments
                toolCalls.add(new AssistantMessage.ToolCall(
                        toolCall.getId(),
                        toolCall.getType() != null ? toolCall.getType() : "function",
                        toolCall.getFunctionName(),
                        toolCall.getArguments()
                ));
            }
        }

        // AssistantMessage 构造函数: (content, metadata, toolCalls)
        // metadata 可以为 null
        return new AssistantMessage(msg.getContent(), null, toolCalls);
    }

    /**
     * 转换响应格式
     */
    private ChatResponse convertResponse(org.springframework.ai.chat.model.ChatResponse springAiResponse) {
        if (springAiResponse == null || springAiResponse.getResult() == null) {
            return ChatResponse.text("");
        }

        var output = springAiResponse.getResult().getOutput();
        String content = output.getText();

        // 检查是否有工具调用
        if (output instanceof AssistantMessage) {
            AssistantMessage assistantMessage = (AssistantMessage) output;
            List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();

            if (toolCalls != null && !toolCalls.isEmpty()) {
                // 转换工具调用格式
                List<ToolCall> convertedToolCalls = toolCalls.stream()
                        .map(this::convertToolCall)
                        .collect(Collectors.toList());

                log.info("Spring AI returned {} tool calls", convertedToolCalls.size());
                return ChatResponse.withToolCalls(content, convertedToolCalls);
            }
        }

        // 普通文本响应
        return ChatResponse.text(content);
    }

    /**
     * 转换工具调用格式
     */
    private ToolCall convertToolCall(AssistantMessage.ToolCall toolCall) {
        return ToolCall.builder()
                .id(toolCall.id())
                .type(toolCall.type())
                .functionName(toolCall.name())
                .arguments(toolCall.arguments())
                .build();
    }
}

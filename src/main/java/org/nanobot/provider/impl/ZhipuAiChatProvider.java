package org.nanobot.provider.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nanobot.model.ChatMessage;
import org.nanobot.model.ToolCall;
import org.nanobot.provider.ChatProvider;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Zhipu AI (GLM) Chat Provider 实现
 * 基于 Spring AI ZhipuAiChatModel
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZhipuAiChatProvider implements ChatProvider {

    private final ZhiPuAiChatModel chatModel;

    @Override
    public Mono<ChatResponse> chat(List<ChatMessage> messages, List<org.nanobot.tool.Tool> tools) {
        return Mono.fromCallable(() -> {
            log.debug("Calling Zhipu AI with {} messages", messages.size());

            // 转换消息格式
            List<Message> springMessages = convertMessages(messages);

            // 构建请求
            Prompt prompt;
            if (tools != null && !tools.isEmpty()) {
                // 带工具调用的请求
                // TODO: 实现工具调用功能
                log.warn("Tool calling is not yet implemented for ZhipuAiChatProvider");
                prompt = new Prompt(springMessages);
            } else {
                // 普通请求
                prompt = new Prompt(springMessages);
            }

            // 调用 Spring AI
            org.springframework.ai.chat.model.ChatResponse springResponse = chatModel.call(prompt);

            // 转换响应
            return convertResponse(springResponse);
        });
    }

    @Override
    public Flux<String> chatStream(List<ChatMessage> messages, List<org.nanobot.tool.Tool> tools) {
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
                    // Spring AI 不直接支持 ASSISTANT 角色，我们可以将其转换为 UserMessage
                    result.add(new UserMessage(msg.getContent()));
                    break;
                case TOOL:
                    // 工具消息，将其转换为 UserMessage 并标注来源
                    String toolContent = String.format("[Tool Result]: %s", msg.getContent());
                    result.add(new UserMessage(toolContent));
                    break;
            }
        }

        return result;
    }

    /**
     * 转换响应格式
     */
    private ChatResponse convertResponse(org.springframework.ai.chat.model.ChatResponse springResponse) {
        if (springResponse == null || springResponse.getResult() == null) {
            return ChatResponse.text("");
        }

        // 使用 getText() 获取内容
        String content = springResponse.getResult().getOutput().getText();

        // TODO: 处理工具调用响应
        // 暂时返回文本响应
        return ChatResponse.text(content);
    }
}

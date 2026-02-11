package org.nanobot.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nanobot.config.AgentConfig;
import org.nanobot.model.ChatMessage;
import org.nanobot.model.Message;
import org.nanobot.model.ToolCall;
import org.nanobot.model.ToolResult;
import org.nanobot.provider.ChatProvider;
import org.nanobot.provider.registry.ProviderRegistry;
import org.nanobot.tool.Tool;
import org.nanobot.tool.ToolExecutor;
import org.nanobot.tool.ToolRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Agent 处理循环
 * 负责协调 LLM 调用和工具执行
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentLoop {

    private final ContextBuilder contextBuilder;
    private final ProviderRegistry providerRegistry;
    private final ToolExecutor toolExecutor;
    private final ToolRegistry toolRegistry;
    private final SessionManager sessionManager;
    private final AgentConfig agentConfig;

    // 注入 ChatProvider（可以是 ZhipuAiChatProvider 或其他实现）
    private final Optional<ChatProvider> chatProvider;

    /**
     * 处理单次对话
     *
     * @param message 用户消息
     * @return Mono<String> AI 回复
     */
    public Mono<String> process(Message message) {
        return process(message, agentConfig.getDefaultAgent().getMaxIterations());
    }

    /**
     * 处理单次对话
     *
     * @param message       用户消息
     * @param maxIterations 最大迭代次数
     * @return Mono<String> AI 回复
     */
    public Mono<String> process(Message message, int maxIterations) {
        // 1. 构建上下文
        List<ChatMessage> context = contextBuilder.build(message);

        // 2. 获取 Provider
        String model = agentConfig.getDefaultAgent().getModel();
        var providerSpec = providerRegistry.matchByModel(model);

        log.info("Processing message with model: {}, provider: {}", model, providerSpec.getName());

        // 3. 执行处理循环
        return processLoop(context, maxIterations, 0, message);
    }

    /**
     * 处理循环（支持工具调用迭代）
     */
    private Mono<String> processLoop(
            List<ChatMessage> messages,
            int maxIterations,
            int iteration,
            Message originalMessage
    ) {
        if (iteration >= maxIterations) {
            log.warn("Reached maximum iterations: {}", maxIterations);
            return Mono.just("已达到最大迭代次数");
        }

        log.debug("Iteration {}/{}", iteration + 1, maxIterations);

        // 调用 LLM
        return callLLM(messages)
                .flatMap(response -> {
                    if (response.hasToolCalls()) {
                        // 有工具调用，执行工具并继续
                        log.info("LLM returned {} tool calls", response.getToolCalls().size());
                        return executeToolsAndContinue(messages, response,
                                maxIterations, iteration + 1, originalMessage);
                    } else {
                        // 最终回复
                        log.info("LLM returned final response");

                        // 保存对话历史
                        saveHistory(originalMessage, messages, response.getContent());

                        return Mono.just(response.getContent());
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error in process loop at iteration {}", iteration, e);
                    return Mono.just("处理过程中发生错误: " + e.getMessage());
                });
    }

    /**
     * 执行工具并继续循环
     */
    private Mono<String> executeToolsAndContinue(
            List<ChatMessage> messages,
            ChatProvider.ChatResponse response,
            int maxIterations,
            int nextIteration,
            Message originalMessage
    ) {
        // 添加助手消息（包含工具调用）
        messages.add(ChatMessage.assistant(response.getContent(), response.getToolCalls()));

        // 并行执行所有工具调用
        List<Mono<ToolResult>> toolResults = response.getToolCalls().stream()
                .map(toolCall -> toolExecutor.execute(toolCall)
                        .onErrorResume(e -> Mono.just(ToolResult.error(toolCall.getId(), e)))
                )
                .toList();

        return Flux.merge(toolResults)
                .collectList()
                .flatMap(results -> {
                    // 添加工具结果消息
                    for (ToolResult result : results) {
                        messages.add(result.toMessage());
                    }

                    // 继续下一轮
                    return processLoop(messages, maxIterations, nextIteration, originalMessage);
                });
    }

    /**
     * 调用 LLM
     */
    private Mono<ChatProvider.ChatResponse> callLLM(List<ChatMessage> messages) {
        // 使用注入的 ChatProvider
        if (chatProvider.isPresent()) {
            return chatProvider.get().chat(messages, toolRegistry.getAllTools());
        }

        // 如果没有注入 ChatProvider，返回错误
        return Mono.error(new IllegalStateException("No ChatProvider available. " +
                "Please ensure ZhipuAiChatProvider is properly configured."));
    }

    /**
     * 保存对话历史
     */
    private void saveHistory(Message originalMessage, List<ChatMessage> messages, String finalResponse) {
        String sessionId = getSessionId(originalMessage);

        // 保存用户消息
        ChatMessage userMsg = ChatMessage.user(originalMessage.getContent());
        sessionManager.appendMessage(sessionId, userMsg);

        // 保存助手回复
        ChatMessage assistantMsg = ChatMessage.assistant(finalResponse);
        sessionManager.appendMessage(sessionId, assistantMsg);
    }

    /**
     * 获取会话 ID
     */
    private String getSessionId(Message message) {
        return String.format("%s:%s", message.getChannelType(), message.getUserId());
    }
}

package org.nanobot.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nanobot.model.ToolCall;
import org.nanobot.model.ToolResult;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 工具执行器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutor {

    private final ToolRegistry registry;

    /**
     * 执行工具
     *
     * @param call 工具调用
     * @return ToolResult
     */
    public Mono<ToolResult> execute(ToolCall call) {
        return Mono.defer(() -> {
            Tool tool = registry.getTool(call.getFunctionName());

            if (tool == null) {
                log.warn("Tool not found: {}", call.getFunctionName());
                return Mono.just(ToolResult.error(
                        call.getId(),
                        new IllegalArgumentException("Tool not found: " + call.getFunctionName())
                ));
            }

            log.info("Executing tool: {} with id: {}", call.getFunctionName(), call.getId());

            return tool.execute(call)
                    .doOnSuccess(result -> {
                        if (result.isSuccess()) {
                            log.info("Tool {} executed successfully", call.getFunctionName());
                        } else {
                            log.error("Tool {} execution failed: {}",
                                    call.getFunctionName(), result.getError());
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("Tool {} execution error", call.getFunctionName(), e);
                        return Mono.just(ToolResult.error(call.getId(), e));
                    });
        });
    }

    /**
     * 批量执行工具
     *
     * @param calls 工具调用列表
     * @return List<ToolResult>
     */
    public Mono<List<ToolResult>> executeBatch(List<ToolCall> calls) {
        return Flux.fromIterable(calls)
                .flatMap(this::execute)
                .collectList();
    }
}

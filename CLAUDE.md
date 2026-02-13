# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the project
mvn clean package

# Run tests (when available)
mvn test

# Run the application
java -jar target/nanobot-java-1.0.0-SNAPSHOT.jar

# Run with specific mode
java -jar nanobot.jar --mode=gateway  # HTTP gateway mode
java -jar nanobot.jar --mode=agent     # CLI interactive mode (TODO)
```

## Architecture Overview

Nanobot-Java is an event-driven, reactive AI assistant framework built on Spring Boot + Spring AI. The architecture follows a plugin-based design with three core layers:

### Message Flow

```
Channel (Feishu/QQ/Wecom)
  → MessageBus.publishInbound()
  → AgentLoop.process()
    → ContextBuilder builds system prompt + history
    → Provider.chat() with tools
    → If tool calls: ToolExecutor → loop with results
    → Else: SessionManager saves history
  → MessageBus.publishOutbound()
  → Channel.sendMessage()
```

### Core Components

**AgentLoop** (`src/main/java/org/nanobot/agent/AgentLoop.java`)
- Main processing loop with tool calling iteration (max 10 iterations by default)
- Orchestrates LLM calls and tool execution in parallel
- Saves conversation history via SessionManager after completion

**ReactorMessageBus** (`src/main/java/org/nanobot/bus/ReactorMessageBus.java`)
- Reactor `Sinks.Many` based pub/sub for async message routing
- Inbound queue (Channel → Agent) and Outbound queue (Agent → Channel)
- Per-channel sinks for targeted routing with backpressure buffering

**ChannelManager** (`src/main/java/org/nanobot/channel/ChannelManager.java`)
- Auto-registers and starts all enabled channels on `ApplicationReadyEvent`
- Dispatches outbound messages by channel type
- Authorization checks per channel (whitelist in config)

**ToolRegistry** (`src/main/java/org/nanobot/tool/ToolRegistry.java`)
- Auto-registers all `@Component` beans implementing `Tool` interface
- Tools are filtered by `isEnabled()` before registration

**ProviderRegistry** (`src/main/java/org/nanobot/provider/registry/ProviderRegistry.java`)
- Auto-registers providers: GLM, DeepSeek, Qwen, Moonshot, OpenRouter
- Matches provider by model name keyword or API key prefix
- Default provider is GLM if no match found

**SessionManager** (`src/main/java/org/nanobot/agent/SessionManager.java`)
- JSONL file-based persistence in `~/.nanobot/sessions/`
- Session ID format: `{channelType}:{userId}`
- `getHistory()` loads last N messages, `appendMessage()` appends line-by-line

## Adding New Components

### Add a new Tool
Implement `Tool` interface and add `@Component`:
```java
@Component
public class MyTool implements Tool {
    public String getName() { return "my_tool"; }
    public String getDescription() { return "..."; }
    public String getParameterSchema() { return "..."; } // JSON Schema
    public Mono<ToolResult> execute(ToolCall call) { ... }
    public boolean isEnabled() { return true; }
}
```

### Add a new Channel
Implement `Channel` interface with `@Component` and `@ConditionalOnProperty`:
```java
@Component
@ConditionalOnProperty(name = "nanobot.channels.myplatform.enabled", havingValue = "true")
public class MyPlatformChannel implements Channel {
    public String getType() { return "myplatform"; }
    public String getId() { return "..."; }
    public Mono<Void> start() { ... }
    public Mono<Void> stop() { ... }
    public Mono<Void> sendMessage(Message msg) { ... }
    public boolean isEnabled() { return true; }
}
```

### Add a new Provider
1. Register in `ProviderRegistry.registerProviders()`
2. Add config to `application.yml` under `nanobot.providers`
3. Implement `ChatProvider` interface (see `ZhipuAiChatProvider.java`)

## Configuration Structure

The main config is `src/main/resources/application.yml`:

- `nanobot.agents.*` - Model, workspace path, max iterations, history limit, temperature
- `nanobot.providers.*` - LLM provider API keys and base URLs
- `nanobot.channels.*` - Platform credentials and authorized user lists
- `nanobot.tools.*` - Workspace restrictions, file size limits, shell timeout, blocked commands
- `spring.ai.zhipuai.*` - Spring AI integration for Zhipu GLM

Environment variables are used for sensitive data (API keys).

## Important Design Decisions

- **Workspace boundary**: File tools restrict access to configured workspace for security
- **Shell blacklist**: Commands like `rm -rf`, `format`, `dd`, `mkfs` are blocked
- **Parallel tool execution**: Multiple tools in one LLM response execute concurrently via `Flux.merge`
- **Reactive throughout**: All async operations use `Mono`/`Flux` from Project Reactor
- **Spring AI integration**: Currently wraps Spring AI's ZhipuAiChatModel; tool calling via Spring AI is TODO

## Known TODOs

- Agent mode (CLI interactive) not implemented
- Tool calling with Spring AI not fully integrated
- ToolCall argument JSON parsing incomplete
- ChatMessage JSON serialization incomplete
- Some channel WebSocket/polling implementations incomplete

## Completed Tasks

### 2026-02-13: Feishu/Lark SDK Integration
- Integrated official Feishu/Lark SDK (oapi-sdk 2.4.22)
- Implemented WebSocket long connection for receiving real-time events
  - Uses `com.lark.oapi.ws.Client` for WebSocket connection
  - Uses `EventDispatcher` with `P2MessageReceiveV1Handler` for message events
- Implemented HTTP API client for sending messages
  - Uses `com.lark.oapi.Client` for REST API calls
- Event data structure correctly mapped:
  - `P2MessageReceiveV1Data.getSender().getSenderId().getOpenId()` for sender ID
  - `P2MessageReceiveV1Data.getMessage()` for message content
  - `EventMessage.getChatId()`, `getMessageId()`, `getContent()` for message details
- Fixed import conflicts between SDK Client classes using fully qualified names
- Message content sent as JSON: `{"text":"content"}`

### 2026-02-13: Fixed FeishuConfig Bean Wiring Issue
- Fixed `@Getter` and `@Setter` annotation placement issue
  - Annotations were incorrectly placed on `ChannelsConfig` class instead of `FeishuConfig` class
  - Moved annotations to correct location inside `FeishuConfig` class
  - Application now starts successfully and finds required `FeishuConfig` bean

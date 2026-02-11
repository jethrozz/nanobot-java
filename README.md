# Nanobot-Java

> 超轻量级个人 AI 助手框架 - Java 实现

Nanobot-Java 是 Nanobot 项目的 Java 语言实现版本，基于 Spring Boot + Spring AI 构建，为 Java 开发者提供一个可研究、易修改、极度便携的 AI 助手解决方案。

## 特性

- **超轻量级**：核心代码精简，易于理解和维护
- **多平台支持**：支持飞书、企业微信、QQ 等多种聊天平台接入
- **模型无关**：支持 GLM、DeepSeek、Qwen、Moonshot 等多种 LLM 提供商
- **易于扩展**：插件化工具系统、技能系统、消息总线
- **企业级特性**：基于 Spring 生态，天然支持配置中心、服务发现、监控等

## 技术栈

- **Java**: 17+
- **Spring Boot**: 3.3+
- **Spring AI**: 1.0+
- **Reactor**: 响应式编程
- **Maven**: 项目构建

## 快速开始

### 前置要求

- JDK 17 或更高版本
- Maven 3.6+

### 构建

```bash
git clone https://github.com/your-org/nanobot-java.git
cd nanobot-java
mvn clean package
```

### 配置

复制 `application.yml` 并配置必要的参数：

```yaml
nanobot:
  providers:
    glm:
      api-key: "your-glm-api-key"
    deepseek:
      api-key: "your-deepseek-api-key"

  channels:
    feishu:
      enabled: true
      app-id: "your-feishu-app-id"
      app-secret: "your-feishu-app-secret"
```

### 运行

```bash
java -jar target/nanobot-java-1.0.0-SNAPSHOT.jar
```

## 配置

### Provider 配置

支持的 LLM 提供商：

| 提供商 | 模型示例 | 状态 |
|--------|----------|------|
| 智谱 GLM | glm-4-plus, glm-4-flash | ✅ |
| DeepSeek | deepseek-chat | ✅ |
| 阿里云百炼 | qwen-plus | ✅ |
| Moonshot | moonshot-v1-8k | ✅ |

### 频道配置

支持的聊天平台：

| 平台 | 支持方式 | 状态 |
|------|----------|------|
| 飞书 | WebSocket | ✅ |
| 企业微信 | Webhook/API | ✅ |
| QQ | QQ Bot SDK | ✅ |
| 钉钉 | Stream Mode | 🚧 |
| Telegram | Long Polling | 🚧 |

### 工具配置

内置工具：

- `read_file` - 读取文件内容
- `write_file` - 写入文件内容
- `exec` - 执行 Shell 命令
- `message` - 发送消息到指定频道

## 项目结构

```
nanobot-java/
├── src/main/java/org/nanobot/
│   ├── agent/          # Agent 核心引擎
│   ├── api/            # HTTP API
│   ├── bus/            # 消息总线
│   ├── channel/        # 频道接入
│   ├── config/         # 配置类
│   ├── model/          # 数据模型
│   ├── provider/       # LLM Provider
│   ├── session/        # 会话管理
│   ├── skill/          # 技能系统
│   └── tool/           # 工具系统
└── src/main/resources/
    ├── application.yml  # 配置文件
    └── bootstrap/       # 引导文件模板
```

## API 接口

### 健康检查

```
GET /api/health
```

### 发送消息

```
POST /api/messages/send
Content-Type: application/json

{
  "content": "你好",
  "userId": "user123",
  "channelType": "api"
}
```

### 获取频道状态

```
GET /api/channels
```

## 开发

### 新增 Provider

1. 在 `ProviderRegistry` 中注册 Provider 规范
2. 添加配置项到 `application.yml`

### 新增 Channel

1. 实现 `Channel` 接口
2. 添加配置类
3. 使用 `@ConditionalOnProperty` 控制启用

### 新增 Tool

1. 实现 `Tool` 接口
2. 添加 `@Component` 注册为 Spring Bean

## 许可证

MIT License

## 致谢

- [Nanobot Python](https://github.com/nanobot-framework/nanobot) - 原始 Python 实现
- [Spring AI](https://docs.spring.io/spring-ai/reference/) - Spring AI 框架

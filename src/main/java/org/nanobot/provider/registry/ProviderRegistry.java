package org.nanobot.provider.registry;

import lombok.extern.slf4j.Slf4j;
import org.nanobot.provider.ProviderSpec;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Provider 注册表
 */
@Slf4j
@Component
public class ProviderRegistry {

    private final Map<String, ProviderSpec> providers = new ConcurrentHashMap<>();

    public ProviderRegistry() {
        // 注册所有 Provider
        registerProviders();
    }

    /**
     * 注册所有 Provider
     */
    private void registerProviders() {
        // 智谱 GLM
        register(new ProviderSpec(
                "glm",
                Arrays.asList("glm", "zhipu", "智谱"),
                "GLM_API_KEY",
                "",
                false,
                null,
                "https://open.bigmodel.cn/api/paas/v4"
        ));

        // DeepSeek
        register(new ProviderSpec(
                "deepseek",
                Arrays.asList("deepseek", "深度求索"),
                "DEEPSEEK_API_KEY",
                "",
                false,
                "sk-",
                "https://api.deepseek.com/v1"
        ));

        // 阿里云百炼 (Qwen)
        register(new ProviderSpec(
                "qwen",
                Arrays.asList("qwen", "dashscope", "通义千问"),
                "DASHSCOPE_API_KEY",
                "",
                false,
                "sk-",
                "https://dashscope.aliyuncs.com/compatible-mode/v1"
        ));

        // Moonshot (Kimi)
        register(new ProviderSpec(
                "moonshot",
                Arrays.asList("moonshot", "kimi", "月之暗面"),
                "MOONSHOT_API_KEY",
                "",
                false,
                "sk-",
                "https://api.moonshot.cn/v1"
        ));

        // OpenRouter (网关)
        register(new ProviderSpec(
                "openrouter",
                Arrays.asList("openrouter"),
                "OPENROUTER_API_KEY",
                "openrouter/",
                true,
                "sk-or-",
                "https://openrouter.ai/api/v1"
        ));

        log.info("Registered {} providers: {}",
                providers.size(),
                providers.keySet().stream().collect(Collectors.joining(", ")));
    }

    /**
     * 注册 Provider
     */
    public void register(ProviderSpec spec) {
        providers.put(spec.getName(), spec);
        log.debug("Registered provider: {}", spec.getName());
    }

    /**
     * 根据名称获取 Provider
     */
    public ProviderSpec getByName(String name) {
        return providers.get(name.toLowerCase());
    }

    /**
     * 根据模型名匹配 Provider
     */
    public ProviderSpec matchByModel(String model) {
        return providers.values().stream()
                .filter(spec -> spec.matchesModel(model))
                .findFirst()
                .orElse(getDefaultProvider());
    }

    /**
     * 根据 API Key 匹配 Provider
     */
    public ProviderSpec matchByApiKey(String apiKey) {
        return providers.values().stream()
                .filter(spec -> spec.matchesApiKey(apiKey))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取默认 Provider
     */
    public ProviderSpec getDefaultProvider() {
        return providers.get("glm");
    }

    /**
     * 获取所有 Provider
     */
    public List<ProviderSpec> getAllProviders() {
        return List.copyOf(providers.values());
    }

    /**
     * 获取所有已配置的 Provider（有 API Key 的）
     */
    public List<ProviderSpec> getAvailableProviders() {
        return providers.values().stream()
                .filter(this::hasApiKey)
                .collect(Collectors.toList());
    }

    /**
     * 检查 Provider 是否有 API Key
     */
    private boolean hasApiKey(ProviderSpec spec) {
        String apiKey = System.getenv(spec.getEnvKey());
        return apiKey != null && !apiKey.isEmpty();
    }
}

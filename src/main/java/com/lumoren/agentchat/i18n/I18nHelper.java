package com.lumoren.agentchat.i18n;

import com.google.gson.JsonParser;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 国际化工具类，封装 Minecraft 的 {@link Component#translatable(String, Object...)}。
 * <p>
 * 所有 UI 文本必须通过此类获取，禁止直接硬编码字符串。
 * 翻译键定义见 {@link I18nKeys}，语言文件见 {@code assets/agentchat/lang/*.json}。
 */
public final class I18nHelper {
    private static final String FALLBACK_LANG = "/assets/agentchat/lang/en_us.json";
    private static volatile Map<String, String> fallbackTranslations;

    private I18nHelper() {
        // 工具类，禁止实例化
    }

    /**
     * 根据翻译键和可选参数创建可渲染的 {@link MutableComponent}。
     * <p>
     * 示例：
     * <pre>{@code
     * // "正在查询 钻石..."
     * Component translatable = I18nHelper.translate(I18nKeys.LOADING_TOOL_CALL, "钻石");
     * }</pre>
     *
     * @param key  翻译键（定义于 {@link I18nKeys}）
     * @param args 格式化参数（对应 lang 文件中的 {@code %s}）
     * @return 可渲染的文本组件
     */
    public static MutableComponent translate(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /**
     * 根据翻译键和可选参数获取纯文本字符串。
     * <p>
     * 注意：此方法需要在 Minecraft 客户端或服务端环境下调用，
     * 因为 {@link Component#getString()} 需要已加载的语言数据。
     * 在单元测试中应避免调用此方法。
     *
     * @param key  翻译键（定义于 {@link I18nKeys}）
     * @param args 格式化参数（对应 lang 文件中的 {@code %s}）
     * @return 翻译后的纯文本字符串
     */
    public static String translateToString(String key, Object... args) {
        String translated = Component.translatable(key, args).getString();
        if (!key.equals(translated)) {
            return translated;
        }
        String template = fallbackTranslations().get(key);
        if (template == null) {
            return translated;
        }
        if (args == null || args.length == 0) {
            return template;
        }
        try {
            return String.format(Locale.ROOT, template, args);
        } catch (RuntimeException e) {
            return template;
        }
    }

    private static Map<String, String> fallbackTranslations() {
        Map<String, String> cached = fallbackTranslations;
        if (cached != null) {
            return cached;
        }
        Map<String, String> loaded = new HashMap<>();
        try (var in = I18nHelper.class.getResourceAsStream(FALLBACK_LANG)) {
            if (in != null) {
                try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    var root = JsonParser.parseReader(reader).getAsJsonObject();
                    for (var entry : root.entrySet()) {
                        if (!entry.getValue().isJsonNull()) {
                            loaded.put(entry.getKey(), entry.getValue().getAsString());
                        }
                    }
                }
            }
        } catch (RuntimeException | java.io.IOException ignored) {
            // Minecraft language data remains the primary source in live clients.
        }
        fallbackTranslations = Map.copyOf(loaded);
        return fallbackTranslations;
    }
}

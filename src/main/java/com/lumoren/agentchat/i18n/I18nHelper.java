package com.lumoren.agentchat.i18n;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 国际化工具类，封装 Minecraft 的 {@link Component#translatable(String, Object...)}。
 * <p>
 * 所有 UI 文本必须通过此类获取，禁止直接硬编码字符串。
 * 翻译键定义见 {@link I18nKeys}，语言文件见 {@code assets/agentchat/lang/*.json}。
 */
public final class I18nHelper {

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
        return Component.translatable(key, args).getString();
    }
}

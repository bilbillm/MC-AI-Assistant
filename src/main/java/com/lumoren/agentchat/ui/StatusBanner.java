package com.lumoren.agentchat.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 统一状态横幅组件，用于在聊天面板顶部展示短暂状态提示。
 * <p>
 * 支持四种状态类型：
 * <ul>
 *   <li>{@link Type#SUCCESS} — 绿色（已连接等）</li>
 *   <li>{@link Type#WARNING} — 黄色（警告提示）</li>
 *   <li>{@link Type#ERROR} — 红色（网络错误、无 API Key 等）</li>
 *   <li>{@link Type#LOADING} — 蓝色（加载中）</li>
 * </ul>
 * 显示后会在指定毫秒数后自动消失。
 */
public class StatusBanner {

    public enum Type {
        SUCCESS(0xFF4CAF50),
        WARNING(0xFFFFC107),
        ERROR(0xFFF44336),
        LOADING(0xFF2196F3);

        private final int color;

        Type(int color) {
            this.color = color;
        }

        public int getColor() {
            return color;
        }
    }

    private String message;
    private Type type;
    private long showUntil;

    /**
     * 显示一条状态横幅。
     *
     * @param message    要展示的文字
     * @param type       横幅类型（决定颜色）
     * @param durationMs 持续毫秒数，结束后自动隐藏
     */
    public void show(String message, Type type, int durationMs) {
        this.message = message;
        this.type = type;
        this.showUntil = System.currentTimeMillis() + durationMs;
    }

    /**
     * 清除当前横幅，立即隐藏。
     */
    public void hide() {
        this.showUntil = 0;
    }

    /**
     * @return 当前横幅是否处于可见状态
     */
    public boolean isVisible() {
        return System.currentTimeMillis() < showUntil;
    }

    /**
     * 渲染横幅。
     *
     * @param graphics 绘制上下文
     * @param font     字体渲染器
     * @param x        横幅左上角 X
     * @param y        横幅左上角 Y
     * @param width    横幅宽度
     * @param height   横幅高度（建议 16–20）
     */
    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height) {
        if (!isVisible()) {
            return;
        }

        int color = type != null ? type.getColor() : 0xFF888888;
        graphics.fill(x, y, x + width, y + height, color);

        if (message != null && !message.isEmpty()) {
            int textColor = 0xFFFFFFFF;
            int textX = x + 6;
            int textY = y + (height - font.lineHeight) / 2;
            graphics.drawString(font, message, textX, textY, textColor);
        }
    }

    /**
     * 根据 API Key 与连接状态自动展示对应提示。
     *
     * @param hasApiKey 是否已配置 API Key
     * @param connected 是否已连接到 API
     */
    public void updateFromState(boolean hasApiKey, boolean connected) {
        if (!hasApiKey) {
            show("No API Key configured! Use /agentchat config to set up", Type.ERROR, 5000);
        } else if (connected) {
            show("Connected to API", Type.SUCCESS, 3000);
        } else {
            show("Disconnected", Type.WARNING, 3000);
        }
    }
}

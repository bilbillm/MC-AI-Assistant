package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.AgentChat;
import com.lumoren.agentchat.client.ClientServiceManager;
import com.lumoren.agentchat.config.Config;
import com.lumoren.agentchat.config.ConfigManager;
import com.lumoren.agentchat.i18n.I18nHelper;
import com.lumoren.agentchat.i18n.I18nKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * AI 助手图形配置界面。
 * <p>
 * 提供以下配置项的可视化编辑：
 * <ul>
 *   <li>API Key（密码掩码，展示后 4 位）</li>
 *   <li>API 地址（Base URL）</li>
 *   <li>模型名称</li>
 *   <li>温度（0.0 - 2.0 滑块）</li>
 *   <li>最大 Token（整数输入）</li>
 *   <li>侧边栏位置（LEFT / RIGHT 下拉）</li>
 *   <li>侧边栏宽度（20% - 50% 滑块）</li>
 * </ul>
 * 保存时同时写入 {@link Config} 与 {@link SecretsConfig}，并重置 {@link ClientServiceManager}。
 */
public class ConfigScreen extends Screen {

    private static final int PADDING = 20;
    private static final int LINE_HEIGHT = 24;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int FIELD_WIDTH = 200;
    private static final int LABEL_WIDTH = 120;
    private static final int MASK_LENGTH = 8;
    private static final char MASK_CHAR = '\u2022'; // bullet

    private final Screen parent;

    private EditBox apiKeyField;
    private EditBox baseUrlField;
    private EditBox modelField;
    private ConfigSlider temperatureSlider;
    private EditBox maxTokensField;
    private CycleButton<String> positionButton;
    private ConfigSlider widthSlider;

    private double tempValue;
    private int widthValue;
    private String positionValue;
    private String originalApiKey;

    public ConfigScreen(Screen parent) {
        super(I18nHelper.translate(I18nKeys.CONFIG_TITLE));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        ConfigManager cfg = ConfigManager.getInstance();
        this.originalApiKey = cfg.getApiKey();
        this.tempValue = cfg.getTemperature();
        this.widthValue = cfg.getSidebarWidth();
        this.positionValue = cfg.getSidebarPosition();

        int centerX = this.width / 2;
        int startY = PADDING + 20;
        int fieldX = centerX - FIELD_WIDTH / 2;
        int labelX = fieldX - LABEL_WIDTH - 10;

        int y = startY;

        // API Key
        apiKeyField = new EditBox(font, fieldX, y, FIELD_WIDTH, BUTTON_HEIGHT, Component.empty());
        apiKeyField.setMaxLength(256);
        apiKeyField.setValue(maskApiKey(originalApiKey));
        addRenderableWidget(apiKeyField);
        y += LINE_HEIGHT;

        // Base URL
        baseUrlField = new EditBox(font, fieldX, y, FIELD_WIDTH, BUTTON_HEIGHT, Component.empty());
        baseUrlField.setMaxLength(256);
        baseUrlField.setValue(Config.BASE_URL.get());
        addRenderableWidget(baseUrlField);
        y += LINE_HEIGHT;

        // Model
        modelField = new EditBox(font, fieldX, y, FIELD_WIDTH, BUTTON_HEIGHT, Component.empty());
        modelField.setMaxLength(128);
        modelField.setValue(Config.MODEL.get());
        addRenderableWidget(modelField);
        y += LINE_HEIGHT;

        // Temperature slider
        temperatureSlider = new ConfigSlider(
                fieldX, y, FIELD_WIDTH, BUTTON_HEIGHT,
                I18nHelper.translate(I18nKeys.CONFIG_TEMPERATURE),
                0.0, 2.0, tempValue, 1,
                v -> tempValue = v
        );
        addRenderableWidget(temperatureSlider);
        y += LINE_HEIGHT;

        // Max Tokens
        maxTokensField = new EditBox(font, fieldX, y, FIELD_WIDTH, BUTTON_HEIGHT, Component.empty());
        maxTokensField.setMaxLength(6);
        maxTokensField.setValue(String.valueOf(Config.MAX_TOKENS.get()));
        maxTokensField.setFilter(s -> s.matches("\\d*"));
        addRenderableWidget(maxTokensField);
        y += LINE_HEIGHT;

        // Sidebar Position dropdown
        positionButton = CycleButton.<String>builder(Component::literal)
                .withValues("LEFT", "RIGHT")
                .withInitialValue(positionValue)
                .create(fieldX, y, FIELD_WIDTH, BUTTON_HEIGHT,
                        I18nHelper.translate(I18nKeys.CONFIG_SIDEBAR_POSITION),
                        (btn, val) -> positionValue = val);
        addRenderableWidget(positionButton);
        y += LINE_HEIGHT;

        // Sidebar Width slider
        widthSlider = new ConfigSlider(
                fieldX, y, FIELD_WIDTH, BUTTON_HEIGHT,
                I18nHelper.translate(I18nKeys.CONFIG_SIDEBAR_WIDTH),
                20, 50, widthValue, 0,
                v -> widthValue = v.intValue()
        );
        addRenderableWidget(widthSlider);
        y += LINE_HEIGHT + 10;

        // Save button
        int buttonY = Math.min(y, this.height - PADDING - BUTTON_HEIGHT);
        addRenderableWidget(
                Button.builder(I18nHelper.translate(I18nKeys.BUTTON_SAVE), this::onSave)
                        .bounds(centerX - BUTTON_WIDTH - 5, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                        .build()
        );

        // Cancel button
        addRenderableWidget(
                Button.builder(I18nHelper.translate(I18nKeys.BUTTON_CANCEL), this::onCancel)
                        .bounds(centerX + 5, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                        .build()
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, this.title, this.width / 2, PADDING, 0xFFFFFFFF);

        int centerX = this.width / 2;
        int startY = PADDING + 20;
        int labelX = centerX - FIELD_WIDTH / 2 - LABEL_WIDTH - 10;

        int y = startY;
        drawLabel(graphics, I18nKeys.CONFIG_API_KEY, labelX, y + 6);
        y += LINE_HEIGHT;
        drawLabel(graphics, I18nKeys.CONFIG_BASE_URL, labelX, y + 6);
        y += LINE_HEIGHT;
        drawLabel(graphics, I18nKeys.CONFIG_MODEL, labelX, y + 6);
        y += LINE_HEIGHT;
        drawLabel(graphics, I18nKeys.CONFIG_TEMPERATURE, labelX, y + 6);
        y += LINE_HEIGHT;
        drawLabel(graphics, I18nKeys.CONFIG_MAX_TOKENS, labelX, y + 6);
        y += LINE_HEIGHT;
        drawLabel(graphics, I18nKeys.CONFIG_SIDEBAR_POSITION, labelX, y + 6);
        y += LINE_HEIGHT;
        drawLabel(graphics, I18nKeys.CONFIG_SIDEBAR_WIDTH, labelX, y + 6);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawLabel(GuiGraphics graphics, String key, int x, int y) {
        graphics.drawString(font, I18nHelper.translate(key), x, y, 0xFFCCCCCC, false);
    }

    private String maskApiKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        if (key.length() <= 4) {
            return String.valueOf(MASK_CHAR).repeat(MASK_LENGTH);
        }
        String last4 = key.substring(key.length() - 4);
        return String.valueOf(MASK_CHAR).repeat(MASK_LENGTH) + last4;
    }

    private boolean isMasked(String value) {
        return !value.isEmpty() && value.charAt(0) == MASK_CHAR;
    }

    private void onSave(Button button) {
        // API Key
        String apiKeyInput = apiKeyField.getValue().trim();
        if (!isMasked(apiKeyInput) && !apiKeyInput.isEmpty()) {
            ConfigManager.getInstance().getSecrets().saveApiKey(apiKeyInput);
        }

        // Base URL
        Config.BASE_URL.set(baseUrlField.getValue().trim());

        // Model
        Config.MODEL.set(modelField.getValue().trim());

        // Temperature
        Config.TEMPERATURE.set(tempValue);

        // Max Tokens
        try {
            int maxTokens = Integer.parseInt(maxTokensField.getValue().trim());
            if (maxTokens >= 100 && maxTokens <= 4096) {
                Config.MAX_TOKENS.set(maxTokens);
            }
        } catch (NumberFormatException ignored) {
            // keep existing
        }

        // Sidebar Position
        Config.SIDEBAR_POSITION.set(positionValue);

        // Sidebar Width
        Config.SIDEBAR_WIDTH.set(widthValue);

        // Reset AI service to pick up new config
        ClientServiceManager.reset();

        onClose();
    }

    private void onCancel(Button button) {
        onClose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    /**
     * 简易配置滑块，基于 {@link AbstractSliderButton}。
     */
    private static class ConfigSlider extends AbstractSliderButton {

        private final double min;
        private final double max;
        private final int decimalPlaces;
        private final Consumer<Double> onChange;

        ConfigSlider(int x, int y, int width, int height, Component label,
                     double min, double max, double current, int decimalPlaces,
                     Consumer<Double> onChange) {
            super(x, y, width, height, label, (current - min) / (max - min));
            this.min = min;
            this.max = max;
            this.decimalPlaces = decimalPlaces;
            this.onChange = onChange;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double val = min + this.value * (max - min);
            String format = decimalPlaces > 0 ? "%." + decimalPlaces + "f" : "%.0f";
            setMessage(Component.literal(String.format(format, val)));
        }

        @Override
        protected void applyValue() {
            double val = min + this.value * (max - min);
            if (decimalPlaces == 0) {
                val = Math.round(val);
            }
            onChange.accept(val);
        }
    }
}

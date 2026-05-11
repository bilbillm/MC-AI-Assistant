package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.client.ClientServiceManager;
import com.lumoren.agentchat.config.Config;
import com.lumoren.agentchat.config.ConfigManager;
import com.lumoren.agentchat.i18n.I18nHelper;
import com.lumoren.agentchat.i18n.I18nKeys;
import com.lumoren.agentchat.ui.theme.ChatColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * AI 助手图形配置界面。
 * 所有文字通过 LabelWidget 渲染（widget 管线确保清晰）。
 * 关闭界面时自动保存配置。
 */
public class ConfigScreen extends Screen {

    private static final int PADDING = 20;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int FIELD_WIDTH = 200;
    private static final int LABEL_GAP = 8;
    private static final int ROW_HEIGHT = 24;
    private static final char MASK_CHAR = '*';

    private final Screen parent;

    private EditBox apiKeyField;
    private EditBox baseUrlField;
    private EditBox modelField;
    private ConfigSlider temperatureSlider;
    private EditBox maxTokensField;

    private double tempValue;

    private String savedApiKey; // original key for change detection

    public ConfigScreen(Screen parent) {
        super(I18nHelper.translate(I18nKeys.CONFIG_TITLE));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        ConfigManager cfg = ConfigManager.getInstance();
        this.tempValue = cfg.getTemperature();

        // Center the form horizontally. label+gap+field must fit screen.
        // Find widest label to compute total width.
        int maxLabelW = Math.max(font.width(I18nHelper.translateToString(I18nKeys.CONFIG_BASE_URL)),
                font.width(I18nHelper.translateToString(I18nKeys.CONFIG_MAX_TOKENS)));
        int formWidth = maxLabelW + LABEL_GAP + FIELD_WIDTH;
        int formX = Math.max(PADDING, (this.width - formWidth) / 2);
        int fieldX = formX + maxLabelW + LABEL_GAP;

        // Title (centered)
        String titleStr = I18nHelper.translateToString(I18nKeys.CONFIG_TITLE);
        this.addRenderableWidget(new LabelWidget(
                this.width / 2 - font.width(titleStr) / 2, PADDING,
                titleStr, ChatColors.TEXT_PRIMARY, font));

        int y = PADDING + 24;

        // API Key
        addLabel(I18nKeys.CONFIG_API_KEY, formX, y);
        this.savedApiKey = cfg.getApiKey();
        apiKeyField = new EditBox(font, fieldX, y, FIELD_WIDTH, BUTTON_HEIGHT, Component.empty());
        apiKeyField.setMaxLength(256);
        if (savedApiKey != null && !savedApiKey.isBlank()) apiKeyField.setValue(maskApiKey(savedApiKey));
        apiKeyField.setHint(I18nHelper.translate(I18nKeys.CONFIG_HINT_API_KEY));
        addRenderableWidget(apiKeyField);
        y += ROW_HEIGHT;

        // Base URL
        addLabel(I18nKeys.CONFIG_BASE_URL, formX, y);
        baseUrlField = new EditBox(font, fieldX, y, FIELD_WIDTH, BUTTON_HEIGHT, Component.empty());
        baseUrlField.setMaxLength(256);
        baseUrlField.setValue(cfg.getBaseUrl());
        baseUrlField.setHint(I18nHelper.translate(I18nKeys.CONFIG_HINT_BASE_URL));
        addRenderableWidget(baseUrlField);
        y += ROW_HEIGHT;

        // Model
        addLabel(I18nKeys.CONFIG_MODEL, formX, y);
        modelField = new EditBox(font, fieldX, y, FIELD_WIDTH, BUTTON_HEIGHT, Component.empty());
        modelField.setMaxLength(128);
        modelField.setValue(cfg.getModel());
        modelField.setHint(I18nHelper.translate(I18nKeys.CONFIG_HINT_MODEL));
        addRenderableWidget(modelField);
        y += ROW_HEIGHT;

        // Temperature
        addLabel(I18nKeys.CONFIG_TEMPERATURE, formX, y);
        temperatureSlider = new ConfigSlider(fieldX, y, FIELD_WIDTH, BUTTON_HEIGHT,
                I18nHelper.translateToString(I18nKeys.CONFIG_TEMPERATURE), "",
                0.0, 2.0, tempValue, 1, v -> tempValue = v);
        addRenderableWidget(temperatureSlider);
        y += ROW_HEIGHT;

        // Max Tokens
        addLabel(I18nKeys.CONFIG_MAX_TOKENS, formX, y);
        maxTokensField = new EditBox(font, fieldX, y, FIELD_WIDTH, BUTTON_HEIGHT, Component.empty());
        maxTokensField.setMaxLength(6);
        maxTokensField.setFilter(s -> s.matches("\\d*"));
        maxTokensField.setValue(String.valueOf(cfg.getMaxTokens()));
        maxTokensField.setHint(I18nHelper.translate(I18nKeys.CONFIG_HINT_MAX_TOKENS));
        addRenderableWidget(maxTokensField);
        y += ROW_HEIGHT + 10;

        // Buttons
        int buttonY = Math.min(y, this.height - PADDING - BUTTON_HEIGHT);
        int btnCenterX = formX + formWidth / 2;
        addRenderableWidget(Button.builder(I18nHelper.translate(I18nKeys.BUTTON_SAVE), this::onSave)
                .bounds(btnCenterX - BUTTON_WIDTH - 5, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(I18nHelper.translate(I18nKeys.BUTTON_CANCEL), this::onCancel)
                .bounds(btnCenterX + 5, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void addLabel(String key, int x, int y) {
        this.addRenderableWidget(new LabelWidget(
                x, y,
                I18nHelper.translateToString(key),
                ChatColors.TEXT_SECONDARY, font
        ));
    }

    private void doSave() {
        // API Key — only save if user modified the field
        String apiKeyInput = apiKeyField.getValue().trim();
        if (!apiKeyInput.isEmpty()) {
            String masked = savedApiKey != null ? maskApiKey(savedApiKey) : "";
            if (!apiKeyInput.equals(masked)) {
                ConfigManager.getInstance().getSecrets().saveApiKey(apiKeyInput);
            }
        }

        String baseUrl = baseUrlField.getValue().trim();
        if (!baseUrl.isEmpty()) {
            Config.BASE_URL.set(baseUrl);
        }

        String model = modelField.getValue().trim();
        if (!model.isEmpty()) {
            Config.MODEL.set(model);
        }

        Config.TEMPERATURE.set(tempValue);

        String maxTokensInput = maxTokensField.getValue().trim();
        if (!maxTokensInput.isEmpty()) {
            try {
                int maxTokens = Integer.parseInt(maxTokensInput);
                if (maxTokens >= 100 && maxTokens <= 4096) {
                    Config.MAX_TOKENS.set(maxTokens);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        ClientServiceManager.reset();

        // Persist to disk
        Config.save();
    }

    private void onSave(Button button) {
        doSave();
        onClose();
    }

    private void onCancel(Button button) {
        onClose();
    }

    @Override
    public void onClose() {
        doSave(); // auto-save on close (ESC / Cancel)
        Minecraft.getInstance().setScreen(parent);
    }

    private void onApiKeyChanged(String text) {
        // When user types in the masked field, the mask chars are replaced.
        // No special action needed — doSave compares against the original mask.
    }

    /**
     * Mask API key: show last 4 characters, replace the rest with '*'.
     * Example: "sk-abc123xyz" → "********xyz"
     */
    private static String maskApiKey(String key) {
        if (key == null || key.isBlank()) return "";
        int maskLen = Math.max(0, key.length() - 4);
        return "*".repeat(maskLen) + (maskLen >= key.length() ? "" : key.substring(maskLen));
    }

    /**
     * 配置滑块，显示"标签: 值后缀"格式。
     */
    private static class ConfigSlider extends AbstractSliderButton {

        private final String labelPrefix;
        private final String suffix;
        private final double min;
        private final double max;
        private final int decimalPlaces;
        private final Consumer<Double> onChange;

        ConfigSlider(int x, int y, int width, int height, String labelPrefix, String suffix,
                     double min, double max, double current, int decimalPlaces,
                     Consumer<Double> onChange) {
            super(x, y, width, height, Component.empty(), (current - min) / (max - min));
            this.labelPrefix = labelPrefix;
            this.suffix = suffix;
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
            setMessage(Component.literal(labelPrefix + ": " + String.format(format, val) + suffix));
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

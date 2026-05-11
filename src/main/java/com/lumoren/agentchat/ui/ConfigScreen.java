package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.client.ClientServiceManager;
import com.lumoren.agentchat.config.Config;
import com.lumoren.agentchat.config.ConfigManager;
import com.lumoren.agentchat.i18n.I18nHelper;
import com.lumoren.agentchat.i18n.I18nKeys;
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
    private static final int LINE_HEIGHT = 24;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int FIELD_WIDTH = 200;
    private static final int LABEL_WIDTH = 120;
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

        int centerX = this.width / 2;
        int startY = PADDING + 20;
        int fieldX = centerX - FIELD_WIDTH / 2;
        int labelX = centerX - FIELD_WIDTH / 2 - LABEL_WIDTH - 10;

        // Title label (centered)
        String titleStr = I18nHelper.translateToString(I18nKeys.CONFIG_TITLE);
        this.addRenderableWidget(new LabelWidget(
                centerX - font.width(titleStr) / 2, PADDING,
                titleStr, 0xFFFFFFFF, font
        ));

        int y = startY;

        // Labels + fields (label widgets drawn before fields for proper hit-testing)
        addLabel(I18nKeys.CONFIG_API_KEY, labelX, y + 4);
        this.savedApiKey = cfg.getApiKey();
        apiKeyField = new EditBox(font, fieldX, y, FIELD_WIDTH, BUTTON_HEIGHT, Component.empty());
        apiKeyField.setMaxLength(256);
        if (savedApiKey != null && !savedApiKey.isBlank()) {
            apiKeyField.setValue(maskApiKey(savedApiKey));
        }
        apiKeyField.setHint(I18nHelper.translate(I18nKeys.CONFIG_HINT_API_KEY));
        apiKeyField.setResponder(this::onApiKeyChanged);
        addRenderableWidget(apiKeyField);
        y += LINE_HEIGHT;

        addLabel(I18nKeys.CONFIG_BASE_URL, labelX, y + 4);
        baseUrlField = new EditBox(font, fieldX, y, FIELD_WIDTH, BUTTON_HEIGHT, Component.empty());
        baseUrlField.setMaxLength(256);
        baseUrlField.setValue(cfg.getBaseUrl());
        baseUrlField.setHint(I18nHelper.translate(I18nKeys.CONFIG_HINT_BASE_URL));
        addRenderableWidget(baseUrlField);
        y += LINE_HEIGHT;

        addLabel(I18nKeys.CONFIG_MODEL, labelX, y + 4);
        modelField = new EditBox(font, fieldX, y, FIELD_WIDTH, BUTTON_HEIGHT, Component.empty());
        modelField.setMaxLength(128);
        modelField.setValue(cfg.getModel());
        modelField.setHint(I18nHelper.translate(I18nKeys.CONFIG_HINT_MODEL));
        addRenderableWidget(modelField);
        y += LINE_HEIGHT;

        addLabel(I18nKeys.CONFIG_TEMPERATURE, labelX, y + 4);
        temperatureSlider = new ConfigSlider(
                fieldX, y, FIELD_WIDTH, BUTTON_HEIGHT,
                I18nHelper.translateToString(I18nKeys.CONFIG_TEMPERATURE),
                "", 0.0, 2.0, tempValue, 1,
                v -> tempValue = v
        );
        addRenderableWidget(temperatureSlider);
        y += LINE_HEIGHT;

        addLabel(I18nKeys.CONFIG_MAX_TOKENS, labelX, y + 4);
        maxTokensField = new EditBox(font, fieldX, y, FIELD_WIDTH, BUTTON_HEIGHT, Component.empty());
        maxTokensField.setMaxLength(6);
        maxTokensField.setFilter(s -> s.matches("\\d*"));
        maxTokensField.setValue(String.valueOf(cfg.getMaxTokens()));
        maxTokensField.setHint(I18nHelper.translate(I18nKeys.CONFIG_HINT_MAX_TOKENS));
        addRenderableWidget(maxTokensField);
        y += LINE_HEIGHT + 10;

        // Save + Cancel buttons
        int buttonY = Math.min(y, this.height - PADDING - BUTTON_HEIGHT);
        addRenderableWidget(
                Button.builder(I18nHelper.translate(I18nKeys.BUTTON_SAVE), this::onSave)
                        .bounds(centerX - BUTTON_WIDTH - 5, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                        .build()
        );
        addRenderableWidget(
                Button.builder(I18nHelper.translate(I18nKeys.BUTTON_CANCEL), this::onCancel)
                        .bounds(centerX + 5, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                        .build()
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        // All labels, fields, sliders, buttons rendered via super.render()
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
                0xFFCCCCCC, font
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

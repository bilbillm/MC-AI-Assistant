package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.client.ClientServiceManager;
import com.lumoren.agentchat.config.Config;
import com.lumoren.agentchat.config.ConfigManager;
import com.lumoren.agentchat.i18n.I18nHelper;
import com.lumoren.agentchat.i18n.I18nKeys;
import com.lumoren.agentchat.ui.theme.ChatColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
    private EditBox temperatureField;
    private EditBox maxTokensField;

    private int maxTokensValue; // tracked via responder to avoid stale EditBox value

    private String savedApiKey; // original key for change detection

    public ConfigScreen(Screen parent) {
        super(I18nHelper.translate(I18nKeys.CONFIG_TITLE));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        ConfigManager cfg = ConfigManager.getInstance();

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
        temperatureField = new EditBox(font, fieldX, y, FIELD_WIDTH, BUTTON_HEIGHT, Component.empty());
        temperatureField.setMaxLength(5);
        temperatureField.setFilter(s -> s.matches("[0-9]*\\.?[0-9]*"));
        temperatureField.setValue(String.format("%.1f", cfg.getTemperature()));
        temperatureField.setHint(Component.literal("0.0-2.0"));
        addRenderableWidget(temperatureField);
        y += ROW_HEIGHT;

        // Max Tokens
        addLabel(I18nKeys.CONFIG_MAX_TOKENS, formX, y);
        maxTokensField = new EditBox(font, fieldX, y, FIELD_WIDTH, BUTTON_HEIGHT, Component.empty());
        maxTokensField.setMaxLength(6);
        maxTokensField.setFilter(s -> s.matches("\\d*"));
        maxTokensField.setValue(String.valueOf(cfg.getMaxTokens()));
        maxTokensField.setHint(I18nHelper.translate(I18nKeys.CONFIG_HINT_MAX_TOKENS));
        // Track value via responder to avoid stale getValue() at save time
        this.maxTokensValue = cfg.getMaxTokens();
        maxTokensField.setResponder(s -> {
            try { maxTokensValue = s.isEmpty() ? 0 : Integer.parseInt(s); }
            catch (NumberFormatException ignored) { maxTokensValue = 0; }
        });
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

        String tempInput = temperatureField.getValue().trim();
        if (!tempInput.isEmpty()) {
            try {
                double val = Double.parseDouble(tempInput);
                if (val >= 0.0 && val <= 2.0) {
                    Config.TEMPERATURE.set(val);
                }
            } catch (NumberFormatException ignored) {}
        }

        String maxTokensInput = maxTokensField.getValue().trim();
        if (!maxTokensInput.isEmpty() || maxTokensValue > 0) {
            int maxTokens = maxTokensValue > 0 ? maxTokensValue
                    : Integer.parseInt(maxTokensInput);
            if (maxTokens >= 100 && maxTokens <= 4096) {
                Config.MAX_TOKENS.set(Integer.valueOf(maxTokens));
            }
        }

        ClientServiceManager.reset();

        // Persist to disk
        Config.save();
    }

    private void onSave(Button button) {
        onClose(); // onClose() handles save + close
    }

    private void onCancel(Button button) {
        onClose();
    }

    @Override
    public void onClose() {
        doSave(); // auto-save on any close (Save, Cancel, ESC)
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
}

package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.client.ClientServiceManager;
import com.lumoren.agentchat.config.Config;
import com.lumoren.agentchat.config.ConfigManager;
import com.lumoren.agentchat.i18n.I18nHelper;
import com.lumoren.agentchat.i18n.I18nKeys;
import com.lumoren.agentchat.ui.theme.GuideTheme;
import com.lumoren.agentchat.ui.theme.MechanicalSprites;
import com.lumoren.agentchat.ui.widget.MechanicalButton;
import com.lumoren.agentchat.ui.widget.MechanicalPanelWidget;
import com.lumoren.agentchat.ui.widget.MechanicalToggleWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Mechanical-styled config screen. Save persists only via the explicit Save
 * button; Cancel and ESC discard all edits. API key stays masked and is only
 * written when the field content actually changed.
 */
public class ConfigScreen extends Screen {

    private static final int PADDING = 20;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int LABEL_GAP = 8;
    private static final int ROW_HEIGHT = 24;
    private static final char MASK_CHAR = '*';

    private final Screen parent;

    private EditBox apiKeyField;
    private EditBox baseUrlField;
    private EditBox modelField;
    private EditBox temperatureField;
    private EditBox maxTokensField;
    private boolean guideModeDefault;
    private boolean webKnowledgeEnabled;

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

        int maxLabelW = Math.max(font.width(I18nHelper.translateToString(I18nKeys.CONFIG_BASE_URL)),
                font.width(I18nHelper.translateToString(I18nKeys.CONFIG_MAX_TOKENS)));
        int fieldWidth = Math.min(200, Math.max(120, width - maxLabelW - LABEL_GAP - PADDING * 2));
        int formWidth = maxLabelW + LABEL_GAP + fieldWidth;
        int formX = Math.max(PADDING, (this.width - formWidth) / 2);
        int fieldX = formX + maxLabelW + LABEL_GAP;

        // Background panel
        int panelX = formX - 10;
        int panelW = formWidth + 20;
        this.addRenderableWidget(new MechanicalPanelWidget(panelX, PADDING - 6,
                panelW, this.height - PADDING * 2 + 12, MechanicalSprites.PANEL_IRON, true));

        // Title (centered)
        String titleStr = I18nHelper.translateToString(I18nKeys.CONFIG_TITLE);
        this.addRenderableWidget(new LabelWidget(
                this.width / 2 - font.width(titleStr) / 2, PADDING,
                titleStr, GuideTheme.TEXT_PRIMARY, font));

        int y = PADDING + 24;

        // API Key
        addLabel(I18nKeys.CONFIG_API_KEY, formX, y);
        this.savedApiKey = cfg.getApiKey();
        apiKeyField = addField(fieldX, y, fieldWidth);
        apiKeyField.setMaxLength(256);
        if (savedApiKey != null && !savedApiKey.isBlank()) apiKeyField.setValue(maskApiKey(savedApiKey));
        apiKeyField.setHint(I18nHelper.translate(I18nKeys.CONFIG_HINT_API_KEY));
        y += ROW_HEIGHT;

        // Base URL
        addLabel(I18nKeys.CONFIG_BASE_URL, formX, y);
        baseUrlField = addField(fieldX, y, fieldWidth);
        baseUrlField.setMaxLength(256);
        baseUrlField.setValue(cfg.getBaseUrl());
        baseUrlField.setHint(I18nHelper.translate(I18nKeys.CONFIG_HINT_BASE_URL));
        y += ROW_HEIGHT;

        // Model
        addLabel(I18nKeys.CONFIG_MODEL, formX, y);
        modelField = addField(fieldX, y, fieldWidth);
        modelField.setMaxLength(128);
        modelField.setValue(cfg.getModel());
        modelField.setHint(I18nHelper.translate(I18nKeys.CONFIG_HINT_MODEL));
        y += ROW_HEIGHT;

        // Temperature
        addLabel(I18nKeys.CONFIG_TEMPERATURE, formX, y);
        temperatureField = addField(fieldX, y, fieldWidth);
        temperatureField.setMaxLength(5);
        temperatureField.setFilter(s -> s.matches("[0-9]*\\.?[0-9]*"));
        temperatureField.setValue(String.format("%.1f", cfg.getTemperature()));
        temperatureField.setHint(Component.literal("0.0-2.0"));
        y += ROW_HEIGHT;

        // Max Tokens
        addLabel(I18nKeys.CONFIG_MAX_TOKENS, formX, y);
        maxTokensField = addField(fieldX, y, fieldWidth);
        maxTokensField.setMaxLength(6);
        maxTokensField.setFilter(s -> s.matches("\\d*"));
        maxTokensField.setValue(String.valueOf(cfg.getMaxTokens()));
        maxTokensField.setHint(I18nHelper.translate(I18nKeys.CONFIG_HINT_MAX_TOKENS));
        this.maxTokensValue = cfg.getMaxTokens();
        this.guideModeDefault = cfg.isGuideModeDefault();
        this.webKnowledgeEnabled = cfg.isWebKnowledgeEnabled();
        maxTokensField.setResponder(s -> {
            try { maxTokensValue = s.isEmpty() ? 0 : Integer.parseInt(s); }
            catch (NumberFormatException ignored) { maxTokensValue = 0; }
        });
        y += ROW_HEIGHT;

        // Guide mode default toggle
        addLabel(I18nKeys.CONFIG_GUIDE_MODE_DEFAULT, formX, y);
        addRenderableWidget(new MechanicalToggleWidget(fieldX, y + 4, guideModeDefault,
                I18nHelper.translate(I18nKeys.CONFIG_GUIDE_MODE_DEFAULT),
                v -> guideModeDefault = v));
        y += ROW_HEIGHT;

        // Web knowledge toggle
        addLabel(I18nKeys.CONFIG_WEB_KNOWLEDGE, formX, y);
        addRenderableWidget(new MechanicalToggleWidget(fieldX, y + 4, webKnowledgeEnabled,
                I18nHelper.translate(I18nKeys.CONFIG_WEB_KNOWLEDGE),
                v -> webKnowledgeEnabled = v));
        y += ROW_HEIGHT + 10;

        // Buttons — Save is the ONLY persistence path; Cancel/ESC discard
        int buttonY = Math.min(y, this.height - PADDING - BUTTON_HEIGHT);
        int btnCenterX = formX + formWidth / 2;
        addRenderableWidget(new MechanicalButton(font, btnCenterX - BUTTON_WIDTH - 5, buttonY,
                BUTTON_WIDTH, BUTTON_HEIGHT, I18nHelper.translate(I18nKeys.BUTTON_SAVE),
                MechanicalButton.Variant.BRASS, b -> {
            doSave();
            onClose();
        }));
        addRenderableWidget(new MechanicalButton(font, btnCenterX + 5, buttonY,
                BUTTON_WIDTH, BUTTON_HEIGHT, I18nHelper.translate(I18nKeys.BUTTON_CANCEL),
                MechanicalButton.Variant.IRON, b -> onClose()));
    }

    private EditBox addField(int x, int y, int width) {
        this.addRenderableWidget(new MechanicalPanelWidget(x - 2, y - 2, width + 4,
                BUTTON_HEIGHT + 4, MechanicalSprites.PANEL_INSET, false));
        EditBox field = new EditBox(font, x, y + 3, width, BUTTON_HEIGHT - 4, Component.empty());
        field.setBordered(false);
        field.setTextColor(GuideTheme.TEXT_PRIMARY);
        addRenderableWidget(field);
        return field;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, GuideTheme.SCRIM);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void addLabel(String key, int x, int y) {
        this.addRenderableWidget(new LabelWidget(
                x, y + 5,
                I18nHelper.translateToString(key),
                GuideTheme.TEXT_SECONDARY, font
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

        Config.GUIDE_MODE_DEFAULT.set(guideModeDefault);
        Config.WEB_KNOWLEDGE_ENABLED.set(webKnowledgeEnabled);

        // Persist to disk
        Config.save();
    }

    @Override
    public void onClose() {
        // Deliberately no auto-save: only the Save button persists edits.
        Minecraft.getInstance().setScreen(parent);
    }

    /**
     * Mask API key: show last 4 characters, replace the rest with '*'.
     * Example: "sk-abc123xyz" → "********xyz"
     */
    private static String maskApiKey(String key) {
        if (key == null || key.isBlank()) return "";
        int maskLen = Math.max(0, key.length() - 4);
        return MASK_CHAR + "*".repeat(Math.max(0, maskLen - 1))
                + (maskLen >= key.length() ? "" : key.substring(maskLen));
    }
}

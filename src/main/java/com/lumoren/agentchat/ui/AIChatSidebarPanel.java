package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.ai.AIChatService;
import com.lumoren.agentchat.client.ClientServiceManager;
import com.lumoren.agentchat.config.ConfigManager;
import com.lumoren.agentchat.i18n.I18nHelper;
import com.lumoren.agentchat.i18n.I18nKeys;
import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.model.ConversationThread;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * JEI-style sidebar panel rendered inside the {@link net.minecraft.client.gui.screens.inventory.InventoryScreen}.
 * <p>
 * Features:
 * <ul>
 *   <li>Panel on LEFT or RIGHT side of inventory (configurable)</li>
 *   <li>Width: configurable percentage of screen (default 30%), minimum 200px</li>
 *   <li>Title bar, scrollable message list, input field, Send button, New Chat button</li>
 *   <li>Connected to {@link AIChatService} for message sending</li>
 *   <li>Semi-transparent background overlay</li>
 * </ul>
 * <p>
 * This panel does NOT extend {@link Screen} — it is a composite widget container
 * managed by {@link com.lumoren.agentchat.client.ScreenEventHandler}.
 */
public class AIChatSidebarPanel {

    private static final int PADDING = 6;
    private static final int TITLE_HEIGHT = 24;
    private static final int INPUT_HEIGHT = 18;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_WIDTH = 50;
    private static final int NEW_CHAT_BUTTON_WIDTH = 70;
    private static final int MIN_SIDEBAR_WIDTH = 200;
    private static final int SEPARATOR_COLOR = 0xFF555555;
    private static final int BG_COLOR = 0xDD1E1E2E;
    private static final int TITLE_COLOR = 0xFFFFFFFF;

    private final ConversationThread thread;
    private final StreamingChatRenderer streamer;
    private final AIChatService chatService;

    private MessageListWidget messageList;
    private EditBox inputField;
    private Button sendButton;
    private Button newChatButton;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private boolean initialized;

    /**
     * Create a new sidebar panel with a fresh conversation thread.
     */
    public AIChatSidebarPanel() {
        this.thread = new ConversationThread(I18nHelper.translateToString(I18nKeys.THREAD_NEW));
        this.chatService = ClientServiceManager.getChatService();
        this.streamer = new StreamingChatRenderer(thread, this::onStreamUpdate);
    }

    /**
     * Initialize the sidebar panel: create widgets and add them to the parent screen.
     * Called from {@link com.lumoren.agentchat.client.ScreenEventHandler} during
     * {@code ScreenEvent.Init.Post}.
     *
     * @param screen   the parent screen (typically InventoryScreen)
     * @param font     the Minecraft font
     * @param addWidget a consumer that adds widgets to the screen (e.g. event::addListener)
     */
    public void init(Screen screen, Font font, Consumer<GuiEventListener> addWidget) {
        int screenWidth = screen.width;
        int screenHeight = screen.height;

        // Calculate panel dimensions from config
        int configPercent = ConfigManager.getInstance().getSidebarWidth();
        panelWidth = Math.max(MIN_SIDEBAR_WIDTH, screenWidth * configPercent / 100);
        panelWidth = Math.min(panelWidth, screenWidth / 2); // at most half the screen

        String position = ConfigManager.getInstance().getSidebarPosition();
        boolean isRight = "RIGHT".equalsIgnoreCase(position);
        panelX = isRight ? screenWidth - panelWidth : 0;
        panelY = 0;
        panelHeight = screenHeight;

        // Title + new chat button row
        int titleBarTop = PADDING;
        int newChatX = panelX + panelWidth - NEW_CHAT_BUTTON_WIDTH - PADDING;
        this.newChatButton = Button.builder(
                        I18nHelper.translate(I18nKeys.BUTTON_NEW_THREAD),
                        this::onNewChat
                )
                .bounds(newChatX, titleBarTop, NEW_CHAT_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        addWidget.accept(newChatButton);

        // Message list area
        int listTop = titleBarTop + TITLE_HEIGHT + PADDING;
        int inputRowHeight = INPUT_HEIGHT + PADDING + BUTTON_HEIGHT + PADDING;
        int listBottom = screenHeight - inputRowHeight - PADDING;
        int listHeight = listBottom - listTop;
        int listWidth = panelWidth - PADDING * 2;

        this.messageList = new MessageListWidget(thread, streamer);
        this.messageList.setBounds(
                panelX + PADDING,
                listTop,
                listWidth,
                listHeight,
                font
        );

        // Send button (bottom right of panel)
        int sendButtonX = panelX + panelWidth - BUTTON_WIDTH - PADDING;
        int sendButtonY = listBottom + PADDING;
        this.sendButton = Button.builder(
                        I18nHelper.translate(I18nKeys.BUTTON_SEND),
                        this::onSend
                )
                .bounds(sendButtonX, sendButtonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.sendButton.active = false;
        addWidget.accept(sendButton);

        // Input field (bottom of panel, left of send button)
        int inputWidth = sendButtonX - panelX - PADDING - PADDING;
        this.inputField = new EditBox(
                font,
                panelX + PADDING,
                sendButtonY,
                inputWidth,
                INPUT_HEIGHT,
                Component.literal("")
        );
        this.inputField.setMaxLength(512);
        this.inputField.setHint(I18nHelper.translate(I18nKeys.INPUT_PLACEHOLDER));
        this.inputField.setResponder(this::onInputChanged);
        addWidget.accept(inputField);

        this.initialized = true;
    }

    /**
     * Render the sidebar panel background and message list.
     * Called from {@code ScreenEvent.Render.Post}.
     *
     * @param graphics    the GuiGraphics context
     * @param mouseX      mouse X position
     * @param mouseY      mouse Y position
     * @param partialTick partial tick time
     */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!initialized) return;

        // Draw semi-transparent background
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, BG_COLOR);

        // Draw right-edge separator if on the right, or left-edge if on the left
        boolean isRight = "RIGHT".equalsIgnoreCase(ConfigManager.getInstance().getSidebarPosition());
        int sepX = isRight ? panelX : panelX + panelWidth;
        graphics.fill(sepX, panelY, sepX + 1, panelY + panelHeight, SEPARATOR_COLOR);

        // Draw title
        Font font = Minecraft.getInstance().font;
        if (font != null) {
            graphics.drawString(
                    font,
                    I18nHelper.translate(I18nKeys.TITLE),
                    panelX + PADDING,
                    PADDING + 4,
                    TITLE_COLOR,
                    false
            );
        }

        // Render message list
        messageList.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * Handle mouse scroll events.
     *
     * @param mouseX mouse X
     * @param mouseY mouse Y
     * @param deltaX horizontal scroll delta
     * @param deltaY vertical scroll delta
     * @return true if handled
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (!initialized) return false;
        return messageList.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    /**
     * @return the EditBox for the key binding to focus
     */
    public EditBox getInputField() {
        return inputField;
    }

    /**
     * @return true if the panel has been initialized and is ready
     */
    public boolean isInitialized() {
        return initialized;
    }

    // ==================== Internal ====================

    private void onSend(Button button) {
        String text = inputField.getValue().strip();
        if (!text.isEmpty()) {
            sendMessage(text);
        }
    }

    private void onNewChat(Button button) {
        thread.clearMessages();
        inputField.setValue("");
        messageList.scrollToBottom();
    }

    private void onInputChanged(String text) {
        if (sendButton != null) {
            sendButton.active = !text.isBlank();
        }
    }

    private void onStreamUpdate() {
        if (messageList != null) {
            messageList.onMessageAdded();
        }
    }

    private void sendMessage(String text) {
        // Add user message to thread (for immediate display)
        thread.addMessage(ChatMessage.user(text));
        inputField.setValue("");
        sendButton.active = false;

        // Start streaming
        streamer.startStreaming();
        messageList.scrollToBottom();

        // Pass history EXCLUDING the just-added user message, because
        // AIChatService.sendMessage() internally adds the user message.
        List<ChatMessage> history = thread.getMessages().subList(0, thread.getMessages().size() - 1);
        chatService.sendMessage(text, history, streamer);
    }
}

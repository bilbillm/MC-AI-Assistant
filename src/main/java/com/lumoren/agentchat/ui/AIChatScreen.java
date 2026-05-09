package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.ai.AIChatService;
import com.lumoren.agentchat.client.ClientServiceManager;
import com.lumoren.agentchat.i18n.I18nHelper;
import com.lumoren.agentchat.i18n.I18nKeys;
import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.model.ConversationThread;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Full-screen AI chat overlay screen.
 * <p>
 * Opens when the user presses the backtick key outside of inventory.
 * Does not pause the game ({@link #isPauseScreen()} returns {@code false}).
 * <p>
 * Layout:
 * <pre>
 *  +------------------------------------------+
 *  |  AI Assistant                    [New]    |
 *  |                                           |
 *  |  +------ Message List Area ------------+  |
 *  |  |                                      |  |
 *  |  |  (user/ai message bubbles)           |  |
 *  |  |                                      |  |
 *  |  +--------------------------------------+  |
 *  |                                           |
 *  |  [ Input field _________________ ][Send]  |
 *  +------------------------------------------+
 * </pre>
 */
public class AIChatScreen extends Screen {

    private static final int PADDING = 8;
    private static final int INPUT_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 60;
    private static final int NEW_CHAT_BUTTON_WIDTH = 80;
    private static final int TITLE_HEIGHT = 30;

    private final ConversationThread thread;
    private final StreamingChatRenderer streamer;
    private final AIChatService chatService;

    private MessageListWidget messageList;
    private EditBox inputField;
    private Button sendButton;
    private Button newChatButton;

    /**
     * Create a new AI chat screen with a fresh conversation thread.
     */
    public AIChatScreen() {
        super(I18nHelper.translate(I18nKeys.TITLE));
        this.thread = new ConversationThread(I18nHelper.translateToString(I18nKeys.THREAD_NEW));
        this.chatService = ClientServiceManager.getChatService();
        this.streamer = new StreamingChatRenderer(thread, this::onStreamUpdate);
    }

    /**
     * Create a screen from an existing thread (for restoring conversations).
     */
    public AIChatScreen(ConversationThread existingThread) {
        super(I18nHelper.translate(I18nKeys.TITLE));
        this.thread = existingThread;
        this.chatService = ClientServiceManager.getChatService();
        this.streamer = new StreamingChatRenderer(thread, this::onStreamUpdate);
    }

    @Override
    protected void init() {
        super.init();

        int contentLeft = PADDING;
        int contentRight = width - PADDING;
        int contentWidth = width - PADDING * 2;

        // New Chat button (top right)
        int newChatX = contentRight - NEW_CHAT_BUTTON_WIDTH;
        this.newChatButton = this.addRenderableWidget(
                Button.builder(
                                I18nHelper.translate(I18nKeys.BUTTON_NEW_THREAD),
                                this::onNewChat
                        )
                        .bounds(newChatX, PADDING, NEW_CHAT_BUTTON_WIDTH, 20)
                        .build()
        );

        // Message list area (between title area and input)
        int listTop = PADDING + TITLE_HEIGHT;
        int listBottom = height - PADDING - INPUT_HEIGHT - PADDING - 20; // 20 for send row
        int listHeight = listBottom - listTop;
        int listRight = contentRight;
        int listWidth = listRight - contentLeft;

        this.messageList = new MessageListWidget(thread, streamer);
        this.messageList.setBounds(contentLeft, listTop, listWidth, listHeight, font);

        // Send button (bottom right)
        int sendButtonX = contentRight - BUTTON_WIDTH;
        int sendButtonY = height - PADDING - 20;

        this.sendButton = this.addRenderableWidget(
                Button.builder(
                                I18nHelper.translate(I18nKeys.BUTTON_SEND),
                                this::onSend
                        )
                        .bounds(sendButtonX, sendButtonY, BUTTON_WIDTH, 20)
                        .build()
        );

        // Input field (bottom left, next to send button)
        int inputWidth = sendButtonX - PADDING - contentLeft;
        this.inputField = new EditBox(
                font,
                contentLeft,
                sendButtonY,
                inputWidth,
                20,
                Component.literal("")
        );
        this.inputField.setMaxLength(512);
        this.inputField.setHint(I18nHelper.translate(I18nKeys.INPUT_PLACEHOLDER));
        this.inputField.setResponder(this::onInputChanged);
        this.addRenderableWidget(inputField);

        // Set initial focus to input
        this.setInitialFocus(inputField);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Draw a semi-transparent dark background
        graphics.fill(0, 0, width, height, 0xCC111111);

        // Draw title
        graphics.drawString(
                font,
                I18nHelper.translate(I18nKeys.TITLE),
                PADDING,
                PADDING + 6,
                0xFFFFFFFF,
                false
        );

        // Render message list
        messageList.render(graphics, mouseX, mouseY, partialTick);

        // Render widgets (buttons, edit box) - these are rendered automatically
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC closes the screen
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            this.onClose();
            return true;
        }

        // Enter sends the message
        if (keyCode == 257 || keyCode == 335) { // GLFW_KEY_ENTER or KP_ENTER
            if (inputField.isFocused() && !inputField.getValue().isBlank()) {
                sendMessage(inputField.getValue());
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (messageList.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    // ==================== Event handlers ====================

    private void onSend(Button button) {
        String text = inputField.getValue().strip();
        if (!text.isEmpty()) {
            sendMessage(text);
        }
    }

    private void onNewChat(Button button) {
        thread.clearMessages();
        inputField.setValue("");
        streamer.startStreaming(); // Reset streaming state
        messageList.scrollToBottom();
    }

    private void onInputChanged(String text) {
        sendButton.active = !text.isBlank();
    }

    private void onStreamUpdate() {
        // Called from StreamingChatRenderer when state changes
        // Triggers re-render by scheduling on the main thread
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

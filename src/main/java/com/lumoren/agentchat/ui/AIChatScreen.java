package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.ai.AIChatService;
import com.lumoren.agentchat.client.ClientServiceManager;
import com.lumoren.agentchat.i18n.I18nHelper;
import com.lumoren.agentchat.i18n.I18nKeys;
import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.model.ConversationThread;
import com.lumoren.agentchat.persistence.ConversationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;

/**
 * AI chat screen with persistent conversation history.
 * Left sidebar: ThreadListWidget (collapsible). Right: chat + input.
 */
public class AIChatScreen extends Screen {

    private static final int PADDING = 8;
    private static final int INPUT_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 50;
    private static final int TITLE_HEIGHT = 20;

    private ConversationThread thread;
    private StreamingChatRenderer streamer;
    private final AIChatService chatService;

    private ThreadListWidget threadList;
    private MessageListWidget messageList;
    private EditBox inputField;
    private Button sendButton;
    private Button newChatButton;
    private Button configButton;
    private LabelWidget titleLabel;

    private int lastSidebarWidth = -1; // track for dynamic layout

    public AIChatScreen() {
        super(I18nHelper.translate(I18nKeys.TITLE));
        this.chatService = ClientServiceManager.getChatService();
    }

    @Override
    protected void init() {
        super.init();

        var mgr = ConversationManager.getInstance();
        this.thread = mgr != null ? mgr.getCurrentThread() : null;
        if (this.thread == null) {
            this.thread = new ConversationThread(I18nHelper.translateToString(I18nKeys.THREAD_NEW));
        }
        this.streamer = new StreamingChatRenderer(thread, this::onStreamUpdate);

        var threads = mgr != null ? mgr.getAllThreads().stream()
                .sorted(Comparator.comparing(ConversationThread::getUpdatedAt).reversed()).toList()
                : List.<ConversationThread>of();

        // Thread list sidebar (widget pipeline — crisp text)
        this.threadList = new ThreadListWidget(this::switchToThread);
        this.threadList.refresh(threads, thread.getId(), height, font);
        this.addRenderableWidget(threadList);

        int chatLeft = PADDING + threadList.getEffectiveWidth() + PADDING;
        int chatRight = width - PADDING;

        // Title
        this.titleLabel = new LabelWidget(chatLeft, PADDING + 2,
                I18nHelper.translateToString(I18nKeys.TITLE), 0xFFFFFFFF, font);
        this.addRenderableWidget(this.titleLabel);

        // New Chat & Config buttons
        this.newChatButton = this.addRenderableWidget(Button.builder(
                        I18nHelper.translate(I18nKeys.BUTTON_NEW_THREAD), this::onNewChat)
                .bounds(chatRight - 140, PADDING, 70, 18).build());
        this.configButton = this.addRenderableWidget(Button.builder(
                        I18nHelper.translate(I18nKeys.BUTTON_CONFIG), this::onOpenConfig)
                .bounds(chatRight - 65, PADDING, 55, 18).build());

        // Message list
        int listTop = PADDING + TITLE_HEIGHT + PADDING;
        int listBottom = height - PADDING - INPUT_HEIGHT - PADDING - 20;
        this.messageList = new MessageListWidget(thread, streamer);
        this.messageList.setBounds(chatLeft, listTop, chatRight - chatLeft, listBottom - listTop, font);
        this.addRenderableWidget(messageList);

        // Input + Send
        int sendX = chatRight - BUTTON_WIDTH;
        int inputY = height - PADDING - 20;
        this.sendButton = this.addRenderableWidget(Button.builder(
                        I18nHelper.translate(I18nKeys.BUTTON_SEND), this::onSend)
                .bounds(sendX, inputY, BUTTON_WIDTH, 20).build());
        this.sendButton.active = false;

        this.inputField = new EditBox(font, chatLeft, inputY,
                sendX - chatLeft - PADDING, 20, Component.literal(""));
        this.inputField.setMaxLength(512);
        this.inputField.setHint(I18nHelper.translate(I18nKeys.INPUT_PLACEHOLDER));
        this.inputField.setResponder(this::onInputChanged);
        this.addRenderableWidget(inputField);
        this.setInitialFocus(inputField);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dynamic layout: reposition chat widgets when sidebar collapses/expands
        int currentSidebarW = threadList.getEffectiveWidth();
        if (currentSidebarW != lastSidebarWidth) {
            repositionForSidebar(currentSidebarW);
            lastSidebarWidth = currentSidebarW;
        }

        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0xCC111111);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // ==================== Thread switching ====================

    private void switchToThread(ConversationThread t) {
        if (t.getId().equals(thread.getId())) return;
        var mgr = ConversationManager.getInstance();
        if (mgr != null) mgr.save();
        thread = t;
        streamer = new StreamingChatRenderer(thread, this::onStreamUpdate);
        rebuildLayout();
    }

    private void rebuildLayout() {
        lastSidebarWidth = -1; // force reposition on next render
        this.clearWidgets();
        this.children().clear();
        this.renderables.clear();
        init();
    }

    /** Reposition chat-area widgets when sidebar width changes. */
    private void repositionForSidebar(int sidebarW) {
        int chatLeft = PADDING + sidebarW + PADDING;
        int chatRight = width - PADDING;
        int listTop = PADDING + TITLE_HEIGHT + PADDING;
        int listBottom = height - PADDING - INPUT_HEIGHT - PADDING - 20;
        int sendX = chatRight - BUTTON_WIDTH;
        int inputY = height - PADDING - 20;

        // Title
        if (titleLabel != null) titleLabel.setX(chatLeft);
        // New Chat & Config buttons
        if (newChatButton != null) newChatButton.setX(chatRight - 140);
        if (configButton != null) configButton.setX(chatRight - 65);
        // Message list
        if (messageList != null) {
            messageList.setX(chatLeft);
            messageList.setWidth(chatRight - chatLeft);
        }
        // Input
        if (inputField != null) {
            inputField.setX(chatLeft);
            inputField.setWidth(sendX - chatLeft - PADDING);
        }
        // Send button
        if (sendButton != null) sendButton.setX(sendX);
    }

    private void refreshThreadList() {
        var mgr = ConversationManager.getInstance();
        if (mgr != null) {
            threadList.refresh(mgr.getAllThreads().stream()
                    .sorted(Comparator.comparing(ConversationThread::getUpdatedAt).reversed()).toList(),
                    thread.getId(), height, font);
        }
    }

    // ==================== Event handlers ====================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { this.onClose(); return true; }
        if (keyCode == 257 || keyCode == 335) {
            if (inputField.isFocused() && !inputField.getValue().isBlank()) {
                sendMessage(inputField.getValue());
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        var mgr = ConversationManager.getInstance();
        if (mgr != null) mgr.save();
        Minecraft.getInstance().setScreen(null);
    }

    private void onSend(Button b) {
        String text = inputField.getValue().strip();
        if (!text.isEmpty()) sendMessage(text);
    }

    private void onNewChat(Button b) {
        var mgr = ConversationManager.getInstance();
        if (mgr != null) {
            mgr.save();
            switchToThread(mgr.createThread());
        } else {
            thread = new ConversationThread(I18nHelper.translateToString(I18nKeys.THREAD_NEW));
            streamer = new StreamingChatRenderer(thread, this::onStreamUpdate);
            rebuildLayout();
        }
        refreshThreadList();
    }

    private void onOpenConfig(Button b) {
        var mgr = ConversationManager.getInstance();
        if (mgr != null) mgr.save();
        Minecraft.getInstance().setScreen(new ConfigScreen(this));
    }

    private void onInputChanged(String text) { sendButton.active = !text.isBlank(); }
    private void onStreamUpdate() { if (messageList != null) messageList.onMessageAdded(); }

    private void sendMessage(String text) {
        thread.addMessage(ChatMessage.user(text));
        inputField.setValue("");
        sendButton.active = false;
        streamer.startStreaming();
        messageList.scrollToBottom();
        autoSave();
        List<ChatMessage> history = thread.getMessages().subList(0, thread.getMessages().size() - 1);
        chatService.sendMessage(text, history, streamer);
        refreshThreadList();
    }

    private void autoSave() {
        var mgr = ConversationManager.getInstance();
        if (mgr != null) mgr.save();
    }
}

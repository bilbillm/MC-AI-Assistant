package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.ai.AIChatService;
import com.lumoren.agentchat.ai.ProjectPlanningService;
import com.lumoren.agentchat.ai.TestSuite;
import com.lumoren.agentchat.client.ClientServiceManager;
import com.lumoren.agentchat.client.JeiGuiBridge;
import com.lumoren.agentchat.config.ConfigManager;
import com.lumoren.agentchat.guide.ModpackGuideService;
import com.lumoren.agentchat.i18n.I18nHelper;
import com.lumoren.agentchat.i18n.I18nKeys;
import com.lumoren.agentchat.model.AssistantAction;
import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.model.ConversationThread;
import com.lumoren.agentchat.model.ItemRequirement;
import com.lumoren.agentchat.model.Project;
import com.lumoren.agentchat.model.Task;
import com.lumoren.agentchat.model.TaskStatus;
import com.lumoren.agentchat.model.TaskType;
import com.lumoren.agentchat.persistence.ConversationManager;
import com.lumoren.agentchat.persistence.ProjectManager;
import com.lumoren.agentchat.ui.action.ActionExecutionResult;
import com.lumoren.agentchat.ui.action.AssistantActionExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI chat screen — Create-inspired layout.
 * Left: collapsible thread list. Right: chat messages + input.
 * Layout recalculated via init() when sidebar toggles.
 */
public class AIChatScreen extends Screen {

    private ConversationThread thread;
    private StreamingChatRenderer streamer;
    private final AIChatService chatService;

    private ThreadListWidget threadList;
    private MessageListWidget messageList;
    private EditBox inputField;
    private com.lumoren.agentchat.ui.widget.MechanicalIconButton sendButton;
    private com.lumoren.agentchat.ui.widget.ModeSegmentedWidget modeSegmented;
    private com.lumoren.agentchat.ui.widget.SourceRailWidget sourceRail;
    private com.lumoren.agentchat.ui.layout.ChatLayout currentLayout;
    private boolean rebuilding;
    private boolean pendingCollapsed;
    private int savedScroll = -1; // preserve scroll across rebuild
    private String firstUserMessage; // for auto-rename after first AI response
    private static boolean globalSidebarCollapsed = false; // persist across screen opens
    private static int savedMessageScroll = 0; // persist message list scroll across screen opens
    private boolean sending; // guard against concurrent message sends
    private boolean autoRenamePending; // prevent multiple auto-rename calls
    private long autoRenameStartTime; // timeout guard for auto-rename
    private String selectedMessageUuid; // right-click selected message for recall/edit
    private String pendingEditMessageUuid; // UUID of message being deep-edited
    private java.util.Set<String> expandedReasonings = new java.util.HashSet<>(); // persist across rebuildLayout
    private boolean guideMode;
    private final ModpackGuideService guideService = new ModpackGuideService();
    private final AssistantActionExecutor actionExecutor =
            new AssistantActionExecutor(Minecraft::getInstance, new JeiGuiBridge());
    private final com.lumoren.agentchat.guide.GuideRequestCoordinator guideCoordinator =
            ClientServiceManager.getGuideCoordinator();
    private long activeGuideRequestId = -1;

    // ==================== Project system ====================
    private boolean projectJustCompleted;
    private boolean pendingProjectCreation;
    private String pendingProjectName;
    private List<Task> pendingTasks;
    private String pendingPlanningPrompt; // injected into API call, NOT displayed in UI

    public AIChatScreen() {
        super(I18nHelper.translate(I18nKeys.TITLE));
        this.chatService = ClientServiceManager.getChatService();
        this.guideMode = ConfigManager.getInstance().isGuideModeDefault();
    }

    @Override
    protected void init() {
        super.init();

        firstUserMessage = null; // reset for new conversation
        selectedMessageUuid = null; // reset recall/edit selection
        pendingEditMessageUuid = null;

        var mgr = ConversationManager.getInstance();
        this.thread = mgr != null ? mgr.getCurrentThread() : null;
        if (this.thread == null) {
            this.thread = new ConversationThread(I18nHelper.translateToString(I18nKeys.THREAD_NEW));
        }
        this.streamer = new StreamingChatRenderer(thread, this::onStreamUpdate);

        var threads = mgr != null ? mgr.getAllThreads().stream()
                .sorted(Comparator.comparing(ConversationThread::getUpdatedAt).reversed()).toList()
                : List.<ConversationThread>of();

        boolean expanded = !(globalSidebarCollapsed || pendingCollapsed);
        pendingCollapsed = false;
        int tier = com.lumoren.agentchat.ui.layout.ChatLayout.tierOf(width);
        int toolbarButtons = tier == com.lumoren.agentchat.ui.layout.ChatLayout.TIER_NARROW ? 3 : 4;
        int segmentedWidth = measureSegmentedWidth();
        com.lumoren.agentchat.ui.layout.ChatLayout layout = com.lumoren.agentchat.ui.layout.ChatLayout
                .compute(width, height, expanded, segmentedWidth, toolbarButtons);
        this.currentLayout = layout;

        // Background panel (decorative, non-interactive)
        this.addRenderableWidget(new com.lumoren.agentchat.ui.widget.MechanicalPanelWidget(
                layout.contentX(), layout.contentY(), layout.contentW(), layout.contentH(),
                com.lumoren.agentchat.ui.theme.MechanicalSprites.PANEL_IRON, true));

        // Message list
        this.messageList = new MessageListWidget(thread, streamer, expandedReasonings);
        this.messageList.setOnRecall(this::onRecallMessage);
        this.messageList.setOnEdit(this::onEditMessage);
        this.messageList.setOnCopy(this::onCopyMessage);
        this.messageList.setOnAction(this::onAssistantAction);
        this.messageList.setBounds(layout.messageX(), layout.messageY(),
                layout.messageW(), layout.messageH(), font);
        this.messageList.setScrollOffset(savedMessageScroll);
        this.addRenderableWidget(messageList);

        // Sidebar — renders after the message list so the overlay drawer
        // (compact/narrow tiers) draws on top. Collapse triggers rebuildLayout().
        this.threadList = new ThreadListWidget(this::switchToThread, this::rebuildLayout);
        this.threadList.refresh(threads, thread.getId(), font);
        this.threadList.setBounds(layout.sidebarX(), layout.sidebarY(),
                layout.sidebarW(), layout.sidebarH());
        this.threadList.setCollapsed(!expanded, false);
        this.threadList.visible = layout.sidebarW() > 0;
        this.addRenderableWidget(threadList);

        // Title
        this.addRenderableWidget(new LabelWidget(layout.titleX(),
                layout.titleY() + (layout.titleH() - font.lineHeight) / 2,
                I18nHelper.translateToString(I18nKeys.TITLE),
                com.lumoren.agentchat.ui.theme.GuideTheme.TEXT_PRIMARY, font));

        // Mode segmented control (Chat | Guide)
        this.modeSegmented = this.addRenderableWidget(
                new com.lumoren.agentchat.ui.widget.ModeSegmentedWidget(font,
                        layout.segmentedX(), layout.segmentedY(),
                        layout.segmentedW(), layout.segmentedH(),
                        I18nHelper.translate(I18nKeys.MODE_CHAT),
                        I18nHelper.translate(I18nKeys.MODE_GUIDE),
                        guideMode ? com.lumoren.agentchat.ui.widget.ModeSegmentedWidget.Mode.GUIDE
                                : com.lumoren.agentchat.ui.widget.ModeSegmentedWidget.Mode.CHAT,
                        this::onModeChanged));

        // Toolbar icon buttons (right-aligned)
        int bx = layout.toolbarX();
        if (tier == com.lumoren.agentchat.ui.layout.ChatLayout.TIER_NARROW) {
            bx = addToolbarIcon(bx, com.lumoren.agentchat.ui.theme.MechanicalSprites.ICON_HISTORY,
                    I18nKeys.TOOLTIP_HISTORY, b -> onToggleSidebar());
        } else {
            bx = addToolbarIcon(bx, com.lumoren.agentchat.ui.theme.MechanicalSprites.ICON_PLUS,
                    I18nKeys.TOOLTIP_NEW_THREAD, b -> onNewChat());
        }
        bx = addToolbarIcon(bx, com.lumoren.agentchat.ui.theme.MechanicalSprites.ICON_SETTINGS,
                I18nKeys.TOOLTIP_CONFIG, b -> onOpenConfig());
        if (tier != com.lumoren.agentchat.ui.layout.ChatLayout.TIER_NARROW) {
            bx = addToolbarIcon(bx, com.lumoren.agentchat.ui.theme.MechanicalSprites.ICON_TRASH,
                    I18nKeys.TOOLTIP_DELETE_THREAD, com.lumoren.agentchat.ui.theme.GuideTheme.DANGER_RED,
                    b -> onDeleteThread());
        }
        addToolbarIcon(bx, com.lumoren.agentchat.ui.theme.MechanicalSprites.ICON_CLOSE,
                I18nKeys.TOOLTIP_CLOSE, b -> onClose());

        // Source status rail
        if (layout.sourceRailVisible()) {
            this.sourceRail = new com.lumoren.agentchat.ui.widget.SourceRailWidget(font,
                    layout.sourceRailX(), layout.sourceRailY(),
                    layout.contentW() - 8, layout.sourceRailH());
            this.sourceRail.setSources(computeSourceStatuses());
            this.sourceRail.setLoading(activeGuideRequestId >= 0);
            this.addRenderableWidget(sourceRail);
        } else {
            this.sourceRail = null;
        }

        // Input console: inset plate + borderless editbox + send icon
        this.addRenderableWidget(new com.lumoren.agentchat.ui.widget.MechanicalPanelWidget(
                layout.contentX(), layout.inputY(), layout.contentW(), layout.inputH(),
                com.lumoren.agentchat.ui.theme.MechanicalSprites.PANEL_INSET, false));
        this.sendButton = new com.lumoren.agentchat.ui.widget.MechanicalIconButton(font,
                layout.sendX(), layout.sendY(), layout.sendH(), layout.sendH(),
                com.lumoren.agentchat.ui.theme.MechanicalSprites.ICON_SEND,
                I18nHelper.translate(I18nKeys.TOOLTIP_SEND),
                com.lumoren.agentchat.ui.theme.GuideTheme.BRASS_HIGHLIGHT,
                b -> onSend());
        this.sendButton.active = false;
        this.addRenderableWidget(sendButton);

        this.inputField = new EditBox(font, layout.inputX(), layout.inputY(),
                layout.inputW(), 18, Component.literal(""));
        this.inputField.setBordered(false);
        this.inputField.setTextColor(com.lumoren.agentchat.ui.theme.GuideTheme.TEXT_PRIMARY);
        this.inputField.setMaxLength(512);
        this.inputField.setHint(I18nHelper.translate(inputPlaceholderKey()));
        this.inputField.setResponder(this::onInputChanged);
        this.addRenderableWidget(inputField);
        this.setInitialFocus(inputField);
    }

    private String inputPlaceholderKey() {
        return guideMode ? I18nKeys.INPUT_PLACEHOLDER_GUIDE : I18nKeys.INPUT_PLACEHOLDER;
    }

    private int measureSegmentedWidth() {
        int chat = font.width(I18nHelper.translate(I18nKeys.MODE_CHAT));
        int guide = font.width(I18nHelper.translate(I18nKeys.MODE_GUIDE));
        return Math.max(72, Math.max(chat, guide) * 2 + 28);
    }

    private int addToolbarIcon(int x, net.minecraft.resources.ResourceLocation icon, String tooltipKey,
                               com.lumoren.agentchat.ui.widget.MechanicalIconButton.OnPress onPress) {
        return addToolbarIcon(x, icon, tooltipKey,
                com.lumoren.agentchat.ui.theme.GuideTheme.BRASS_HIGHLIGHT, onPress);
    }

    private int addToolbarIcon(int x, net.minecraft.resources.ResourceLocation icon, String tooltipKey,
                               int hoverColor,
                               com.lumoren.agentchat.ui.widget.MechanicalIconButton.OnPress onPress) {
        this.addRenderableWidget(new com.lumoren.agentchat.ui.widget.MechanicalIconButton(font,
                x, currentLayout.toolbarY(), currentLayout.toolbarH(), currentLayout.toolbarH(),
                icon, I18nHelper.translate(tooltipKey), hoverColor, onPress));
        return x + currentLayout.toolbarH() + 2;
    }

    private java.util.List<com.lumoren.agentchat.ui.widget.SourceRailWidget.SourceStatus> computeSourceStatuses() {
        Minecraft mc = Minecraft.getInstance();
        boolean local = mc != null && mc.player != null;
        boolean jei = com.lumoren.agentchat.AgentChat.hasJei();
        boolean ftb = isModLoaded("ftbquests");
        boolean patchouli = isModLoaded("patchouli");
        boolean web = ConfigManager.getInstance().isWebKnowledgeEnabled();
        return java.util.List.of(
                sourceStatus("LOCAL", local),
                sourceStatus("JEI", jei),
                sourceStatus("FTB", ftb),
                sourceStatus("PATCH", patchouli),
                sourceStatus("WEB", web));
    }

    private static com.lumoren.agentchat.ui.widget.SourceRailWidget.SourceStatus sourceStatus(
            String label, boolean available) {
        return new com.lumoren.agentchat.ui.widget.SourceRailWidget.SourceStatus(label,
                available ? com.lumoren.agentchat.ui.widget.SourceRailWidget.Status.AVAILABLE
                        : com.lumoren.agentchat.ui.widget.SourceRailWidget.Status.DISABLED);
    }

    private static boolean isModLoaded(String modId) {
        try {
            return net.neoforged.fml.ModList.get() != null && net.neoforged.fml.ModList.get().isLoaded(modId);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void onModeChanged(com.lumoren.agentchat.ui.widget.ModeSegmentedWidget.Mode mode) {
        // Mode is a session-only UI state; never pollute the persisted
        // conversation history with mode-switch system messages.
        this.guideMode = mode == com.lumoren.agentchat.ui.widget.ModeSegmentedWidget.Mode.GUIDE;
        if (inputField != null) {
            inputField.setHint(I18nHelper.translate(inputPlaceholderKey()));
        }
    }

    private void onToggleSidebar() {
        globalSidebarCollapsed = !globalSidebarCollapsed;
        pendingCollapsed = globalSidebarCollapsed;
        rebuildLayout();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, com.lumoren.agentchat.ui.theme.GuideTheme.SCRIM);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // ==================== Thread switching ====================

    private void switchToThread(ConversationThread t) {
        if (t.getId().equals(thread.getId())) return;
        cancelPendingGuideRequest();
        sending = false; // a cancelled guide request never completes — unblock input
        var mgr = ConversationManager.getInstance();
        if (mgr != null) {
            mgr.save();
            mgr.switchThread(t.getId()); // persist the switch
        }
        thread = t;
        streamer = new StreamingChatRenderer(thread, this::onStreamUpdate);
        rebuildLayout();
    }

    private void cancelPendingGuideRequest() {
        if (activeGuideRequestId >= 0) {
            guideCoordinator.cancel(activeGuideRequestId);
            activeGuideRequestId = -1;
        }
    }

    private void rebuildLayout() {
        if (rebuilding) return;
        rebuilding = true;
        try {
            pendingCollapsed = threadList != null && threadList.isCollapsed();
            globalSidebarCollapsed = pendingCollapsed; // sync persistent state
            savedScroll = threadList != null ? threadList.getScroll() : 0;
            this.clearWidgets();
            this.children().clear();
            this.renderables.clear();
            init();
            if (savedScroll >= 0 && threadList != null) {
                threadList.setScroll(savedScroll);
            }
        } finally {
            rebuilding = false;
        }
    }

    private void refreshThreadList() {
        var mgr = ConversationManager.getInstance();
        if (mgr != null && threadList != null) {
            threadList.refresh(mgr.getAllThreads().stream()
                    .sorted(Comparator.comparing(ConversationThread::getUpdatedAt).reversed()).toList(),
                    thread.getId(), font);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Overlay drawer: clicks inside it go straight to the list (the
        // message list overlaps its bounds on compact/narrow tiers), clicks
        // outside dismiss it.
        if (threadList != null && threadList.visible && isSidebarOverlayOpen()) {
            boolean inside = mx >= threadList.getX() && mx <= threadList.getX() + threadList.getWidth()
                    && my >= threadList.getY() && my <= threadList.getY() + threadList.getHeight();
            if (inside) {
                if (threadList.mouseClicked(mx, my, button)) {
                    return true;
                }
            } else {
                onToggleSidebar();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private boolean isSidebarOverlayOpen() {
        return currentLayout != null
                && currentLayout.tier() != com.lumoren.agentchat.ui.layout.ChatLayout.TIER_WIDE
                && !globalSidebarCollapsed;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (threadList != null && threadList.mouseScrolled(mx, my, sx, sy)) return true;
        if (messageList != null && messageList.mouseScrolled(mx, my, sx, sy)) return true;
        return super.mouseScrolled(mx, my, sx, sy);
    }

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

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        cancelPendingGuideRequest();
        var mgr = ConversationManager.getInstance();
        if (mgr != null) mgr.save();
        if (messageList != null) savedMessageScroll = messageList.getScrollOffset();
        Minecraft.getInstance().setScreen(null);
    }

    private void onSend() {
        String text = inputField.getValue().strip();
        if (!text.isEmpty()) sendMessage(text);
    }

    private void onNewChat() {
        cancelPendingGuideRequest();
        sending = false;
        var mgr = ConversationManager.getInstance();
        if (mgr != null) {
            mgr.save();
            switchToThread(mgr.createThread());
        } else {
            thread = new ConversationThread(I18nHelper.translateToString(I18nKeys.THREAD_NEW));
            streamer = new StreamingChatRenderer(thread, this::onStreamUpdate);
            rebuildLayout();
        }
        firstUserMessage = null;
        refreshThreadList();
    }

    private void onOpenConfig() {
        var mgr = ConversationManager.getInstance();
        if (mgr != null) mgr.save();
        Minecraft.getInstance().setScreen(new ConfigScreen(this));
    }

    private void onDeleteThread() {
        var mgr = ConversationManager.getInstance();
        if (mgr == null) return;
        if (thread != null) {
            cancelPendingGuideRequest();
            sending = false;
            mgr.deleteThread(thread.getId());
            thread = mgr.getCurrentThread();
            if (thread == null) thread = mgr.createThread();
            streamer = new StreamingChatRenderer(thread, this::onStreamUpdate);
            firstUserMessage = null;
            rebuildLayout();
            refreshThreadList();
        }
    }

    private void onInputChanged(String text) { sendButton.active = !text.isBlank(); }
    private void onStreamUpdate() {
        if (messageList != null) messageList.onMessageAdded();

        // Reset sending guard when stream completes or errors
        if (streamer.isCompleted() || streamer.hasError()) {
            sending = false;
        }

        // Check if we need to parse project creation response from AI
        if (streamer.isCompleted() && !streamer.hasError() && pendingProjectCreation) {
            pendingProjectCreation = false;
            tryParseProjectFromLastResponse();
        }

        // Check project completion (all tasks DONE → archive + celebrate)
        checkProjectCompletion();

        // Auto-rename after first AI response: ask AI for a concise title
        if (firstUserMessage != null && streamer.isCompleted() && !streamer.hasError() && !autoRenamePending) {
            String msg = firstUserMessage;
            firstUserMessage = null;
            autoRenamePending = true;
            autoRenameStartTime = System.currentTimeMillis();
            // Generate title via AI in background
            chatService.sendMessage(
                "Generate a concise title (max 15 chars) for a conversation that starts with: \"" + msg + "\". Reply with ONLY the title, no quotes or extra text.",
                List.of(),
                new com.lumoren.agentchat.ai.AIChatService.ChatCallback() {
                    @Override
                    public void onToken(String token) {
                        // Timeout: abort auto-rename if it takes too long
                        if (System.currentTimeMillis() - autoRenameStartTime > 5000) {
                            autoRenamePending = false;
                        }
                    }
                    @Override public void onThinking(String s) {}
                    @Override public void onError(String e) {
                        autoRenamePending = false;
                    }
                    @Override public void onComplete(String title) {
                        autoRenamePending = false;
                        if (title != null && !title.isBlank()) {
                            thread.setName(title.strip().replace("\"", ""));
                            refreshThreadList();
                            autoSave();
                        }
                    }
                }
            );
        }
    }

    private void sendMessage(String text) {
        // Guard against concurrent message sends (rapid Enter presses)
        if (sending) return;

        // Handle built-in test suite (debug mode only)
        if (AIChatService.isDebugMode() && text.strip().equals("运行测试清单")) {
            sending = true;
            inputField.setValue("");
            sendButton.active = false;
            thread.addMessage(ChatMessage.system("[TEST SUITE STARTED]"));
            List<ChatMessage> testHistory = new ArrayList<>();
            testHistory.add(ChatMessage.system(TestSuite.TEST_SUITE_PROMPT));
            testHistory.add(ChatMessage.user("Run all tests now. Execute each test by calling tools. Report results."));
            streamer.startStreaming();
            messageList.resetAutoScroll();
            autoSave();
            chatService.sendMessage("Run all tests now", testHistory, streamer);
            return;
        }

        // Handle debug commands locally (when debug mode is active)
        if (handleDebugCommand(text)) {
            return;
        }

        // Handle project edit commands locally (don't send to AI)
        if (handleEditCommand(text)) {
            return;
        }

        // Handle project cancellation
        if (handleCancelProject(text)) {
            return;
        }

        // Handle project creation confirmation locally
        if (handleConfirmation(text)) {
            return;
        }

        // If pending tasks exist but user typed something else, clear them
        if (pendingTasks != null) {
            pendingTasks = null;
            pendingProjectName = null;
        }

        sending = true;

        // Deep edit pre-amble: truncate at the pending edit message, abort streaming
        if (pendingEditMessageUuid != null) {
            int editIndex = thread.findMessageIndex(pendingEditMessageUuid);
            if (editIndex >= 0) {
                if (streamer.isStreaming() || !streamer.isCompleted()) {
                    chatService.cancel();
                    streamer.abort();
                }
                thread.removeMessagesFrom(editIndex);
            }
            pendingEditMessageUuid = null;
        }

        // Check project intent (may inject system message into thread)
        checkProjectIntent(text);

        // Track first message for auto-rename
        if (thread.messageCount() == 0) {
            firstUserMessage = text;
        }
        thread.addMessage(ChatMessage.user(text));
        inputField.setValue("");
        sendButton.active = false;
        streamer.startStreaming();
        messageList.resetAutoScroll();
        autoSave();

        if (guideService.shouldEnhance(text, guideMode)) {
            // Async guide pipeline: snapshot on this thread, file/web work on
            // background executor, then completeSend on the main thread.
            ConversationThread requestThread = thread;
            boolean webEnabled = ConfigManager.getInstance().isWebKnowledgeEnabled();
            if (sourceRail != null) {
                sourceRail.setLoading(true);
            }
            activeGuideRequestId = guideCoordinator.request(Minecraft.getInstance(), text, guideMode,
                    webEnabled, new com.lumoren.agentchat.guide.GuideRequestCoordinator.Callback() {
                        @Override
                        public void onPhase(com.lumoren.agentchat.guide.GuideRequestCoordinator.Phase phase) {
                            streamer.onThinking(switch (phase) {
                                case COLLECTING_LOCAL -> I18nHelper.translateToString(I18nKeys.GUIDE_PHASE_COLLECTING);
                                case QUERYING_SOURCES -> I18nHelper.translateToString(I18nKeys.GUIDE_PHASE_QUERYING);
                                case SEARCHING_WEB -> I18nHelper.translateToString(I18nKeys.GUIDE_PHASE_WEB);
                            });
                        }

                        @Override
                        public void onReady(long requestId, String guideSystemPrompt) {
                            completeSend(text, guideSystemPrompt, requestId, requestThread);
                        }

                        @Override
                        public void onFailed(long requestId, String reason) {
                            completeSend(text, null, requestId, requestThread);
                        }
                    });
            return;
        }

        completeSend(text, null, -1, thread);
    }

    /**
     * Final send stage: augments API history with pending planning/guide
     * prompts and hands off to the chat service. Stale guide callbacks
     * (cancelled, superseded, or from a different conversation thread) are
     * dropped here.
     */
    private void completeSend(String text, String guidePrompt, long requestId,
                              ConversationThread requestThread) {
        if (requestId >= 0 && requestId != activeGuideRequestId) return; // superseded
        if (thread != requestThread) return; // thread switched while gathering
        activeGuideRequestId = -1;
        if (sourceRail != null) {
            sourceRail.setLoading(false);
        }

        List<ChatMessage> history = thread.getMessages().subList(0, thread.getMessages().size() - 1);

        // Inject pending project planning prompt into API history (not visible in UI)
        if (pendingPlanningPrompt != null) {
            List<ChatMessage> augmented = new java.util.ArrayList<>(history);
            augmented.add(ChatMessage.system(pendingPlanningPrompt));
            history = augmented;
            pendingPlanningPrompt = null;
        }
        if (guidePrompt != null) {
            List<ChatMessage> augmented = new java.util.ArrayList<>(history);
            augmented.add(ChatMessage.system(guidePrompt));
            history = augmented;
        }

        chatService.sendMessage(text, history, streamer);
        refreshThreadList();
    }

    private void autoSave() {
        var mgr = ConversationManager.getInstance();
        if (mgr != null) mgr.save();
    }

    // ==================== Recall / Edit ====================

    /** Recall: truncate conversation at the given message position. */
    private void onRecallMessage(String messageUuid) {
        int index = thread.findMessageIndex(messageUuid);
        if (index < 0) return;

        // Abort any in-flight streaming to prevent thread corruption
        if (streamer.isStreaming() || !streamer.isCompleted()) {
            chatService.cancel();
            streamer.abort();
            sending = false;
        }
        cancelPendingGuideRequest();

        thread.removeMessagesFrom(index);
        selectedMessageUuid = null;
        pendingEditMessageUuid = null;
        expandedReasonings.clear();
        streamer = new StreamingChatRenderer(thread, this::onStreamUpdate);
        autoSave();
        rebuildLayout();
        refreshThreadList();
    }

    /** Edit: fill input with old message text, prepare deep edit on submit. */
    private void onEditMessage(String messageUuid) {
        int index = thread.findMessageIndex(messageUuid);
        if (index < 0) return;

        ChatMessage msg = thread.getMessages().get(index);
        pendingEditMessageUuid = messageUuid;
        inputField.setValue(msg.content());
        inputField.moveCursorToEnd(false);
        inputField.setFocused(true);
        sendButton.active = true;
        selectedMessageUuid = null;
    }

    /** Copy message text to system clipboard. */
    private void onCopyMessage(String messageUuid) {
        int index = thread.findMessageIndex(messageUuid);
        if (index < 0) return;
        ChatMessage msg = thread.getMessages().get(index);
        Minecraft.getInstance().keyboardHandler.setClipboard(msg.content());
    }

    private void onAssistantAction(AssistantAction action) {
        ActionExecutionResult result = actionExecutor.execute(action, actionContext);
        if (result.status() == ActionExecutionResult.Status.FAILURE && !result.message().isBlank()) {
            thread.addMessage(ChatMessage.system(result.message()));
            refreshProjectUI();
            autoSave();
        }
    }

    private final AssistantActionExecutor.ActionContext actionContext =
            new AssistantActionExecutor.ActionContext() {
                @Override
                public void appendToolResult(String content) {
                    AIChatScreen.this.appendToolResult(content);
                }

                @Override
                public void sendFollowUp(String query) {
                    sendMessage(query);
                }

                @Override
                public void showSystemMessage(String message) {
                    thread.addMessage(ChatMessage.system(message));
                    refreshProjectUI();
                    autoSave();
                }
            };

    private void appendToolResult(String content) {
        thread.addMessage(new ChatMessage("tool", content, null, null, null, null));
        refreshProjectUI();
        autoSave();
    }

    // ==================== Project System ====================

    /**
     * Detect project creation intent from user message.
     * If a keyword is detected, injects a system message asking the AI to
     * produce a JSON task chain and sets {@link #pendingProjectCreation}.
     */
    private void checkProjectIntent(String userMessage) {
        if (userMessage.matches(".*(我要做|我想做|I want to make|I want to build|帮我规划).*")) {
            pendingProjectCreation = true;
            String cleanGoal = userMessage.replaceAll(
                "(我要做|我想做|I want to make|I want to build|帮我规划)", "").strip();
            if (cleanGoal.isEmpty()) {
                cleanGoal = "New Project";
            }
            pendingProjectName = cleanGoal;

            // Store planning prompt for API injection — do NOT add to ConversationThread
            // to prevent the raw system prompt from appearing in the chat UI
            pendingPlanningPrompt = 
                "The player wants to accomplish: \"" + cleanGoal + "\". "
                + "Use the 'manage_project' tool with action='create_project' to create a project "
                + "breaking this down into 3-8 steps. Each step should have: "
                + "description (what to do), type (CRAFT|GATHER|GO_TO|USE|KILL|PLAN), "
                + "and items array with itemId and count where applicable. "
                + "IMPORTANT: call the tool, do NOT output JSON as text.";
        }
    }

    /**
     * Called externally (from ClientEventHandler tick) when all tasks are auto-completed.
     */
    public void onProjectCompleted(String projectName) {
        String msg = I18nHelper.translateToString(I18nKeys.PROJECT_COMPLETE, projectName);
        thread.addMessage(ChatMessage.system(msg));
        if (messageList != null) {
            messageList.onMessageAdded();
        }
    }

    /**
     * Check if the active project has all tasks DONE.
     * If so, archives the project and sends a celebration message.
     */
    private void checkProjectCompletion() {
        ProjectManager pm = ProjectManager.getInstance();
        if (pm == null || pm.getActiveProject() == null) {
            return;
        }

        Project project = pm.getActiveProject();
        boolean allDone = project.getTasks().stream()
            .allMatch(t -> t.status() == TaskStatus.DONE);

        if (allDone && !projectJustCompleted) {
            projectJustCompleted = true;
            String projectName = project.getName();
            pm.archiveCurrentProject();
            onProjectCompleted(projectName);
        }
    }

    /**
     * Parse the last AI assistant response for a JSON task chain.
     * If found, stores tasks as pending and posts a confirmation message.
     */
    private void tryParseProjectFromLastResponse() {
        String lastAiResponse = getLastAiResponse();
        if (lastAiResponse == null || lastAiResponse.isBlank()) {
            return;
        }

        // Extract JSON from possible markdown code blocks
        String jsonStr = extractJsonFromMarkdown(lastAiResponse);

        List<Task> tasks = ProjectPlanningService.parseTaskChain(jsonStr);
        if (tasks.isEmpty()) {
            return;
        }

        pendingTasks = tasks;

        // Auto-create the project directly — no user confirmation needed
        String projectName = pendingProjectName != null ? pendingProjectName : "Project";
        Project project = new Project(projectName);
        project.setTasks(new java.util.ArrayList<>(tasks));

        ProjectManager pm = ProjectManager.getInstance();
        if (pm != null) {
            pm.saveProject(project);
            projectJustCompleted = false;
        }

        thread.addMessage(ChatMessage.system("📋 Project \"" + projectName + "\" created with " + tasks.size() + " steps"));
        for (Task t : tasks) {
            String line = "☐ " + t.description();
            if (t.requiredItems() != null && !t.requiredItems().isEmpty()) {
                StringBuilder items = new StringBuilder("  [需要: ");
                for (int i = 0; i < t.requiredItems().size(); i++) {
                    ItemRequirement ir = t.requiredItems().get(i);
                    if (i > 0) items.append(", ");
                    String shortName = ir.itemId().contains(":")
                        ? ir.itemId().substring(ir.itemId().lastIndexOf(':') + 1)
                        : ir.itemId();
                    items.append(shortName).append(" x ").append(ir.needed());
                }
                items.append("]");
                line += items.toString();
            }
            thread.addMessage(ChatMessage.system(line));
        }
        pendingTasks = null;
        pendingProjectCreation = false;
        if (messageList != null) {
            messageList.onMessageAdded();
        }
    }

    /** @return the content of the last assistant message in the thread, or null */
    private String getLastAiResponse() {
        List<ChatMessage> messages = thread.getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("assistant".equals(messages.get(i).role())) {
                return messages.get(i).content();
            }
        }
        return null;
    }

    /** @return the index of the last assistant message in the thread, or -1 */
    private int getLastAiResponseIndex() {
        List<ChatMessage> messages = thread.getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("assistant".equals(messages.get(i).role())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Extract JSON string from markdown code blocks (```json ... ```).
     * If no code block found, returns the original text for direct parsing.
     */
    private String extractJsonFromMarkdown(String text) {
        Pattern p = Pattern.compile("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```");
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1).strip();
        }
        return text;
    }

    /**
     * Handle project cancellation commands.
     * "取消项目" / "删除项目" / "cancel project" → archives the active project.
     *
     * @return true if the text was handled as a cancel command
     */
    private boolean handleCancelProject(String text) {
        ProjectManager pm = ProjectManager.getInstance();
        if (pm == null || pm.getActiveProject() == null) {
            return false;
        }

        String t = text.strip().toLowerCase();
        if (!t.equals("取消项目") && !t.equals("删除项目")
                && !t.equals("cancel project") && !t.equals("cancel")) {
            return false;
        }

        String projectName = pm.getActiveProject().getName();
        pm.archiveCurrentProject();
        thread.addMessage(ChatMessage.system("🚫 Project \"" + projectName + "\" cancelled"));
        if (messageList != null) {
            messageList.onMessageAdded();
        }
        return true;
    }

    /**
     * Handle manual task editing commands.
     * <ul>
     *   <li>"删除第N步" — remove task at index N-1</li>
     *   <li>"把第N步改成..." — update task description at index N-1</li>
     *   <li>"添加步骤: ..." — add a new task</li>
     * </ul>
     *
     * @return true if the text was handled as an edit command
     */
    private boolean handleEditCommand(String text) {
        ProjectManager pm = ProjectManager.getInstance();
        if (pm == null || pm.getActiveProject() == null) {
            return false;
        }

        Project project = pm.getActiveProject();

        // Match "删除第N步"
        Matcher m = Pattern.compile("删除第(\\d+)步").matcher(text);
        if (m.matches()) {
            int stepNum = Integer.parseInt(m.group(1));
            int index = stepNum - 1;
            List<Task> tasks = new ArrayList<>(project.getTasks());
            if (index >= 0 && index < tasks.size()) {
                Task removed = tasks.remove(index);
                project.setTasks(tasks);
                pm.saveProject(project);
                String msg = I18nHelper.translateToString(I18nKeys.PROJECT_TASK_REMOVED, removed.description());
                thread.addMessage(ChatMessage.system(msg));
                refreshProjectUI();
            }
            return true;
        }

        // Match "把第N步改成XXX"
        m = Pattern.compile("把第(\\d+)步改成(.+)").matcher(text);
        if (m.matches()) {
            int stepNum = Integer.parseInt(m.group(1));
            String newDesc = m.group(2).strip();
            int index = stepNum - 1;
            List<Task> tasks = new ArrayList<>(project.getTasks());
            if (index >= 0 && index < tasks.size()) {
                Task oldTask = tasks.get(index);
                Task newTask = new Task(oldTask.id(), newDesc, oldTask.type(),
                    oldTask.status(), oldTask.requiredItems(), oldTask.note());
                tasks.set(index, newTask);
                project.setTasks(tasks);
                pm.saveProject(project);
                String msg = I18nHelper.translateToString(I18nKeys.PROJECT_TASK_ADDED, newDesc);
                thread.addMessage(ChatMessage.system(msg));
                refreshProjectUI();
            }
            return true;
        }

        // Match "添加步骤: XXX" or "添加步骤：XXX"
        m = Pattern.compile("添加步骤[：:](.+)").matcher(text);
        if (m.matches()) {
            String desc = m.group(1).strip();
            Task newTask = Task.of(desc, TaskType.PLAN, List.of());
            project.addTask(newTask);
            pm.saveProject(project);
            String msg = I18nHelper.translateToString(I18nKeys.PROJECT_TASK_ADDED, desc);
            thread.addMessage(ChatMessage.system(msg));
            refreshProjectUI();
            return true;
        }

        return false;
    }

    /**
     * Handle project creation confirmation ("确认", "confirm").
     * Creates the project from pending tasks and resets state.
     *
     * @return true if the text was handled as confirmation
     */
    private boolean handleConfirmation(String text) {
        if (pendingTasks == null || pendingTasks.isEmpty()) {
            return false;
        }

        String t = text.strip().toLowerCase();
        if (!t.equals("确认") && !t.equals("confirm") && !t.equals("yes") && !t.equals("是")) {
            return false;
        }

        // Create project from pending tasks
        String projectName = pendingProjectName != null ? pendingProjectName : "Project";
        Project project = new Project(projectName);
        project.setTasks(new ArrayList<>(pendingTasks));

        ProjectManager pm = ProjectManager.getInstance();
        if (pm != null) {
            pm.saveProject(project);
            // Reset completion guard since we have a fresh project
            projectJustCompleted = false;
        }

        thread.addMessage(ChatMessage.system(
            "📋 Project \"" + projectName + "\" created with " + pendingTasks.size() + " steps"));

        pendingTasks = null;
        pendingProjectName = null;
        refreshProjectUI();
        return true;
    }

    /**
     * Handle debug commands when debug mode is active.
     * <ul>
     *   <li>"show project state" — dump full project/task internals</li>
     *   <li>"simulate inventory: item1 x5, item2 x3" — pretend items exist</li>
     *   <li>"show conversation" — dump raw thread messages</li>
     *   <li>"show performance" — timing stub</li>
     * </ul>
     *
     * @return true if the text was handled as a debug command
     */
    private boolean handleDebugCommand(String text) {
        if (!AIChatService.isDebugMode()) {
            return false;
        }

        String t = text.strip().toLowerCase();

        if (t.startsWith("show project state")) {
            ProjectManager pm = ProjectManager.getInstance();
            if (pm != null && pm.getActiveProject() != null) {
                Project p = pm.getActiveProject();
                StringBuilder sb = new StringBuilder("[PROJECT DEBUG]\n");
                sb.append("Name: ").append(p.getName()).append("\n");
                sb.append("Status: ").append(p.getStatus()).append("\n");
                sb.append("Tasks: ").append(p.getTasks().size()).append("\n");
                for (int i = 0; i < p.getTasks().size(); i++) {
                    Task task = p.getTasks().get(i);
                    sb.append("  ").append(i + 1).append(". [").append(task.status()).append("] ")
                      .append(task.description()).append("\n");
                    if (task.requiredItems() != null && !task.requiredItems().isEmpty()) {
                        for (ItemRequirement ir : task.requiredItems()) {
                            sb.append("     needs: ").append(ir.itemId()).append(" x").append(ir.needed())
                              .append(" (has: ").append(ir.owned()).append(")\n");
                        }
                    }
                }
                thread.addMessage(ChatMessage.system(sb.toString()));
            } else {
                thread.addMessage(ChatMessage.system("[PROJECT DEBUG] No active project"));
            }
            refreshProjectUI();
            return true;
        }

        if (t.startsWith("simulate inventory:")) {
            int colonIdx = text.strip().indexOf(":");
            String items = text.strip().substring(colonIdx + 1).strip();
            thread.addMessage(ChatMessage.system("[SIMULATED INVENTORY] " + items));
            thread.addMessage(ChatMessage.system("Tell the AI these items exist now."));
            String prompt = "DEBUG SIMULATION: The player now HAS these items: " + items
                + ". Pretend they are in the inventory. Use this for testing.";
            thread.addMessage(ChatMessage.system(prompt));
            refreshProjectUI();
            return true;
        }

        if (t.startsWith("show conversation")) {
            StringBuilder sb = new StringBuilder("[CONVERSATION DUMP]\n");
            for (ChatMessage msg : thread.getMessages()) {
                sb.append("[").append(msg.role()).append("] ");
                if (msg.content() != null && msg.content().length() > 100) {
                    sb.append(msg.content().substring(0, 100)).append("...");
                } else {
                    sb.append(msg.content());
                }
                if (msg.toolCalls() != null) {
                    sb.append(" | tool_calls: ").append(msg.toolCalls().size());
                }
                sb.append("\n");
            }
            thread.addMessage(ChatMessage.system(sb.toString()));
            refreshProjectUI();
            return true;
        }

        if (t.startsWith("show performance")) {
            thread.addMessage(ChatMessage.system("[PERFORMANCE] Not yet implemented. Use tools to test timing."));
            refreshProjectUI();
            return true;
        }

        return false;
    }

    /** Refresh UI after project state changes. */
    private void refreshProjectUI() {
        if (messageList != null) {
            messageList.onMessageAdded();
        }
    }
}

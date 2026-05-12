package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.ai.AIChatService;
import com.lumoren.agentchat.ai.ProjectPlanningService;
import com.lumoren.agentchat.client.ClientServiceManager;
import com.lumoren.agentchat.i18n.I18nHelper;
import com.lumoren.agentchat.i18n.I18nKeys;
import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.model.ConversationThread;
import com.lumoren.agentchat.model.ItemRequirement;
import com.lumoren.agentchat.model.Project;
import com.lumoren.agentchat.model.Task;
import com.lumoren.agentchat.model.TaskStatus;
import com.lumoren.agentchat.model.TaskType;
import com.lumoren.agentchat.persistence.ConversationManager;
import com.lumoren.agentchat.persistence.ProjectManager;
import com.lumoren.agentchat.ui.theme.ChatColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
    private Button deleteButton;
    private boolean rebuilding;
    private boolean pendingCollapsed;
    private int savedScroll = -1; // preserve scroll across rebuild
    private String firstUserMessage; // for auto-rename after first AI response
    private static boolean globalSidebarCollapsed = false; // persist across screen opens
    private boolean sending; // guard against concurrent message sends
    private boolean autoRenamePending; // prevent multiple auto-rename calls
    private long autoRenameStartTime; // timeout guard for auto-rename
    private String selectedMessageUuid; // right-click selected message for recall/edit
    private String pendingEditMessageUuid; // UUID of message being deep-edited
    private java.util.Set<String> expandedReasonings = new java.util.HashSet<>(); // persist across rebuildLayout

    // ==================== Project system ====================
    private boolean projectJustCompleted;
    private boolean pendingProjectCreation;
    private String pendingProjectName;
    private List<Task> pendingTasks;
    private String pendingPlanningPrompt; // injected into API call, NOT displayed in UI

    public AIChatScreen() {
        super(I18nHelper.translate(I18nKeys.TITLE));
        this.chatService = ClientServiceManager.getChatService();
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

        // Sidebar — collapse triggers rebuildLayout()
        this.threadList = new ThreadListWidget(this::switchToThread, this::rebuildLayout);
        this.threadList.refresh(threads, thread.getId(), height, font);
        if (globalSidebarCollapsed || pendingCollapsed) {
            // Collapse without triggering rebuildLayout — this init() already calculates
            // the correct layout. Triggering rebuildLayout here causes a double-init cycle
            // that leaves stale rendering state, producing text overlap artifacts.
            threadList.setCollapsed(true, false);
            pendingCollapsed = false;
        }
        this.addRenderableWidget(threadList);

        int sidebarW = threadList.getEffectiveWidth();
        int chatLeft = PADDING + sidebarW + PADDING;
        int chatRight = width - PADDING;
        int listTop = PADDING + TITLE_HEIGHT + PADDING;
        int listBottom = height - PADDING - INPUT_HEIGHT - PADDING - 20;

        // Title
        this.addRenderableWidget(new LabelWidget(chatLeft, PADDING + 2,
                I18nHelper.translateToString(I18nKeys.TITLE), ChatColors.TEXT_PRIMARY, font));

        // New Chat, Config & Delete buttons (right side of title bar)
        this.newChatButton = this.addRenderableWidget(Button.builder(
                        I18nHelper.translate(I18nKeys.BUTTON_NEW_THREAD), this::onNewChat)
                .bounds(chatRight - 185, PADDING, 55, 18).build());
        this.configButton = this.addRenderableWidget(Button.builder(
                        I18nHelper.translate(I18nKeys.BUTTON_CONFIG), this::onOpenConfig)
                .bounds(chatRight - 125, PADDING, 40, 18).build());
        this.deleteButton = this.addRenderableWidget(Button.builder(
                        I18nHelper.translate(I18nKeys.BUTTON_DELETE), this::onDeleteThread)
                .bounds(chatRight - 80, PADDING, 40, 18).build());

        // Message list
        this.messageList = new MessageListWidget(thread, streamer, expandedReasonings);
        this.messageList.setOnRecall(this::onRecallMessage);
        this.messageList.setOnEdit(this::onEditMessage);
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
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, ChatColors.BG_OVERLAY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // ==================== Thread switching ====================

    private void switchToThread(ConversationThread t) {
        if (t.getId().equals(thread.getId())) return;
        var mgr = ConversationManager.getInstance();
        if (mgr != null) {
            mgr.save();
            mgr.switchThread(t.getId()); // persist the switch
        }
        thread = t;
        streamer = new StreamingChatRenderer(thread, this::onStreamUpdate);
        rebuildLayout();
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
        if (mgr != null) {
            threadList.refresh(mgr.getAllThreads().stream()
                    .sorted(Comparator.comparing(ConversationThread::getUpdatedAt).reversed()).toList(),
                    thread.getId(), height, font);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return super.mouseClicked(mx, my, button);
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
        firstUserMessage = null;
        refreshThreadList();
    }

    private void onOpenConfig(Button b) {
        var mgr = ConversationManager.getInstance();
        if (mgr != null) mgr.save();
        Minecraft.getInstance().setScreen(new ConfigScreen(this));
    }

    private void onDeleteThread(Button b) {
        var mgr = ConversationManager.getInstance();
        if (mgr == null) return;
        if (thread != null) {
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

        // Handle project edit commands locally (don't send to AI)
        if (handleEditCommand(text)) {
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
        List<ChatMessage> history = thread.getMessages().subList(0, thread.getMessages().size() - 1);

        // Inject pending project planning prompt into API history (not visible in UI)
        if (pendingPlanningPrompt != null) {
            List<ChatMessage> augmented = new java.util.ArrayList<>(history);
            augmented.add(ChatMessage.system(pendingPlanningPrompt));
            history = augmented;
            pendingPlanningPrompt = null;
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
                "The player wants to accomplish: \"" + cleanGoal + "\". " +
                "Break this down into a structured JSON task chain with 3-8 steps. " +
                "Return ONLY valid JSON in this format: " +
                "{\"tasks\": [{\"description\": \"...\", " +
                "\"type\": \"CRAFT|GATHER|GO_TO|USE|KILL|PLAN\", " +
                "\"items\": [{\"itemId\": \"minecraft:xxx\", \"count\": N}]}]}";
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
            String msg = I18nHelper.translateToString(I18nKeys.PROJECT_COMPLETE, projectName);
            thread.addMessage(ChatMessage.system(msg));
            if (messageList != null) {
                messageList.onMessageAdded();
            }
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

    /** Refresh UI after project state changes. */
    private void refreshProjectUI() {
        if (messageList != null) {
            messageList.onMessageAdded();
        }
    }
}

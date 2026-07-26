package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.model.AssistantAction;
import com.lumoren.agentchat.model.CardSource;
import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.model.ConversationThread;
import com.lumoren.agentchat.ui.theme.GuideTheme;
import com.lumoren.agentchat.ui.theme.MechanicalSprites;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Scrollable message list widget. Supports click-to-expand reasoning sections,
 * action-card interactions, and right-click context actions.
 * <p>
 * Message widgets are created once per frame and cached, so hit testing uses
 * the exact instances and regions produced by the render pass — positions are
 * never recomputed a second way.
 */
public class MessageListWidget extends AbstractWidget {

    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_MARGIN = 2;

    private final ConversationThread thread;
    private final StreamingChatRenderer streamer;
    private final Set<String> expandedReasonings;
    private final Set<String> expandedCardDetails;

    private int scrollOffset;
    private int maxScrollOffset;
    private boolean autoScroll = true;
    private Font font;

    // right-click selection for recall/edit context buttons
    private String selectedMessageUuid;
    private java.util.function.Consumer<String> onRecall;
    private java.util.function.Consumer<String> onEdit;
    private java.util.function.Consumer<String> onCopy;
    private java.util.function.Consumer<AssistantAction> onAction;

    // Scrollbar drag state
    private boolean draggingScrollbar;
    private double dragStartY;
    private int dragStartOffset;

    /** Per-frame message widgets + positions, shared by render and hit tests. */
    private record FrameEntry(ChatMessageWidget widget, int y, int height) {}
    private final Map<String, FrameEntry> frameWidgets = new LinkedHashMap<>();

    public MessageListWidget(ConversationThread thread, StreamingChatRenderer streamer,
                             Set<String> expandedReasonings) {
        this(thread, streamer, expandedReasonings, new HashSet<>());
    }

    public MessageListWidget(ConversationThread thread, StreamingChatRenderer streamer,
                             Set<String> expandedReasonings, Set<String> expandedCardDetails) {
        super(0, 0, 0, 0, Component.empty());
        this.thread = thread;
        this.streamer = streamer;
        this.expandedReasonings = expandedReasonings != null ? expandedReasonings : new HashSet<>();
        this.expandedCardDetails = expandedCardDetails != null ? expandedCardDetails : new HashSet<>();
        this.scrollOffset = 0;
    }

    public void setBounds(int x, int y, int width, int height, Font font) {
        setX(x);
        setY(y);
        setWidth(width);
        setHeight(height);
        this.font = font;
    }

    public void setSelectedMessageUuid(String uuid) { this.selectedMessageUuid = uuid; }
    public String getSelectedMessageUuid() { return selectedMessageUuid; }
    public void setOnRecall(java.util.function.Consumer<String> onRecall) { this.onRecall = onRecall; }
    public void setOnEdit(java.util.function.Consumer<String> onEdit) { this.onEdit = onEdit; }
    public void setOnCopy(java.util.function.Consumer<String> onCopy) { this.onCopy = onCopy; }
    public void setOnAction(java.util.function.Consumer<AssistantAction> onAction) { this.onAction = onAction; }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (font == null) return;

        int listX = getX();
        int listY = getY();
        int listWidth = getWidth();
        int listHeight = getHeight();
        int contentWidth = listWidth - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;
        if (contentWidth <= 0) return;

        int totalHeight = computeTotalContentHeight(contentWidth);
        maxScrollOffset = Math.max(0, totalHeight - listHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));

        graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
        int currentY = listY - scrollOffset;
        frameWidgets.clear();

        for (ChatMessage msg : thread.getMessages()) {
            boolean isSelected = msg.id().equals(selectedMessageUuid);
            ChatMessageWidget widget = new ChatMessageWidget(msg, contentWidth, expandedReasonings,
                    expandedCardDetails, isSelected);
            int h = widget.getHeight(font, listWidth);
            frameWidgets.put(msg.id(), new FrameEntry(widget, currentY, h));
            if (currentY + h > listY && currentY < listY + listHeight) {
                widget.render(graphics, listX, currentY, listWidth, font);
            }
            currentY += h;
        }

        // Streaming placeholder: show if streaming content OR reasoning exists
        if (streamer.isStreaming() && (streamer.hasStreamingContent() || streamer.hasReasoningContent())) {
            ChatMessage placeholder = new ChatMessage("assistant",
                    streamer.getCurrentContent(), null, null, null,
                    streamer.hasReasoningContent() ? streamer.getReasoningContent() : null);
            ChatMessageWidget streamingWidget = new ChatMessageWidget(placeholder, contentWidth,
                    expandedReasonings, expandedCardDetails, false);
            int widgetHeight = streamingWidget.getHeight(font, listWidth);
            if (currentY + widgetHeight > listY && currentY < listY + listHeight) {
                streamingWidget.render(graphics, listX, currentY, listWidth, font);
            }
            currentY += widgetHeight;
        }

        if (streamer.isStreaming() && !streamer.hasStreamingContent() && !streamer.hasReasoningContent()) {
            int statusY = Math.max(listY, Math.min(currentY, listY + listHeight - font.lineHeight - 8));
            if (streamer.hasStatus()) {
                MechanicalSprites.blitIcon(graphics,
                        MechanicalSprites.spinnerFrame(System.currentTimeMillis()),
                        listX + 4, statusY + 2);
                graphics.drawString(font, streamer.getStatusText(), listX + 20, statusY + 4,
                        GuideTheme.TEXT_DIM);
            } else {
                graphics.drawString(font, "...", listX + 4, statusY + 4, GuideTheme.TEXT_DIM);
            }
        }

        graphics.disableScissor();
        if (totalHeight > listHeight) renderScrollbar(graphics, listX, listY, listWidth, listHeight);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Only handle left and right clicks
        if (button != 0 && button != 1) return false;
        int listX = getX(), listY = getY();
        int listWidth = getWidth(), listHeight = getHeight();
        if (mouseX < listX || mouseX > listX + listWidth || mouseY < listY || mouseY > listY + listHeight)
            return false;

        // Check scrollbar interaction first (left-click only)
        if (button == 0 && maxScrollOffset > 0) {
            int sbX = listX + listWidth - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;
            if (mouseX >= sbX && mouseX <= sbX + SCROLLBAR_WIDTH) {
                float thumbRatio = Math.min(1.0f, (float) listHeight / (listHeight + maxScrollOffset));
                int thumbHeight = Math.max(8, (int) (listHeight * thumbRatio));
                int thumbY = listY + (int) ((listHeight - thumbHeight) * ((float) scrollOffset / maxScrollOffset));
                if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
                    draggingScrollbar = true;
                    dragStartY = mouseY;
                    dragStartOffset = scrollOffset;
                    return true;
                }
                float clickProgress = (float) (mouseY - listY) / listHeight;
                scrollOffset = (int) (clickProgress * maxScrollOffset);
                scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
                autoScroll = (scrollOffset >= maxScrollOffset);
                return true;
            }
        }

        // First pass: context actions (recall/edit/copy) on the selected message
        if (button == 0 && selectedMessageUuid != null) {
            FrameEntry selected = frameWidgets.get(selectedMessageUuid);
            if (selected != null) {
                for (ChatMessageWidget.ContextRegion region : selected.widget().getContextRegions()) {
                    if (mouseX >= region.x1() && mouseX <= region.x2()
                            && mouseY >= region.y1() && mouseY <= region.y2()) {
                        String uuid = selectedMessageUuid;
                        switch (region.action()) {
                            case RECALL -> {
                                selectedMessageUuid = null;
                                if (onRecall != null) onRecall.accept(uuid);
                            }
                            case EDIT -> {
                                selectedMessageUuid = null;
                                if (onEdit != null) onEdit.accept(uuid);
                            }
                            case COPY -> {
                                // Don't deselect — keep showing buttons for repeated copy
                                if (onCopy != null) onCopy.accept(uuid);
                            }
                        }
                        return true;
                    }
                }
                selectedMessageUuid = null;
                return false;
            }
            selectedMessageUuid = null;
        }

        // Second pass: hit-test each message
        int contentWidth = listWidth - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;
        for (ChatMessage msg : thread.getMessages()) {
            FrameEntry entry = frameWidgets.get(msg.id());
            if (entry == null) continue;
            int currentY = entry.y();
            int h = entry.height();
            if (mouseY >= currentY && mouseY <= currentY + h) {
                ChatMessageWidget widget = entry.widget();
                if (button == 0) {
                    ChatMessageWidget.CardInteraction interaction =
                            widget.getCardInteractionAt(mouseX, mouseY, listX, currentY, font, listWidth);
                    if (!interaction.isNone()) {
                        if (interaction.showAllToggle()) {
                            widget.toggleCardDetails();
                        } else if (interaction.action() != null) {
                            if (onAction != null) {
                                onAction.accept(interaction.action());
                            }
                        } else if (interaction.source() != null) {
                            openSource(interaction.source());
                        }
                        return true;
                    }
                    // Left-click: reasoning toggle or deselect
                    if (widget.hitReasoningToggle(mouseX, mouseY, listX, currentY, font)) {
                        widget.toggleReasoning();
                        return true;
                    }
                    // Check if click falls on a specific link region
                    if (widget.hasLinks()) {
                        widget.computeLinkPositions(listX, currentY, font, listWidth);
                        for (ChatMessageWidget.LinkRegion r : widget.getLinkRegions()) {
                            if (mouseX >= r.x1() && mouseX <= r.x2()
                                    && mouseY >= r.y1() && mouseY <= r.y2()) {
                                openUrl(r.url());
                                return true;
                            }
                        }
                    }
                    selectedMessageUuid = null;
                    return false;
                } else {
                    // Right-click: select user/assistant message for context actions
                    if ("user".equals(msg.role()) || "assistant".equals(msg.role())) {
                        selectedMessageUuid = msg.id().equals(selectedMessageUuid) ? null : msg.id();
                    } else {
                        selectedMessageUuid = null;
                    }
                    return true;
                }
            }
        }
        // Also check streaming placeholder (left-click only)
        if (button == 0 && streamer.isStreaming() && (streamer.hasStreamingContent() || streamer.hasReasoningContent())) {
            int currentY = getY() - scrollOffset;
            for (ChatMessage msg : thread.getMessages()) {
                currentY += new ChatMessageWidget(msg, contentWidth, expandedReasonings,
                        expandedCardDetails, false).getHeight(font, listWidth);
            }
            ChatMessage placeholder = new ChatMessage("assistant",
                    streamer.getCurrentContent(), null, null, null,
                    streamer.hasReasoningContent() ? streamer.getReasoningContent() : null);
            ChatMessageWidget sw = new ChatMessageWidget(placeholder, contentWidth, expandedReasonings,
                    expandedCardDetails, false);
            int h = sw.getHeight(font, listWidth);
            if (mouseY >= currentY && mouseY <= currentY + h) {
                if (sw.hitReasoningToggle(mouseX, mouseY, listX, currentY, font)) {
                    sw.toggleReasoning();
                    return true;
                }
                if (sw.hasLinks()) {
                    sw.computeLinkPositions(listX, currentY, font, listWidth);
                    for (ChatMessageWidget.LinkRegion r : sw.getLinkRegions()) {
                        if (mouseX >= r.x1() && mouseX <= r.x2()
                                && mouseY >= r.y1() && mouseY <= r.y2()) {
                            openUrl(r.url());
                            return true;
                        }
                    }
                }
            }
        }
        selectedMessageUuid = null;
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            int listHeight = getHeight();
            double deltaY = mouseY - dragStartY;
            float thumbRatio = Math.min(1.0f, (float) listHeight / (listHeight + maxScrollOffset));
            int trackHeight = listHeight - Math.max(8, (int) (listHeight * thumbRatio));
            if (trackHeight > 0) {
                int newOffset = dragStartOffset + (int) (deltaY * maxScrollOffset / trackHeight);
                scrollOffset = Math.max(0, Math.min(newOffset, maxScrollOffset));
                autoScroll = (scrollOffset >= maxScrollOffset);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int listX = getX(), listY = getY();
        int listWidth = getWidth(), listHeight = getHeight();
        if (mouseX >= listX && mouseX <= listX + listWidth
                && mouseY >= listY && mouseY <= listY + listHeight) {
            int contentWidth = listWidth - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;
            int totalHeight = computeTotalContentHeight(contentWidth);
            maxScrollOffset = Math.max(0, totalHeight - listHeight);
            scrollOffset -= (int) (scrollY * 20);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
            autoScroll = (scrollOffset >= maxScrollOffset);
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}

    public void scrollToBottom() {
        // Recompute maxScrollOffset so it reflects latest content even before next render
        int cw = getWidth() - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;
        if (cw > 0) {
            int totalHeight = computeTotalContentHeight(cw);
            maxScrollOffset = Math.max(0, totalHeight - getHeight());
        }
        if (maxScrollOffset > 0) scrollOffset = maxScrollOffset;
    }

    public int getScrollOffset() { return scrollOffset; }

    public void setScrollOffset(int offset) { this.scrollOffset = Math.max(0, offset); }

    public void onMessageAdded() {
        if (autoScroll) scrollToBottom();
    }

    /** Force auto-scroll for new user message — re-enables following. */
    public void resetAutoScroll() {
        autoScroll = true;
        scrollToBottom();
    }

    private void openUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return;
        try {
            String url = rawUrl.startsWith("http") ? rawUrl : "https://" + rawUrl;
            Util.getPlatform().openUri(url);
        } catch (Exception e) {
            System.err.println("[AgentChat] Failed to open link: " + e.getMessage());
        }
    }

    private void openSource(CardSource source) {
        if (source == null) return;
        if (source.isWebLink()) {
            openUrl(source.sourceIdOrUrl());
        } else if (source.sourceIdOrUrl() != null && !source.sourceIdOrUrl().isBlank()) {
            // Local ids/paths are not opened; copy so the player can inspect them
            net.minecraft.client.Minecraft.getInstance().keyboardHandler
                    .setClipboard(source.sourceIdOrUrl());
        }
    }

    private int computeTotalContentHeight(int contentWidth) {
        if (font == null) return 0;
        int total = 0;
        for (ChatMessage msg : thread.getMessages()) {
            total += new ChatMessageWidget(msg, contentWidth, expandedReasonings,
                    expandedCardDetails, false).getHeight(font, getWidth());
        }
        if (streamer.isStreaming() && (streamer.hasStreamingContent() || streamer.hasReasoningContent())) {
            ChatMessage placeholder = new ChatMessage("assistant",
                    streamer.getCurrentContent(), null, null, null,
                    streamer.hasReasoningContent() ? streamer.getReasoningContent() : null);
            total += new ChatMessageWidget(placeholder, contentWidth, expandedReasonings,
                    expandedCardDetails, false).getHeight(font, getWidth());
        }
        return total;
    }

    private void renderScrollbar(GuiGraphics graphics, int lx, int ly, int lw, int lh) {
        int sx = lx + lw - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;
        graphics.fill(sx, ly, sx + SCROLLBAR_WIDTH, ly + lh, GuideTheme.IRON_INSET);
        if (maxScrollOffset > 0) {
            float progress = (float) scrollOffset / maxScrollOffset;
            float ratio = Math.min(1.0f, (float) lh / (lh + maxScrollOffset));
            int th = Math.max(8, (int) (lh * ratio));
            int ty = ly + (int) ((lh - th) * progress);
            MechanicalSprites.blitNineSlice(graphics, MechanicalSprites.SCROLLBAR,
                    sx - 1, ty, SCROLLBAR_WIDTH + 2, th, 8, 24, 4);
        }
    }
}

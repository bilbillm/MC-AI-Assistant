package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.model.ConversationThread;
import com.lumoren.agentchat.ui.theme.ChatColors;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Scrollable message list widget. Supports click-to-expand reasoning sections.
 */
public class MessageListWidget extends AbstractWidget {

    private static final int SCROLLBAR_WIDTH = 4;
    private static final int SCROLLBAR_MARGIN = 2;
    private static final int SCROLLBAR_COLOR = ChatColors.SCROLLBAR_THUMB;
    private static final int SCROLLBAR_BG_COLOR = ChatColors.SCROLLBAR_BG;

    private final ConversationThread thread;
    private final StreamingChatRenderer streamer;
    private final Set<String> expandedReasonings;

    private int scrollOffset;
    private int maxScrollOffset;
    private boolean autoScroll = true;
    private Font font;

    // right-click selection for recall/edit context buttons
    private String selectedMessageUuid;
    private java.util.function.Consumer<String> onRecall;
    private java.util.function.Consumer<String> onEdit;

    // Scrollbar drag state
    private boolean draggingScrollbar;
    private double dragStartY;
    private int dragStartOffset;

    public MessageListWidget(ConversationThread thread, StreamingChatRenderer streamer, Set<String> expandedReasonings) {
        super(0, 0, 0, 0, Component.empty());
        this.thread = thread;
        this.streamer = streamer;
        this.expandedReasonings = expandedReasonings != null ? expandedReasonings : new HashSet<>();
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

        for (ChatMessage msg : thread.getMessages()) {
            boolean isSelected = msg.id().equals(selectedMessageUuid);
            ChatMessageWidget widget = new ChatMessageWidget(msg, contentWidth, expandedReasonings, isSelected);
            int h = widget.getHeight(font, listWidth);
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
            ChatMessageWidget streamingWidget = new ChatMessageWidget(placeholder, contentWidth, expandedReasonings, false);
            int widgetHeight = streamingWidget.getHeight(font, listWidth);
            if (currentY + widgetHeight > listY && currentY < listY + listHeight) {
                streamingWidget.render(graphics, listX, currentY, listWidth, font);
            }
            currentY += widgetHeight;
        }

        if (streamer.isStreaming() && !streamer.hasStreamingContent() && !streamer.hasReasoningContent()) {
            int statusY = Math.max(listY, Math.min(currentY, listY + listHeight - font.lineHeight - 8));
            graphics.drawString(font, "...", listX + 4, statusY + 4, ChatColors.STATUS_TEXT);
        } else if (streamer.hasStatus() && !streamer.hasStreamingContent() && !streamer.hasReasoningContent()) {
            int statusY = Math.max(listY, Math.min(currentY, listY + listHeight - font.lineHeight - 8));
            graphics.drawString(font, streamer.getStatusText(), listX + 4, statusY + 4, ChatColors.STATUS_TEXT);
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

        int contentWidth = listWidth - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;
        int currentY = listY - scrollOffset;

        // First pass: check for context button clicks on the selected message (left-click only)
        if (button == 0 && selectedMessageUuid != null) {
            for (ChatMessage msg : thread.getMessages()) {
                if (!msg.id().equals(selectedMessageUuid)) {
                    ChatMessageWidget tmp = new ChatMessageWidget(msg, contentWidth, expandedReasonings, false);
                    currentY += tmp.getHeight(font, listWidth);
                    continue;
                }
                if (!"user".equals(msg.role())) { break; }
                ChatMessageWidget widget = new ChatMessageWidget(msg, contentWidth, expandedReasonings, true);
                int h = widget.getHeight(font, listWidth);
                // Check if click is on recall or edit action button area.
                // Buttons render at: recallX = bubbleX + bubbleWidth - 68, editX = recallX + 34
                // where bubbleX + bubbleWidth ≈ listX + listWidth - AVATAR_SIZE - AVATAR_GAP = listX + listWidth - 22
                // So recallX ≈ listX + listWidth - 90, editX ≈ listX + listWidth - 56
                // Y area: roughly currentY + h - 22 to currentY + h - 2 (generous hitbox)
                int btnTop = currentY + h - 22;
                int btnBot = currentY + h - 2;
                int recallStart = listX + listWidth - 92;
                int editStart = listX + listWidth - 56;
                if (mouseX >= recallStart && mouseX <= recallStart + 34
                        && mouseY >= btnTop && mouseY <= btnBot) {
                    String recalledUuid = selectedMessageUuid;
                    selectedMessageUuid = null;
                    if (onRecall != null) onRecall.accept(recalledUuid);
                    return true;
                }
                if (mouseX >= editStart && mouseX <= editStart + 34
                        && mouseY >= btnTop && mouseY <= btnBot) {
                    String editedUuid = selectedMessageUuid;
                    selectedMessageUuid = null;
                    if (onEdit != null) onEdit.accept(editedUuid);
                    return true;
                }
                // Click elsewhere — deselect
                selectedMessageUuid = null;
                return false;
            }
        }

        // Second pass: hit-test each message
        currentY = listY - scrollOffset;
        for (ChatMessage msg : thread.getMessages()) {
            ChatMessageWidget widget = new ChatMessageWidget(msg, contentWidth, expandedReasonings);
            int h = widget.getHeight(font, listWidth);
            if (mouseY >= currentY && mouseY <= currentY + h) {
                if (button == 0) {
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
                        // Fallback: no specific region matched — don't open anything
                    }
                    selectedMessageUuid = null;
                    return false;
                } else {
                    // Right-click: select user message for context actions
                    if ("user".equals(msg.role())) {
                        selectedMessageUuid = msg.id().equals(selectedMessageUuid) ? null : msg.id();
                    } else {
                        selectedMessageUuid = null;
                    }
                    return true;
                }
            }
            currentY += h;
        }
        // Also check streaming placeholder (left-click only)
        if (button == 0 && streamer.isStreaming() && (streamer.hasStreamingContent() || streamer.hasReasoningContent())) {
            ChatMessage placeholder = new ChatMessage("assistant",
                    streamer.getCurrentContent(), null, null, null,
                    streamer.hasReasoningContent() ? streamer.getReasoningContent() : null);
            ChatMessageWidget sw = new ChatMessageWidget(placeholder, contentWidth, expandedReasonings, false);
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
                    // Fallback: no specific region matched — don't open anything
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

    private int computeTotalContentHeight(int contentWidth) {
        if (font == null) return 0;
        int total = 0;
        for (ChatMessage msg : thread.getMessages()) {
            total += new ChatMessageWidget(msg, contentWidth, expandedReasonings).getHeight(font, getWidth());
        }
        if (streamer.isStreaming() && (streamer.hasStreamingContent() || streamer.hasReasoningContent())) {
            ChatMessage placeholder = new ChatMessage("assistant",
                    streamer.getCurrentContent(), null, null, null,
                    streamer.hasReasoningContent() ? streamer.getReasoningContent() : null);
            total += new ChatMessageWidget(placeholder, contentWidth, expandedReasonings, false).getHeight(font, getWidth());
        }
        return total;
    }

    private void renderScrollbar(GuiGraphics graphics, int lx, int ly, int lw, int lh) {
        int sx = lx + lw - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;
        graphics.fill(sx, ly, sx + SCROLLBAR_WIDTH, ly + lh, SCROLLBAR_BG_COLOR);
        if (maxScrollOffset > 0) {
            float progress = (float) scrollOffset / maxScrollOffset;
            float ratio = Math.min(1.0f, (float) lh / (lh + maxScrollOffset));
            int th = Math.max(8, (int) (lh * ratio));
            int ty = ly + (int) ((lh - th) * progress);
            graphics.fill(sx, ty, sx + SCROLLBAR_WIDTH, ty + th, SCROLLBAR_COLOR);
        }
    }
}

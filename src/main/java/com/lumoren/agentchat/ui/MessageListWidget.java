package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.model.ConversationThread;
import com.lumoren.agentchat.ui.theme.ChatColors;
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
    private final Set<String> expandedReasonings = new HashSet<>();

    private int scrollOffset;
    private int maxScrollOffset;
    private boolean autoScroll = true;
    private Font font;

    // Scrollbar drag state
    private boolean draggingScrollbar;
    private double dragStartY;
    private int dragStartOffset;

    public MessageListWidget(ConversationThread thread, StreamingChatRenderer streamer) {
        super(0, 0, 0, 0, Component.empty());
        this.thread = thread;
        this.streamer = streamer;
        this.scrollOffset = 0;
    }

    public void setBounds(int x, int y, int width, int height, Font font) {
        setX(x);
        setY(y);
        setWidth(width);
        setHeight(height);
        this.font = font;
    }

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
            ChatMessageWidget widget = new ChatMessageWidget(msg, contentWidth, expandedReasonings);
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
            ChatMessageWidget streamingWidget = new ChatMessageWidget(placeholder, contentWidth, expandedReasonings);
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
        if (button != 0) return false;
        int listX = getX(), listY = getY();
        int listWidth = getWidth(), listHeight = getHeight();
        if (mouseX < listX || mouseX > listX + listWidth || mouseY < listY || mouseY > listY + listHeight)
            return false;

        // Check scrollbar interaction first
        if (maxScrollOffset > 0) {
            int sbX = listX + listWidth - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;
            if (mouseX >= sbX && mouseX <= sbX + SCROLLBAR_WIDTH) {
                float thumbRatio = Math.min(1.0f, (float) listHeight / (listHeight + maxScrollOffset));
                int thumbHeight = Math.max(8, (int) (listHeight * thumbRatio));
                int thumbY = listY + (int) ((listHeight - thumbHeight) * ((float) scrollOffset / maxScrollOffset));
                if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
                    // Start dragging the thumb
                    draggingScrollbar = true;
                    dragStartY = mouseY;
                    dragStartOffset = scrollOffset;
                    return true;
                }
                // Clicked on track (above or below thumb) → jump scroll
                float clickProgress = (float) (mouseY - listY) / listHeight;
                scrollOffset = (int) (clickProgress * maxScrollOffset);
                scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
                autoScroll = (scrollOffset >= maxScrollOffset);
                return true;
            }
        }

        int contentWidth = listWidth - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;
        int currentY = listY - scrollOffset;

        for (ChatMessage msg : thread.getMessages()) {
            ChatMessageWidget widget = new ChatMessageWidget(msg, contentWidth, expandedReasonings);
            int h = widget.getHeight(font, listWidth);
            if (mouseY >= currentY && mouseY <= currentY + h) {
                if (widget.hitReasoningToggle(mouseX, mouseY, listX, currentY, font)) {
                    widget.toggleReasoning();
                    return true;
                }
                return false;
            }
            currentY += h;
        }
        // Also check streaming placeholder
        if (streamer.isStreaming() && (streamer.hasStreamingContent() || streamer.hasReasoningContent())) {
            ChatMessage placeholder = new ChatMessage("assistant",
                    streamer.getCurrentContent(), null, null, null,
                    streamer.hasReasoningContent() ? streamer.getReasoningContent() : null);
            ChatMessageWidget sw = new ChatMessageWidget(placeholder, contentWidth, expandedReasonings);
            int h = sw.getHeight(font, listWidth);
            if (mouseY >= currentY && mouseY <= currentY + h) {
                if (sw.hitReasoningToggle(mouseX, mouseY, listX, currentY, font)) {
                    sw.toggleReasoning();
                    return true;
                }
            }
        }
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
        autoScroll = true;
        if (maxScrollOffset > 0) scrollOffset = maxScrollOffset;
    }

    public void onMessageAdded() {
        if (autoScroll) scrollToBottom();
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
            total += new ChatMessageWidget(placeholder, contentWidth, expandedReasonings).getHeight(font, getWidth());
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

package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.model.ConversationThread;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;



/**
 * Scrollable message list component that renders a sequence of
 * {@link ChatMessageWidget} instances with vertical scrolling support.
 * <p>
 * Features:
 * <ul>
 *   <li>Renders messages from a {@link ConversationThread}</li>
 *   <li>Mouse wheel scrolling</li>
 *   <li>Scrollbar indicator on the right side</li>
 *   <li>Auto-scroll to bottom on new messages</li>
 *   <li>Viewport clipping for performance</li>
 * </ul>
 */
public class MessageListWidget {

    private static final int SCROLLBAR_WIDTH = 4;
    private static final int SCROLLBAR_MARGIN = 2;
    private static final int SCROLLBAR_COLOR = 0x88AAAAAA;
    private static final int SCROLLBAR_BG_COLOR = 0x33333333;

    private final ConversationThread thread;
    private final StreamingChatRenderer streamer;

    private int scrollOffset;
    private int maxScrollOffset;
    private boolean autoScroll = true;

    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;
    private Font font;

    /**
     * @param thread   the conversation thread whose messages to display
     * @param streamer the streaming renderer for in-flight AI responses
     */
    public MessageListWidget(ConversationThread thread, StreamingChatRenderer streamer) {
        this.thread = thread;
        this.streamer = streamer;
        this.scrollOffset = 0;
    }

    /**
     * Set the bounds of the message list area.
     *
     * @param x      left X coordinate
     * @param y      top Y coordinate
     * @param width  width in pixels
     * @param height height in pixels
     * @param font   Minecraft font for text measurement
     */
    public void setBounds(int x, int y, int width, int height, Font font) {
        this.listX = x;
        this.listY = y;
        this.listWidth = width;
        this.listHeight = height;
        this.font = font;
    }

    /**
     * Render all visible messages within the list area, clipped to viewport.
     */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (font == null) return;

        int contentWidth = listWidth - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;
        if (contentWidth <= 0) return;

        // Compute total content height
        int totalHeight = computeTotalContentHeight(contentWidth);

        // Update max scroll
        maxScrollOffset = Math.max(0, totalHeight - listHeight);

        // Clamp scroll
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));

        // Enable scissor clipping to viewport
        graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

        // Render visible messages
        int currentY = listY - scrollOffset;

        for (ChatMessage msg : thread.getMessages()) {
            ChatMessageWidget widget = new ChatMessageWidget(msg, contentWidth);
            int widgetHeight = widget.getHeight(font, listWidth);

            // Only render if visible in viewport
            if (currentY + widgetHeight > listY && currentY < listY + listHeight) {
                widget.render(graphics, listX, currentY, listWidth, font);
            }
            currentY += widgetHeight;
        }

        // Render streaming placeholder if active
        if (streamer.hasStreamingContent()) {
            ChatMessage placeholder = ChatMessage.assistant(streamer.getCurrentContent());
            ChatMessageWidget streamingWidget = new ChatMessageWidget(placeholder, contentWidth);
            int widgetHeight = streamingWidget.getHeight(font, listWidth);

            if (currentY + widgetHeight > listY && currentY < listY + listHeight) {
                streamingWidget.render(graphics, listX, currentY, listWidth, font);
            }
            currentY += widgetHeight;
        }

        // Render status text if no streaming content yet
        if (streamer.hasStatus() && !streamer.hasStreamingContent()) {
            int statusY = currentY;
            if (statusY > listY && statusY < listY + listHeight) {
                graphics.drawString(
                        font,
                        streamer.getStatusText(),
                        listX + 4,
                        statusY + 4,
                        0xFFAAAAAA,
                        false
                );
            }
        }

        graphics.disableScissor();

        // Draw scrollbar if content overflows
        if (totalHeight > listHeight) {
            renderScrollbar(graphics);
        }
    }

    /**
     * Handle mouse scroll events.
     *
     * @param mouseX     mouse X position
     * @param mouseY     mouse Y position
     * @param deltaX     horizontal scroll delta
     * @param deltaY     vertical scroll delta (positive = scroll up)
     * @return true if the event was handled
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        // Check if mouse is within list bounds
        if (mouseX >= listX && mouseX <= listX + listWidth
                && mouseY >= listY && mouseY <= listY + listHeight) {
            int contentWidth = listWidth - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;
            int totalHeight = computeTotalContentHeight(contentWidth);
            maxScrollOffset = Math.max(0, totalHeight - listHeight);

            scrollOffset -= (int) (deltaY * 20); // 20px per scroll tick
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
            autoScroll = (scrollOffset >= maxScrollOffset);
            return true;
        }
        return false;
    }

    /**
     * Scroll to the bottom of the message list.
     */
    public void scrollToBottom() {
        autoScroll = true;
        if (maxScrollOffset > 0) {
            scrollOffset = maxScrollOffset;
        }
    }

    /**
     * @return true if currently auto-scrolling (following new messages)
     */
    public boolean isAutoScroll() {
        return autoScroll;
    }

    /**
     * Called when new messages are added — auto-scrolls if enabled.
     */
    public void onMessageAdded() {
        if (autoScroll) {
            scrollToBottom();
        }
    }

    /**
     * Compute the total height of all messages + streaming content using font metrics.
     */
    public int computeTotalContentHeight(int contentWidth) {
        if (font == null) return 0;
        int total = 0;
        for (ChatMessage msg : thread.getMessages()) {
            ChatMessageWidget widget = new ChatMessageWidget(msg, contentWidth);
            total += widget.getHeight(font, listWidth);
        }
        if (streamer.hasStreamingContent()) {
            ChatMessage placeholder = ChatMessage.assistant(streamer.getCurrentContent());
            ChatMessageWidget widget = new ChatMessageWidget(placeholder, contentWidth);
            total += widget.getHeight(font, listWidth);
        }
        return total;
    }

    /**
     * Render the scrollbar indicator.
     */
    private void renderScrollbar(GuiGraphics graphics) {
        int scrollbarX = listX + listWidth - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;
        int scrollbarHeight = listHeight;

        // Background track
        graphics.fill(
                scrollbarX, listY,
                scrollbarX + SCROLLBAR_WIDTH, listY + scrollbarHeight,
                SCROLLBAR_BG_COLOR
        );

        // Thumb
        if (maxScrollOffset > 0) {
            float progress = (float) scrollOffset / maxScrollOffset;
            float thumbRatio = Math.min(1.0f, (float) listHeight / (listHeight + maxScrollOffset));
            int thumbHeight = Math.max(8, (int) (scrollbarHeight * thumbRatio));
            int thumbY = listY + (int) ((scrollbarHeight - thumbHeight) * progress);

            graphics.fill(
                    scrollbarX, thumbY,
                    scrollbarX + SCROLLBAR_WIDTH, thumbY + thumbHeight,
                    SCROLLBAR_COLOR
            );
        }
    }
}

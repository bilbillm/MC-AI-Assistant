package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.model.ChatMessage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Renders a single chat message bubble in the Minecraft GUI.
 * <p>
 * Three visual modes:
 * <ul>
 *   <li><b>User messages</b> — right-aligned, blue background (#3B82F6)</li>
 *   <li><b>AI / assistant messages</b> — left-aligned, gray background (#374151)</li>
 *   <li><b>Error messages</b> — left-aligned, red text, no bubble background</li>
 * </ul>
 * <p>
 * Supports multi-line text with word wrapping and Markdown rendering
 * for AI messages via {@link MarkdownRenderer}.
 */
public class ChatMessageWidget {

    private static final int PADDING = 8;
    private static final int BUBBLE_RADIUS = 4;
    private static final int GAP = 4;

    private static final int COLOR_USER_BG = 0xFF3B82F6;
    private static final int COLOR_AI_BG = 0xFF374151;
    private static final int COLOR_ERROR_TEXT = 0xFFFF4444;
    private static final int COLOR_USER_TEXT = 0xFFFFFFFF;
    private static final int COLOR_AI_TEXT = 0xFFE5E7EB;

    private final ChatMessage message;
    private final int maxWidth;
    private final Component renderedContent;

    /**
     * Create a widget to render the given message within the specified width.
     *
     * @param message the chat message to render
     * @param maxWidth the available width for the bubble (in pixels)
     */
    public ChatMessageWidget(ChatMessage message, int maxWidth) {
        this.message = message;
        this.maxWidth = Math.max(50, maxWidth); // minimum 50px
        this.renderedContent = isError()
                ? Component.literal(message.content())
                : MarkdownRenderer.render(message.content());
    }

    /**
     * Render this message bubble at the given position.
     * After rendering, the height is known via {@link #getLastComputedHeight()}.
     *
     * @param graphics the GuiGraphics context
     * @param x        left X coordinate of the message list area
     * @param y        top Y coordinate to render at
     * @param width    full width of the message list area
     * @param font     Minecraft font for text rendering
     */
    public void render(GuiGraphics graphics, int x, int y, int width, Font font) {
        int contentWidth = Math.min(maxWidth, width - PADDING * 2);
        int innerWidth = contentWidth - PADDING * 2;

        if (isError()) {
            renderError(graphics, x, y, contentWidth, innerWidth, font);
        } else if (isUser()) {
            renderUserBubble(graphics, x, y, contentWidth, innerWidth, font);
        } else {
            renderAIBubble(graphics, x, y, contentWidth, innerWidth, font);
        }
    }

    /**
     * Compute the height this widget would occupy when rendered with the given font and available width.
     * <p>
     * This can be called without rendering, for layout calculations.
     *
     * @param font          the Minecraft font
     * @param availableWidth total width available for the widget
     * @return height in pixels
     */
    public int getHeight(Font font, int availableWidth) {
        int contentWidth = Math.min(maxWidth, availableWidth - PADDING * 2);
        int innerWidth = contentWidth - PADDING * 2;
        if (innerWidth <= 0) innerWidth = 10;

        int textHeight = font.wordWrapHeight(renderedContent, innerWidth);

        if (isError()) {
            return PADDING + textHeight + PADDING;
        }
        return PADDING + textHeight + PADDING + GAP; // bubble body + gap after
    }

    /**
     * @return true if this message represents an error
     */
    public boolean isError() {
        return "error".equals(message.role());
    }

    /**
     * @return true if this is a user message
     */
    public boolean isUser() {
        return "user".equals(message.role());
    }

    /**
     * @return the raw ChatMessage backing this widget
     */
    public ChatMessage getMessage() {
        return message;
    }

    /**
     * @return the rendered Component (post-Markdown)
     */
    public Component getRenderedContent() {
        return renderedContent;
    }

    // ==================== Rendering methods ====================

    private void renderError(GuiGraphics graphics, int x, int y, int contentWidth, int innerWidth, Font font) {
        int textHeight = font.wordWrapHeight(renderedContent, innerWidth);

        graphics.drawString(
                font,
                renderedContent,
                x + PADDING,
                y + PADDING,
                COLOR_ERROR_TEXT,
                false
        );
    }

    private void renderUserBubble(GuiGraphics graphics, int x, int y, int contentWidth, int innerWidth, Font font) {
        int textHeight = font.wordWrapHeight(renderedContent, innerWidth);
        int bubbleHeight = PADDING + textHeight + PADDING;
        int bubbleX = x + (maxWidth - contentWidth); // right-align within maxWidth

        // Draw bubble background
        fillRoundedRect(graphics, bubbleX, y, contentWidth, bubbleHeight, COLOR_USER_BG);

        // Draw text (left-aligned within bubble)
        graphics.drawString(
                font,
                renderedContent,
                bubbleX + PADDING,
                y + PADDING,
                COLOR_USER_TEXT,
                false
        );
    }

    private void renderAIBubble(GuiGraphics graphics, int x, int y, int contentWidth, int innerWidth, Font font) {
        int textHeight = font.wordWrapHeight(renderedContent, innerWidth);
        int bubbleHeight = PADDING + textHeight + PADDING;
        int bubbleX = x; // left-align

        // Draw bubble background
        fillRoundedRect(graphics, bubbleX, y, contentWidth, bubbleHeight, COLOR_AI_BG);

        // Draw text
        graphics.drawString(
                font,
                renderedContent,
                bubbleX + PADDING,
                y + PADDING,
                COLOR_AI_TEXT,
                false
        );
    }

    /**
     * Draw a filled rectangle with rounded corners using a simple approach:
     * overlapping fills for a faux-rounded look.
     */
    private static void fillRoundedRect(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        // Main body
        graphics.fill(x, y + BUBBLE_RADIUS, x + width, y + height - BUBBLE_RADIUS, color);
        // Top strip
        graphics.fill(x, y, x + width, y + BUBBLE_RADIUS, color);
        // Bottom strip
        graphics.fill(x, y + height - BUBBLE_RADIUS, x + width, y + height, color);
        // Left strip
        graphics.fill(x, y, x + BUBBLE_RADIUS, y + height, color);
        // Right strip
        graphics.fill(x + width - BUBBLE_RADIUS, y, x + width, y + height, color);
    }
}

package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.ui.theme.ChatColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * WeChat-style chat bubble with avatar and collapsible reasoning section.
 * <p>
 * User messages: avatar on right, blue bubble.<br>
 * AI messages: avatar on left, gray bubble.<br>
 * AI messages with reasoning: collapsible "思考过程" section above reply.
 */
public class ChatMessageWidget {

    private static final int PADDING = 8;
    private static final int AVATAR_SIZE = 16;
    private static final int AVATAR_GAP = 6;
    private static final int BUBBLE_RADIUS = 6;
    private static final int GAP = 6;
    private static final int REASONING_HEADER_HEIGHT = 14;

    private final ChatMessage message;
    private final Component renderedContent;
    private final Component renderedReasoning;
    private final Set<String> expandedReasonings;

    // Bounding box for click detection of reasoning toggle — set during render
    private int toggleX, toggleY, toggleWidth, toggleHeight;

    public ChatMessageWidget(ChatMessage message, int maxWidth, Set<String> expandedReasonings) {
        this.message = message;
        this.expandedReasonings = expandedReasonings != null ? expandedReasonings : new HashSet<>();
        this.renderedContent = isError()
                ? Component.literal(message.content())
                : MarkdownRenderer.render(message.content());
        this.renderedReasoning = hasReasoning()
                ? MarkdownRenderer.render(message.reasoningContent())
                : null;
    }

    public void render(GuiGraphics graphics, int x, int y, int width, Font font) {
        if (isError()) {
            renderError(graphics, x, y, width, font);
        } else if (isUser()) {
            renderUserBubble(graphics, x, y, width, font);
        } else {
            renderAIBubble(graphics, x, y, width, font);
        }
    }

    public int getHeight(Font font, int availableWidth) {
        int bubbleMaxWidth = availableWidth - AVATAR_SIZE - AVATAR_GAP - PADDING;
        int innerWidth = bubbleMaxWidth - PADDING * 2;
        if (innerWidth <= 10) innerWidth = 10;
        int textHeight = font.wordWrapHeight(renderedContent, innerWidth);
        int reasoningExtra = 0;
        if (hasReasoning()) {
            if (isReasoningExpanded()) {
                int rHeight = font.wordWrapHeight(renderedReasoning, innerWidth);
                reasoningExtra = REASONING_HEADER_HEIGHT + 4 + rHeight + 4;
            } else {
                reasoningExtra = REASONING_HEADER_HEIGHT + 2;
            }
        }
        int bubbleHeight = reasoningExtra + PADDING + textHeight + PADDING;
        return isError() ? PADDING + textHeight + PADDING + GAP
                         : Math.max(AVATAR_SIZE, bubbleHeight) + GAP;
    }

    public boolean isError() { return "error".equals(message.role()); }
    public boolean isUser() { return "user".equals(message.role()); }
    public ChatMessage getMessage() { return message; }
    public boolean hasReasoning() {
        return "assistant".equals(message.role())
                && message.reasoningContent() != null
                && !message.reasoningContent().isBlank();
    }
    public boolean isReasoningExpanded() {
        // Auto-expand during streaming when content is still empty (thinking phase)
        if (hasReasoning() && (message.content() == null || message.content().isBlank())) {
            return true;
        }
        return expandedReasonings.contains(message.id());
    }
    public void toggleReasoning() {
        if (expandedReasonings.contains(message.id())) {
            expandedReasonings.remove(message.id());
        } else {
            expandedReasonings.add(message.id());
        }
    }

    /** Check if a click at absolute screen coords hits the reasoning toggle for this widget at (widgetX, widgetY). */
    public boolean hitReasoningToggle(double mx, double my, int widgetX, int widgetY, Font font) {
        if (!hasReasoning()) return false;
        // Toggle is at bubble start: widgetX + avatar + gap + padding, widgetY + padding
        int toggleAbsX = widgetX + AVATAR_SIZE + AVATAR_GAP + PADDING;
        int toggleAbsY = widgetY + PADDING;
        String toggleLabel = (isReasoningExpanded() ? "▼ " : "▶ ") + "思考过程";
        int tw = font.width(toggleLabel) + 8;
        int th = REASONING_HEADER_HEIGHT;
        return mx >= toggleAbsX && mx <= toggleAbsX + tw
                && my >= toggleAbsY && my <= toggleAbsY + th;
    }

    // ==================== Rendering ====================

    private void renderError(GuiGraphics graphics, int x, int y, int width, Font font) {
        int innerWidth = width - PADDING * 2;
        if (innerWidth <= 10) innerWidth = 10;
        graphics.drawWordWrap(font, renderedContent, x + PADDING, y + PADDING, innerWidth, ChatColors.TEXT_ERROR);
    }

    private void renderUserBubble(GuiGraphics graphics, int x, int y, int width, Font font) {
        int bubbleMaxWidth = width - AVATAR_SIZE - AVATAR_GAP - PADDING;
        if (bubbleMaxWidth <= 20) bubbleMaxWidth = 20;
        int innerWidth = bubbleMaxWidth - PADDING * 2;
        if (innerWidth <= 10) innerWidth = 10;

        int textHeight = font.wordWrapHeight(renderedContent, innerWidth);
        int bubbleWidth = Math.min(bubbleMaxWidth, maxContentWidth(font, renderedContent, bubbleMaxWidth));
        int bubbleHeight = PADDING + textHeight + PADDING;

        int bubbleX = x + width - AVATAR_SIZE - AVATAR_GAP - bubbleWidth;
        drawAvatar(graphics, x + width - AVATAR_SIZE, y, "U", ChatColors.AVATAR_USER_BG, font);
        drawBubble(graphics, bubbleX, y, bubbleWidth, bubbleHeight, ChatColors.BUBBLE_USER_BG);
        graphics.drawWordWrap(font, renderedContent, bubbleX + PADDING, y + PADDING, innerWidth, ChatColors.TEXT_PRIMARY);
    }

    private void renderAIBubble(GuiGraphics graphics, int x, int y, int width, Font font) {
        int bubbleMaxWidth = width - AVATAR_SIZE - AVATAR_GAP - PADDING;
        if (bubbleMaxWidth <= 20) bubbleMaxWidth = 20;
        int innerWidth = bubbleMaxWidth - PADDING * 2;
        if (innerWidth <= 10) innerWidth = 10;

        int textHeight = font.wordWrapHeight(renderedContent, innerWidth);
        int reasoningExtra = 0;
        int reasoningHeight = 0;
        if (hasReasoning()) {
            if (isReasoningExpanded()) {
                reasoningHeight = font.wordWrapHeight(renderedReasoning, innerWidth);
                reasoningExtra = REASONING_HEADER_HEIGHT + 4 + reasoningHeight + 4;
            } else {
                reasoningExtra = REASONING_HEADER_HEIGHT + 2;
            }
        }

        int bubbleWidth = Math.min(bubbleMaxWidth, maxContentWidth(font, renderedContent, bubbleMaxWidth));
        // Ensure bubble is wide enough for reasoning text when content is empty
        if (hasReasoning() && isReasoningExpanded()) {
            bubbleWidth = Math.max(bubbleWidth, maxContentWidth(font, renderedReasoning, bubbleMaxWidth));
        }
        // Ensure bubble is wide enough for reasoning header text
        if (hasReasoning()) {
            String toggleLabel = (isReasoningExpanded() ? "▼ " : "▶ ") + "思考过程";
            bubbleWidth = Math.max(bubbleWidth, font.width(toggleLabel) + PADDING * 2 + 20);
        }
        int bubbleHeight = reasoningExtra + PADDING + textHeight + PADDING;

        int avatarX = x;
        int bubbleX = x + AVATAR_SIZE + AVATAR_GAP;

        drawAvatar(graphics, avatarX, y, "AI", ChatColors.AVATAR_AI_BG, font);
        drawBubble(graphics, bubbleX, y, bubbleWidth, bubbleHeight, ChatColors.BUBBLE_AI_BG);

        int contentY = y + PADDING;
        if (hasReasoning()) {
            // Reasoning header (clickable toggle)
            String toggleLabel = (isReasoningExpanded() ? "▼ " : "▶ ") + "思考过程";
            toggleX = bubbleX + PADDING;
            toggleY = contentY;
            toggleWidth = font.width(toggleLabel) + 8;
            toggleHeight = REASONING_HEADER_HEIGHT;
            graphics.drawString(font, toggleLabel, toggleX, toggleY, ChatColors.TEXT_REASONING);
            contentY += REASONING_HEADER_HEIGHT + 2;

            if (isReasoningExpanded()) {
                // Separator
                graphics.fill(bubbleX + PADDING, contentY, bubbleX + bubbleWidth - PADDING, contentY + 1, ChatColors.SEPARATOR);
                contentY += 4;
                // Reasoning text
                graphics.drawWordWrap(font, renderedReasoning, bubbleX + PADDING, contentY, innerWidth, ChatColors.TEXT_REASONING);
                contentY += reasoningHeight + 4;
            }
        }
        // Main response text
        graphics.drawWordWrap(font, renderedContent, bubbleX + PADDING, contentY, innerWidth, ChatColors.TEXT_SECONDARY);
    }

    // ==================== Helpers ====================

    private static int maxContentWidth(Font font, Component text, int maxW) {
        int w = font.width(text) + PADDING * 2;
        w = Math.max(w, 40);
        return Math.min(w, maxW);
    }

    private static void drawAvatar(GuiGraphics graphics, int x, int y, String label, int color, Font font) {
        graphics.fill(x, y, x + AVATAR_SIZE, y + AVATAR_SIZE, color);
        int textWidth = font.width(label);
        int textX = x + (AVATAR_SIZE - textWidth) / 2;
        int textY = y + (AVATAR_SIZE - font.lineHeight) / 2;
        graphics.drawString(font, label, textX, textY, ChatColors.AVATAR_TEXT);
    }

    private static void drawBubble(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        int r = BUBBLE_RADIUS;
        graphics.fill(x + r, y, x + width - r, y + height, color);
        graphics.fill(x, y + r, x + width, y + height - r, color);
        graphics.fill(x, y, x + r, y + r, color);
        graphics.fill(x + width - r, y, x + width, y + r, color);
        graphics.fill(x, y + height - r, x + r, y + height, color);
        graphics.fill(x + width - r, y + height - r, x + width, y + height, color);
    }
}

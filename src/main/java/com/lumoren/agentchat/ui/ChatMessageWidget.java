package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.ui.theme.ChatColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(([^\\)]+)\\)");
    private static final Pattern PLAIN_URL_PATTERN = Pattern.compile("https?://[^\\s<>\"']+");

    private final ChatMessage message;
    private final Component renderedContent;
    private final Component renderedReasoning;
    private final Set<String> expandedReasonings;
    private final boolean selected; // right-click selected for context actions
    private final List<String> linkUrls = new ArrayList<>();

    /** Records the bounding box (screen-absolute) and URL of a clickable link within this message. */
    public record LinkRegion(int x1, int y1, int x2, int y2, String url) {}
    private final List<LinkRegion> linkRegions = new ArrayList<>();

    // Bounding box for click detection of reasoning toggle — set during render
    private int toggleX, toggleY, toggleWidth, toggleHeight;

    public ChatMessageWidget(ChatMessage message, int maxWidth, Set<String> expandedReasonings) {
        this(message, maxWidth, expandedReasonings, false);
    }

    public ChatMessageWidget(ChatMessage message, int maxWidth, Set<String> expandedReasonings, boolean selected) {
        this.message = message;
        this.expandedReasonings = expandedReasonings != null ? expandedReasonings : new HashSet<>();
        this.selected = selected;
        this.renderedContent = isError() || isTool() || isSystem()
                ? Component.literal(message.content())
                : MarkdownRenderer.render(message.content());
        this.renderedReasoning = hasReasoning()
                ? MarkdownRenderer.render(message.reasoningContent())
                : null;

        // Extract links from raw markdown for click handling
        if (!isError() && !isTool() && !isSystem()) {
            // Plain http(s):// URLs
            Matcher urlMatcher = PLAIN_URL_PATTERN.matcher(message.content());
            while (urlMatcher.find()) {
                linkUrls.add(urlMatcher.group());
            }
            // Markdown [text](url) links
            Matcher m = MARKDOWN_LINK_PATTERN.matcher(message.content());
            while (m.find()) {
                linkUrls.add(m.group(2));
            }
        }
    }

    public void render(GuiGraphics graphics, int x, int y, int width, Font font) {
        if (isError()) {
            renderError(graphics, x, y, width, font);
        } else if (isUser()) {
            renderUserBubble(graphics, x, y, width, font);
        } else if (isTool()) {
            renderToolBlock(graphics, x, y, width, font);
        } else if (isSystem()) {
            renderSystemText(graphics, x, y, width, font);
        } else {
            renderAIBubble(graphics, x, y, width, font);
        }
    }

    public int getHeight(Font font, int availableWidth) {
        if (isTool()) {
            int innerW = availableWidth - PADDING * 2;
            if (innerW <= 10) innerW = 10;
            return PADDING / 2 + font.lineHeight + 4 + GAP;
        }
        if (isSystem()) {
            int innerW = availableWidth - PADDING * 2;
            if (innerW <= 10) innerW = 10;
            return 2 + font.wordWrapHeight(renderedContent, innerW) + 4;
        }
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
    public boolean isTool() { return "tool".equals(message.role()); }
    public boolean isSystem() { return "system".equals(message.role()); }
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

    /** Get extracted link URLs from markdown content. */
    public List<String> getLinkUrls() { return linkUrls; }
    /** True if this message contains one or more markdown links. */
    public boolean hasLinks() { return !linkUrls.isEmpty(); }
    /** Get computed link hit regions (populated during render). */
    public List<LinkRegion> getLinkRegions() { return linkRegions; }

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

    private void renderToolBlock(GuiGraphics graphics, int x, int y, int width, Font font) {
        int px = x + PADDING;
        int py = y + PADDING / 2;
        int maxW = width - PADDING * 2;
        if (maxW <= 10) maxW = 10;

        // Tool name header with background
        int textW = Math.min(font.width(renderedContent) + 10, maxW);
        graphics.fill(px, py, px + textW, py + font.lineHeight + 4, 0x44_1E293B);
        graphics.drawString(font, renderedContent.getString(), px + 4, py + 2, ChatColors.TEXT_ACCENT);
    }

    private void renderSystemText(GuiGraphics graphics, int x, int y, int width, Font font) {
        // Gray text, no bubble
        int px = x + PADDING;
        int py = y + 2;
        int maxW = width - PADDING * 2;
        graphics.drawWordWrap(font, renderedContent, px, py, maxW, ChatColors.TEXT_DIM);
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

        // Context action buttons (recall / edit) when right-click selected
        if (selected) {
            int btnY = y + bubbleHeight - 14;
            int btnW = 30;
            int btnH = 12;
            // Recall button
            int recallX = bubbleX + bubbleWidth - (btnW * 2) - 8;
            graphics.fill(recallX, btnY, recallX + btnW, btnY + btnH, 0x88_3B82F6);
            graphics.drawString(font, "撤回", recallX + 4, btnY + 1, ChatColors.TEXT_PRIMARY);
            // Edit button
            int editX = recallX + btnW + 4;
            graphics.fill(editX, btnY, editX + btnW, btnY + btnH, 0x88_3B82F6);
            graphics.drawString(font, "编辑", editX + 4, btnY + 1, ChatColors.TEXT_PRIMARY);
        }
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
        // Main response text — manual word-wrap to track link positions
        renderManualWordWrap(graphics, font, bubbleX + PADDING, contentY, innerWidth, ChatColors.TEXT_SECONDARY);
    }

    // ==================== Manual word-wrap rendering with link tracking ====================

    /**
     * Renders text with manual word wrapping, detecting and highlighting links.
     * Also populates {@link #linkRegions} for click targeting.
     */
    private void renderManualWordWrap(GuiGraphics graphics, Font font, int x, int y, int maxWidth, int color) {
        linkRegions.clear();
        doManualWordWrap(graphics, font, x, y, maxWidth, color);
    }

    /**
     * Computes link regions for this widget at a given screen position, without rendering.
     * Called during hit-testing so that link regions are available for click detection.
     */
    public void computeLinkPositions(int widgetX, int widgetY, Font font, int listWidth) {
        if (!"assistant".equals(message.role()) || linkUrls.isEmpty()) return;

        int bubbleMaxWidth = listWidth - AVATAR_SIZE - AVATAR_GAP - PADDING;
        if (bubbleMaxWidth <= 20) bubbleMaxWidth = 20;
        int innerWidth = bubbleMaxWidth - PADDING * 2;
        if (innerWidth <= 10) innerWidth = 10;

        int bubbleX = widgetX + AVATAR_SIZE + AVATAR_GAP;
        int contentY = widgetY + PADDING;

        // Adjust for reasoning section (same offsets as renderAIBubble)
        if (hasReasoning()) {
            contentY += REASONING_HEADER_HEIGHT + 2;
            if (isReasoningExpanded()) {
                contentY += 4; // separator area
                int reasoningHeight = font.wordWrapHeight(renderedReasoning, innerWidth);
                contentY += reasoningHeight + 4; // reasoning text + bottom gap
            }
        }

        linkRegions.clear();
        doManualWordWrap(null, font, bubbleX + PADDING, contentY, innerWidth, 0);
    }

    /**
     * Core word-wrapping logic. Populates {@link #linkRegions} and optionally renders.
     * @param graphics nullable — if null, only link positions are computed (no drawing)
     */
    private void doManualWordWrap(GuiGraphics graphics,
            Font font, int x, int y, int maxWidth, int color) {

        String plainText = renderedContent.getString();
        String raw = message.content();

        // Build link table from raw markdown: [text](url) and plain URLs
        java.util.List<int[]> links = new java.util.ArrayList<>(); // [start, end, urlIndex]
        java.util.List<String> urls = new java.util.ArrayList<>();

        // Markdown [text](url) links
        int searchOffset = 0;
        Matcher m = MARKDOWN_LINK_PATTERN.matcher(raw);
        while (m.find()) {
            String linkText = m.group(1);
            String url = m.group(2);
            int pos = plainText.indexOf(linkText, searchOffset);
            if (pos >= 0) {
                links.add(new int[]{pos, pos + linkText.length(), urls.size()});
                urls.add(url);
                searchOffset = pos + linkText.length();
            }
        }

        // Plain http(s):// URLs
        int urlSearchOffset = 0;
        Matcher urlMatcher = PLAIN_URL_PATTERN.matcher(raw);
        while (urlMatcher.find()) {
            String url = urlMatcher.group();
            int pos = plainText.indexOf(url, urlSearchOffset);
            if (pos >= 0) {
                boolean covered = false;
                for (int[] link : links) {
                    if (pos >= link[0] && pos < link[1]) { covered = true; break; }
                }
                if (!covered) {
                    links.add(new int[]{pos, pos + url.length(), urls.size()});
                    urls.add(url);
                }
                urlSearchOffset = pos + url.length();
            }
        }

        // Manual word wrapping — split on whitespace boundaries, preserving delimiters
        String[] words = plainText.split("(?<=\\s)|(?=\\s)");
        int lineWidth = 0;
        int currentY = y;
        int charIndex = 0;
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            // Handle explicit newline characters
            if (word.equals("\n")) {
                if (currentLine.length() > 0) {
                    int lineStartChar = charIndex - currentLine.length();
                    processLine(graphics, font, currentLine.toString(), x, currentY,
                            lineStartChar, color, links, urls);
                }
                currentLine.setLength(0);
                lineWidth = 0;
                currentY += font.lineHeight;
                charIndex++; // skip the newline
                continue;
            }

            int wordWidth = font.width(word);
            if (lineWidth + wordWidth > maxWidth && lineWidth > 0) {
                // Line would overflow — render current line and start new
                if (currentLine.length() > 0) {
                    int lineStartChar = charIndex - currentLine.length();
                    processLine(graphics, font, currentLine.toString(), x, currentY,
                            lineStartChar, color, links, urls);
                }
                currentLine.setLength(0);
                lineWidth = 0;
                currentY += font.lineHeight;
            }
            currentLine.append(word);
            lineWidth += wordWidth;
            charIndex += word.length();
        }

        // Render final line
        if (currentLine.length() > 0) {
            int lineStartChar = charIndex - currentLine.length();
            processLine(graphics, font, currentLine.toString(), x, currentY,
                    lineStartChar, color, links, urls);
        }
    }

    /**
     * Renders a single line, splitting on link boundaries for color highlighting.
     * Links are drawn with {@link ChatColors#TASK_ACTIVE} and their regions recorded.
     */
    private void processLine(GuiGraphics graphics,
            Font font, String line, int x, int y, int lineStartChar, int color,
            java.util.List<int[]> links, java.util.List<String> urls) {

        int lineLen = line.length();
        int charPos = 0;
        int pixelPos = x;

        while (charPos < lineLen) {
            int globalPos = lineStartChar + charPos;
            int[] foundLink = null;
            for (int[] link : links) {
                if (globalPos >= link[0] && globalPos < link[1]) {
                    foundLink = link;
                    break;
                }
            }

            if (foundLink != null) {
                int linkEndInLine = foundLink[1] - lineStartChar;
                if (linkEndInLine > lineLen) linkEndInLine = lineLen;
                String linkFragment = line.substring(charPos, linkEndInLine);
                int linkW = font.width(linkFragment);
                linkRegions.add(new LinkRegion(pixelPos, y, pixelPos + linkW,
                        y + font.lineHeight, urls.get(foundLink[2])));
                if (graphics != null) {
                    graphics.drawString(font, linkFragment, pixelPos, y, ChatColors.TASK_ACTIVE);
                }
                pixelPos += linkW;
                charPos = linkEndInLine;
            } else {
                // Find next link start or end of line
                int nextLink = lineLen;
                for (int[] link : links) {
                    int linkStart = link[0] - lineStartChar;
                    if (linkStart > charPos && linkStart < nextLink) {
                        nextLink = linkStart;
                    }
                }
                String plain = line.substring(charPos, nextLink);
                if (graphics != null) {
                    graphics.drawString(font, plain, pixelPos, y, color);
                }
                pixelPos += font.width(plain);
                charPos = nextLink;
            }
        }
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

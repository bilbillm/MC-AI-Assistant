package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.i18n.I18nHelper;
import com.lumoren.agentchat.i18n.I18nKeys;
import com.lumoren.agentchat.model.ActionCard;
import com.lumoren.agentchat.model.AssistantAction;
import com.lumoren.agentchat.model.CardSource;
import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.ui.card.ActionCardLayout;
import com.lumoren.agentchat.ui.theme.GuideTheme;
import com.lumoren.agentchat.ui.theme.MechanicalSprites;
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
 * Mechanical-styled chat bubble. Internal sub-component of
 * {@link MessageListWidget} (the registered widget); card geometry comes from
 * {@link ActionCardLayout}, the single layout model shared by rendering and
 * hit testing.
 * <p>
 * User messages: right-aligned, iron bubble with copper edge.
 * AI messages: left-aligned, raised iron bubble, collapsible reasoning drawer.
 */
public class ChatMessageWidget {

    private static final int PADDING = 8;
    private static final int AVATAR_SIZE = 16;
    private static final int AVATAR_GAP = 6;
    private static final int GAP = 6;
    private static final int REASONING_HEADER_HEIGHT = 14;
    private static final int SCROLLBAR_SAFETY = 8;

    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(([^\\)]+)\\)");
    private static final Pattern PLAIN_URL_PATTERN = Pattern.compile("https?://[^\\s<>\"']+");

    private final ChatMessage message;
    private final Component renderedContent;
    private final Component renderedReasoning;
    private final Set<String> expandedReasonings;
    private final Set<String> expandedCardDetails;
    private final boolean selected; // right-click selected for context actions
    private final List<String> linkUrls = new ArrayList<>();
    private final ActionCard actionCard;

    /** Records the bounding box (screen-absolute) and URL of a clickable link within this message. */
    public record LinkRegion(int x1, int y1, int x2, int y2, String url) {}
    public record ContextRegion(int x1, int y1, int x2, int y2, ContextAction action) {}
    public enum ContextAction { RECALL, EDIT, COPY }

    /** Result of a click inside an action card. */
    public record CardInteraction(AssistantAction action, CardSource source, boolean showAllToggle) {
        static final CardInteraction NONE = new CardInteraction(null, null, false);

        boolean isNone() {
            return action == null && source == null && !showAllToggle;
        }
    }

    private final List<LinkRegion> linkRegions = new ArrayList<>();
    private final List<ContextRegion> contextRegions = new ArrayList<>();

    public ChatMessageWidget(ChatMessage message, int maxWidth, Set<String> expandedReasonings) {
        this(message, maxWidth, expandedReasonings, new HashSet<>(), false);
    }

    public ChatMessageWidget(ChatMessage message, int maxWidth, Set<String> expandedReasonings, boolean selected) {
        this(message, maxWidth, expandedReasonings, new HashSet<>(), selected);
    }

    public ChatMessageWidget(ChatMessage message, int maxWidth, Set<String> expandedReasonings,
                             Set<String> expandedCardDetails, boolean selected) {
        this.message = message;
        this.expandedReasonings = expandedReasonings != null ? expandedReasonings : new HashSet<>();
        this.expandedCardDetails = expandedCardDetails != null ? expandedCardDetails : new HashSet<>();
        this.selected = selected;
        this.actionCard = ActionCard.fromToolResultContent(message.content());
        this.renderedContent = isError() || isTool() || isSystem()
                ? Component.literal(message.content())
                : MarkdownRenderer.render(message.content());
        this.renderedReasoning = hasReasoning()
                ? MarkdownRenderer.render(message.reasoningContent())
                : null;

        // Extract links from raw markdown for click handling
        if (!isError() && !isTool() && !isSystem()) {
            Matcher urlMatcher = PLAIN_URL_PATTERN.matcher(message.content());
            while (urlMatcher.find()) {
                linkUrls.add(urlMatcher.group());
            }
            Matcher m = MARKDOWN_LINK_PATTERN.matcher(message.content());
            while (m.find()) {
                linkUrls.add(m.group(2));
            }
        }
    }

    public void render(GuiGraphics graphics, int x, int y, int width, Font font) {
        if (isActionCard()) {
            renderActionCard(graphics, x, y, width, font);
        } else if (isError()) {
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
        if (isActionCard()) {
            return cardLayout(font, availableWidth).totalHeight() + GAP + 2;
        }
        if (isTool()) {
            return PADDING / 2 + font.lineHeight + 4 + GAP;
        }
        if (isSystem()) {
            int innerW = Math.max(10, availableWidth - PADDING * 2);
            return 2 + font.wordWrapHeight(renderedContent, innerW) + 4;
        }
        int bubbleMaxWidth = availableWidth - AVATAR_SIZE - AVATAR_GAP - PADDING;
        int innerWidth = Math.max(10, bubbleMaxWidth - PADDING * 2);
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
    public boolean isActionCard() { return actionCard != null; }
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

    public void toggleCardDetails() {
        if (expandedCardDetails.contains(message.id())) {
            expandedCardDetails.remove(message.id());
        } else {
            expandedCardDetails.add(message.id());
        }
    }

    public List<String> getLinkUrls() { return linkUrls; }
    public boolean hasLinks() { return !linkUrls.isEmpty(); }
    public List<LinkRegion> getLinkRegions() { return linkRegions; }
    public List<ContextRegion> getContextRegions() { return contextRegions; }

    // ==================== Action card (unified layout) ====================

    private ActionCardLayout cardLayout(Font font, int listWidth) {
        int innerW = Math.max(60, listWidth - PADDING * 2 - SCROLLBAR_SAFETY - 2 * ActionCardLayout.PADDING);
        return ActionCardLayout.compute(actionCard, innerW, measurer(font),
                expandedCardDetails.contains(message.id()),
                cardTypeLabel(actionCard.type()), showAllLabel());
    }

    private String showAllLabel() {
        if (expandedCardDetails.contains(message.id())) {
            return I18nHelper.translateToString(I18nKeys.CARD_SHOW_LESS);
        }
        return I18nHelper.translateToString(I18nKeys.CARD_SHOW_ALL, actionCard.facts().size());
    }

    static String cardTypeLabel(ActionCard.Type type) {
        String key = switch (type) {
            case PROJECT -> I18nKeys.CARD_TYPE_PROJECT;
            case MATERIALS -> I18nKeys.CARD_TYPE_MATERIALS;
            case RECIPE -> I18nKeys.CARD_TYPE_RECIPE;
            case WAYPOINT -> I18nKeys.CARD_TYPE_WAYPOINT;
            case GUIDE_STEP -> I18nKeys.CARD_TYPE_GUIDE_STEP;
            case DIAGNOSIS -> I18nKeys.CARD_TYPE_DIAGNOSIS;
            case SOURCE_SUMMARY -> I18nKeys.CARD_TYPE_SOURCE_SUMMARY;
            default -> I18nKeys.CARD_TYPE_STATUS;
        };
        return I18nHelper.translateToString(key);
    }

    private static ActionCardLayout.TextMeasurer measurer(Font font) {
        return new ActionCardLayout.TextMeasurer() {
            @Override
            public int width(String text) {
                return font.width(text);
            }

            @Override
            public int lineHeight() {
                return font.lineHeight;
            }

            @Override
            public int wrapHeight(String text, int maxWidth) {
                return font.wordWrapHeight(Component.literal(text), maxWidth);
            }
        };
    }

    /** Hit-test a card click; the SAME layout model as rendering is used. */
    public CardInteraction getCardInteractionAt(double mouseX, double mouseY, int widgetX, int widgetY,
                                                Font font, int listWidth) {
        if (!isActionCard()) {
            return CardInteraction.NONE;
        }
        ActionCardLayout layout = cardLayout(font, listWidth);
        int cardX = widgetX + 4;
        int cardY = widgetY + 2;
        int relX = (int) mouseX - cardX;
        int relY = (int) mouseY - cardY;
        AssistantAction action = layout.actionAt(relX, relY);
        if (action != null) {
            return new CardInteraction(action, null, false);
        }
        if (layout.showAllAt(relX, relY)) {
            return new CardInteraction(null, null, true);
        }
        CardSource source = layout.sourceAt(relX, relY);
        if (source != null) {
            return new CardInteraction(null, source, false);
        }
        return CardInteraction.NONE;
    }

    private void renderActionCard(GuiGraphics graphics, int x, int y, int width, Font font) {
        ActionCardLayout layout = cardLayout(font, width);
        int cardX = x + 4;
        int cardY = y + 2;
        int cardW = Math.max(80, width - PADDING - SCROLLBAR_SAFETY);
        int cardH = layout.totalHeight();
        int accent = accentForCard(actionCard.type());

        MechanicalSprites.blitPanel(graphics, MechanicalSprites.PANEL_IRON, cardX, cardY, cardW, cardH);
        graphics.fill(cardX + 2, cardY + 2, cardX + 4, cardY + cardH - 2, accent);

        graphics.drawString(font, layout.titleText(),
                cardX + ActionCardLayout.PADDING, cardY + layout.titleY(), GuideTheme.TEXT_PRIMARY);

        for (ActionCardLayout.TextLine line : layout.summaryLines()) {
            graphics.drawWordWrap(font, Component.literal(line.text()),
                    cardX + line.relX(), cardY + line.relY(), cardW - ActionCardLayout.PADDING * 2,
                    GuideTheme.TEXT_SECONDARY);
        }
        for (ActionCardLayout.TextLine line : layout.factLines()) {
            graphics.drawWordWrap(font, Component.literal(line.text()),
                    cardX + line.relX(), cardY + line.relY(), cardW - ActionCardLayout.PADDING * 2,
                    GuideTheme.TEXT_DIM);
        }

        if (layout.showAllRegion() != null) {
            var region = layout.showAllRegion();
            graphics.drawString(font, layout.showAllLabel(),
                    cardX + region.relX(), cardY + region.relY(), GuideTheme.WEB_BLUE);
        }

        for (var region : layout.sourceRegions()) {
            renderSourceBadge(graphics, font, cardX + region.relX(), cardY + region.relY(), region);
        }

        for (var region : layout.buttonRegions()) {
            MechanicalSprites.blitPanel(graphics, MechanicalSprites.TAB_SEGMENT,
                    region.relX() + cardX, region.relY() + cardY, region.width(), region.height());
            graphics.fill(region.relX() + cardX + 1, region.relY() + cardY + region.height() - 2,
                    region.relX() + cardX + region.width() - 1, region.relY() + cardY + region.height() - 1,
                    GuideTheme.BRASS);
            String label = region.action().label() == null || region.action().label().isBlank()
                    ? region.action().kind().name() : region.action().label();
            int labelW = font.width(label);
            graphics.drawString(font, label,
                    region.relX() + cardX + (region.width() - labelW) / 2,
                    region.relY() + cardY + 3, GuideTheme.TEXT_PRIMARY);
        }
    }

    private void renderSourceBadge(GuiGraphics graphics, Font font, int x, int y,
                                   ActionCardLayout.SourceRegion region) {
        CardSource source = region.source();
        int dotColor = "WEB".equals(source.type())
                ? GuideTheme.WEB_BLUE
                : source.verified() ? GuideTheme.VERDIGRIS : GuideTheme.STEEL_EDGE;
        int yCenter = y + region.height() / 2;
        graphics.fill(x + 1, yCenter - 3, x + 7, yCenter + 3, dotColor);
        int color = source.isWebLink() ? GuideTheme.WEB_BLUE : GuideTheme.TEXT_DIM;
        graphics.drawString(font, region.label(), x + 10, yCenter - 4, color);
    }

    private static int accentForCard(ActionCard.Type type) {
        return switch (type) {
            case DIAGNOSIS -> GuideTheme.DANGER_RED;
            case GUIDE_STEP, PROJECT -> GuideTheme.BRASS;
            case RECIPE, MATERIALS -> GuideTheme.COPPER;
            case WAYPOINT, STATUS -> GuideTheme.VERDIGRIS;
            case SOURCE_SUMMARY -> GuideTheme.WEB_BLUE;
        };
    }

    /** Check if a click at absolute screen coords hits the reasoning toggle for this widget at (widgetX, widgetY). */
    public boolean hitReasoningToggle(double mx, double my, int widgetX, int widgetY, Font font) {
        if (!hasReasoning()) return false;
        int toggleAbsX = widgetX + AVATAR_SIZE + AVATAR_GAP + PADDING;
        int toggleAbsY = widgetY + PADDING;
        String toggleLabel = reasoningToggleLabel();
        int tw = font.width(toggleLabel) + 8;
        int th = REASONING_HEADER_HEIGHT;
        return mx >= toggleAbsX && mx <= toggleAbsX + tw
                && my >= toggleAbsY && my <= toggleAbsY + th;
    }

    private String reasoningToggleLabel() {
        return (isReasoningExpanded() ? "▼ " : "▶ ")
                + I18nHelper.translateToString(I18nKeys.REASONING_TOGGLE);
    }

    // ==================== Rendering ====================

    private void renderError(GuiGraphics graphics, int x, int y, int width, Font font) {
        int innerWidth = Math.max(10, width - PADDING * 2);
        graphics.fill(x + 2, y + 2, x + 4, y + PADDING + font.wordWrapHeight(renderedContent, innerWidth) + 2,
                GuideTheme.DANGER_RED);
        graphics.drawWordWrap(font, renderedContent, x + PADDING, y + PADDING, innerWidth, GuideTheme.DANGER_RED);
    }

    private void renderToolBlock(GuiGraphics graphics, int x, int y, int width, Font font) {
        int px = x + PADDING;
        int py = y + PADDING / 2;
        int maxW = Math.max(10, width - PADDING * 2);

        MechanicalSprites.blitIcon(graphics, MechanicalSprites.spinnerFrame(System.currentTimeMillis()),
                px, py - 1);
        graphics.drawString(font, renderedContent.getString(), px + 16, py + 2, GuideTheme.TEXT_DIM);
    }

    private void renderSystemText(GuiGraphics graphics, int x, int y, int width, Font font) {
        int px = x + PADDING;
        int py = y + 2;
        int maxW = width - PADDING * 2;
        graphics.drawWordWrap(font, renderedContent, px, py, maxW, GuideTheme.TEXT_DIM);
    }

    private void renderUserBubble(GuiGraphics graphics, int x, int y, int width, Font font) {
        contextRegions.clear();
        int bubbleMaxWidth = Math.max(20, width - AVATAR_SIZE - AVATAR_GAP - PADDING);
        int innerWidth = Math.max(10, bubbleMaxWidth - PADDING * 2);

        int textHeight = font.wordWrapHeight(renderedContent, innerWidth);
        int bubbleWidth = Math.min(bubbleMaxWidth, maxContentWidth(font, renderedContent, bubbleMaxWidth));
        int bubbleHeight = PADDING + textHeight + PADDING;

        int bubbleX = x + width - AVATAR_SIZE - AVATAR_GAP - bubbleWidth;
        drawAvatar(graphics, x + width - AVATAR_SIZE, y, "U", font);
        MechanicalSprites.blitPanel(graphics, MechanicalSprites.PANEL_IRON,
                bubbleX, y, bubbleWidth, bubbleHeight);
        // copper right edge marks the user's own messages
        graphics.fill(bubbleX + bubbleWidth - 3, y + 2, bubbleX + bubbleWidth - 1, y + bubbleHeight - 2,
                GuideTheme.COPPER);
        graphics.drawWordWrap(font, renderedContent, bubbleX + PADDING, y + PADDING, innerWidth,
                GuideTheme.TEXT_PRIMARY);

        if (selected) {
            int btnY = y + bubbleHeight - 16;
            int copyX = bubbleX + bubbleWidth - 18;
            int editX = copyX - 18;
            int recallX = editX - 18;
            drawContextIcon(graphics, MechanicalSprites.ICON_RECALL, recallX, btnY);
            contextRegions.add(new ContextRegion(recallX - 2, btnY - 2, recallX + 14, btnY + 14, ContextAction.RECALL));
            drawContextIcon(graphics, MechanicalSprites.ICON_EDIT, editX, btnY);
            contextRegions.add(new ContextRegion(editX - 2, btnY - 2, editX + 14, btnY + 14, ContextAction.EDIT));
            drawContextIcon(graphics, MechanicalSprites.ICON_COPY, copyX, btnY);
            contextRegions.add(new ContextRegion(copyX - 2, btnY - 2, copyX + 14, btnY + 14, ContextAction.COPY));
        }
    }

    private void renderAIBubble(GuiGraphics graphics, int x, int y, int width, Font font) {
        contextRegions.clear();
        int bubbleMaxWidth = Math.max(20, width - AVATAR_SIZE - AVATAR_GAP - PADDING);
        int innerWidth = Math.max(10, bubbleMaxWidth - PADDING * 2);

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
        if (hasReasoning() && isReasoningExpanded()) {
            bubbleWidth = Math.max(bubbleWidth, maxContentWidth(font, renderedReasoning, bubbleMaxWidth));
        }
        if (hasReasoning()) {
            String toggleLabel = reasoningToggleLabel();
            bubbleWidth = Math.max(bubbleWidth, font.width(toggleLabel) + PADDING * 2 + 20);
        }
        int bubbleHeight = reasoningExtra + PADDING + textHeight + PADDING;

        int avatarX = x;
        int bubbleX = x + AVATAR_SIZE + AVATAR_GAP;

        drawAvatar(graphics, avatarX, y, "AI", font);
        MechanicalSprites.blitPanel(graphics, MechanicalSprites.PANEL_IRON,
                bubbleX, y, bubbleWidth, bubbleHeight);
        graphics.fill(bubbleX + 2, y + 2, bubbleX + 4, y + bubbleHeight - 2, GuideTheme.STEEL_EDGE);

        int contentY = y + PADDING;
        if (hasReasoning()) {
            String toggleLabel = reasoningToggleLabel();
            graphics.drawString(font, toggleLabel, bubbleX + PADDING, contentY, GuideTheme.TEXT_DIM);
            contentY += REASONING_HEADER_HEIGHT + 2;

            if (isReasoningExpanded()) {
                graphics.drawWordWrap(font, renderedReasoning, bubbleX + PADDING, contentY, innerWidth,
                        GuideTheme.TEXT_DIM);
                contentY += reasoningHeight + 2;
                graphics.fill(bubbleX + PADDING, contentY, bubbleX + bubbleWidth - PADDING, contentY + 1,
                        GuideTheme.STEEL_EDGE);
                contentY += 4;
            }
        }
        graphics.drawWordWrap(font, renderedContent, bubbleX + PADDING, contentY, innerWidth,
                GuideTheme.TEXT_SECONDARY);

        if (selected) {
            int btnY = y + bubbleHeight - 16;
            int copyX = bubbleX + bubbleWidth - 18;
            drawContextIcon(graphics, MechanicalSprites.ICON_COPY, copyX, btnY);
            contextRegions.add(new ContextRegion(copyX - 2, btnY - 2, copyX + 14, btnY + 14, ContextAction.COPY));
        }
    }

    private static void drawContextIcon(GuiGraphics graphics, net.minecraft.resources.ResourceLocation icon,
                                        int x, int y) {
        MechanicalSprites.blitNineSlice(graphics, MechanicalSprites.TAB_SEGMENT, x - 2, y - 2, 16, 16, 24, 24, 8);
        MechanicalSprites.blitIcon(graphics, icon, x, y);
    }

    // ==================== Manual word-wrap rendering with link tracking =================

    /**
     * Computes link regions for this widget at a given screen position, without rendering.
     * Called during hit-testing so that link regions are available for click detection.
     */
    public void computeLinkPositions(int widgetX, int widgetY, Font font, int listWidth) {
        if (!"assistant".equals(message.role()) || linkUrls.isEmpty()) return;

        int bubbleMaxWidth = Math.max(20, listWidth - AVATAR_SIZE - AVATAR_GAP - PADDING);
        int innerWidth = Math.max(10, bubbleMaxWidth - PADDING * 2);

        int bubbleX = widgetX + AVATAR_SIZE + AVATAR_GAP;
        int contentY = widgetY + PADDING;

        // Adjust for reasoning section (same offsets as renderAIBubble)
        if (hasReasoning()) {
            contentY += REASONING_HEADER_HEIGHT + 2;
            if (isReasoningExpanded()) {
                contentY += 4;
                int reasoningHeight = font.wordWrapHeight(renderedReasoning, innerWidth);
                contentY += reasoningHeight + 4;
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

        java.util.List<int[]> links = new java.util.ArrayList<>();
        java.util.List<String> urls = new java.util.ArrayList<>();

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

        String[] words = plainText.split("(?<=\\s)|(?=\\s)");
        int lineWidth = 0;
        int currentY = y;
        int charIndex = 0;
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (word.equals("\n")) {
                if (currentLine.length() > 0) {
                    int lineStartChar = charIndex - currentLine.length();
                    processLine(graphics, font, currentLine.toString(), x, currentY,
                            lineStartChar, color, links, urls);
                }
                currentLine.setLength(0);
                lineWidth = 0;
                currentY += font.lineHeight;
                charIndex++;
                continue;
            }

            int wordWidth = font.width(word);
            if (lineWidth + wordWidth > maxWidth && lineWidth > 0) {
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

        if (currentLine.length() > 0) {
            int lineStartChar = charIndex - currentLine.length();
            processLine(graphics, font, currentLine.toString(), x, currentY,
                    lineStartChar, color, links, urls);
        }
    }

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
                    graphics.drawString(font, linkFragment, pixelPos, y, GuideTheme.WEB_BLUE);
                }
                pixelPos += linkW;
                charPos = linkEndInLine;
            } else {
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

    private static void drawAvatar(GuiGraphics graphics, int x, int y, String label, Font font) {
        MechanicalSprites.blitNineSlice(graphics, MechanicalSprites.TAB_SEGMENT,
                x, y, AVATAR_SIZE, AVATAR_SIZE, 24, 24, 8);
        int textWidth = font.width(label);
        int textX = x + (AVATAR_SIZE - textWidth) / 2;
        int textY = y + (AVATAR_SIZE - font.lineHeight) / 2;
        graphics.drawString(font, label, textX, textY, GuideTheme.TEXT_SECONDARY);
    }
}

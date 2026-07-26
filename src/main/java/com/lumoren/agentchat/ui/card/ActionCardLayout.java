package com.lumoren.agentchat.ui.card;

import com.lumoren.agentchat.model.ActionCard;
import com.lumoren.agentchat.model.AssistantAction;
import com.lumoren.agentchat.model.CardSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Single layout model for {@link ActionCard} rendering AND hit testing.
 * Both the render pass and the click pass consume the exact same regions —
 * positions are never computed twice.
 * <p>
 * All geometry is relative to the card origin (0,0); the caller adds its
 * absolute offset. Text measurement goes through {@link TextMeasurer} so the
 * layout is fully unit-testable without a Minecraft Font.
 */
public final class ActionCardLayout {

    /** Abstraction over font metrics (Minecraft Font in production). */
    public interface TextMeasurer {
        int width(String text);

        int lineHeight();

        int wrapHeight(String text, int maxWidth);
    }

    public record ButtonRegion(int relX, int relY, int width, int height, AssistantAction action) {
    }

    public record ToggleRegion(int relX, int relY, int width, int height) {
    }

    public record SourceRegion(int relX, int relY, int width, int height, CardSource source, String label) {
    }

    /** A positioned line of wrapped text (facts/summary) for rendering. */
    public record TextLine(int relX, int relY, String text, int colorRole) {
    }

    public static final int PADDING = 8;
    public static final int BUTTON_HEIGHT = 14;
    public static final int BUTTON_GAP = 4;
    public static final int VISIBLE_FACTS = 3;
    public static final int MAX_BADGES = 6;

    private final ActionCard card;
    private final boolean factsExpanded;
    private final List<TextLine> summaryLines = new ArrayList<>();
    private final List<TextLine> factLines = new ArrayList<>();
    private final List<SourceRegion> sourceRegions = new ArrayList<>();
    private final List<ButtonRegion> buttonRegions = new ArrayList<>();
    private ToggleRegion showAllRegion;
    private String titleText = "";
    private String showAllLabel = "";
    private int titleY;
    private int factsY;
    private int sourcesY;
    private int buttonsY;
    private int totalHeight;

    private ActionCardLayout(ActionCard card, boolean factsExpanded) {
        this.card = card;
        this.factsExpanded = factsExpanded;
    }

    /**
     * Computes the full layout for a card at the given inner width.
     *
     * @param typeLabel localized display name of the card type
     * @param showAllLabel label for the expand toggle (e.g. "Show all (7)")
     */
    public static ActionCardLayout compute(ActionCard card, int innerWidth, TextMeasurer measurer,
                                           boolean factsExpanded, String typeLabel, String showAllLabel) {
        ActionCardLayout layout = new ActionCardLayout(card, factsExpanded);
        layout.doLayout(innerWidth, measurer, typeLabel, showAllLabel);
        return layout;
    }

    private void doLayout(int innerW, TextMeasurer m, String typeLabel, String showAll) {
        int textX = PADDING;
        int y = PADDING;

        titleText = (typeLabel == null || typeLabel.isBlank() ? "" : "[" + typeLabel + "] ")
                + (card.title() == null ? "" : card.title());
        titleY = y;
        y += m.lineHeight() + 3;

        if (card.summary() != null && !card.summary().isBlank()) {
            summaryLines.add(new TextLine(textX, y, card.summary(), 1));
            y += m.wrapHeight(card.summary(), innerW) + 3;
        }

        List<String> facts = card.facts();
        int visible = factsExpanded ? facts.size() : Math.min(VISIBLE_FACTS, facts.size());
        factsY = y;
        for (int i = 0; i < visible; i++) {
            String fact = "- " + facts.get(i);
            factLines.add(new TextLine(textX, y, fact, 2));
            y += m.wrapHeight(fact, innerW) + 1;
        }
        if (facts.size() > VISIBLE_FACTS) {
            showAllLabel = showAll;
            showAllRegion = new ToggleRegion(textX, y, m.width(showAll) + 4, m.lineHeight());
            y += m.lineHeight() + 2;
        }

        if (!card.sources().isEmpty()) {
            sourcesY = y;
            int badgeX = textX;
            int count = 0;
            for (CardSource source : card.sources()) {
                if (count >= MAX_BADGES) {
                    break;
                }
                String label = badgeLabel(source);
                // Shrink long labels to the remaining row width instead of
                // dropping the badge entirely.
                int available = textX + innerW - badgeX - 14;
                while (m.width(label) > available && label.length() > 4) {
                    label = label.substring(0, label.length() - 2) + "…";
                }
                int badgeW = 10 + m.width(label) + 4;
                if (badgeW > innerW) {
                    badgeW = innerW;
                }
                sourceRegions.add(new SourceRegion(badgeX, y, badgeW, m.lineHeight() + 4, source, label));
                badgeX += badgeW + 4;
                count++;
                if (badgeX >= textX + innerW - 20) {
                    break;
                }
            }
            y += m.lineHeight() + 6;
        }

        if (!card.actions().isEmpty()) {
            buttonsY = y + BUTTON_GAP;
            int bx = textX;
            int by = buttonsY;
            for (AssistantAction action : card.actions()) {
                String label = action.label() == null || action.label().isBlank()
                        ? action.kind().name() : action.label();
                int buttonW = Math.min(innerW, Math.max(42, m.width(label) + 12));
                if (bx + buttonW > textX + innerW) {
                    // wrap to the next row instead of dropping the button
                    bx = textX;
                    by += BUTTON_HEIGHT + BUTTON_GAP;
                }
                buttonRegions.add(new ButtonRegion(bx, by, buttonW, BUTTON_HEIGHT, action));
                bx += buttonW + BUTTON_GAP;
            }
            if (!buttonRegions.isEmpty()) {
                ButtonRegion last = buttonRegions.get(buttonRegions.size() - 1);
                y = last.relY() + BUTTON_HEIGHT;
            }
        }

        totalHeight = y + PADDING;
    }

    private static String badgeLabel(CardSource source) {
        String label = source.label();
        if (label == null || label.isBlank()) {
            label = source.type();
        }
        return label.length() > 24 ? label.substring(0, 23) + "…" : label;
    }

    public String titleText() {
        return titleText;
    }

    public int titleY() {
        return titleY;
    }

    public List<TextLine> summaryLines() {
        return summaryLines;
    }

    public int factsY() {
        return factsY;
    }

    public List<TextLine> factLines() {
        return factLines;
    }

    public int sourcesY() {
        return sourcesY;
    }

    public int buttonsY() {
        return buttonsY;
    }

    public int totalHeight() {
        return totalHeight;
    }

    public List<SourceRegion> sourceRegions() {
        return sourceRegions;
    }

    public List<ButtonRegion> buttonRegions() {
        return buttonRegions;
    }

    public ToggleRegion showAllRegion() {
        return showAllRegion;
    }

    public String showAllLabel() {
        return showAllLabel;
    }

    public boolean hasSources() {
        return !sourceRegions.isEmpty();
    }

    /** Hit test in card-relative coordinates. */
    public AssistantAction actionAt(int relX, int relY) {
        for (ButtonRegion region : buttonRegions) {
            if (relX >= region.relX() && relX <= region.relX() + region.width()
                    && relY >= region.relY() && relY <= region.relY() + region.height()) {
                return region.action();
            }
        }
        return null;
    }

    /** Show-all toggle hit test in card-relative coordinates. */
    public boolean showAllAt(int relX, int relY) {
        ToggleRegion region = showAllRegion;
        return region != null
                && relX >= region.relX() && relX <= region.relX() + region.width()
                && relY >= region.relY() && relY <= region.relY() + region.height();
    }

    /** Source badge hit test in card-relative coordinates. */
    public CardSource sourceAt(int relX, int relY) {
        for (SourceRegion region : sourceRegions) {
            if (relX >= region.relX() && relX <= region.relX() + region.width()
                    && relY >= region.relY() && relY <= region.relY() + region.height()) {
                return region.source();
            }
        }
        return null;
    }
}

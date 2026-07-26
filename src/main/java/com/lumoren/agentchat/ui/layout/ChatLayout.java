package com.lumoren.agentchat.ui.layout;

import com.lumoren.agentchat.ui.theme.GuideTheme;

/**
 * Pure responsive layout calculator for {@code AIChatScreen}. No Minecraft
 * runtime — fully unit-testable. Three tiers:
 * <ul>
 *   <li>{@link #TIER_WIDE} (width &ge; 480): docked sidebar or rail</li>
 *   <li>{@link #TIER_COMPACT} (320–479): 22 px rail, overlay drawer on expand</li>
 *   <li>{@link #TIER_NARROW} (&lt; 320): no rail, overlay drawer via toolbar</li>
 * </ul>
 */
public record ChatLayout(
        int tier,
        boolean sidebarInline,        // docked panel/rail shifts content right
        int sidebarX, int sidebarY, int sidebarW, int sidebarH,
        int contentX, int contentY, int contentW, int contentH,
        int titleX, int titleY, int titleH,
        int segmentedX, int segmentedY, int segmentedW, int segmentedH,
        int toolbarX, int toolbarY, int toolbarH,
        boolean sourceRailVisible,
        int sourceRailX, int sourceRailY, int sourceRailH,
        int messageX, int messageY, int messageW, int messageH,
        int inputX, int inputY, int inputW, int inputH,
        int sendX, int sendY, int sendH
) {
    public static final int TIER_NARROW = 0;
    public static final int TIER_COMPACT = 1;
    public static final int TIER_WIDE = 2;

    public static int tierOf(int screenWidth) {
        if (screenWidth < GuideTheme.WIDTH_NARROW) {
            return TIER_NARROW;
        }
        return screenWidth < GuideTheme.WIDTH_COMPACT ? TIER_COMPACT : TIER_WIDE;
    }

    /**
     * Computes the full layout.
     *
     * @param sidebarExpanded whether the user wants the thread list open
     * @param segmentedWidth  measured width of the mode segmented control
     * @param toolbarButtons  number of 20x20 icon buttons in the toolbar
     */
    public static ChatLayout compute(int screenW, int screenH, boolean sidebarExpanded,
                                     int segmentedWidth, int toolbarButtons) {
        int pad = GuideTheme.PADDING;
        int tier = tierOf(screenW);

        int inlineSidebarW;
        if (sidebarExpanded) {
            inlineSidebarW = tier == TIER_WIDE ? GuideTheme.SIDEBAR_WIDTH : 0;
        } else {
            inlineSidebarW = tier == TIER_NARROW ? 0 : GuideTheme.SIDEBAR_RAIL_WIDTH;
        }

        int contentX = pad + inlineSidebarW + (inlineSidebarW > 0 ? pad : 0);
        int contentRight = screenW - pad;
        int contentW = Math.max(120, contentRight - contentX);

        // Sidebar rect: docked panel/rail when inline, overlay drawer otherwise
        int sidebarW;
        if (sidebarExpanded) {
            sidebarW = tier == TIER_WIDE ? GuideTheme.SIDEBAR_WIDTH
                    : Math.min(GuideTheme.SIDEBAR_WIDTH, (int) (screenW * 0.6));
        } else {
            sidebarW = tier == TIER_NARROW ? 0 : GuideTheme.SIDEBAR_RAIL_WIDTH;
        }
        int sidebarH = screenH - pad * 2;

        int contentY = pad;
        int contentH = screenH - pad * 2;

        int titleH = GuideTheme.TITLE_BAR_HEIGHT;
        int toolbarH = GuideTheme.ICON_BUTTON_SIZE;
        int toolbarW = toolbarButtons * (GuideTheme.ICON_BUTTON_SIZE + 2) - 2;
        int toolbarX = contentRight - toolbarW;
        int toolbarY = contentY + (titleH - toolbarH) / 2;

        int segmentedW = Math.min(segmentedWidth, Math.max(64, toolbarX - 6 - (contentX + 2)));
        int segmentedX = toolbarX - 4 - segmentedW;
        int segmentedH = toolbarH;
        int segmentedY = toolbarY;

        boolean sourceRailVisible = tier != TIER_NARROW && screenH >= 220;
        int sourceRailH = sourceRailVisible ? 14 : 0;
        int sourceRailY = contentY + titleH + 2;

        int inputH = GuideTheme.INPUT_BAR_HEIGHT;
        int inputY = screenH - pad - inputH;
        int sendH = inputH - 8;
        int sendX = contentRight - sendH - 4;
        int inputX = contentX + 6;
        int inputW = Math.max(tier == TIER_NARROW ? 180 : 120, sendX - 6 - inputX);

        int messageY = contentY + titleH + 2 + (sourceRailVisible ? sourceRailH + 4 : 0);
        int messageH = Math.max(20, inputY - 4 - messageY);

        return new ChatLayout(
                tier,
                inlineSidebarW > 0,
                pad, pad, sidebarW, sidebarH,
                contentX, contentY, contentW, contentH,
                contentX + 4, contentY, titleH,
                segmentedX, segmentedY, segmentedW, segmentedH,
                toolbarX, toolbarY, toolbarH,
                sourceRailVisible,
                contentX + 4, sourceRailY, sourceRailH,
                contentX, messageY, contentW, messageH,
                inputX, inputY + (inputH - 18) / 2, inputW, inputH,
                sendX, inputY + 4, sendH);
    }
}

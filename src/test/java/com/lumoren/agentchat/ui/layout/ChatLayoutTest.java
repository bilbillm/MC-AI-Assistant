package com.lumoren.agentchat.ui.layout;

import com.lumoren.agentchat.ui.theme.GuideTheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatLayoutTest {

    @Test
    void tierBoundaries() {
        assertEquals(ChatLayout.TIER_NARROW, ChatLayout.tierOf(200));
        assertEquals(ChatLayout.TIER_NARROW, ChatLayout.tierOf(319));
        assertEquals(ChatLayout.TIER_COMPACT, ChatLayout.tierOf(320));
        assertEquals(ChatLayout.TIER_COMPACT, ChatLayout.tierOf(426));
        assertEquals(ChatLayout.TIER_WIDE, ChatLayout.tierOf(480));
        assertEquals(ChatLayout.TIER_WIDE, ChatLayout.tierOf(854));
    }

    @Test
    void wideExpandedDocksSidebarAndShiftsContent() {
        ChatLayout layout = ChatLayout.compute(854, 480, true, 90, 4);

        assertEquals(ChatLayout.TIER_WIDE, layout.tier());
        assertTrue(layout.sidebarInline());
        assertEquals(GuideTheme.SIDEBAR_WIDTH, layout.sidebarW());
        assertEquals(GuideTheme.PADDING + GuideTheme.SIDEBAR_WIDTH + GuideTheme.PADDING,
                layout.contentX());
        assertTrue(layout.contentW() >= 300, "main area should stay generous on wide tier");
    }

    @Test
    void wideCollapsedUsesRail() {
        ChatLayout layout = ChatLayout.compute(854, 480, false, 90, 4);

        assertEquals(GuideTheme.SIDEBAR_RAIL_WIDTH, layout.sidebarW());
        assertTrue(layout.contentX() < GuideTheme.PADDING + GuideTheme.SIDEBAR_WIDTH);
    }

    @Test
    void compactExpandedKeepsContentFullWidth() {
        ChatLayout layout = ChatLayout.compute(426, 240, true, 90, 4);

        assertEquals(ChatLayout.TIER_COMPACT, layout.tier());
        assertFalse(layout.sidebarInline(), "overlay drawer must not shift content");
        assertEquals(GuideTheme.PADDING, layout.contentX());
        assertTrue(layout.sidebarW() <= GuideTheme.SIDEBAR_WIDTH);
        assertTrue(layout.sourceRailVisible());
    }

    @Test
    void compactCollapsedShowsRail() {
        ChatLayout layout = ChatLayout.compute(426, 240, false, 90, 4);

        assertEquals(GuideTheme.SIDEBAR_RAIL_WIDTH, layout.sidebarW());
        assertTrue(layout.sidebarInline());
        assertEquals(GuideTheme.PADDING + GuideTheme.SIDEBAR_RAIL_WIDTH + GuideTheme.PADDING,
                layout.contentX());
    }

    @Test
    void narrowHidesSidebarAndKeepsInputUsable() {
        ChatLayout layout = ChatLayout.compute(300, 180, false, 90, 3);

        assertEquals(ChatLayout.TIER_NARROW, layout.tier());
        assertEquals(0, layout.sidebarW());
        assertFalse(layout.sourceRailVisible(), "source rail hides below 220px height");
        assertTrue(layout.inputW() >= 180, "input must stay >= 180px on narrow tier");
    }

    @Test
    void inputNeverCoversLastMessageOnAllTiers() {
        for (int w : new int[]{300, 426, 640, 854}) {
            for (boolean expanded : new boolean[]{true, false}) {
                ChatLayout layout = ChatLayout.compute(w, 240, expanded, 90, 4);
                int messageBottom = layout.messageY() + layout.messageH();
                assertTrue(messageBottom <= layout.inputY(),
                        "message list must end above the input bar (w=" + w + ", expanded=" + expanded + ")");
                assertTrue(layout.messageH() >= 20, "message list keeps a minimum height");
            }
        }
    }

    @Test
    void toolbarAndSegmentedStayInsideContent() {
        for (int w : new int[]{320, 426, 640}) {
            ChatLayout layout = ChatLayout.compute(w, 240, true, 90, 4);
            int contentRight = layout.contentX() + layout.contentW();
            assertTrue(layout.toolbarX() >= layout.contentX());
            assertTrue(layout.toolbarX() + 4 * 22 <= contentRight + 2);
            assertTrue(layout.segmentedX() >= layout.contentX(),
                    "segmented control must not overlap the sidebar");
        }
    }

    @Test
    void shortHeightHidesSourceRail() {
        ChatLayout layout = ChatLayout.compute(854, 200, true, 90, 4);
        assertFalse(layout.sourceRailVisible());
        ChatLayout tall = ChatLayout.compute(854, 240, true, 90, 4);
        assertTrue(tall.sourceRailVisible());
    }
}

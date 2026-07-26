package com.lumoren.agentchat.ui.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideThemeTest {

    @Test
    void interactionColorsAreOpaque() {
        assertEquals(0xFF, (GuideTheme.BRASS >>> 24) & 0xFF);
        assertEquals(0xFF, (GuideTheme.COPPER >>> 24) & 0xFF);
        assertEquals(0xFF, (GuideTheme.VERDIGRIS >>> 24) & 0xFF);
        assertEquals(0xFF, (GuideTheme.DANGER_RED >>> 24) & 0xFF);
        assertEquals(0xFF, (GuideTheme.TEXT_PRIMARY >>> 24) & 0xFF);
    }

    @Test
    void statusColorsAreDistinctFromInteractionColors() {
        assertNotEquals(GuideTheme.BRASS, GuideTheme.VERDIGRIS);
        assertNotEquals(GuideTheme.BRASS, GuideTheme.DANGER_RED);
        assertNotEquals(GuideTheme.COPPER, GuideTheme.VERDIGRIS);
        assertNotEquals(GuideTheme.WEB_BLUE, GuideTheme.BRASS);
    }

    @Test
    void layoutTiersAreOrdered() {
        assertTrue(GuideTheme.WIDTH_NARROW < GuideTheme.WIDTH_COMPACT);
        assertTrue(GuideTheme.SIDEBAR_RAIL_WIDTH < GuideTheme.SIDEBAR_WIDTH);
        assertTrue(GuideTheme.ICON_BUTTON_SIZE >= 20, "icon buttons must keep a 20px click target");
    }
}

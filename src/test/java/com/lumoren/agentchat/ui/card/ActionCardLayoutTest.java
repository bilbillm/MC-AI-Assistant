package com.lumoren.agentchat.ui.card;

import com.google.gson.JsonObject;
import com.lumoren.agentchat.model.ActionCard;
import com.lumoren.agentchat.model.AssistantAction;
import com.lumoren.agentchat.model.CardSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionCardLayoutTest {

    /** Deterministic measurer: 6px per char, 9px line, rough wrap simulation. */
    private static final ActionCardLayout.TextMeasurer FAKE = new ActionCardLayout.TextMeasurer() {
        @Override
        public int width(String text) {
            return text.length() * 6;
        }

        @Override
        public int lineHeight() {
            return 9;
        }

        @Override
        public int wrapHeight(String text, int maxWidth) {
            int charsPerLine = Math.max(1, maxWidth / 6);
            int lines = Math.max(1, (int) Math.ceil(text.length() / (double) charsPerLine));
            return lines * 9;
        }
    };

    private static AssistantAction action(String label, AssistantAction.Kind kind) {
        return new AssistantAction("id-" + label, label, kind, new JsonObject());
    }

    private static ActionCard card(List<String> facts, List<AssistantAction> actions,
                                   List<CardSource> sources) {
        return new ActionCard(ActionCard.Type.GUIDE_STEP, "Title", "Summary text here",
                facts, actions, "test_tool", sources);
    }

    @Test
    void actionButtonsWrapInsteadOfDisappearing() {
        List<AssistantAction> actions = List.of(
                action("Open recipe", AssistantAction.Kind.OPEN_RECIPE),
                action("Open usage", AssistantAction.Kind.OPEN_USAGE),
                action("Mark done", AssistantAction.Kind.MARK_TASK_DONE),
                action("Set current", AssistantAction.Kind.SET_CURRENT_TASK),
                action("Search more", AssistantAction.Kind.SEARCH_MORE));
        ActionCard c = card(List.of(), actions, List.of());

        ActionCardLayout layout = ActionCardLayout.compute(c, 120, FAKE, false, "Guide", "Show all (0)");

        assertEquals(5, layout.buttonRegions().size(), "all 5 buttons must be laid out");
        long distinctRows = layout.buttonRegions().stream().map(ActionCardLayout.ButtonRegion::relY).distinct().count();
        assertTrue(distinctRows > 1, "buttons must wrap onto multiple rows");
    }

    @Test
    void chineseLabelsMeasureWiderPerCharInRealFontButNeverVanish() {
        List<AssistantAction> actions = List.of(
                action("标记完成", AssistantAction.Kind.MARK_TASK_DONE),
                action("设为当前", AssistantAction.Kind.SET_CURRENT_TASK),
                action("查看项目", AssistantAction.Kind.PIN_TASK),
                action("搜索资料", AssistantAction.Kind.SEARCH_MORE));
        ActionCard c = card(List.of(), actions, List.of());

        for (int width : new int[]{60, 120, 220, 400}) {
            ActionCardLayout layout = ActionCardLayout.compute(c, width, FAKE, false, "向导", "展开全部");
            assertEquals(4, layout.buttonRegions().size(), "width=" + width);
        }
    }

    @Test
    void factsShowThreeAndExpandToAll() {
        List<String> facts = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            facts.add("fact number " + i);
        }
        ActionCard c = card(facts, List.of(), List.of());

        ActionCardLayout collapsed = ActionCardLayout.compute(c, 200, FAKE, false, "Guide", "Show all (10)");
        assertEquals(3, collapsed.factLines().size());
        assertNotNull(collapsed.showAllRegion());

        ActionCardLayout expanded = ActionCardLayout.compute(c, 200, FAKE, true, "Guide", "Show less");
        assertEquals(10, expanded.factLines().size());
        assertTrue(expanded.totalHeight() > collapsed.totalHeight());
    }

    @Test
    void longUrlBadgeIsTruncatedNotOverflowing() {
        CardSource web = new CardSource("WEB", "https://example.com/some/really/long/wiki/page/path",
                "https://example.com/some/really/long/wiki/page/path", true);
        ActionCard c = card(List.of(), List.of(), List.of(web));

        ActionCardLayout layout = ActionCardLayout.compute(c, 100, FAKE, false, "Guide", "");
        assertEquals(1, layout.sourceRegions().size());
        assertTrue(layout.sourceRegions().get(0).width() <= 100 + 10,
                "badge label must be truncated to fit");
    }

    @Test
    void hitTestMatchesButtonRegions() {
        List<AssistantAction> actions = List.of(
                action("One", AssistantAction.Kind.OPEN_RECIPE),
                action("Two", AssistantAction.Kind.OPEN_USAGE));
        ActionCard c = card(List.of(), actions, List.of());
        ActionCardLayout layout = ActionCardLayout.compute(c, 300, FAKE, false, "Guide", "");

        var first = layout.buttonRegions().get(0);
        assertEquals(actions.get(0), layout.actionAt(first.relX() + 2, first.relY() + 2));
        assertNull(layout.actionAt(0, 0));
    }

    @Test
    void hitTestMatchesShowAllToggle() {
        List<String> facts = List.of("a", "b", "c", "d", "e");
        ActionCard c = card(facts, List.of(), List.of());
        ActionCardLayout layout = ActionCardLayout.compute(c, 200, FAKE, false, "Guide", "Show all (5)");

        var region = layout.showAllRegion();
        assertNotNull(region);
        assertTrue(layout.showAllAt(region.relX() + 1, region.relY() + 1));
    }

    @Test
    void hitTestMatchesSourceBadges() {
        CardSource source = new CardSource("JEI", "jei", "jei:minecraft:piston", true);
        ActionCard c = card(List.of(), List.of(), List.of(source));
        ActionCardLayout layout = ActionCardLayout.compute(c, 200, FAKE, false, "Guide", "");

        var region = layout.sourceRegions().get(0);
        assertEquals(source, layout.sourceAt(region.relX() + 1, region.relY() + 1));
        assertNull(layout.sourceAt(500, 500));
    }

    @Test
    void emptyCardStillHasPositiveHeight() {
        ActionCard c = new ActionCard(ActionCard.Type.STATUS, "", "", List.of(), List.of(), "t");
        ActionCardLayout layout = ActionCardLayout.compute(c, 200, FAKE, false, "Status", "");
        assertTrue(layout.totalHeight() > 0);
    }
}

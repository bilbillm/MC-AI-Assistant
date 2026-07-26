package com.lumoren.agentchat.model;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActionCardTest {

    @Test
    void toToolResultContent_roundTripsActionCard() {
        JsonObject payload = new JsonObject();
        payload.addProperty("task_index", 2);
        AssistantAction action = new AssistantAction("a1", "Mark done",
                AssistantAction.Kind.MARK_TASK_DONE, payload);
        ActionCard card = new ActionCard(ActionCard.Type.PROJECT, "Project status",
                "Next: gather wood", List.of("Progress: 1/3"), List.of(action), "manage_project");

        ActionCard parsed = ActionCard.fromToolResultContent(card.toToolResultContent());

        assertNotNull(parsed);
        assertEquals(ActionCard.Type.PROJECT, parsed.type());
        assertEquals("Project status", parsed.title());
        assertEquals("Next: gather wood", parsed.summary());
        assertEquals(List.of("Progress: 1/3"), parsed.facts());
        assertEquals(1, parsed.actions().size());
        assertEquals(AssistantAction.Kind.MARK_TASK_DONE, parsed.actions().get(0).kind());
        assertEquals(2, parsed.actions().get(0).payload().get("task_index").getAsInt());
    }

    @Test
    void fromToolResultContent_returnsNullForPlainText() {
        assertNull(ActionCard.fromToolResultContent("plain tool result"));
    }

    @Test
    void fromToolResultContent_degradesUnknownTypeAndDropsUnknownActionKind() {
        ActionCard parsed = ActionCard.fromToolResultContent("""
                {
                  "action_card": {
                    "type": "FUTURE_CARD",
                    "title": "Future",
                    "summary": "Unknown schema from a newer tool",
                    "facts": ["still render this"],
                    "actions": [
                      {"id":"a1","label":"Do thing","kind":"FUTURE_ACTION","payload":{"x":1}},
                      {"id":"a2","label":"Known","kind":"SEARCH_MORE","payload":{"query":"q"}}
                    ],
                    "source_tool": "future_tool"
                  }
                }
                """);

        assertNotNull(parsed);
        assertEquals(ActionCard.Type.STATUS, parsed.type());
        assertEquals("Future", parsed.title());
        assertEquals(List.of("still render this"), parsed.facts());
        assertEquals(1, parsed.actions().size());
        assertEquals(AssistantAction.Kind.SEARCH_MORE, parsed.actions().get(0).kind());
    }

    @Test
    void sourcesRoundTripAndOldJsonWithoutSourcesIsCompatible() {
        ActionCard withSources = new ActionCard(ActionCard.Type.SOURCE_SUMMARY, "Sources",
                "where facts came from", List.of(),
                List.of(), "modpack_guide",
                List.of(new CardSource("WEB", "Wiki", "https://example.com/wiki", true),
                        new CardSource("JEI", "jei", "jei:minecraft:piston", true)));

        ActionCard parsed = ActionCard.fromToolResultContent(withSources.toToolResultContent());

        assertNotNull(parsed);
        assertEquals(2, parsed.sources().size());
        assertEquals("https://example.com/wiki", parsed.sources().get(0).sourceIdOrUrl());
        assertTrue(parsed.sources().get(0).isWebLink());
        assertFalse(parsed.sources().get(1).isWebLink());

        ActionCard legacy = ActionCard.fromToolResultContent("""
                {
                  "action_card": {
                    "type": "RECIPE",
                    "title": "Old card",
                    "summary": "no sources field",
                    "facts": [],
                    "actions": [],
                    "source_tool": "old_tool"
                  }
                }
                """);
        assertNotNull(legacy);
        assertTrue(legacy.sources().isEmpty());
    }

    @Test
    void toDisplayText_includesFactsAndActions() {
        ActionCard card = new ActionCard(ActionCard.Type.MATERIALS, "Missing materials",
                "1 item is missing", List.of("minecraft:oak_log: missing 4"),
                List.of(new AssistantAction(null, "Show project",
                        AssistantAction.Kind.PIN_TASK, new JsonObject())),
                "manage_project");

        String text = card.toDisplayText();

        assertTrue(text.contains("[MATERIALS] Missing materials"));
        assertTrue(text.contains("minecraft:oak_log"));
        assertTrue(text.contains("Show project"));
    }
}

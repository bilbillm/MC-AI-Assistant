package com.lumoren.agentchat.tools;

import com.lumoren.agentchat.model.ActionCard;
import com.lumoren.agentchat.model.AssistantAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenItemActionToolTest {
    @Test
    void openItemRecipe_returnsStructuredCardWithoutJeiOrWorld() {
        var result = new OpenItemRecipeTool().execute(null, "{\"item_name\":\"minecraft:stone\"}");

        ActionCard card = ActionCard.fromToolResultContent(result.content());

        assertNotNull(card);
        assertEquals(ActionCard.Type.RECIPE, card.type());
        assertTrue(card.title().contains("minecraft:stone"));
        assertTrue(card.facts().stream().anyMatch(f -> f.contains("Source:")));
        assertFalse(card.actions().isEmpty());
        assertEquals(AssistantAction.Kind.OPEN_USAGE, card.actions().get(0).kind());
    }

    @Test
    void openItemUsage_returnsStructuredCardWithoutJeiOrWorld() {
        var result = new OpenItemUsageTool().execute(null, "{\"item_name\":\"minecraft:stone\"}");

        ActionCard card = ActionCard.fromToolResultContent(result.content());

        assertNotNull(card);
        assertEquals(ActionCard.Type.RECIPE, card.type());
        assertTrue(card.title().contains("minecraft:stone"));
        assertTrue(card.facts().stream().anyMatch(f -> f.contains("Source:")));
        assertFalse(card.actions().isEmpty());
        assertEquals(AssistantAction.Kind.OPEN_RECIPE, card.actions().get(0).kind());
    }
}

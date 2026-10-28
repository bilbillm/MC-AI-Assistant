package com.lumoren.agentchat.tools;

import com.lumoren.agentchat.model.ActionCard;
import com.lumoren.agentchat.model.AssistantAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModpackGuideToolTest {
    @Test
    void definition_exposesQuestionAndIntent() {
        ModpackGuideTool tool = new ModpackGuideTool();

        var definition = tool.getDefinition();

        assertEquals("modpack_guide", definition.name());
        assertTrue(definition.parameters().getAsJsonObject("properties").has("question"));
        assertTrue(definition.parameters().getAsJsonObject("properties").has("intent"));
    }

    @Test
    void execute_recipePlanWithNamespacedItem_returnsRecipeActions() {
        ModpackGuideTool tool = new ModpackGuideTool();

        var result = tool.execute(null, """
                {"question":"minecraft:piston 怎么做","intent":"RECIPE_PLAN"}
                """);
        ActionCard card = ActionCard.fromToolResultContent(result.content());

        assertEquals(ActionCard.Type.GUIDE_STEP, card.type());
        assertEquals("Recipe plan", card.title());
        assertEquals(AssistantAction.Kind.OPEN_RECIPE, card.actions().get(0).kind());
        assertEquals("minecraft:piston", card.actions().get(0).payload().get("item_name").getAsString());
    }

    @Test
    void execute_diagnosisWithoutWorld_returnsDiagnosisCard() {
        ModpackGuideTool tool = new ModpackGuideTool();

        var result = tool.execute(null, """
                {"question":"为什么这个机器不工作","intent":"MACHINE_DIAGNOSIS"}
                """);
        ActionCard card = ActionCard.fromToolResultContent(result.content());

        assertEquals(ActionCard.Type.DIAGNOSIS, card.type());
        assertTrue(card.summary().contains("not in a world"));
        assertEquals(AssistantAction.Kind.SEARCH_MORE, card.actions().get(0).kind());
    }
}

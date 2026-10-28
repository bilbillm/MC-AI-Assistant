package com.lumoren.agentchat.model;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuideActionCardTest {
    @Test
    void actionCard_roundTripsGuideStepWithNewActionKind() {
        JsonObject payload = new JsonObject();
        payload.addProperty("task_index", 1);
        ActionCard card = new ActionCard(ActionCard.Type.GUIDE_STEP,
                "Next step",
                "Craft the generator",
                List.of("Source: JEI"),
                List.of(new AssistantAction("a1", "Set current",
                        AssistantAction.Kind.SET_CURRENT_TASK, payload)),
                "test");

        ActionCard parsed = ActionCard.fromToolResultContent(card.toToolResultContent());

        assertEquals(ActionCard.Type.GUIDE_STEP, parsed.type());
        assertEquals(AssistantAction.Kind.SET_CURRENT_TASK, parsed.actions().get(0).kind());
        assertEquals(1, parsed.actions().get(0).payload().get("task_index").getAsInt());
    }
}

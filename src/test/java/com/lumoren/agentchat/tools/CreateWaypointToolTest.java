package com.lumoren.agentchat.tools;

import com.lumoren.agentchat.model.ActionCard;
import com.lumoren.agentchat.model.ToolDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CreateWaypointToolTest {

    @Test
    void getDefinition_requiresName() {
        CreateWaypointTool tool = new CreateWaypointTool();
        ToolDefinition definition = tool.getDefinition();

        assertEquals("create_waypoint", definition.name());
        assertTrue(definition.parameters().getAsJsonObject("properties").has("name"));
        assertTrue(definition.parameters().getAsJsonArray("required").toString().contains("name"));
    }

    @Test
    void execute_withoutCoordinatesOrPlayerReturnsStatusCard() {
        CreateWaypointTool tool = new CreateWaypointTool();

        var result = tool.execute(null, "{\"name\":\"Home\"}");
        ActionCard card = ActionCard.fromToolResultContent(result.content());

        assertNotNull(card);
        assertEquals(ActionCard.Type.WAYPOINT, card.type());
        assertTrue(card.summary().contains("Cannot verify waypoint position"));
    }
}

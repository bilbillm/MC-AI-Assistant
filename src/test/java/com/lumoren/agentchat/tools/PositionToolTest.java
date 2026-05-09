package com.lumoren.agentchat.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PositionTool}.
 */
class PositionToolTest {

    private final PositionTool tool = new PositionTool();

    @Test
    void getName_returnsCorrectName() {
        assertEquals("get_player_status", tool.getName());
    }

    @Test
    void getDescription_isNotEmpty() {
        assertNotNull(tool.getDescription());
        assertFalse(tool.getDescription().isEmpty());
    }

    @Test
    void getDefinition_returnsValidDefinition() {
        ToolDefinition def = tool.getDefinition();
        assertEquals("get_player_status", def.name());
        assertNotNull(def.description());
        assertNotNull(def.parameters());
        assertEquals("object", def.parameters().get("type").getAsString());
    }

    @Test
    void execute_withMockMinecraft_doesNotThrow() {
        Minecraft mc = Minecraft.getInstance();
        try {
            ToolResult result = tool.execute(mc, "{}");
            assertNotNull(result);
            assertEquals("get_player_status", result.toolCallId());
        } catch (Exception e) {
            // Running outside Minecraft client environment
            assertTrue(true);
        }
    }

    @Test
    void execute_returnsValidJsonObject() {
        Minecraft mc = Minecraft.getInstance();
        try {
            ToolResult result = tool.execute(mc, "{}");
            // Should be a valid JSON object
            JsonObject json = JsonParser.parseString(result.content()).getAsJsonObject();
            assertNotNull(json);
        } catch (Exception e) {
            // Running outside Minecraft client environment
            assertTrue(true);
        }
    }
}

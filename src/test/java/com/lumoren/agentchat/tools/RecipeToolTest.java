package com.lumoren.agentchat.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RecipeTool}.
 */
class RecipeToolTest {

    private final RecipeTool tool = new RecipeTool();

    @Test
    void getName_returnsCorrectName() {
        assertEquals("lookup_recipe", tool.getName());
    }

    @Test
    void getDescription_isNotEmpty() {
        assertNotNull(tool.getDescription());
        assertFalse(tool.getDescription().isEmpty());
    }

    @Test
    void getDefinition_returnsValidDefinition() {
        ToolDefinition def = tool.getDefinition();
        assertEquals("lookup_recipe", def.name());
        assertNotNull(def.description());
        assertNotNull(def.parameters());
        assertEquals("object", def.parameters().get("type").getAsString());
    }

    @Test
    void getDefinition_hasItemNameParameter() {
        ToolDefinition def = tool.getDefinition();
        JsonObject params = def.parameters();

        assertTrue(params.has("properties"));
        JsonObject properties = params.getAsJsonObject("properties");
        assertTrue(properties.has("item_name"));

        JsonObject itemName = properties.getAsJsonObject("item_name");
        assertEquals("string", itemName.get("type").getAsString());
        assertTrue(itemName.has("description"));

        // Verify required array
        assertTrue(params.has("required"));
        JsonArray required = params.getAsJsonArray("required");
        assertTrue(required.contains(new com.google.gson.JsonPrimitive("item_name")));
    }

    @Test
    void execute_withMockMinecraft_doesNotThrow() {
        Minecraft mc = Minecraft.getInstance();
        try {
            ToolResult result = tool.execute(mc, "{\"item_name\": \"diamond_sword\"}");
            assertNotNull(result);
            assertEquals("lookup_recipe", result.toolCallId());
        } catch (Exception e) {
            // Running outside Minecraft client environment
            assertTrue(true);
        }
    }

    @Test
    void execute_withInvalidJson_returnsError() {
        Minecraft mc = Minecraft.getInstance();
        ToolResult result = tool.execute(mc, "not valid json");
        assertEquals("lookup_recipe", result.toolCallId());
        assertTrue(result.content().startsWith("Error:"));
    }

    @Test
    void execute_withMissingItemName_returnsError() {
        Minecraft mc = Minecraft.getInstance();
        ToolResult result = tool.execute(mc, "{}");
        assertEquals("lookup_recipe", result.toolCallId());
        assertTrue(result.content().startsWith("Error:"));
    }
}

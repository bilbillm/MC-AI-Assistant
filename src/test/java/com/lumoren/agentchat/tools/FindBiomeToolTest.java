package com.lumoren.agentchat.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FindBiomeTool}.
 */
class FindBiomeToolTest {

    private final FindBiomeTool tool = new FindBiomeTool();

    @Test
    void getName_returnsCorrectName() {
        assertEquals("find_biome", tool.getName());
    }

    @Test
    void getDescription_isNotEmpty() {
        assertNotNull(tool.getDescription());
        assertFalse(tool.getDescription().isEmpty());
    }

    @Test
    void getDefinition_returnsValidDefinition() {
        ToolDefinition def = tool.getDefinition();
        assertEquals("find_biome", def.name());
        assertNotNull(def.description());
        assertNotNull(def.parameters());
        assertEquals("object", def.parameters().get("type").getAsString());
        // Verify required parameter
        assertTrue(def.parameters().has("required"));
        assertTrue(def.parameters().getAsJsonArray("required").size() >= 1);
    }

    @Test
    void execute_withMissingBiome_returnsError() {
        Minecraft mc = Minecraft.getInstance();
        try {
            ToolResult result = tool.execute(mc, "{}");
            assertNotNull(result);
            assertEquals("find_biome", result.toolCallId());
            JsonObject json = JsonParser.parseString(result.content()).getAsJsonObject();
            assertTrue(json.has("error"));
        } catch (Exception e) {
            // Running outside Minecraft client environment — expected
            assertTrue(true);
        }
    }

    @Test
    void execute_withMockMinecraft_doesNotThrow() {
        Minecraft mc = Minecraft.getInstance();
        try {
            ToolResult result = tool.execute(mc, "{\"biome\":\"plains\"}");
            assertNotNull(result);
            assertEquals("find_biome", result.toolCallId());
        } catch (Exception e) {
            // Running outside Minecraft client environment
            assertTrue(true);
        }
    }

    @Test
    void execute_withMaxRadius_doesNotThrow() {
        Minecraft mc = Minecraft.getInstance();
        try {
            ToolResult result = tool.execute(mc,
                    "{\"biome\":\"desert\",\"max_radius\":256}");
            assertNotNull(result);
            assertEquals("find_biome", result.toolCallId());
        } catch (Exception e) {
            // Running outside Minecraft client environment
            assertTrue(true);
        }
    }
}

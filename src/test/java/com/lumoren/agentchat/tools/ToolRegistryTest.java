package com.lumoren.agentchat.tools;

import com.google.gson.JsonObject;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ToolRegistry}.
 */
@ExtendWith(MockitoExtension.class)
class ToolRegistryTest {

    @Mock
    private GameTool mockTool1;

    @Mock
    private GameTool mockTool2;

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    void registerAndGet_returnsToolByName() {
        when(mockTool1.getName()).thenReturn("tool_one");
        registry.register(mockTool1);

        GameTool retrieved = registry.get("tool_one");
        assertSame(mockTool1, retrieved);
    }

    @Test
    void registerAndGet_returnsNullForUnknownName() {
        assertNull(registry.get("nonexistent"));
    }

    @Test
    void getAll_returnsAllRegisteredTools() {
        when(mockTool1.getName()).thenReturn("tool_one");
        when(mockTool2.getName()).thenReturn("tool_two");
        registry.register(mockTool1);
        registry.register(mockTool2);

        List<GameTool> all = registry.getAll();
        assertEquals(2, all.size());
        assertTrue(all.contains(mockTool1));
        assertTrue(all.contains(mockTool2));
    }

    @Test
    void getAll_returnsImmutableList() {
        when(mockTool1.getName()).thenReturn("tool_one");
        registry.register(mockTool1);

        List<GameTool> all = registry.getAll();
        assertThrows(UnsupportedOperationException.class, () -> all.add(mock(GameTool.class)));
    }

    @Test
    void getToolDefinitions_returnsCorrectOpenAISchemaFormat() {
        // Build a schema matching the pattern from existing tests (ModelSerializationTest)
        JsonObject params = new JsonObject();
        params.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject itemName = new JsonObject();
        itemName.addProperty("type", "string");
        itemName.addProperty("description", "Item name to look up");
        properties.add("item_name", itemName);
        params.add("properties", properties);

        ToolDefinition def = new ToolDefinition("query_item", "Query item information", params);

        when(mockTool1.getName()).thenReturn("query_item");
        when(mockTool1.getDescription()).thenReturn("Query item information");
        when(mockTool1.getDefinition()).thenReturn(def);
        registry.register(mockTool1);

        List<ToolDefinition> defs = registry.getToolDefinitions();
        assertEquals(1, defs.size());

        ToolDefinition result = defs.get(0);
        assertEquals("query_item", result.name());
        assertEquals("Query item information", result.description());
        assertSame(params, result.parameters());

        // Verify toJsonObject produces correct OpenAI schema
        JsonObject json = result.toJsonObject();
        assertEquals("function", json.get("type").getAsString());
        assertEquals("query_item", json.getAsJsonObject("function").get("name").getAsString());
        assertSame(params, json.getAsJsonObject("function").get("parameters"));
    }

    @Test
    void executeTool_withMockTool_returnsExpectedResult() {
        Minecraft mc = mock(Minecraft.class);
        ToolResult expected = new ToolResult("call_123", "{\"result\": \"ok\"}");

        when(mockTool1.getName()).thenReturn("my_tool");
        when(mockTool1.execute(mc, "{\"arg\": \"val\"}")).thenReturn(expected);
        registry.register(mockTool1);

        ToolResult result = registry.executeTool("my_tool", mc, "{\"arg\": \"val\"}");
        assertSame(expected, result);
        assertEquals("call_123", result.toolCallId());
        assertEquals("{\"result\": \"ok\"}", result.content());
    }

    @Test
    void executeTool_withUnknownName_returnsErrorToolResult() {
        ToolResult result = registry.executeTool("unknown_tool", mock(Minecraft.class), "{}");

        assertEquals("unknown", result.toolCallId());
        assertTrue(result.content().contains("Tool not found: unknown_tool"));
    }

    @Test
    void size_returnsCorrectCount() {
        assertEquals(0, registry.size());

        when(mockTool1.getName()).thenReturn("tool_a");
        registry.register(mockTool1);
        assertEquals(1, registry.size());

        when(mockTool2.getName()).thenReturn("tool_b");
        registry.register(mockTool2);
        assertEquals(2, registry.size());
    }

    @Test
    void register_nullTool_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> registry.register(null));
    }
}

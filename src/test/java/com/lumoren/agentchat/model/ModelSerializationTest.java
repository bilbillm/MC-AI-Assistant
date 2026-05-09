package com.lumoren.agentchat.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 模型序列化与基础功能的综合测试。
 * <p>
 * 验证 ToolDefinition / InventoryItem / WorldState / RecipeResult / ConversationThread / GameContext 等模型的正确性。
 */
class ModelSerializationTest {

    // ========== ToolDefinition ==========

    @Test
    void toolDefinition_toJsonObject_producesCorrectOpenAISchema() {
        JsonObject params = new JsonObject();
        params.addProperty("type", "object");
        params.add("properties", new JsonObject());

        ToolDefinition def = new ToolDefinition("query_item", "查询物品信息", params);
        JsonObject json = def.toJsonObject();

        assertEquals("function", json.get("type").getAsString());

        JsonObject function = json.getAsJsonObject("function");
        assertNotNull(function);
        assertEquals("query_item", function.get("name").getAsString());
        assertEquals("查询物品信息", function.get("description").getAsString());
        assertSame(params, function.get("parameters"));
    }

    @Test
    void toolDefinition_withComplexParameters() {
        String jsonStr = """
                {
                    "type": "object",
                    "properties": {
                        "item_name": {
                            "type": "string",
                            "description": "物品名称"
                        }
                    },
                    "required": ["item_name"]
                }
                """;
        JsonObject params = JsonParser.parseString(jsonStr).getAsJsonObject();
        ToolDefinition def = new ToolDefinition("test_tool", "测试工具", params);
        JsonObject result = def.toJsonObject();

        JsonObject funcParams = result.getAsJsonObject("function").getAsJsonObject("parameters");
        assertEquals("object", funcParams.get("type").getAsString());
        assertTrue(funcParams.getAsJsonObject("properties").has("item_name"));
        assertTrue(funcParams.getAsJsonArray("required").contains(JsonParser.parseString("\"item_name\"")));
    }

    // ========== InventoryItem ==========

    @Test
    void inventoryItem_createdWithCorrectFields() {
        InventoryItem item = new InventoryItem(0, "minecraft:diamond_sword", "钻石剑", 1, 1561, 0);

        assertEquals(0, item.slot());
        assertEquals("minecraft:diamond_sword", item.itemId());
        assertEquals("钻石剑", item.displayName());
        assertEquals(1, item.count());
        assertEquals(1561, item.maxDamage());
        assertEquals(0, item.currentDamage());
    }

    @Test
    void inventoryItem_defaultValues() {
        InventoryItem item = new InventoryItem(5, "minecraft:stone", "石头", 64, 0, 0);

        assertEquals(5, item.slot());
        assertEquals("minecraft:stone", item.itemId());
        assertEquals(64, item.count());
    }

    // ========== WorldState ==========

    @Test
    void worldState_toJsonObject() {
        WorldState ws = new WorldState(6000L, true, false, "NORMAL", "minecraft:plains");
        JsonObject json = ws.toJsonObject();

        assertEquals(6000L, json.get("day_time").getAsLong());
        assertTrue(json.get("is_raining").getAsBoolean());
        assertFalse(json.get("is_thundering").getAsBoolean());
        assertEquals("NORMAL", json.get("difficulty").getAsString());
        assertEquals("minecraft:plains", json.get("biome").getAsString());
    }

    @Test
    void worldState_allFields() {
        WorldState ws = new WorldState(18000L, true, true, "HARD", "minecraft:badlands");
        assertEquals(18000L, ws.dayTime());
        assertTrue(ws.isRaining());
        assertTrue(ws.isThundering());
        assertEquals("HARD", ws.difficulty());
        assertEquals("minecraft:badlands", ws.biome());
    }

    // ========== ToolResult ==========

    @Test
    void toolResult_createdCorrectly() {
        ToolResult result = new ToolResult("call_001", "{\"items\":[]}");
        assertEquals("call_001", result.toolCallId());
        assertEquals("{\"items\":[]}", result.content());
    }

    // ========== RecipeResult ==========

    @Test
    void recipeResult_createdCorrectly() {
        var input = List.of(
                new RecipeResult.Ingredient("minecraft:diamond", 2),
                new RecipeResult.Ingredient("minecraft:stick", 1)
        );
        var output = new RecipeResult.ItemStack("minecraft:diamond_sword", 1);
        RecipeResult recipe = new RecipeResult("minecraft:diamond_sword", input, output, "crafting");

        assertEquals("minecraft:diamond_sword", recipe.recipeId());
        assertEquals(2, recipe.input().size());
        assertEquals("minecraft:diamond", recipe.input().get(0).itemId());
        assertEquals(2, recipe.input().get(0).count());
        assertEquals("minecraft:diamond_sword", recipe.output().itemId());
        assertEquals(1, recipe.output().count());
        assertEquals("crafting", recipe.type());
    }

    @Test
    void recipeResult_ingredientAndItemStackRecords() {
        var ingredient = new RecipeResult.Ingredient("minecraft:iron_ingot", 3);
        assertEquals("minecraft:iron_ingot", ingredient.itemId());
        assertEquals(3, ingredient.count());

        var stack = new RecipeResult.ItemStack("minecraft:iron_sword", 1);
        assertEquals("minecraft:iron_sword", stack.itemId());
        assertEquals(1, stack.count());
    }

    // ========== GameContext ==========

    @Test
    void gameContext_toJsonObject() {
        var inventory = List.of(
                new InventoryItem(0, "minecraft:diamond_sword", "钻石剑", 1, 1561, 0),
                new InventoryItem(1, "minecraft:dirt", "泥土", 64, 0, 0)
        );
        GameContext ctx = new GameContext("Steve", 100.5, 64.0, -200.3, "minecraft:overworld", inventory, 20.0f, 20);

        JsonObject json = ctx.toJsonObject();

        assertEquals("Steve", json.get("player_name").getAsString());
        assertEquals(100.5, json.get("x").getAsDouble());
        assertEquals(64.0, json.get("y").getAsDouble());
        assertEquals(-200.3, json.get("z").getAsDouble());
        assertEquals("minecraft:overworld", json.get("dimension").getAsString());
        assertEquals(20.0f, json.get("health").getAsFloat());
        assertEquals(20, json.get("hunger").getAsInt());
        assertTrue(json.getAsJsonArray("inventory").size() == 2);
    }

    // ========== ConversationThread ==========

    @Test
    void conversationThread_createsWithDefaultName() {
        ConversationThread thread = new ConversationThread("新对话");
        assertNotNull(thread.getId());
        assertEquals("新对话", thread.getName());
        assertEquals(0, thread.messageCount());
    }

    @Test
    void conversationThread_addMessage() {
        ConversationThread thread = new ConversationThread("测试");
        thread.addMessage(ChatMessage.user("你好"));
        thread.addMessage(ChatMessage.assistant("你好！有什么可以帮你的？"));

        assertEquals(2, thread.messageCount());
        assertEquals("你好", thread.getMessages().get(0).content());
        assertEquals("assistant", thread.getMessages().get(1).role());
    }

    @Test
    void conversationThread_getMessagesReturnsImmutable() {
        ConversationThread thread = new ConversationThread("测试");
        thread.addMessage(ChatMessage.user("hi"));

        List<ChatMessage> msgs = thread.getMessages();
        assertThrows(UnsupportedOperationException.class, () -> msgs.add(ChatMessage.assistant("hello")));
    }

    @Test
    void conversationThread_setName() {
        ConversationThread thread = new ConversationThread("旧名称");
        thread.setName("新名称");
        assertEquals("新名称", thread.getName());
    }

    @Test
    void conversationThread_timestamps() {
        ConversationThread thread = new ConversationThread("时间测试");
        assertNotNull(thread.getCreatedAt());
        assertNotNull(thread.getUpdatedAt());
    }
}

package com.lumoren.agentchat.model;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GameContext} record and JSON serialization.
 */
class GameContextTest {

    @Test
    void recordStoresAllFields() {
        List<InventoryItem> inv = List.of(
                new InventoryItem(0, "minecraft:diamond", "Diamond", 3, 0, 0)
        );
        GameContext ctx = new GameContext("TestPlayer", 100.5, 64.0, -200.3,
                "minecraft:overworld", inv, 20.0f, 20);

        assertEquals("TestPlayer", ctx.playerName());
        assertEquals(100.5, ctx.x());
        assertEquals(64.0, ctx.y());
        assertEquals(-200.3, ctx.z());
        assertEquals("minecraft:overworld", ctx.dimension());
        assertEquals(1, ctx.inventory().size());
        assertEquals(20.0f, ctx.health());
        assertEquals(20, ctx.hunger());
    }

    @Test
    void toJsonObject_includesAllFields() {
        List<InventoryItem> inv = List.of(
                new InventoryItem(0, "minecraft:stone", "Stone", 64, 0, 0),
                new InventoryItem(1, "minecraft:torch", "Torch", 16, 0, 0)
        );
        GameContext ctx = new GameContext("Steve", 50.0, 70.0, 100.0,
                "minecraft:overworld", inv, 18.5f, 15);

        JsonObject json = ctx.toJsonObject();

        assertEquals("Steve", json.get("player_name").getAsString());
        assertEquals(50.0, json.get("x").getAsDouble());
        assertEquals(70.0, json.get("y").getAsDouble());
        assertEquals(100.0, json.get("z").getAsDouble());
        assertEquals("minecraft:overworld", json.get("dimension").getAsString());
        assertEquals(18.5f, json.get("health").getAsFloat());
        assertEquals(15, json.get("hunger").getAsInt());

        var invArray = json.getAsJsonArray("inventory");
        assertEquals(2, invArray.size());

        var firstItem = invArray.get(0).getAsJsonObject();
        assertEquals(0, firstItem.get("slot").getAsInt());
        assertEquals("minecraft:stone", firstItem.get("item_id").getAsString());
        assertEquals("Stone", firstItem.get("display_name").getAsString());
        assertEquals(64, firstItem.get("count").getAsInt());
        assertEquals(0, firstItem.get("max_damage").getAsInt());
        assertEquals(0, firstItem.get("current_damage").getAsInt());
    }

    @Test
    void toJsonObject_emptyInventory() {
        GameContext ctx = new GameContext("Alex", 0, 64, 0,
                "minecraft:the_end", List.of(), 10.0f, 5);

        JsonObject json = ctx.toJsonObject();
        assertEquals(0, json.getAsJsonArray("inventory").size());
        assertEquals("minecraft:the_end", json.get("dimension").getAsString());
        assertEquals(10.0f, json.get("health").getAsFloat());
        assertEquals(5, json.get("hunger").getAsInt());
    }

    @Test
    void recordEquality() {
        List<InventoryItem> inv = List.of(
                new InventoryItem(0, "minecraft:dirt", "Dirt", 1, 0, 0)
        );
        GameContext a = new GameContext("P", 0, 0, 0, "overworld", inv, 20f, 20);
        GameContext b = new GameContext("P", 0, 0, 0, "overworld", inv, 20f, 20);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}

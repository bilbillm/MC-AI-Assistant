package com.lumoren.agentchat.model;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WorldState} record and JSON serialization.
 */
class WorldStateTest {

    @Test
    void recordStoresAllFields() {
        WorldState state = new WorldState(6000, false, false, "PEACEFUL", "minecraft:plains");

        assertEquals(6000, state.dayTime());
        assertFalse(state.isRaining());
        assertFalse(state.isThundering());
        assertEquals("PEACEFUL", state.difficulty());
        assertEquals("minecraft:plains", state.biome());
    }

    @Test
    void toJsonObject_includesAllKeys() {
        WorldState state = new WorldState(12000, true, false, "NORMAL", "minecraft:desert");
        JsonObject json = state.toJsonObject();

        assertEquals(12000, json.get("day_time").getAsLong());
        assertTrue(json.get("is_raining").getAsBoolean());
        assertFalse(json.get("is_thundering").getAsBoolean());
        assertEquals("NORMAL", json.get("difficulty").getAsString());
        assertEquals("minecraft:desert", json.get("biome").getAsString());
    }

    @Test
    void toJsonObject_stormyWeather() {
        WorldState state = new WorldState(18000, true, true, "HARD", "minecraft:swamp");
        JsonObject json = state.toJsonObject();

        assertTrue(json.get("is_raining").getAsBoolean());
        assertTrue(json.get("is_thundering").getAsBoolean());
        assertEquals("HARD", json.get("difficulty").getAsString());
    }

    @Test
    void recordEquality() {
        WorldState a = new WorldState(1000, false, false, "EASY", "minecraft:forest");
        WorldState b = new WorldState(1000, false, false, "EASY", "minecraft:forest");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}

package com.lumoren.agentchat.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AssistantActionTest {

    @Test
    void unknownKindIsDroppedFailClosed() {
        JsonObject obj = JsonParser.parseString(
                "{\"label\":\"Do thing\",\"kind\":\"DELETE_WORLD\",\"payload\":{}}").getAsJsonObject();

        assertNull(AssistantAction.fromJsonObject(obj));
    }

    @Test
    void removedKindIsDroppedFailClosed() {
        JsonObject obj = JsonParser.parseString(
                "{\"label\":\"Plan\",\"kind\":\"CREATE_PROJECT_FROM_PLAN\",\"payload\":{}}").getAsJsonObject();

        assertNull(AssistantAction.fromJsonObject(obj));
    }

    @Test
    void missingKindIsDroppedFailClosed() {
        JsonObject obj = JsonParser.parseString(
                "{\"label\":\"No kind\",\"payload\":{}}").getAsJsonObject();

        assertNull(AssistantAction.fromJsonObject(obj));
    }

    @Test
    void typoKindIsDroppedFailClosed() {
        JsonObject obj = JsonParser.parseString(
                "{\"label\":\"Typo\",\"kind\":\"open_recipe\",\"payload\":{}}").getAsJsonObject();

        assertNull(AssistantAction.fromJsonObject(obj));
    }

    @Test
    void knownKindRoundTrips() {
        JsonObject payload = new JsonObject();
        payload.addProperty("item_name", "minecraft:stone");
        AssistantAction action = new AssistantAction("stable-id", "Recipe",
                AssistantAction.Kind.OPEN_RECIPE, payload);

        AssistantAction parsed = AssistantAction.fromJsonObject(action.toJsonObject());

        assertNotNull(parsed);
        assertEquals("stable-id", parsed.id());
        assertEquals("Recipe", parsed.label());
        assertEquals(AssistantAction.Kind.OPEN_RECIPE, parsed.kind());
        assertEquals("minecraft:stone", parsed.payload().get("item_name").getAsString());
    }

    @Test
    void nullJsonYieldsNull() {
        assertNull(AssistantAction.fromJsonObject(null));
    }
}

package com.lumoren.agentchat.model;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ToolDefinition} record and JSON serialization.
 */
class ToolDefinitionTest {

    @Test
    void toJsonObject_includesTypeAndFunction() {
        JsonObject params = new JsonObject();
        params.addProperty("type", "object");
        ToolDefinition def = new ToolDefinition("inventory", "Query player inventory", params);

        JsonObject json = def.toJsonObject();

        assertEquals("function", json.get("type").getAsString());
        JsonObject function = json.getAsJsonObject("function");
        assertEquals("inventory", function.get("name").getAsString());
        assertEquals("Query player inventory", function.get("description").getAsString());
        assertNotNull(function.get("parameters"));
    }

    @Test
    void toJsonObject_handlesEmptyParameters() {
        JsonObject emptyParams = new JsonObject();
        ToolDefinition def = new ToolDefinition("test", "A test tool", emptyParams);

        JsonObject json = def.toJsonObject();
        JsonObject function = json.getAsJsonObject("function");

        assertEquals("test", function.get("name").getAsString());
        assertEquals("{}", function.getAsJsonObject("parameters").toString());
    }

    @Test
    void toJsonObject_handlesComplexParameters() {
        JsonObject params = new JsonObject();
        params.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject itemProp = new JsonObject();
        itemProp.addProperty("type", "string");
        itemProp.addProperty("description", "Item name to query");
        props.add("item", itemProp);
        params.add("properties", props);
        params.add("required", new com.google.gson.JsonArray());

        ToolDefinition def = new ToolDefinition("query_item", "Query an item", params);
        JsonObject json = def.toJsonObject();

        JsonObject function = json.getAsJsonObject("function");
        assertEquals("query_item", function.get("name").getAsString());
        assertEquals("Query an item", function.get("description").getAsString());
        assertTrue(function.getAsJsonObject("parameters").has("properties"));
    }

    @Test
    void recordEquality() {
        JsonObject params = new JsonObject();
        ToolDefinition a = new ToolDefinition("test", "desc", params);
        ToolDefinition b = new ToolDefinition("test", "desc", params);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}

package com.lumoren.agentchat.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lumoren.agentchat.ai.GameDataAccess;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool to scan nearby entities (monsters, animals, villagers, items, players)
 * within a configurable radius around the player.
 */
public class NearbyEntitiesTool implements GameTool {

    private static final String NAME = "nearby_entities";
    private static final String DESCRIPTION =
            "Scan for entities (monsters, animals, villagers, dropped items, other players) " +
            "within a radius around the player. Returns entity type, name, distance, direction, " +
            "health, and position. Use to check for nearby dangers, resources, or NPCs.";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject params = new JsonObject();
        params.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject radiusProp = new JsonObject();
        radiusProp.addProperty("type", "integer");
        radiusProp.addProperty("description", "Scan radius in blocks (1-32, default 10)");
        radiusProp.addProperty("default", 10);
        props.add("radius", radiusProp);

        JsonObject categoriesProp = new JsonObject();
        categoriesProp.addProperty("type", "array");
        categoriesProp.addProperty("description",
                "Entity categories to include: monster, animal, villager, item, player, all (default). " +
                "Example: [\"monster\", \"item\"]");
        JsonObject items = new JsonObject();
        items.addProperty("type", "string");
        JsonArray enumVals = new JsonArray();
        enumVals.add("monster");
        enumVals.add("animal");
        enumVals.add("villager");
        enumVals.add("item");
        enumVals.add("player");
        enumVals.add("all");
        items.add("enum", enumVals);
        categoriesProp.add("items", items);
        props.add("categories", categoriesProp);

        JsonObject detailProp = new JsonObject();
        detailProp.addProperty("type", "string");
        detailProp.addProperty("description",
                "Output detail level: \"summary\" (default, aggregated counts + top 8 highlights) " +
                "or \"full\" (all matching entities with full details)");
        JsonArray detailEnum = new JsonArray();
        detailEnum.add("summary");
        detailEnum.add("full");
        detailProp.add("enum", detailEnum);
        props.add("detail", detailProp);

        params.add("properties", props);
        return new ToolDefinition(NAME, DESCRIPTION, params);
    }

    @Override
    public ToolResult execute(Minecraft mc, String arguments) {
        try {
            JsonObject args = JsonParser.parseString(arguments).getAsJsonObject();
            int radius = args.has("radius") ? args.get("radius").getAsInt() : 10;
            List<String> categories = new ArrayList<>();
            if (args.has("categories") && args.get("categories").isJsonArray()) {
                JsonArray arr = args.getAsJsonArray("categories");
                for (int i = 0; i < arr.size(); i++) {
                    categories.add(arr.get(i).getAsString());
                }
            }
            if (categories.isEmpty()) {
                categories.add("all");
            }
            String detail = args.has("detail") ? args.get("detail").getAsString() : "summary";

            String result = GameDataAccess.getNearbyEntities(mc, radius, categories, detail);
            return new ToolResult(NAME, result);
        } catch (Exception e) {
            return new ToolResult(NAME, "Error scanning entities: " + e.getMessage());
        }
    }
}

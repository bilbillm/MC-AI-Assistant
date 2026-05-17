package com.lumoren.agentchat.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lumoren.agentchat.ai.GameDataAccess;
import com.lumoren.agentchat.ai.MaterialCalculator;
import com.lumoren.agentchat.model.InventoryItem;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public class MaterialCalculatorTool implements GameTool {

    private static final String NAME = "calculate_materials";
    private static final String DESCRIPTION =
            "Calculate all raw materials needed to craft an item in bulk. Recursively expands recipes to show the complete material tree, comparing against your current inventory.";

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() { return DESCRIPTION; }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        JsonObject itemName = new JsonObject();
        itemName.addProperty("type", "string");
        itemName.addProperty("description", "Name of the item to calculate materials for (e.g. 'piston', 'diamond_sword')");
        properties.add("item_name", itemName);

        JsonObject count = new JsonObject();
        count.addProperty("type", "integer");
        count.addProperty("description", "How many to craft (default: 1)");
        properties.add("count", count);

        parameters.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("item_name");
        parameters.add("required", required);

        return new ToolDefinition(NAME, DESCRIPTION, parameters);
    }

    @Override
    public ToolResult execute(Minecraft mc, String arguments) {
        try {
            JsonObject args = JsonParser.parseString(arguments).getAsJsonObject();
            String itemName = args.get("item_name").getAsString();
            int count = args.has("count") ? args.get("count").getAsInt() : 1;

            ResourceLocation itemId = GameDataAccess.resolveItemId(itemName);

            // Build inventory map: itemId → count
            Map<String, Integer> inventory = new HashMap<>();
            for (InventoryItem item : GameDataAccess.getInventorySnapshot(mc)) {
                inventory.merge(item.itemId(), item.count(), Integer::sum);
            }

            // Expand material tree
            MaterialCalculator.MaterialNode root = MaterialCalculator.expand(mc, itemId, count, inventory);

            // Convert to JSON
            JsonObject result = nodeToJson(root, count);
            return new ToolResult(NAME, result.toString());
        } catch (Exception e) {
            return new ToolResult(NAME, "Error: " + e.getMessage());
        }
    }

    /** Convert a MaterialNode tree to JSON */
    private JsonObject nodeToJson(MaterialCalculator.MaterialNode node, int originalCount) {
        JsonObject obj = new JsonObject();
        obj.addProperty("item_id", node.itemId());
        obj.addProperty("display_name", node.displayName());
        obj.addProperty("count_needed", node.countNeeded());
        obj.addProperty("count_have", node.countHave());
        obj.addProperty("missing", Math.max(0, node.countNeeded() - node.countHave()));
        obj.addProperty("source", node.source());
        obj.addProperty("source_note", node.sourceNote());
        obj.addProperty("depth", node.depth());

        if (!node.ingredients().isEmpty()) {
            JsonArray ingredients = new JsonArray();
            for (MaterialCalculator.MaterialNode ing : node.ingredients()) {
                ingredients.add(nodeToJson(ing, originalCount));
            }
            obj.add("ingredients", ingredients);
        }

        return obj;
    }
}

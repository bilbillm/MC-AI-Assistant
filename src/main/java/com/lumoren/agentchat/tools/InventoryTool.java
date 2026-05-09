package com.lumoren.agentchat.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lumoren.agentchat.ai.GameDataAccess;
import com.lumoren.agentchat.model.InventoryItem;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Tool to query the player's inventory.
 * <p>
 * Returns a list of items with slot, item ID, display name, count, and durability.
 */
public class InventoryTool implements GameTool {

    private static final String NAME = "get_inventory";
    private static final String DESCRIPTION = "Query the player's inventory. Returns a list of items with slot, item ID, display name, count, and durability.";

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
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        return new ToolDefinition(NAME, DESCRIPTION, parameters);
    }

    @Override
    public ToolResult execute(Minecraft mc, String arguments) {
        try {
            List<InventoryItem> items = GameDataAccess.getInventorySnapshot(mc);
            JsonArray itemsArray = new JsonArray();
            for (InventoryItem item : items) {
                JsonObject itemObj = new JsonObject();
                itemObj.addProperty("slot", item.slot());
                itemObj.addProperty("item_id", item.itemId());
                itemObj.addProperty("display_name", item.displayName());
                itemObj.addProperty("count", item.count());
                itemObj.addProperty("max_damage", item.maxDamage());
                itemObj.addProperty("current_damage", item.currentDamage());
                itemsArray.add(itemObj);
            }
            return new ToolResult(NAME, itemsArray.toString());
        } catch (Exception e) {
            return new ToolResult(NAME, "Error: " + e.getMessage());
        }
    }
}

package com.lumoren.agentchat.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lumoren.agentchat.ai.GameDataAccess;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Tool to get detailed information about an item.
 * <p>
 * Returns max stack size, rarity, and food properties.
 */
public class ItemEncyclopediaTool implements GameTool {

    private static final String NAME = "item_info";
    private static final String DESCRIPTION = "Get detailed information about an item, including max stack size, rarity, and food properties.";

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

        JsonObject properties = new JsonObject();
        JsonObject itemName = new JsonObject();
        itemName.addProperty("type", "string");
        itemName.addProperty("description", "Name of the item (e.g. 'diamond_sword', 'bread')");
        properties.add("item_name", itemName);
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
            ResourceLocation itemId = GameDataAccess.resolveItemId(itemName);
            String info = GameDataAccess.getItemInfo(mc, itemId);
            return new ToolResult(NAME, info);
        } catch (Exception e) {
            return new ToolResult(NAME, "Error: " + e.getMessage());
        }
    }
}

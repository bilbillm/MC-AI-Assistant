package com.lumoren.agentchat.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lumoren.agentchat.AgentChat;
import com.lumoren.agentchat.ai.GameDataAccess;
import com.lumoren.agentchat.model.RecipeResult;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Tool to look up what an item can be used for — reverse recipe lookup.
 * <p>
 * Uses JEI runtime via {@link RecipeTool#lookupUsagesJEI} when available
 * for comprehensive usage data across all recipe types. Falls back to vanilla
 * {@link GameDataAccess#getUsagesForInput} when JEI is not installed.
 */
public class UsageTool implements GameTool {

    private static final String NAME = "lookup_usages";
    private static final String DESCRIPTION = "Look up what an item can be used for — recipes that use this item as an ingredient. Returns recipe type, output, and required inputs.";

    // ──────────────────────────────────────────────
    // ToolDefinition
    // ──────────────────────────────────────────────

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
        itemName.addProperty("description", "Name of the item to look up usages for (e.g. 'diamond', 'iron_ingot')");
        properties.add("item_name", itemName);
        parameters.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("item_name");
        parameters.add("required", required);

        return new ToolDefinition(NAME, DESCRIPTION, parameters);
    }

    // ──────────────────────────────────────────────
    // execute — primary entry point
    // ──────────────────────────────────────────────

    @Override
    public ToolResult execute(Minecraft mc, String arguments) {
        try {
            JsonObject args = JsonParser.parseString(arguments).getAsJsonObject();
            String itemName = args.get("item_name").getAsString();
            ResourceLocation itemId = GameDataAccess.resolveItemId(itemName);

            // Prefer JEI when available
            if (AgentChat.hasJei()) {
                String jeiResult = RecipeTool.lookupUsagesJEI(itemId);
                if (jeiResult != null && !jeiResult.isBlank()) {
                    return new ToolResult(NAME, jeiResult);
                }
            }

            // Vanilla fallback
            List<RecipeResult> recipes = GameDataAccess.getUsagesForInput(mc, itemId);
            return new ToolResult(NAME, RecipeResult.toJsonArray(recipes).toString());
        } catch (Exception e) {
            return new ToolResult(NAME, "Error: " + e.getMessage());
        }
    }
}

package com.lumoren.agentchat.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lumoren.agentchat.ai.GameDataAccess;
import com.lumoren.agentchat.model.RecipeResult;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Tool to look up crafting recipes for an item by name.
 * <p>
 * Returns recipe type, input ingredients, and output.
 */
public class RecipeTool implements GameTool {

    private static final String NAME = "lookup_recipe";
    private static final String DESCRIPTION = "Look up crafting recipes for an item by name. Returns recipe type, input ingredients, and output.";

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
        itemName.addProperty("description", "Name of the item to look up recipes for (e.g. 'diamond_sword', 'bread')");
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
            JsonObject args = com.google.gson.JsonParser.parseString(arguments).getAsJsonObject();
            String itemName = args.get("item_name").getAsString();
            ResourceLocation itemId = GameDataAccess.resolveItemId(itemName);
            List<RecipeResult> recipes = GameDataAccess.getRecipesForOutput(mc, itemId);

            JsonArray recipesArray = new JsonArray();
            for (RecipeResult recipe : recipes) {
                JsonObject recipeObj = new JsonObject();
                recipeObj.addProperty("recipe_id", recipe.recipeId());
                recipeObj.addProperty("type", recipe.type());

                JsonObject outputObj = new JsonObject();
                outputObj.addProperty("item_id", recipe.output().itemId());
                outputObj.addProperty("count", recipe.output().count());
                recipeObj.add("output", outputObj);

                JsonArray inputsArray = new JsonArray();
                for (RecipeResult.Ingredient ing : recipe.input()) {
                    JsonObject ingObj = new JsonObject();
                    ingObj.addProperty("item_id", ing.itemId());
                    ingObj.addProperty("count", ing.count());
                    inputsArray.add(ingObj);
                }
                recipeObj.add("input", inputsArray);

                recipesArray.add(recipeObj);
            }
            return new ToolResult(NAME, recipesArray.toString());
        } catch (Exception e) {
            return new ToolResult(NAME, "Error: " + e.getMessage());
        }
    }
}

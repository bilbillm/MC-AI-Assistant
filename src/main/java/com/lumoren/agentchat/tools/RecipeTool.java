package com.lumoren.agentchat.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lumoren.agentchat.AgentChat;
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
 * Uses JEI runtime when available for comprehensive recipe data
 * (including all recipe types, usages, and tags). Falls back to vanilla
 * {@link GameDataAccess#getRecipesForOutput} when JEI is not installed.
 * <p>
 * JEI is a compileOnly dependency, so this class must not reference any
 * mezz.jei types directly — all JEI access lives in {@link JeiLookup},
 * which is only loaded behind {@link AgentChat#hasJei()}.
 */
public class RecipeTool implements GameTool {

    private static final String NAME = "lookup_recipe";
    private static final String DESCRIPTION = "Look up crafting recipes for an item by name. Returns recipe type, input ingredients, and output.";

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
        itemName.addProperty("description", "Name of the item to look up recipes for (e.g. 'diamond_sword', 'bread')");
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
            JsonObject args = com.google.gson.JsonParser.parseString(arguments).getAsJsonObject();
            String itemName = args.get("item_name").getAsString();
            ResourceLocation itemId = GameDataAccess.resolveItemId(itemName);

            // Prefer JEI when available
            if (AgentChat.hasJei()) {
                String jeiResult = lookupRecipesJEI(itemId);
                if (jeiResult != null && !jeiResult.isBlank()) {
                    return new ToolResult(NAME, jeiResult);
                }
            }

            // Vanilla fallback
            List<RecipeResult> recipes = GameDataAccess.getRecipesForOutput(mc, itemId);
            return new ToolResult(NAME, RecipeResult.toJsonArray(recipes).toString());
        } catch (Exception e) {
            return new ToolResult(NAME, "Error: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────
    // JEI-powered lookup helpers
    // ──────────────────────────────────────────────

    /**
     * JEI-based forward lookup: what recipes produce {@code itemId} as output?
     * <p>
     * Callers must guard with {@link AgentChat#hasJei()} before calling so
     * that {@link JeiLookup} (which references mezz.jei types) is only
     * loaded when JEI is actually installed.
     *
     * @param itemId the target item
     * @return formatted recipe text, or {@code null} when JEI is not available
     */
    public static String lookupRecipesJEI(ResourceLocation itemId) {
        if (!AgentChat.hasJei()) return null;
        return JeiLookup.lookupRecipes(itemId);
    }

    /**
     * JEI-based reverse lookup: what recipes use {@code itemId} as an ingredient?
     * <p>
     * Same {@link AgentChat#hasJei()} guard requirement as
     * {@link #lookupRecipesJEI}.
     *
     * @param itemId the target item used as input
     * @return formatted usage text, or {@code null} when JEI is not available
     */
    public static String lookupUsagesJEI(ResourceLocation itemId) {
        if (!AgentChat.hasJei()) return null;
        return JeiLookup.lookupUsages(itemId);
    }

    /**
     * JEI-based tag lookup: returns all item tags for {@code itemId}.
     *
     * @param itemId the target item
     * @return comma-separated tag names, or empty string when JEI is not available
     */
    public static String getItemTags(ResourceLocation itemId) {
        if (!AgentChat.hasJei()) return "";
        return JeiLookup.getItemTags(itemId);
    }

    // ──────────────────────────────────────────────
    // internal helpers
    // ──────────────────────────────────────────────

    /**
     * Formats a recipe object into a human-readable string showing inputs → outputs.
     * <p>
     * Package-private so {@link JeiLookup} can reuse it. Attempts structured
     * extraction when the recipe is a vanilla
     * {@link net.minecraft.world.item.crafting.Recipe} or {@code RecipeHolder}.
     * Falls back to {@code toString()} truncated to 200 chars.
     */
    static String formatRecipeDetail(Object recipe) {
        // Unwrap RecipeHolder
        if (recipe instanceof net.minecraft.world.item.crafting.RecipeHolder<?> holder) {
            return formatRecipeDetail(holder.value());
        }

        // Structured rendering for vanilla Recipe
        if (recipe instanceof net.minecraft.world.item.crafting.Recipe<?> mcRecipe) {
            try {
                StringBuilder sb = new StringBuilder();
                var ingredients = mcRecipe.getIngredients();
                for (var ing : ingredients) {
                    if (!ing.isEmpty()) {
                        var items = ing.getItems();
                        if (items.length > 0) {
                            if (!sb.isEmpty()) sb.append(" + ");
                            sb.append(items[0].getItem().toString());
                            if (items[0].getCount() > 1) sb.append(" x").append(items[0].getCount());
                        }
                    }
                }

                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    var result = mcRecipe.getResultItem(mc.level.registryAccess());
                    if (!result.isEmpty()) {
                        sb.append(" → ").append(result.getItem().toString());
                        if (result.getCount() > 1) sb.append(" x").append(result.getCount());
                    }
                }

                if (!sb.isEmpty()) return sb.toString();
            } catch (Exception ignored) {
                // Fall through to toString fallback
            }
        }

        // Fallback: raw toString with max length guard
        String s = recipe.toString();
        if (s.length() > 200) {
            return s.substring(0, 197) + "...";
        }
        return s;
    }
}

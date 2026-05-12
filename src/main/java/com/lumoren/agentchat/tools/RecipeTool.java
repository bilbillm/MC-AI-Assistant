package com.lumoren.agentchat.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lumoren.agentchat.AgentChat;
import com.lumoren.agentchat.ai.GameDataAccess;
import com.lumoren.agentchat.model.RecipeResult;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tool to look up crafting recipes for an item by name.
 * <p>
 * Uses JEI runtime when available for comprehensive recipe data
 * (including all recipe types, usages, and tags). Falls back to vanilla
 * {@link GameDataAccess#getRecipesForOutput} when JEI is not installed.
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

    // ──────────────────────────────────────────────
    // JEI-powered lookup helpers
    // ──────────────────────────────────────────────

    /**
     * JEI-based forward lookup: what recipes produce {@code itemId} as output?
     *
     * @param itemId the target item
     * @return formatted recipe text, or {@code null} when JEI is not available
     */
    @SuppressWarnings("unchecked")
    public static String lookupRecipesJEI(ResourceLocation itemId) {
        if (!AgentChat.hasJei()) return null;

        IJeiRuntime runtime = AgentChat.jeiRuntime;
        IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
        IRecipeManager recipeManager = runtime.getRecipeManager();

        ItemStack stack = makeItemStack(itemId);
        if (stack == null) return null;

        IFocus<ItemStack> focus = focusFactory.createFocus(
                RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack);

        var categories = recipeManager.createRecipeCategoryLookup()
                .limitFocus(Collections.singletonList(focus))
                .get()
                .toList();

        if (categories.isEmpty()) {
            // JEI found nothing — signal caller to try vanilla fallback
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Recipes producing ").append(itemId).append(" ===\n");

        for (IRecipeCategory<?> category : categories) {
            sb.append("\n[").append(category.getTitle().getString()).append("]\n");
            var recipes = recipeManager.createRecipeLookup(category.getRecipeType())
                    .limitFocus(Collections.singletonList(focus))
                    .get()
                    .toList();

            for (Object recipe : recipes) {
                sb.append("  - ").append(recipe).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * JEI-based reverse lookup: what recipes use {@code itemId} as an ingredient?
     *
     * @param itemId the target item used as input
     * @return formatted usage text, or {@code null} when JEI is not available
     */
    @SuppressWarnings("unchecked")
    public static String lookupUsagesJEI(ResourceLocation itemId) {
        if (!AgentChat.hasJei()) return null;

        IJeiRuntime runtime = AgentChat.jeiRuntime;
        IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
        IRecipeManager recipeManager = runtime.getRecipeManager();

        ItemStack stack = makeItemStack(itemId);
        if (stack == null) return null;

        IFocus<ItemStack> focus = focusFactory.createFocus(
                RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, stack);

        var categories = recipeManager.createRecipeCategoryLookup()
                .limitFocus(Collections.singletonList(focus))
                .get()
                .toList();

        if (categories.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Recipes using ").append(itemId).append(" as input ===\n");

        for (IRecipeCategory<?> category : categories) {
            sb.append("\n[").append(category.getTitle().getString()).append("]\n");
            var recipes = recipeManager.createRecipeLookup(category.getRecipeType())
                    .limitFocus(Collections.singletonList(focus))
                    .get()
                    .toList();

            for (Object recipe : recipes) {
                sb.append("  - ").append(recipe).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * JEI-based tag lookup: returns all item tags for {@code itemId}.
     *
     * @param itemId the target item
     * @return comma-separated tag names, or empty string when JEI is not available
     */
    public static String getItemTags(ResourceLocation itemId) {
        if (!AgentChat.hasJei()) return "";

        IIngredientManager ingredientManager = AgentChat.jeiRuntime.getIngredientManager();
        IIngredientHelper<ItemStack> helper = ingredientManager.getIngredientHelper(VanillaTypes.ITEM_STACK);
        ItemStack stack = makeItemStack(itemId);
        if (stack == null) return "";

        Set<ResourceLocation> tags = helper.getTagStream(stack).collect(Collectors.toSet());
        return tags.stream()
                .map(ResourceLocation::toString)
                .collect(Collectors.joining(", "));
    }

    // ──────────────────────────────────────────────
    // internal helpers
    // ──────────────────────────────────────────────

    /**
     * Creates an {@link ItemStack} from a {@link ResourceLocation} via
     * {@link GameDataAccess}. Returns {@code null} when the item cannot
     * be found.
     */
    private static ItemStack makeItemStack(ResourceLocation itemId) {
        try {
            net.minecraft.world.item.Item item = GameDataAccess.lookupItem(itemId);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                return null;
            }
            return new ItemStack(item);
        } catch (Exception e) {
            return null;
        }
    }
}

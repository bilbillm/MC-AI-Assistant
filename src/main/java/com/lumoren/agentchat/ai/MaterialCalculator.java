package com.lumoren.agentchat.ai;

import com.lumoren.agentchat.model.RecipeResult;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.*;

/**
 * Recursive recipe expansion engine.
 * <p>
 * Builds a full material tree for crafting any item in bulk. Starting from a
 * target item and desired quantity, recursively expands every ingredient's
 * recipe until reaching raw materials (items with no recipe) or hitting
 * depth/cycle limits.
 * <p>
 * Used by tools to answer questions like
 * "how many raw materials do I need to craft 64 pistons?"
 */
public final class MaterialCalculator {

    /** Maximum recipe expansion depth to prevent runaway recursion. */
    private static final int MAX_DEPTH = 4;

    private MaterialCalculator() {}

    /**
     * Expand a material tree for crafting the given item.
     *
     * @param mc        Minecraft client instance
     * @param itemId    target item ID (e.g. {@code minecraft:piston})
     * @param count     how many to craft
     * @param inventory player's current inventory (itemId → count map)
     * @return root {@link MaterialNode} with full tree
     */
    public static MaterialNode expand(Minecraft mc, ResourceLocation itemId, int count,
                                       Map<String, Integer> inventory) {
        Set<String> visited = new HashSet<>();
        return expandRecursive(mc, itemId, count, inventory, visited, 0);
    }

    /**
     * Recursive core of the expansion engine.
     * <p>
     * Each recursive call gets a <b>copy</b> of the visited set so that
     * different branches (e.g. stick used in both pickaxe and sword) do not
     * interfere with each other. Only a single linear path is checked for
     * cycles.
     *
     * @param mc          Minecraft client instance
     * @param itemId      item being expanded
     * @param countNeeded desired quantity
     * @param inventory   what the player already holds
     * @param visited     items currently on this expansion path
     * @param depth       current recursion depth
     * @return tree node for this item
     */
    private static MaterialNode expandRecursive(Minecraft mc, ResourceLocation itemId,
                                                  int countNeeded,
                                                  Map<String, Integer> inventory,
                                                  Set<String> visited, int depth) {
        String id = itemId.toString();
        int have = inventory.getOrDefault(id, 0);

        // --- Stop conditions ---

        if (depth >= MAX_DEPTH) {
            return new MaterialNode(id, getDisplayName(mc, itemId), countNeeded, have,
                    "too_deep", "Max recipe depth reached", List.of(), depth);
        }
        if (visited.contains(id)) {
            return new MaterialNode(id, getDisplayName(mc, itemId), countNeeded, have,
                    "cycle", "Cycle detected", List.of(), depth);
        }
        if (have >= countNeeded) {
            return new MaterialNode(id, getDisplayName(mc, itemId), countNeeded, have,
                    "have_enough", "Already have enough", List.of(), depth);
        }

        visited.add(id);

        // --- Recipe lookup ---

        List<RecipeResult> recipes = GameDataAccess.getRecipesForOutput(mc, itemId);
        if (recipes.isEmpty()) {
            visited.remove(id);
            return new MaterialNode(id, getDisplayName(mc, itemId), countNeeded, have,
                    "raw", "Raw material — gather or mine this item", List.of(), depth);
        }

        // Use the first recipe (usually the primary one)
        RecipeResult recipe = recipes.get(0);
        int recipeOutputCount = recipe.output().count(); // items produced per craft
        int batches = (int) Math.ceil((double) (countNeeded - have) / recipeOutputCount);

        List<MaterialNode> ingredients = new ArrayList<>();
        for (RecipeResult.Ingredient ing : recipe.input()) {
            int ingNeeded = ing.count() * batches;
            ResourceLocation ingId = ResourceLocation.parse(ing.itemId());
            // Each branch gets its own visited copy so parallel branches don't clash
            MaterialNode sub = expandRecursive(mc, ingId, ingNeeded, inventory,
                    new HashSet<>(visited), depth + 1);
            ingredients.add(sub);
        }

        visited.remove(id);
        return new MaterialNode(id, getDisplayName(mc, itemId), countNeeded, have,
                "crafting", "Craft at crafting table", ingredients, depth);
    }

    /**
     * Resolve a human-readable display name for the given item.
     * Falls back to the raw item ID string if the item cannot be found.
     */
    private static String getDisplayName(Minecraft mc, ResourceLocation itemId) {
        try {
            Item item = GameDataAccess.lookupItem(itemId);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return item.getDefaultInstance().getHoverName().getString();
            }
        } catch (Exception ignored) {
            // If anything goes wrong, fall through to raw ID
        }
        return itemId.toString();
    }

    /**
     * Material tree node — a node in the recursive expansion tree.
     * <p>
     * Each node represents one item type at one depth level, with its
     * ingredient children forming a tree that reaches down to raw materials.
     *
     * @param itemId       item registry key, e.g. {@code "minecraft:oak_planks"}
     * @param displayName  human-readable name, e.g. {@code "Oak Planks"}
     * @param countNeeded  how many of this item are required
     * @param countHave    how many the player already has
     * @param source       node type: {@code "crafting"}, {@code "raw"},
     *                     {@code "have_enough"}, {@code "cycle"}, {@code "too_deep"}
     * @param sourceNote   human-readable note explaining the source
     * @param ingredients  child nodes (empty for leaf nodes)
     * @param depth        recursion depth (0 = root)
     */
    public record MaterialNode(
            String itemId,
            String displayName,
            int countNeeded,
            int countHave,
            String source,
            String sourceNote,
            List<MaterialNode> ingredients,
            int depth
    ) {}
}

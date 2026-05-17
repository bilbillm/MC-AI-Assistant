package com.lumoren.agentchat.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * 合成配方结果记录。
 * <p>
 * 表示一个合成/烧炼配方的输入和输出。支持多种配方类型：
 * crafting（合成）、smelting（熔炼）、blasting（爆破）、campfire_cooking（营火烹饪）、
 * stonecutting（切石）等。
 *
 * @param recipeId 配方唯一 ID（如 "minecraft:diamond_sword"）
 * @param input    输入材料列表
 * @param output   输出产物
 * @param type     配方类型
 */
public record RecipeResult(
        String recipeId,
        List<Ingredient> input,
        ItemStack output,
        String type
) {

    /**
     * 配方原料。
     *
     * @param itemId 物品标识符
     * @param count  所需数量
     */
    public record Ingredient(
            String itemId,
            int count
    ) {}

    /**
     * 物品堆（配方产物）。
     *
     * @param itemId 物品标识符
     * @param count  产出数量
     */
    public record ItemStack(
            String itemId,
            int count
    ) {}

    /**
     * Convert a list of RecipeResult objects to a Gson JsonArray.
     * <p>
     * Output schema per recipe:
     * <pre>{@code
     * {
     *   "recipe_id": "...",
     *   "type": "...",
     *   "output": { "item_id": "...", "count": N },
     *   "input": [{ "item_id": "...", "count": N }, ...]
     * }
     * }</pre>
     */
    public static JsonArray toJsonArray(List<RecipeResult> recipes) {
        JsonArray arr = new JsonArray();
        for (RecipeResult r : recipes) {
            JsonObject obj = new JsonObject();
            obj.addProperty("recipe_id", r.recipeId());
            obj.addProperty("type", r.type());

            JsonObject out = new JsonObject();
            out.addProperty("item_id", r.output().itemId());
            out.addProperty("count", r.output().count());
            obj.add("output", out);

            JsonArray inputs = new JsonArray();
            for (Ingredient ing : r.input()) {
                JsonObject io = new JsonObject();
                io.addProperty("item_id", ing.itemId());
                io.addProperty("count", ing.count());
                inputs.add(io);
            }
            obj.add("input", inputs);
            arr.add(obj);
        }
        return arr;
    }
}

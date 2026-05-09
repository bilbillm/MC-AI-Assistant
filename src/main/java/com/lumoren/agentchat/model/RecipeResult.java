package com.lumoren.agentchat.model;

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
}

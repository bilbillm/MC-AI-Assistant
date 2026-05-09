package com.lumoren.agentchat.testutil;

import com.lumoren.agentchat.model.InventoryItem;
import com.lumoren.agentchat.model.RecipeResult;
import com.lumoren.agentchat.model.WorldState;

import java.util.List;

/**
 * 预置测试数据集，提供标准化的测试数据工厂方法。
 * <p>
 * 用于单元测试中快速创建具有合理默认值的游戏数据对象，
 * 减少测试代码中的重复数据定义。
 */
public class MockGameData {

    /**
     * 获取标准测试背包物品列表。
     * <p>
     * 包含：钻石剑 x1（槽位 0）、面包 x5（槽位 1）、火把 x64（槽位 2）。
     *
     * @return 包含 3 个物品的背包列表
     */
    public static List<InventoryItem> getStandardInventory() {
        return List.of(
                new InventoryItem(0, "minecraft:diamond_sword", "钻石剑", 1, 1561, 0),
                new InventoryItem(1, "minecraft:bread", "面包", 5, 0, 0),
                new InventoryItem(2, "minecraft:torch", "火把", 64, 0, 0)
        );
    }

    /**
     * 获取主世界测试坐标数据。
     *
     * @return 主世界 (100.5, 64.0, -200.3) 的位置记录
     */
    public static Position getOverworldPosition() {
        return new Position(100.5, 64.0, -200.3, "minecraft:overworld");
    }

    /**
     * 获取钻石剑的合成配方。
     * <p>
     * 配方需要：钻石 x2 + 木棍 x1。
     *
     * @return 钻石剑合成配方
     */
    public static RecipeResult getDiamondSwordRecipe() {
        return new RecipeResult(
                "minecraft:diamond_sword",
                List.of(
                        new RecipeResult.Ingredient("minecraft:diamond", 2),
                        new RecipeResult.Ingredient("minecraft:stick", 1)
                ),
                new RecipeResult.ItemStack("minecraft:diamond_sword", 1),
                "crafting"
        );
    }

    /**
     * 获取默认世界状态。
     * <p>
     * 时间：白天（dayTime=6000），晴朗天气，
     * 和平难度（PEACEFUL），平原生物群系。
     *
     * @return 默认世界状态
     */
    public static WorldState getDefaultWorldState() {
        return new WorldState(6000, false, false, "PEACEFUL", "minecraft:plains");
    }

    /**
     * 三维坐标 + 维度记录。
     *
     * @param x         X 坐标
     * @param y         Y 坐标
     * @param z         Z 坐标
     * @param dimension 维度标识
     */
    public record Position(double x, double y, double z, String dimension) {}
}

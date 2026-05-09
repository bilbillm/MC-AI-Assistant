package com.lumoren.agentchat.model;

/**
 * 背包物品记录。
 * <p>
 * 表示玩家背包中的单个物品槽位，包含物品 ID、显示名称、数量及耐久度信息。
 *
 * @param slot          背包槽位索引（0-35 主背包，36-39 快捷栏）
 * @param itemId        物品标识符（如 "minecraft:diamond_sword"）
 * @param displayName   显示名称（可为中文，如 "钻石剑"）
 * @param count         物品堆叠数量
 * @param maxDamage     物品最大耐久度（0 表示无耐久）
 * @param currentDamage 当前已损耗的耐久度
 */
public record InventoryItem(
        int slot,
        String itemId,
        String displayName,
        int count,
        int maxDamage,
        int currentDamage
) {}

package com.lumoren.agentchat.model;

/**
 * 物品需求记录。
 * <p>
 * 描述完成任务所需的物品：物品 ID、所需数量、已拥有数量。
 *
 * @param itemId 物品 ID（如 "minecraft:diamond"）
 * @param needed 所需数量
 * @param owned  已拥有数量
 */
public record ItemRequirement(
        String itemId,
        int needed,
        int owned
) {
}

package com.lumoren.agentchat.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

/**
 * 玩家游戏上下文记录。
 * <p>
 * 包含位置坐标、维度、背包物品列表、生命值和饥饿度。
 * 用于向 AI 提供当前游戏状态信息。
 *
 * @param playerName 玩家名称
 * @param x          X 坐标
 * @param y          Y 坐标
 * @param z          Z 坐标
 * @param dimension  维度（如 "minecraft:overworld"）
 * @param inventory  背包物品列表
 * @param health     当前生命值
 * @param hunger     当前饥饿度
 */
public record GameContext(
        String playerName,
        double x,
        double y,
        double z,
        String dimension,
        List<InventoryItem> inventory,
        float health,
        int hunger
) {

    /**
     * 转换为 JSON 对象，用于向 AI 传递格式化上下文。
     *
     * @return 包含完整游戏上下文的 JsonObject
     */
    public JsonObject toJsonObject() {
        JsonObject obj = new JsonObject();
        obj.addProperty("player_name", playerName);
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("z", z);
        obj.addProperty("dimension", dimension);

        JsonArray invArray = new JsonArray();
        for (InventoryItem item : inventory) {
            JsonObject itemObj = new JsonObject();
            itemObj.addProperty("slot", item.slot());
            itemObj.addProperty("item_id", item.itemId());
            itemObj.addProperty("display_name", item.displayName());
            itemObj.addProperty("count", item.count());
            itemObj.addProperty("max_damage", item.maxDamage());
            itemObj.addProperty("current_damage", item.currentDamage());
            invArray.add(itemObj);
        }
        obj.add("inventory", invArray);

        obj.addProperty("health", health);
        obj.addProperty("hunger", hunger);
        return obj;
    }
}

package com.lumoren.agentchat.model;

import com.google.gson.JsonObject;

/**
 * 游戏世界状态记录。
 * <p>
 * 包含当前时间、天气、难度和生物群系信息。
 * 用于向 AI 提供环境上下文。
 *
 * @param dayTime     当前游戏时间（tick 数，0-24000）
 * @param isRaining   是否下雨
 * @param isThundering 是否雷暴
 * @param difficulty  游戏难度：PEACEFUL / EASY / NORMAL / HARD
 * @param biome       当前生物群系 ID（如 "minecraft:plains"）
 */
public record WorldState(
        long dayTime,
        boolean isRaining,
        boolean isThundering,
        String difficulty,
        String biome
) {

    /**
     * 转换为 JSON 对象。
     *
     * @return 包含世界状态的 JsonObject
     */
    public JsonObject toJsonObject() {
        JsonObject obj = new JsonObject();
        obj.addProperty("day_time", dayTime);
        obj.addProperty("is_raining", isRaining);
        obj.addProperty("is_thundering", isThundering);
        obj.addProperty("difficulty", difficulty);
        obj.addProperty("biome", biome);
        return obj;
    }
}

package com.lumoren.agentchat.model;

import java.util.List;
import java.util.UUID;

/**
 * 任务记录。
 * <p>
 * 描述项目中的一个可执行任务，包含描述、类型、状态、所需物品和备注。
 *
 * @param id            任务唯一 ID（自动生成 UUID）
 * @param description   任务描述
 * @param type          任务类型
 * @param status        任务状态
 * @param requiredItems 所需物品列表
 * @param note          任务备注
 */
public record Task(
        String id,
        String description,
        TaskType type,
        TaskStatus status,
        List<ItemRequirement> requiredItems,
        String note
) {
    /**
     * 紧凑构造函数：若 ID 为 null，则自动生成 UUID。
     */
    public Task {
        id = (id == null) ? UUID.randomUUID().toString() : id;
    }

    /**
     * 创建任务的便捷工厂方法（自动生成 UUID，状态默认为 PENDING）。
     *
     * @param description   任务描述
     * @param type          任务类型
     * @param requiredItems 所需物品列表
     * @return 新建的 Task 实例
     */
    public static Task of(String description, TaskType type, List<ItemRequirement> requiredItems) {
        return new Task(null, description, type, TaskStatus.PENDING, requiredItems, null);
    }
}

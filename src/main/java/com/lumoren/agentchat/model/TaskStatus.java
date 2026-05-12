package com.lumoren.agentchat.model;

/**
 * 任务状态枚举。
 * <p>
 * 定义单个任务在其执行周期中的各个阶段。
 */
public enum TaskStatus {
    PENDING,
    IN_PROGRESS,
    DONE,
    BLOCKED
}

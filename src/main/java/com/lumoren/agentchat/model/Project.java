package com.lumoren.agentchat.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 项目实体 — 管理单项目中的任务列表和状态。
 * <p>
 * 每个项目包含唯一 ID、名称、任务列表、状态以及创建/更新时间戳。
 * 可变的，支持添加任务、替换任务列表、更新状态等操作。
 */
public class Project {

    private String id;
    private String name;
    private final List<Task> tasks;
    private ProjectStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * 创建一个新项目（自动生成 UUID，状态为 CREATED）。
     *
     * @param name 项目名称
     */
    public Project(String name) {
        this(UUID.randomUUID().toString(), name, new ArrayList<>(), ProjectStatus.CREATED, Instant.now(), Instant.now());
    }

    /**
     * 全参构造函数（用于从持久化数据恢复）。
     *
     * @param id        项目唯一 ID
     * @param name      项目名称
     * @param tasks     任务列表
     * @param status    项目状态
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public Project(String id, String name, List<Task> tasks, ProjectStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.tasks = new ArrayList<>(tasks);
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ========== 行为方法 ==========

    /**
     * 添加一个任务到项目，并更新 {@code updatedAt} 时间戳。
     *
     * @param task 要添加的任务
     */
    public void addTask(Task task) {
        tasks.add(task);
        updatedAt = Instant.now();
    }

    /**
     * 替换整个任务列表，并更新 {@code updatedAt} 时间戳。
     *
     * @param newTasks 新的任务列表
     */
    public void setTasks(List<Task> newTasks) {
        tasks.clear();
        tasks.addAll(newTasks);
        updatedAt = Instant.now();
    }

    /**
     * 清空所有任务并更新时间戳。
     */
    public void clearTasks() {
        tasks.clear();
        updatedAt = Instant.now();
    }

    // ========== Getter / Setter ==========

    /**
     * 获取不可变的任务列表副本。
     *
     * @return 只读任务列表
     */
    public List<Task> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
        updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

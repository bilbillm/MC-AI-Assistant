package com.lumoren.agentchat.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 对话线程 — 管理单次聊天会话的消息列表。
 * <p>
 * 每个线程包含唯一 ID、名称、消息历史以及创建/更新时间戳。
 * 按存档存储（每个世界独立），支持持久化到 JSON。
 */
public class ConversationThread {

    private final String id;
    private String name;
    private final List<ChatMessage> messages;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * 创建一个新的对话线程（自动生成 UUID）。
     *
     * @param name 对话名称
     */
    public ConversationThread(String name) {
        this(UUID.randomUUID().toString(), name, new ArrayList<>(), Instant.now(), Instant.now());
    }

    /**
     * 全参构造函数（用于从持久化数据恢复）。
     *
     * @param id        线程唯一 ID
     * @param name      对话名称
     * @param messages  消息列表
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public ConversationThread(String id, String name, List<ChatMessage> messages, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.messages = new ArrayList<>(messages);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ========== 行为方法 ==========

    /**
     * 添加一条消息到对话历史，并更新 {@code updatedAt} 时间戳。
     *
     * @param message 要添加的消息
     */
    public void addMessage(ChatMessage message) {
        messages.add(message);
        updatedAt = Instant.now();
    }

    /**
     * 获取不可变的消息列表副本。
     *
     * @return 只读消息列表
     */
    public List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    /**
     * 获取消息数量。
     *
     * @return 消息总数
     */
    public int messageCount() {
        return messages.size();
    }

    // ========== Getter ==========

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

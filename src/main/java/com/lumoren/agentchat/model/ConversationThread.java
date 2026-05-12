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
     * 清空所有消息并更新时间戳。
     */
    public void clearMessages() {
        messages.clear();
        updatedAt = Instant.now();
    }

    /**
     * 从指定位置（含）截断消息列表。
     * 保留 index 之前的所有消息，删除 index 及之后的消息。
     *
     * @param fromIndexInclusive 开始删除的位置（含）
     * @throws IndexOutOfBoundsException 若 index 越界
     */
    public void removeMessagesFrom(int fromIndexInclusive) {
        if (fromIndexInclusive < 0 || fromIndexInclusive > messages.size()) {
            throw new IndexOutOfBoundsException(
                "Index: " + fromIndexInclusive + ", Size: " + messages.size());
        }
        if (fromIndexInclusive < messages.size()) {
            messages.subList(fromIndexInclusive, messages.size()).clear();
        }
        updatedAt = Instant.now();
    }

    /**
     * 根据消息 ID 找到索引并从该位置截断。
     *
     * @param messageId 目标消息的 UUID
     * @return 截断的起始索引，若未找到则返回 -1
     */
    public int findMessageIndex(String messageId) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).id().equals(messageId)) {
                return i;
            }
        }
        return -1;
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

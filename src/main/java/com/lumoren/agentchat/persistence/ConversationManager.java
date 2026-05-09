package com.lumoren.agentchat.persistence;

import com.lumoren.agentchat.i18n.I18nHelper;
import com.lumoren.agentchat.i18n.I18nKeys;
import com.lumoren.agentchat.model.ConversationThread;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 多会话管理器，负责创建、切换、删除对话线程，并自动持久化到当前存档。
 * <p>
 * 每个线程拥有独立的消息历史，默认名称为 {@code 新对话 #1}、{@code 新对话 #2} ……
 * 所有变更操作（创建、删除、重命名、切线）都会触发自动保存。
 */
public class ConversationManager {

    private static ConversationManager instance;

    private final ChatHistoryManager historyManager;
    private final List<ConversationThread> threads = new ArrayList<>();
    private ConversationThread currentThread;

    /**
     * 初始化全局单例，绑定到指定存档目录。
     *
     * @param worldSaveDir 当前世界的存档根目录
     */
    public static synchronized void initialize(Path worldSaveDir) {
        instance = new ConversationManager(new ChatHistoryManager(worldSaveDir));
    }

    /**
     * 关闭当前管理器并保存数据，随后清空单例。
     */
    public static synchronized void shutdown() {
        if (instance != null) {
            instance.save();
            instance = null;
        }
    }

    /**
     * 获取当前全局单例。
     *
     * @return 当前 ConversationManager，若尚未初始化则返回 {@code null}
     */
    public static synchronized ConversationManager getInstance() {
        return instance;
    }

    private ConversationManager(ChatHistoryManager historyManager) {
        this.historyManager = historyManager;
        List<ConversationThread> loaded = this.historyManager.load();
        this.threads.addAll(loaded);
        if (!this.threads.isEmpty()) {
            this.currentThread = this.threads.get(0);
        }
    }

    /**
     * 创建一个新的对话线程并设为当前线程。
     *
     * @return 新创建的线程
     */
    public synchronized ConversationThread createThread() {
        String name = generateDefaultName();
        ConversationThread thread = new ConversationThread(name);
        threads.add(thread);
        currentThread = thread;
        save();
        return thread;
    }

    /**
     * 使用指定名称创建新线程并设为当前线程。
     *
     * @param name 对话名称
     * @return 新创建的线程
     */
    public synchronized ConversationThread createThread(String name) {
        ConversationThread thread = new ConversationThread(name);
        threads.add(thread);
        currentThread = thread;
        save();
        return thread;
    }

    /**
     * 切换到指定 ID 的线程。
     *
     * @param threadId 目标线程 ID
     * @return 是否切换成功
     */
    public synchronized boolean switchThread(String threadId) {
        for (ConversationThread t : threads) {
            if (t.getId().equals(threadId)) {
                currentThread = t;
                return true;
            }
        }
        return false;
    }

    /**
     * 删除指定 ID 的线程。删除后自动切换到剩余线程中的第一个；
     * 若无剩余线程，则创建一个新的默认线程。
     *
     * @param threadId 要删除的线程 ID
     * @return 是否删除成功
     */
    public synchronized boolean deleteThread(String threadId) {
        boolean removed = threads.removeIf(t -> t.getId().equals(threadId));
        if (!removed) {
            return false;
        }
        if (currentThread != null && currentThread.getId().equals(threadId)) {
            if (threads.isEmpty()) {
                currentThread = createThread();
            } else {
                currentThread = threads.get(0);
            }
        }
        save();
        return true;
    }

    /**
     * 重命名指定线程。
     *
     * @param threadId 目标线程 ID
     * @param name     新名称
     * @return 是否重命名成功
     */
    public synchronized boolean renameThread(String threadId, String name) {
        for (ConversationThread t : threads) {
            if (t.getId().equals(threadId)) {
                t.setName(name);
                save();
                return true;
            }
        }
        return false;
    }

    /**
     * 获取当前活跃线程。若不存在则自动创建一个。
     *
     * @return 当前线程（不会返回 {@code null}）
     */
    public synchronized ConversationThread getCurrentThread() {
        if (currentThread == null) {
            currentThread = createThread();
        }
        return currentThread;
    }

    /**
     * 获取所有线程的不可变列表副本。
     *
     * @return 线程列表
     */
    public synchronized List<ConversationThread> getAllThreads() {
        return Collections.unmodifiableList(new ArrayList<>(threads));
    }

    /**
     * 保存当前所有线程到磁盘。
     */
    public synchronized void save() {
        historyManager.save(new ArrayList<>(threads));
    }

    private String generateDefaultName() {
        int index = threads.size() + 1;
        return I18nHelper.translateToString(I18nKeys.THREAD_NEW) + " #" + index;
    }
}

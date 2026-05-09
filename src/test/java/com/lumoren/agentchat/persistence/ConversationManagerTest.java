package com.lumoren.agentchat.persistence;

import com.lumoren.agentchat.model.ConversationThread;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConversationManager 单元测试。
 * <p>
 * 验证线程创建、切换、删除、重命名及持久化行为。
 */
class ConversationManagerTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ConversationManager.initialize(tempDir.resolve("world"));
    }

    @AfterEach
    void tearDown() {
        ConversationManager.shutdown();
    }

    @Test
    void createThreadSetsCurrentAndAutoNames() {
        ConversationManager manager = ConversationManager.getInstance();
        assertNotNull(manager);

        ConversationThread t1 = manager.createThread();
        assertNotNull(t1.getId());
        assertTrue(t1.getName().contains("#1"));
        assertSame(t1, manager.getCurrentThread());

        ConversationThread t2 = manager.createThread();
        assertTrue(t2.getName().contains("#2"));
        assertSame(t2, manager.getCurrentThread());
    }

    @Test
    void createThreadWithCustomName() {
        ConversationManager manager = ConversationManager.getInstance();
        ConversationThread t = manager.createThread("我的对话");

        assertEquals("我的对话", t.getName());
        assertSame(t, manager.getCurrentThread());
    }

    @Test
    void switchThreadChangesCurrent() {
        ConversationManager manager = ConversationManager.getInstance();
        ConversationThread t1 = manager.createThread("A");
        ConversationThread t2 = manager.createThread("B");

        assertSame(t2, manager.getCurrentThread());

        boolean switched = manager.switchThread(t1.getId());
        assertTrue(switched);
        assertSame(t1, manager.getCurrentThread());
    }

    @Test
    void switchThreadReturnsFalseForUnknownId() {
        ConversationManager manager = ConversationManager.getInstance();
        manager.createThread();

        boolean switched = manager.switchThread("non-existent-id");
        assertFalse(switched);
    }

    @Test
    void deleteThreadRemovesAndSwitches() {
        ConversationManager manager = ConversationManager.getInstance();
        ConversationThread t1 = manager.createThread("A");
        ConversationThread t2 = manager.createThread("B");

        boolean deleted = manager.deleteThread(t1.getId());
        assertTrue(deleted);

        List<ConversationThread> all = manager.getAllThreads();
        assertEquals(1, all.size());
        assertSame(t2, manager.getCurrentThread());
    }

    @Test
    void deleteLastThreadCreatesNewDefault() {
        ConversationManager manager = ConversationManager.getInstance();
        ConversationThread t1 = manager.createThread("唯一对话");

        boolean deleted = manager.deleteThread(t1.getId());
        assertTrue(deleted);

        List<ConversationThread> all = manager.getAllThreads();
        assertEquals(1, all.size());
        assertNotEquals(t1.getId(), manager.getCurrentThread().getId());
    }

    @Test
    void deleteThreadReturnsFalseForUnknownId() {
        ConversationManager manager = ConversationManager.getInstance();
        manager.createThread();

        boolean deleted = manager.deleteThread("unknown");
        assertFalse(deleted);
    }

    @Test
    void renameThreadUpdatesName() {
        ConversationManager manager = ConversationManager.getInstance();
        ConversationThread t = manager.createThread("旧名称");

        boolean renamed = manager.renameThread(t.getId(), "新名称");
        assertTrue(renamed);
        assertEquals("新名称", t.getName());
    }

    @Test
    void renameThreadReturnsFalseForUnknownId() {
        ConversationManager manager = ConversationManager.getInstance();
        manager.createThread();

        boolean renamed = manager.renameThread("unknown", "名称");
        assertFalse(renamed);
    }

    @Test
    void persistenceAcrossInstances() {
        ConversationManager manager = ConversationManager.getInstance();
        ConversationThread t = manager.createThread("持久化测试");
        t.addMessage(com.lumoren.agentchat.model.ChatMessage.user("测试消息"));
        manager.save();

        // 模拟重新加载（shutdown + initialize）
        ConversationManager.shutdown();
        ConversationManager.initialize(tempDir.resolve("world"));

        ConversationManager reloaded = ConversationManager.getInstance();
        assertNotNull(reloaded);
        List<ConversationThread> all = reloaded.getAllThreads();
        assertEquals(1, all.size());
        assertEquals("持久化测试", all.get(0).getName());
        assertEquals(1, all.get(0).messageCount());
    }

    @Test
    void getAllThreadsReturnsImmutableCopy() {
        ConversationManager manager = ConversationManager.getInstance();
        manager.createThread();

        List<ConversationThread> all = manager.getAllThreads();
        assertThrows(UnsupportedOperationException.class, all::clear);
    }
}

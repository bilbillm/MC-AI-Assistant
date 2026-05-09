package com.lumoren.agentchat.persistence;

import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.model.ConversationThread;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatHistoryManager 单元测试。
 * <p>
 * 验证 JSON 序列化 / 反序列化、多存档隔离、maxHistory 裁剪等核心行为。
 */
class ChatHistoryManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadRoundTrip() {
        Path worldDir = tempDir.resolve("world1");
        ChatHistoryManager manager = new ChatHistoryManager(worldDir);

        ConversationThread t1 = new ConversationThread("对话 1");
        t1.addMessage(ChatMessage.user("你好"));
        t1.addMessage(ChatMessage.assistant("你好！有什么可以帮你的？"));

        ConversationThread t2 = new ConversationThread("对话 2");
        t2.addMessage(ChatMessage.user("今天天气怎么样"));

        List<ConversationThread> threads = List.of(t1, t2);
        manager.save(threads);

        List<ConversationThread> loaded = manager.load();
        assertEquals(2, loaded.size());

        ConversationThread lt1 = loaded.get(0);
        assertEquals(t1.getId(), lt1.getId());
        assertEquals("对话 1", lt1.getName());
        assertEquals(2, lt1.messageCount());
        assertEquals("你好", lt1.getMessages().get(0).content());
        assertEquals("assistant", lt1.getMessages().get(1).role());

        ConversationThread lt2 = loaded.get(1);
        assertEquals("对话 2", lt2.getName());
        assertEquals(1, lt2.messageCount());
    }

    @Test
    void loadReturnsEmptyListWhenFileMissing() {
        Path worldDir = tempDir.resolve("empty_world");
        ChatHistoryManager manager = new ChatHistoryManager(worldDir);

        List<ConversationThread> loaded = manager.load();
        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void deleteRemovesThread() {
        Path worldDir = tempDir.resolve("world_delete");
        ChatHistoryManager manager = new ChatHistoryManager(worldDir);

        ConversationThread t1 = new ConversationThread("对话 A");
        ConversationThread t2 = new ConversationThread("对话 B");
        manager.save(List.of(t1, t2));

        manager.delete(t1.getId());

        List<ConversationThread> loaded = manager.load();
        assertEquals(1, loaded.size());
        assertEquals(t2.getId(), loaded.get(0).getId());
    }

    @Test
    void differentWorldsHaveIsolatedDirectories() {
        Path worldA = tempDir.resolve("world_a");
        Path worldB = tempDir.resolve("world_b");

        ChatHistoryManager managerA = new ChatHistoryManager(worldA);
        ChatHistoryManager managerB = new ChatHistoryManager(worldB);

        ConversationThread tA = new ConversationThread("存档 A 对话");
        tA.addMessage(ChatMessage.user("存档 A 的消息"));
        managerA.save(List.of(tA));

        ConversationThread tB = new ConversationThread("存档 B 对话");
        tB.addMessage(ChatMessage.user("存档 B 的消息"));
        managerB.save(List.of(tB));

        List<ConversationThread> loadedA = managerA.load();
        List<ConversationThread> loadedB = managerB.load();

        assertEquals(1, loadedA.size());
        assertEquals(1, loadedB.size());
        assertEquals("存档 A 对话", loadedA.get(0).getName());
        assertEquals("存档 B 对话", loadedB.get(0).getName());
    }

    @Test
    void saveTrimsMessagesToMaxHistory() {
        Path worldDir = tempDir.resolve("world_trim");
        ChatHistoryManager manager = new ChatHistoryManager(worldDir);

        ConversationThread thread = new ConversationThread("长对话");
        for (int i = 0; i < 250; i++) {
            thread.addMessage(ChatMessage.user("消息 " + i));
        }

        manager.save(List.of(thread));

        List<ConversationThread> loaded = manager.load();
        assertEquals(1, loaded.size());
        // Config 默认 maxHistory = 200，因此保存后应被裁剪到 200 条
        assertTrue(loaded.get(0).messageCount() <= 200,
                "加载后的消息数应不超过 maxHistory（默认 200），实际为 " + loaded.get(0).messageCount());
    }

    @Test
    void timestampsArePreserved() {
        Path worldDir = tempDir.resolve("world_time");
        ChatHistoryManager manager = new ChatHistoryManager(worldDir);

        ConversationThread thread = new ConversationThread("时间测试");
        thread.addMessage(ChatMessage.user("测试"));

        manager.save(List.of(thread));
        List<ConversationThread> loaded = manager.load();

        assertNotNull(loaded.get(0).getCreatedAt());
        assertNotNull(loaded.get(0).getUpdatedAt());
    }
}

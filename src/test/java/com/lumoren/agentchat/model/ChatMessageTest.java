package com.lumoren.agentchat.model;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatMessage 模型的单元测试。
 * <p>
 * 验证工厂方法正确性、字段赋值以及 JSON 序列化/反序列化往返。
 */
class ChatMessageTest {

    private static final Gson GSON = new Gson();

    // ========== 工厂方法测试 ==========

    @Test
    void userMessage_createsMessageWithUserRole() {
        ChatMessage msg = ChatMessage.user("你好");
        assertEquals("user", msg.role());
        assertEquals("你好", msg.content());
        assertNull(msg.toolCalls());
        assertNull(msg.toolCallId());
    }

    @Test
    void assistantMessage_createsMessageWithAssistantRole() {
        ChatMessage msg = ChatMessage.assistant("Hello!");
        assertEquals("assistant", msg.role());
        assertEquals("Hello!", msg.content());
        assertNull(msg.toolCalls());
        assertNull(msg.toolCallId());
    }

    @Test
    void assistantMessage_withToolCalls_createsMessageWithCalls() {
        var function = new ChatMessage.FunctionCall("query_item", "{\"item\":\"diamond\"}");
        var toolCall = new ChatMessage.ToolCall("call_123", function);
        ChatMessage msg = new ChatMessage("assistant", "", null, List.of(toolCall), null);

        assertEquals("assistant", msg.role());
        assertEquals("", msg.content());
        assertNotNull(msg.toolCalls());
        assertEquals(1, msg.toolCalls().size());
        assertEquals("call_123", msg.toolCalls().get(0).id());
        assertEquals("query_item", msg.toolCalls().get(0).function().name());
    }

    @Test
    void systemMessage_createsMessageWithSystemRole() {
        ChatMessage msg = ChatMessage.system("你是一个助手");
        assertEquals("system", msg.role());
        assertEquals("你是一个助手", msg.content());
    }

    @Test
    void toolMessage_createsMessageWithToolRoleAndCallId() {
        ChatMessage msg = ChatMessage.tool("call_456", "{\"result\":\"ok\"}");
        assertEquals("tool", msg.role());
        assertEquals("{\"result\":\"ok\"}", msg.content());
        assertEquals("call_456", msg.toolCallId());
    }

    // ========== JSON 序列化/反序列化往返测试 ==========

    @Test
    void chatMessage_serializationRoundTrip_userMessage() {
        ChatMessage original = ChatMessage.user("测试消息");
        String json = GSON.toJson(original);
        ChatMessage restored = GSON.fromJson(json, ChatMessage.class);

        assertEquals(original.role(), restored.role());
        assertEquals(original.content(), restored.content());
        assertEquals(original.toolCalls(), restored.toolCalls());
        assertEquals(original.toolCallId(), restored.toolCallId());
    }

    @Test
    void chatMessage_serializationRoundTrip_assistantWithToolCalls() {
        var function = new ChatMessage.FunctionCall("get_time", "{}");
        var toolCall = new ChatMessage.ToolCall("call_999", function);
        ChatMessage original = new ChatMessage("assistant", "请稍等", null, List.of(toolCall), null);

        String json = GSON.toJson(original);
        ChatMessage restored = GSON.fromJson(json, ChatMessage.class);

        assertEquals("assistant", restored.role());
        assertEquals("请稍等", restored.content());
        assertNotNull(restored.toolCalls());
        assertEquals(1, restored.toolCalls().size());
        assertEquals("call_999", restored.toolCalls().get(0).id());
        assertEquals("get_time", restored.toolCalls().get(0).function().name());
        assertEquals("{}", restored.toolCalls().get(0).function().arguments());
    }

    @Test
    void chatMessage_serializationRoundTrip_toolMessage() {
        ChatMessage original = ChatMessage.tool("call_777", "执行完毕");
        String json = GSON.toJson(original);
        ChatMessage restored = GSON.fromJson(json, ChatMessage.class);

        assertEquals("tool", restored.role());
        assertEquals("执行完毕", restored.content());
        assertEquals("call_777", restored.toolCallId());
    }

    @Test
    void chatMessage_systemMessageFactory() {
        ChatMessage msg = ChatMessage.system("Be helpful");
        assertEquals("system", msg.role());
        assertEquals("Be helpful", msg.content());
    }
}

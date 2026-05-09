package com.lumoren.agentchat.ai;

import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.model.ToolResult;
import com.lumoren.agentchat.tools.ToolRegistry;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ToolCallDispatcher}.
 */
class ToolCallDispatcherTest {

    @Test
    void executeToolCalls_withOneToolCall_returnsOneToolMessage() {
        ToolRegistry registry = mock(ToolRegistry.class);
        Minecraft mc = mock(Minecraft.class);

        when(registry.executeTool("get_inventory", mc, "{}"))
            .thenReturn(new ToolResult("call_1", "Diamond Sword, Bread x5"));

        ToolCallDispatcher dispatcher = new ToolCallDispatcher(registry);
        List<ChatMessage.ToolCall> toolCalls = List.of(
            new ChatMessage.ToolCall("call_1", new ChatMessage.FunctionCall("get_inventory", "{}"))
        );

        List<ChatMessage> results = dispatcher.executeToolCalls(toolCalls, mc);

        assertEquals(1, results.size());
        ChatMessage msg = results.get(0);
        assertEquals("tool", msg.role());
        assertEquals("call_1", msg.toolCallId());
        assertEquals("Diamond Sword, Bread x5", msg.content());
    }

    @Test
    void executeToolCalls_withTwoToolCalls_returnsTwoToolMessages() {
        ToolRegistry registry = mock(ToolRegistry.class);
        Minecraft mc = mock(Minecraft.class);

        when(registry.executeTool("get_inventory", mc, "{}"))
            .thenReturn(new ToolResult("call_1", "3 items"));
        when(registry.executeTool("get_position", mc, "{}"))
            .thenReturn(new ToolResult("call_2", "x=100, y=64, z=-200"));

        ToolCallDispatcher dispatcher = new ToolCallDispatcher(registry);
        List<ChatMessage.ToolCall> toolCalls = List.of(
            new ChatMessage.ToolCall("call_1", new ChatMessage.FunctionCall("get_inventory", "{}")),
            new ChatMessage.ToolCall("call_2", new ChatMessage.FunctionCall("get_position", "{}"))
        );

        List<ChatMessage> results = dispatcher.executeToolCalls(toolCalls, mc);

        assertEquals(2, results.size());

        assertEquals("tool", results.get(0).role());
        assertEquals("call_1", results.get(0).toolCallId());
        assertEquals("3 items", results.get(0).content());

        assertEquals("tool", results.get(1).role());
        assertEquals("call_2", results.get(1).toolCallId());
        assertEquals("x=100, y=64, z=-200", results.get(1).content());
    }

    @Test
    void executeToolCalls_withUnknownTool_returnsErrorToolMessage() {
        ToolRegistry registry = mock(ToolRegistry.class);
        Minecraft mc = mock(Minecraft.class);

        when(registry.executeTool("unknown_tool", mc, "{}"))
            .thenReturn(new ToolResult("unknown", "Tool not found: unknown_tool"));

        ToolCallDispatcher dispatcher = new ToolCallDispatcher(registry);
        List<ChatMessage.ToolCall> toolCalls = List.of(
            new ChatMessage.ToolCall("call_99", new ChatMessage.FunctionCall("unknown_tool", "{}"))
        );

        List<ChatMessage> results = dispatcher.executeToolCalls(toolCalls, mc);

        assertEquals(1, results.size());
        assertEquals("tool", results.get(0).role());
        assertEquals("call_99", results.get(0).toolCallId());
        assertEquals("Tool not found: unknown_tool", results.get(0).content());
    }

    @Test
    void executeToolCalls_delegatesToToolRegistry() {
        ToolRegistry registry = mock(ToolRegistry.class);
        Minecraft mc = mock(Minecraft.class);

        when(registry.executeTool(anyString(), eq(mc), anyString()))
            .thenReturn(new ToolResult("call_1", "ok"));

        ToolCallDispatcher dispatcher = new ToolCallDispatcher(registry);
        List<ChatMessage.ToolCall> toolCalls = List.of(
            new ChatMessage.ToolCall("call_1", new ChatMessage.FunctionCall("my_tool", "{\"key\":\"value\"}"))
        );

        dispatcher.executeToolCalls(toolCalls, mc);

        verify(registry).executeTool("my_tool", mc, "{\"key\":\"value\"}");
    }
}

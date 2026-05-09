package com.lumoren.agentchat.model;

import java.util.List;
import java.util.UUID;

public record ChatMessage(
    String role,
    String content,
    String toolCallId,
    List<ToolCall> toolCalls,
    String id
) {
    public ChatMessage {
        id = (id == null) ? UUID.randomUUID().toString() : id;
    }

    public record ToolCall(String id, FunctionCall function) {}

    public record FunctionCall(String name, String arguments) {}

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null, null, null);
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null, null);
    }

    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage("tool", content, toolCallId, null, null);
    }
}

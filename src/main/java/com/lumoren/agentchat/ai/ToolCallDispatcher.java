package com.lumoren.agentchat.ai;

import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.model.ToolResult;
import com.lumoren.agentchat.tools.ToolRegistry;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Dispatches tool calls from AI responses to the {@link ToolRegistry} and
 * converts execution results back into tool-role {@link ChatMessage}s.
 */
public class ToolCallDispatcher {

    private final ToolRegistry toolRegistry;

    public ToolCallDispatcher(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * Execute a list of tool calls and return the results as tool messages.
     *
     * @param toolCalls the tool calls from the AI response
     * @param mc        the Minecraft client instance
     * @return list of tool-role chat messages containing execution results
     */
    public List<ChatMessage> executeToolCalls(List<ChatMessage.ToolCall> toolCalls, Minecraft mc) {
        List<ChatMessage> results = new ArrayList<>();
        for (ChatMessage.ToolCall tc : toolCalls) {
            String result = executeSingleTool(tc, mc);
            results.add(ChatMessage.tool(tc.id(), result));
        }
        return results;
    }

    private String executeSingleTool(ChatMessage.ToolCall tc, Minecraft mc) {
        ToolResult result = toolRegistry.executeTool(tc.function().name(), mc, tc.function().arguments());
        return result.content();
    }
}

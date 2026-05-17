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

    /**
     * Execute a single tool call and return the result string.
     * <p>
     * Successful results are returned as-is from {@link ToolResult#content()}.
     * Errors are serialized as a JSON object with {@code "error": true} so the AI
     * can distinguish failures from valid tool output.
     *
     * @param tc the tool call to execute
     * @param mc the Minecraft client instance
     * @return tool result content string
     */
    private String executeSingleTool(ChatMessage.ToolCall tc, Minecraft mc) {
        try {
            ToolResult result = toolRegistry.executeTool(tc.function().name(), mc, tc.function().arguments());
            return result.content();
        } catch (Exception e) {
            return "{\"error\": true, \"tool\": \"" + tc.function().name()
                    + "\", \"message\": \"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}

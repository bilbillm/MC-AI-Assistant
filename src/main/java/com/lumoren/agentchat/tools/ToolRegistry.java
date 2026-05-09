package com.lumoren.agentchat.tools;

import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import net.minecraft.client.Minecraft;

import java.util.*;

/**
 * Registry for managing game tools.
 * <p>
 * Provides registration, lookup, and execution of tools that can be
 * exposed to AI models via OpenAI function calling.
 */
public class ToolRegistry {

    private final Map<String, GameTool> tools = new LinkedHashMap<>();

    /**
     * Register a tool.
     *
     * @param tool the tool to register
     */
    public void register(GameTool tool) {
        Objects.requireNonNull(tool, "tool must not be null");
        tools.put(tool.getName(), tool);
    }

    /**
     * Get a registered tool by name.
     *
     * @param name tool name
     * @return the tool, or null if not found
     */
    public GameTool get(String name) {
        return tools.get(name);
    }

    /**
     * @return unmodifiable list of all registered tools (insertion order)
     */
    public List<GameTool> getAll() {
        return List.copyOf(tools.values());
    }

    /**
     * @return list of OpenAI tool definitions for all registered tools
     */
    public List<ToolDefinition> getToolDefinitions() {
        List<ToolDefinition> defs = new ArrayList<>();
        for (GameTool tool : tools.values()) {
            defs.add(tool.getDefinition());
        }
        return defs;
    }

    /**
     * Execute a tool by name with the provided arguments.
     *
     * @param name      tool name
     * @param mc        Minecraft client instance
     * @param arguments JSON string of arguments
     * @return tool execution result, or error ToolResult if tool not found
     */
    public ToolResult executeTool(String name, Minecraft mc, String arguments) {
        GameTool tool = tools.get(name);
        if (tool == null) {
            return new ToolResult("unknown", "Tool not found: " + name);
        }
        return tool.execute(mc, arguments);
    }

    /**
     * @return number of registered tools
     */
    public int size() {
        return tools.size();
    }
}

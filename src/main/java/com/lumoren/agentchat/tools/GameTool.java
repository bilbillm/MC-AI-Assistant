package com.lumoren.agentchat.tools;

import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import net.minecraft.client.Minecraft;

/**
 * Game tool interface - all tools implement this.
 * <p>
 * Each tool encapsulates a specific game data query (inventory, blocks, entities, recipes, etc.)
 * that can be exposed to AI via OpenAI function calling.
 * <p>
 * execute() reads game data and returns a ToolResult.
 */
public interface GameTool {

    /**
     * @return tool name (e.g. "get_inventory")
     */
    String getName();

    /**
     * @return human-readable description (e.g. "Query the player's inventory")
     */
    String getDescription();

    /**
     * @return OpenAI tool definition schema for function calling registration
     */
    ToolDefinition getDefinition();

    /**
     * Execute the tool with the given JSON arguments and Minecraft client context.
     *
     * @param mc        Minecraft client instance for reading game state
     * @param arguments JSON string of arguments per the tool's parameter schema
     * @return execution result containing tool call ID and content
     */
    ToolResult execute(Minecraft mc, String arguments);
}

package com.lumoren.agentchat.tools;

import com.google.gson.JsonObject;
import com.lumoren.agentchat.ai.GameDataAccess;
import com.lumoren.agentchat.model.GameContext;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import net.minecraft.client.Minecraft;

/**
 * Tool to get the player's current status including position, dimension, health, and hunger.
 */
public class PositionTool implements GameTool {

    private static final String NAME = "get_player_status";
    private static final String DESCRIPTION = "Get the player's current status including position (x, y, z), dimension, health, and hunger.";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        return new ToolDefinition(NAME, DESCRIPTION, parameters);
    }

    @Override
    public ToolResult execute(Minecraft mc, String arguments) {
        try {
            GameContext context = GameDataAccess.getPlayerContext(mc);
            return new ToolResult(NAME, context.toJsonObject().toString());
        } catch (Exception e) {
            return new ToolResult(NAME, "Error: " + e.getMessage());
        }
    }
}

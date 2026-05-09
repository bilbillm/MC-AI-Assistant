package com.lumoren.agentchat.tools;

import com.google.gson.JsonObject;
import com.lumoren.agentchat.ai.GameDataAccess;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import com.lumoren.agentchat.model.WorldState;
import net.minecraft.client.Minecraft;

/**
 * Tool to get the current world state including time, weather, difficulty, and biome.
 */
public class WorldStateTool implements GameTool {

    private static final String NAME = "get_world_state";
    private static final String DESCRIPTION = "Get the current world state including time of day, weather (raining/thundering), difficulty, and biome.";

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
            WorldState worldState = GameDataAccess.getWorldState(mc);
            return new ToolResult(NAME, worldState.toJsonObject().toString());
        } catch (Exception e) {
            return new ToolResult(NAME, "Error: " + e.getMessage());
        }
    }
}

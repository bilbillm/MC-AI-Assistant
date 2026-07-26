package com.lumoren.agentchat.client;

import com.lumoren.agentchat.AgentChat;
import com.lumoren.agentchat.ai.GameDataAccess;
import com.lumoren.agentchat.ui.action.AssistantActionExecutor;
import net.minecraft.resources.ResourceLocation;

/**
 * Production {@link AssistantActionExecutor.JeiGui} backed by the real JEI runtime.
 * Instances are cheap; the class is only wired up on the client.
 */
public class JeiGuiBridge implements AssistantActionExecutor.JeiGui {

    @Override
    public boolean isAvailable() {
        return AgentChat.hasJei();
    }

    @Override
    public boolean showRecipes(String itemId) {
        ResourceLocation id = parse(itemId);
        return id != null && JeiBridge.showRecipes(id);
    }

    @Override
    public boolean showUsages(String itemId) {
        ResourceLocation id = parse(itemId);
        return id != null && JeiBridge.showUsages(id);
    }

    private static ResourceLocation parse(String itemId) {
        try {
            return GameDataAccess.resolveItemId(itemId);
        } catch (RuntimeException e) {
            return null;
        }
    }
}

package com.lumoren.agentchat.client;

import com.lumoren.agentchat.AgentChat;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI plugin bridge — caches the JEI runtime reference so
 * {@link com.lumoren.agentchat.tools.RecipeTool} can use JEI for
 * rich recipe lookup. Falls back to vanilla RecipeManager when JEI
 * is not installed.
 */
@JeiPlugin
public class JeiBridge implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("agentchat", "jei_bridge");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        AgentChat.jeiRuntime = runtime;
        AgentChat.LOGGER.info("JEI runtime available — RecipeTool will use JEI-powered lookups.");
    }

    @Override
    public void onRuntimeUnavailable() {
        AgentChat.jeiRuntime = null;
        AgentChat.LOGGER.info("JEI runtime removed — RecipeTool will use vanilla fallback.");
    }
}

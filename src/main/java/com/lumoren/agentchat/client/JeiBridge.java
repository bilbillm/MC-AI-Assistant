package com.lumoren.agentchat.client;

import com.lumoren.agentchat.AgentChat;
import com.lumoren.agentchat.tools.JeiLookup;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI plugin bridge — caches the JEI runtime reference (as {@link Object}
 * in {@link AgentChat#jeiRuntime}, since JEI is compileOnly) so recipe
 * tools can use JEI for rich lookup. Loaded by JEI itself, only when JEI
 * is installed, so referencing mezz.jei types here is safe.
 * GUI open calls delegate to {@link JeiLookup}.
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
        AgentChat.LOGGER.info("JEI runtime available — recipe tools will use JEI-powered lookups.");
    }

    @Override
    public void onRuntimeUnavailable() {
        AgentChat.jeiRuntime = null;
        AgentChat.LOGGER.info("JEI runtime removed — recipe tools will use vanilla fallback.");
    }

    /**
     * Opens the JEI recipes GUI for the given item. Must be called on the
     * Minecraft main thread.
     *
     * @return {@code true} when the GUI was shown
     */
    public static boolean showRecipes(ResourceLocation itemId) {
        return JeiLookup.showRecipesGui(itemId);
    }

    /**
     * Opens the JEI usages GUI for the given item. Must be called on the
     * Minecraft main thread.
     *
     * @return {@code true} when the GUI was shown
     */
    public static boolean showUsages(ResourceLocation itemId) {
        return JeiLookup.showUsagesGui(itemId);
    }
}

package com.lumoren.agentchat.client;

import com.lumoren.agentchat.AgentChat;
import com.lumoren.agentchat.ai.GameDataAccess;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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

    /**
     * Opens the JEI recipes GUI for the given item. Must be called on the
     * Minecraft main thread.
     *
     * @return {@code true} when the GUI was shown
     */
    public static boolean showRecipes(ResourceLocation itemId) {
        return show(itemId, RecipeIngredientRole.OUTPUT);
    }

    /**
     * Opens the JEI usages GUI for the given item. Must be called on the
     * Minecraft main thread.
     *
     * @return {@code true} when the GUI was shown
     */
    public static boolean showUsages(ResourceLocation itemId) {
        return show(itemId, RecipeIngredientRole.INPUT);
    }

    private static boolean show(ResourceLocation itemId, RecipeIngredientRole role) {
        IJeiRuntime runtime = AgentChat.jeiRuntime;
        if (runtime == null || itemId == null) {
            return false;
        }
        Item item = GameDataAccess.lookupItem(itemId);
        if (item == null || item == Items.AIR) {
            return false;
        }
        try {
            IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
            ItemStack stack = item.getDefaultInstance();
            IFocus<ItemStack> focus = focusFactory.createFocus(role, VanillaTypes.ITEM_STACK, stack);
            runtime.getRecipesGui().show(focus);
            return true;
        } catch (RuntimeException e) {
            AgentChat.LOGGER.warn("JEI could not open {} for {}", role, itemId);
            return false;
        }
    }
}

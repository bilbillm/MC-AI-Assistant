package com.lumoren.agentchat.tools;

import com.lumoren.agentchat.AgentChat;
import com.lumoren.agentchat.ai.GameDataAccess;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ALL direct JEI API access lives in this class and nowhere else.
 * <p>
 * JEI is an optional {@code compileOnly} dependency: when it is not
 * installed, its classes are missing at runtime and any class whose
 * verification touches JEI types throws {@link NoClassDefFoundError} the
 * moment it is loaded. Callers must therefore guard every call with
 * {@link AgentChat#hasJei()} so this class is only ever loaded when the JEI
 * runtime is actually present.
 */
public final class JeiLookup {

    private JeiLookup() {
    }

    private static IJeiRuntime runtime() {
        return (IJeiRuntime) AgentChat.jeiRuntime;
    }

    /** Forward lookup: recipes producing {@code itemId}. Null → try vanilla. */
    public static String lookupRecipes(ResourceLocation itemId) {
        IJeiRuntime runtime = runtime();
        if (runtime == null) {
            return null;
        }
        IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
        IRecipeManager recipeManager = runtime.getRecipeManager();

        ItemStack stack = makeItemStack(itemId);
        if (stack == null) return null;

        IFocus<ItemStack> focus = focusFactory.createFocus(
                RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack);

        var categories = recipeManager.createRecipeCategoryLookup()
                .limitFocus(Collections.singletonList(focus))
                .get()
                .toList();

        if (categories.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Recipes producing ").append(itemId).append(" ===\n");
        for (IRecipeCategory<?> category : categories) {
            sb.append("\n[").append(category.getTitle().getString()).append("]\n");
            var recipes = recipeManager.createRecipeLookup(category.getRecipeType())
                    .limitFocus(Collections.singletonList(focus))
                    .get()
                    .toList();
            for (Object recipe : recipes) {
                sb.append("  - ").append(RecipeTool.formatRecipeDetail(recipe)).append("\n");
            }
        }
        return sb.toString();
    }

    /** Reverse lookup: recipes using {@code itemId} as input. Null → try vanilla. */
    public static String lookupUsages(ResourceLocation itemId) {
        IJeiRuntime runtime = runtime();
        if (runtime == null) {
            return null;
        }
        IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
        IRecipeManager recipeManager = runtime.getRecipeManager();

        ItemStack stack = makeItemStack(itemId);
        if (stack == null) return null;

        IFocus<ItemStack> focus = focusFactory.createFocus(
                RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, stack);

        var categories = recipeManager.createRecipeCategoryLookup()
                .limitFocus(Collections.singletonList(focus))
                .get()
                .toList();

        if (categories.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Recipes using ").append(itemId).append(" as input ===\n");
        for (IRecipeCategory<?> category : categories) {
            sb.append("\n[").append(category.getTitle().getString()).append("]\n");
            var recipes = recipeManager.createRecipeLookup(category.getRecipeType())
                    .limitFocus(Collections.singletonList(focus))
                    .get()
                    .toList();
            for (Object recipe : recipes) {
                sb.append("  - ").append(RecipeTool.formatRecipeDetail(recipe)).append("\n");
            }
        }
        return sb.toString();
    }

    /** All item tags for {@code itemId}, comma separated. */
    public static String getItemTags(ResourceLocation itemId) {
        IJeiRuntime runtime = runtime();
        if (runtime == null) {
            return "";
        }
        IIngredientManager ingredientManager = runtime.getIngredientManager();
        IIngredientHelper<ItemStack> helper = ingredientManager.getIngredientHelper(VanillaTypes.ITEM_STACK);
        ItemStack stack = makeItemStack(itemId);
        if (stack == null) return "";

        Set<ResourceLocation> tags = helper.getTagStream(stack).collect(Collectors.toSet());
        return tags.stream()
                .map(ResourceLocation::toString)
                .collect(Collectors.joining(", "));
    }

    /**
     * Opens the JEI recipes GUI for the given item. Main thread only.
     *
     * @return {@code true} when the GUI was shown
     */
    public static boolean showRecipesGui(ResourceLocation itemId) {
        return showGui(itemId, RecipeIngredientRole.OUTPUT);
    }

    /**
     * Opens the JEI usages GUI for the given item. Main thread only.
     *
     * @return {@code true} when the GUI was shown
     */
    public static boolean showUsagesGui(ResourceLocation itemId) {
        return showGui(itemId, RecipeIngredientRole.INPUT);
    }

    private static boolean showGui(ResourceLocation itemId, RecipeIngredientRole role) {
        IJeiRuntime runtime = runtime();
        if (runtime == null || itemId == null) {
            return false;
        }
        ItemStack stack = makeItemStack(itemId);
        if (stack == null) {
            return false;
        }
        try {
            IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
            IFocus<ItemStack> focus = focusFactory.createFocus(role, VanillaTypes.ITEM_STACK, stack);
            runtime.getRecipesGui().show(focus);
            return true;
        } catch (RuntimeException e) {
            AgentChat.LOGGER.warn("JEI could not open {} for {}", role, itemId);
            return false;
        }
    }

    private static ItemStack makeItemStack(ResourceLocation itemId) {
        try {
            net.minecraft.world.item.Item item = GameDataAccess.lookupItem(itemId);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                return null;
            }
            return new ItemStack(item);
        } catch (Exception e) {
            return null;
        }
    }
}

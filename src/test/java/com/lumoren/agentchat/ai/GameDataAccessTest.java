package com.lumoren.agentchat.ai;

import com.lumoren.agentchat.model.GameContext;
import com.lumoren.agentchat.model.InventoryItem;
import com.lumoren.agentchat.model.RecipeResult;
import com.lumoren.agentchat.model.WorldState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link GameDataAccess} 的单元测试。
 * <p>
 * 使用 Mockito 模拟 Minecraft 客户端对象，验证各类游戏数据查询方法的正确性。
 * 所有涉及 {@link net.minecraft.core.registries.BuiltInRegistries} 的静态调用均通过
 * {@link MockedStatic} 进行桩件处理，确保测试可在无 Minecraft 运行时环境下执行。
 */
class GameDataAccessTest {

    // ========== 1. 背包快照测试 ==========

    @Test
    void getInventorySnapshot_returnsNonEmptyItems() {
        Minecraft mc = mock(Minecraft.class);
        net.minecraft.client.player.LocalPlayer player = mock(net.minecraft.client.player.LocalPlayer.class);
        Inventory inventory = mock(Inventory.class);

        mc.player = player;
        when(player.getInventory()).thenReturn(inventory);

        NonNullList<ItemStack> items = NonNullList.withSize(5, ItemStack.EMPTY);

        ItemStack sword = mock(ItemStack.class);
        when(sword.isEmpty()).thenReturn(false);
        when(sword.getHoverName()).thenReturn(Component.literal("Diamond Sword"));
        when(sword.getCount()).thenReturn(1);
        when(sword.getMaxDamage()).thenReturn(1561);
        when(sword.getDamageValue()).thenReturn(0);
        items.set(0, sword);

        ItemStack bread = mock(ItemStack.class);
        when(bread.isEmpty()).thenReturn(false);
        when(bread.getHoverName()).thenReturn(Component.literal("Bread"));
        when(bread.getCount()).thenReturn(5);
        when(bread.getMaxDamage()).thenReturn(0);
        when(bread.getDamageValue()).thenReturn(0);
        items.set(1, bread);

        ItemStack torch = mock(ItemStack.class);
        when(torch.isEmpty()).thenReturn(false);
        when(torch.getHoverName()).thenReturn(Component.literal("Torch"));
        when(torch.getCount()).thenReturn(64);
        when(torch.getMaxDamage()).thenReturn(0);
        when(torch.getDamageValue()).thenReturn(0);
        items.set(3, torch);

        try {
            Field itemsField = Inventory.class.getField("items");
            itemsField.setAccessible(true);
            itemsField.set(inventory, items);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set inventory items", e);
        }

        try (MockedStatic<GameDataAccess> mocked = mockStatic(GameDataAccess.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> GameDataAccess.getItemKey(any(Item.class)))
                  .thenReturn(ResourceLocation.parse("minecraft:test_item"));

            List<InventoryItem> result = GameDataAccess.getInventorySnapshot(mc);

            assertEquals(3, result.size());
            assertEquals(0, result.get(0).slot());
            assertEquals(1, result.get(1).slot());
            assertEquals(3, result.get(2).slot());
        }
    }

    // ========== 2. 玩家上下文测试 ==========

    @Test
    void getPlayerContext_returnsCorrectPositionHealthHunger() {
        Minecraft mc = mock(Minecraft.class);
        net.minecraft.client.player.LocalPlayer player = mock(net.minecraft.client.player.LocalPlayer.class);
        ClientLevel level = mock(ClientLevel.class);

        mc.player = player;
        mc.level = level;

        when(player.position()).thenReturn(new Vec3(100.5, 64.0, -200.3));
        when(player.getHealth()).thenReturn(18.5f);
        net.minecraft.world.food.FoodData foodData = mock(net.minecraft.world.food.FoodData.class);
        when(foodData.getFoodLevel()).thenReturn(16);
        when(player.getFoodData()).thenReturn(foodData);
        when(player.getName()).thenReturn(Component.literal("TestPlayer"));

        ResourceKey dimensionKey = mock(ResourceKey.class);
        when(dimensionKey.location()).thenReturn(ResourceLocation.parse("minecraft:overworld"));
        when(level.dimension()).thenReturn(dimensionKey);

        try (MockedStatic<GameDataAccess> mocked = mockStatic(GameDataAccess.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> GameDataAccess.getInventorySnapshot(any()))
                  .thenReturn(List.of());

            GameContext ctx = GameDataAccess.getPlayerContext(mc);

            assertEquals("TestPlayer", ctx.playerName());
            assertEquals(100.5, ctx.x());
            assertEquals(64.0, ctx.y());
            assertEquals(-200.3, ctx.z());
            assertEquals("minecraft:overworld", ctx.dimension());
            assertEquals(18.5f, ctx.health());
            assertEquals(16, ctx.hunger());
            assertTrue(ctx.inventory().isEmpty());
        }
    }

    // ========== 3. 物品信息测试 ==========

    @Test
    void getItemInfo_returnsDataForDiamondSword() {
        Minecraft mc = mock(Minecraft.class);

        Item mockItem = mock(Item.class);
        ItemStack mockStack = mock(ItemStack.class);
        when(mockItem.getDefaultInstance()).thenReturn(mockStack);
        when(mockStack.getMaxStackSize()).thenReturn(1);
        when(mockStack.getRarity()).thenReturn(Rarity.EPIC);
        when(mockStack.get(any(DataComponentType.class))).thenReturn(null);

        try (MockedStatic<GameDataAccess> mocked = mockStatic(GameDataAccess.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> GameDataAccess.lookupItem(ResourceLocation.parse("minecraft:diamond_sword")))
                  .thenReturn(mockItem);

            String info = GameDataAccess.getItemInfo(mc, ResourceLocation.parse("minecraft:diamond_sword"));

            assertTrue(info.contains("minecraft:diamond_sword"));
            assertTrue(info.contains("Max Stack: 1"));
            assertTrue(info.contains("Rarity: EPIC"));
            assertFalse(info.contains("Food:"));
        }
    }

    // ========== 4. 世界状态测试 ==========

    @Test
    void getWorldState_returnsCorrectValues() {
        Minecraft mc = mock(Minecraft.class);
        ClientLevel level = mock(ClientLevel.class);
        net.minecraft.client.player.LocalPlayer player = mock(net.minecraft.client.player.LocalPlayer.class);

        mc.level = level;
        mc.player = player;

        when(level.getDayTime()).thenReturn(6000L);
        when(level.isRaining()).thenReturn(true);
        when(level.isThundering()).thenReturn(false);

        var levelData = mock(net.minecraft.client.multiplayer.ClientLevel.ClientLevelData.class);
        when(level.getLevelData()).thenReturn(levelData);
        when(levelData.getDifficulty()).thenReturn(Difficulty.NORMAL);

        when(player.blockPosition()).thenReturn(new net.minecraft.core.BlockPos(10, 64, 20));

        var biomeHolder = mock(net.minecraft.core.Holder.class);
        when(biomeHolder.unwrapKey()).thenReturn(Optional.of(
                ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, ResourceLocation.parse("minecraft:forest"))
        ));
        when(level.getBiome(any(net.minecraft.core.BlockPos.class))).thenReturn(biomeHolder);

        WorldState state = GameDataAccess.getWorldState(mc);

        assertEquals(6000L, state.dayTime());
        assertTrue(state.isRaining());
        assertFalse(state.isThundering());
        assertEquals("NORMAL", state.difficulty());
        assertEquals("minecraft:forest", state.biome());
    }

    // ========== 5. 配方查询测试 ==========

    @SuppressWarnings("unchecked")
    @Test
    void getRecipesForOutput_findsCraftingRecipes() {
        Minecraft mc = mock(Minecraft.class);
        ClientLevel level = mock(ClientLevel.class);
        RecipeManager recipeManager = mock(RecipeManager.class);

        mc.level = level;
        when(level.getRecipeManager()).thenReturn(recipeManager);
        when(level.registryAccess()).thenReturn(mock(RegistryAccess.class));

        Item outputItem = mock(Item.class);
        ItemStack resultStack = mock(ItemStack.class);
        when(resultStack.isEmpty()).thenReturn(false);
        when(resultStack.getItem()).thenReturn(outputItem);
        when(resultStack.getCount()).thenReturn(1);

        Recipe mockRecipe = mock(Recipe.class);
        when(mockRecipe.assemble(any(), any())).thenReturn(resultStack);

        Ingredient ing1 = mock(Ingredient.class);
        when(ing1.isEmpty()).thenReturn(false);
        ItemStack ingStack = mock(ItemStack.class);
        when(ingStack.getItem()).thenReturn(mock(Item.class));
        when(ingStack.getCount()).thenReturn(2);
        when(ing1.getItems()).thenReturn(new ItemStack[] { ingStack });

        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(ing1);
        when(mockRecipe.getIngredients()).thenReturn(ingredients);

        RecipeHolder<?> holder = mock(RecipeHolder.class);
        when(holder.id()).thenReturn(ResourceLocation.parse("minecraft:diamond_sword"));
        when(holder.value()).thenReturn(mockRecipe);

        RecipeType<?> mockRecipeType = mock(RecipeType.class);
        when(recipeManager.getAllRecipesFor(any())).thenReturn(List.of((RecipeHolder) holder));

        try (MockedStatic<GameDataAccess> mocked = mockStatic(GameDataAccess.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> GameDataAccess.lookupItem(ResourceLocation.parse("minecraft:diamond_sword")))
                  .thenReturn(outputItem);
            mocked.when(() -> GameDataAccess.getItemKey(any(Item.class)))
                  .thenReturn(ResourceLocation.parse("minecraft:diamond"));
            mocked.when(GameDataAccess::getCraftingRecipeType).thenReturn(mockRecipeType);

            List<RecipeResult> results = GameDataAccess.getRecipesForOutput(
                    mc, ResourceLocation.parse("minecraft:diamond_sword"));

            assertEquals(1, results.size());
            RecipeResult recipe = results.get(0);
            assertEquals("minecraft:diamond_sword", recipe.recipeId());
            assertEquals("crafting", recipe.type());
            assertEquals("minecraft:diamond", recipe.output().itemId());
            assertEquals(1, recipe.output().count());
            assertEquals(1, recipe.input().size());
            assertEquals("minecraft:diamond", recipe.input().get(0).itemId());
            assertEquals(2, recipe.input().get(0).count());
        }
    }
}

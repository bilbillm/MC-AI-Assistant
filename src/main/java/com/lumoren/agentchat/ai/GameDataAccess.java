package com.lumoren.agentchat.ai;

import com.lumoren.agentchat.model.GameContext;
import com.lumoren.agentchat.model.InventoryItem;
import com.lumoren.agentchat.model.RecipeResult;
import com.lumoren.agentchat.model.WorldState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏数据访问层（客户端侧）。
 * <p>
 * 提供对 Minecraft 客户端数据的统一查询接口，包括玩家背包、位置、世界状态、
 * 物品图鉴及合成配方等信息。所有方法均为纯读取操作，不会修改游戏状态。
 */
public class GameDataAccess {

    /**
     * 获取玩家背包快照。
     * <p>
     * 遍历玩家主背包（{@code items}），过滤空槽位，将每个 {@link ItemStack}
     * 转换为 {@link InventoryItem} 记录。
     *
     * @param mc Minecraft 客户端实例
     * @return 非空槽位的物品列表
     */
    public static List<InventoryItem> getInventorySnapshot(Minecraft mc) {
        List<InventoryItem> result = new ArrayList<>();
        if (mc.player == null) {
            return result;
        }

        Inventory inventory = mc.player.getInventory();
        NonNullList<ItemStack> items = inventory.items;
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                result.add(toInventoryItem(i, stack));
            }
        }

        return result;
    }

    /**
     * 获取玩家当前游戏上下文。
     * <p>
     * 包含玩家名称、坐标、维度、生命值、饥饿度及当前背包快照。
     *
     * @param mc Minecraft 客户端实例
     * @return 玩家上下文记录
     */
    public static GameContext getPlayerContext(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return new GameContext("unknown", 0.0, 0.0, 0.0, "unknown", List.of(), 0.0f, 0);
        }

        Vec3 pos = mc.player.position();
        String dimension = mc.level.dimension().location().toString();
        float health = mc.player.getHealth();
        int hunger = mc.player.getFoodData().getFoodLevel();
        String playerName = mc.player.getName().getString();
        List<InventoryItem> inventory = getInventorySnapshot(mc);

        return new GameContext(playerName, pos.x, pos.y, pos.z, dimension, inventory, health, hunger);
    }

    /**
     * 获取物品图鉴信息。
     * <p>
     * 通过 {@link BuiltInRegistries} 查询指定物品，返回包含最大堆叠数、
     * 稀有度及食物属性（如有）的描述文本。
     *
     * @param mc     Minecraft 客户端实例
     * @param itemId 物品 {@link ResourceLocation}
     * @return 物品信息描述字符串
     */
    public static String getItemInfo(Minecraft mc, ResourceLocation itemId) {
        Item item = lookupItem(itemId);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return "Unknown item: " + itemId;
        }

        ItemStack defaultStack = item.getDefaultInstance();
        StringBuilder sb = new StringBuilder();
        sb.append("Item: ").append(itemId).append("\n");
        sb.append("Max Stack: ").append(defaultStack.getMaxStackSize()).append("\n");
        sb.append("Rarity: ").append(defaultStack.getRarity()).append("\n");

        var food = defaultStack.get(net.minecraft.core.component.DataComponents.FOOD);
        if (food != null) {
            sb.append("Food: nutrition=").append(food.nutrition())
              .append(", saturation=").append(food.saturation()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 获取当前世界状态。
     * <p>
     * 包含游戏时间、天气、难度及玩家所在生物群系。
     *
     * @param mc Minecraft 客户端实例
     * @return 世界状态记录
     */
    public static WorldState getWorldState(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return new WorldState(0L, false, false, "UNKNOWN", "unknown");
        }

        long dayTime = mc.level.getDayTime();
        boolean isRaining = mc.level.isRaining();
        boolean isThundering = mc.level.isThundering();
        String difficulty = mc.level.getLevelData().getDifficulty().name();

        var biomeHolder = mc.level.getBiome(mc.player.blockPosition());
        String biome = biomeHolder.unwrapKey()
                .map(key -> key.location().toString())
                .orElse("unknown");

        return new WorldState(dayTime, isRaining, isThundering, difficulty, biome);
    }

    /**
     * 查询可产出指定物品的所有合成配方。
     * <p>
     * 仅查询工作台合成类配方（{@link RecipeType#CRAFTING}）。
     *
     * @param mc     Minecraft 客户端实例
     * @param itemId 目标产出物品的 {@link ResourceLocation}
     * @return 匹配的配方列表
     */
    @SuppressWarnings("unchecked")
    public static List<RecipeResult> getRecipesForOutput(Minecraft mc, ResourceLocation itemId) {
        if (mc.level == null) {
            return List.of();
        }

        Item outputItem = lookupItem(itemId);
        RecipeManager manager = mc.level.getRecipeManager();
        RegistryAccess registryAccess = mc.level.registryAccess();
        List<RecipeResult> results = new ArrayList<>();

        try {
            // Cast required because getCraftingRecipeType() returns RecipeType<?>
            RecipeType<Recipe<CraftingInput>> craftingType =
                    (RecipeType<Recipe<CraftingInput>>) (RecipeType<?>) getCraftingRecipeType();
            var craftingRecipes = manager.getAllRecipesFor(craftingType);
            for (RecipeHolder<? extends Recipe<CraftingInput>> holder : craftingRecipes) {
                Recipe<CraftingInput> recipe = holder.value();
                CraftingInput dummy = CraftingInput.of(3, 3, NonNullList.withSize(9, ItemStack.EMPTY));
                ItemStack result = recipe.assemble(dummy, registryAccess);

                if (!result.isEmpty() && result.getItem() == outputItem) {
                    List<RecipeResult.Ingredient> inputs = new ArrayList<>();
                    for (net.minecraft.world.item.crafting.Ingredient ingredient : recipe.getIngredients()) {
                        if (!ingredient.isEmpty()) {
                            ItemStack[] stacks = ingredient.getItems();
                            if (stacks.length > 0) {
                                String ingId = getItemKey(stacks[0].getItem()).toString();
                                int count = stacks[0].getCount();
                                inputs.add(new RecipeResult.Ingredient(ingId, count));
                            }
                        }
                    }

                    String recipeId = holder.id().toString();
                    String type = "crafting";
                    results.add(new RecipeResult(
                            recipeId,
                            inputs,
                            new RecipeResult.ItemStack(getItemKey(result.getItem()).toString(), result.getCount()),
                            type
                    ));
                }
            }
        } catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
            // Recipe ingredient list may be empty or registry entry removed — skip gracefully
            System.err.println("[AgentChat] Recipe lookup skipped malformed recipe: " + e.getMessage());
        }

        return results;
    }

    /**
     * 查询所有使用指定物品作为原料的配方（反向配方查询）。
     * <p>
     * 遍历所有配方类型，检查每个配方中是否包含目标物品作为原料。
     * 基于原版 {@link RecipeManager}，不含 JEI 增强数据。
     *
     * @param mc     Minecraft 客户端实例
     * @param itemId 作为原料的物品 {@link ResourceLocation}
     * @return 使用该物品作为原料的配方列表
     */
    @SuppressWarnings("unchecked")
    public static List<RecipeResult> getUsagesForInput(Minecraft mc, ResourceLocation itemId) {
        if (mc.level == null) {
            return List.of();
        }

        RecipeManager manager = mc.level.getRecipeManager();
        RegistryAccess registryAccess = mc.level.registryAccess();
        List<RecipeResult> results = new ArrayList<>();

        try {
            // Iterate all registered recipe types via BuiltInRegistries
            // (RecipeManager does not expose getRecipeTypes() in vanilla 1.21.1)
            for (var entry : BuiltInRegistries.RECIPE_TYPE.entrySet()) {
                RecipeType<?> type = entry.getValue();
                var recipesRaw = manager.getAllRecipesFor((RecipeType) type);
                for (Object obj : recipesRaw) {
                    RecipeHolder<?> holder = (RecipeHolder<?>) obj;
                    Recipe<?> recipe = holder.value();

                    // Check if any ingredient matches the target item
                    boolean usesItem = false;
                    for (net.minecraft.world.item.crafting.Ingredient ing : recipe.getIngredients()) {
                        for (ItemStack stack : ing.getItems()) {
                            ResourceLocation key = getItemKey(stack.getItem());
                            if (itemId.equals(key)) {
                                usesItem = true;
                                break;
                            }
                        }
                        if (usesItem) break;
                    }

                    if (!usesItem) continue;

                    // Build structured input list
                    List<RecipeResult.Ingredient> inputs = new ArrayList<>();
                    for (net.minecraft.world.item.crafting.Ingredient ing : recipe.getIngredients()) {
                        if (!ing.isEmpty()) {
                            ItemStack[] stacks = ing.getItems();
                            if (stacks.length > 0) {
                                String ingId = getItemKey(stacks[0].getItem()).toString();
                                int count = stacks[0].getCount();
                                inputs.add(new RecipeResult.Ingredient(ingId, count));
                            }
                        }
                    }

                    // Extract output
                    ItemStack result = recipe.getResultItem(registryAccess);
                    String outputId = result.isEmpty()
                            ? "unknown"
                            : getItemKey(result.getItem()).toString();
                    int outputCount = result.getCount();

                    String recipeId = holder.id().toString();
                    String typeName = type.toString();

                    results.add(new RecipeResult(
                            recipeId,
                            inputs,
                            new RecipeResult.ItemStack(outputId, outputCount),
                            typeName
                    ));
                }
            }
        } catch (Exception e) {
            System.err.println("[AgentChat] Usages lookup error: " + e.getMessage());
        }

        return results;
    }

    /**
     * 将 {@link ItemStack} 转换为 {@link InventoryItem} 记录。
     *
     * @param slot  背包槽位索引
     * @param stack 物品堆
     * @return 背包物品记录
     */
    public static InventoryItem toInventoryItem(int slot, ItemStack stack) {
        ResourceLocation itemId = getItemKey(stack.getItem());
        String displayName = stack.getHoverName().getString();
        int count = stack.getCount();
        int maxDamage = stack.getMaxDamage();
        int currentDamage = stack.getDamageValue();

        return new InventoryItem(slot, itemId.toString(), displayName, count, maxDamage, currentDamage);
    }

    /**
     * 解析物品名称字符串为 {@link ResourceLocation}。
     * <p>
     * 若名称不含命名空间，则默认使用 {@code minecraft}。
     *
     * @param itemName 物品名称（如 {@code "minecraft:diamond_sword"} 或 {@code "diamond_sword"}）
     * @return 对应的 {@link ResourceLocation}
     */
    public static ResourceLocation resolveItemId(String itemName) {
        if (itemName.contains(":")) {
            return ResourceLocation.parse(itemName);
        }
        return ResourceLocation.withDefaultNamespace(itemName);
    }

    /**
     * 通过 {@link BuiltInRegistries} 查询物品对应的注册键。
     *
     * @param item 物品实例
     * @return 注册键 {@link ResourceLocation}
     */
    static ResourceLocation getItemKey(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    /**
     * 通过 {@link BuiltInRegistries} 按 ID 查找物品。
     *
     * @param itemId 物品注册键
     * @return 物品实例，若不存在则可能返回 {@link net.minecraft.world.item.Items#AIR}
     */
    public static Item lookupItem(ResourceLocation itemId) {
        return BuiltInRegistries.ITEM.get(itemId);
    }

    static RecipeType<?> getCraftingRecipeType() {
        return RecipeType.CRAFTING;
    }
}

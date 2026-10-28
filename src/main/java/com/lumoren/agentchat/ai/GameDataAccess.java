package com.lumoren.agentchat.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lumoren.agentchat.model.GameContext;
import com.lumoren.agentchat.model.InventoryItem;
import com.lumoren.agentchat.model.RecipeResult;
import com.lumoren.agentchat.model.WorldState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

        // Main inventory (36 slots: 0-8 hotbar, 9-35 main)
        NonNullList<ItemStack> items = inventory.items;
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                result.add(toInventoryItem(i, stack));
            }
        }

        // Armor slots (4 slots: 36=boots, 37=leggings, 38=chestplate, 39=helmet)
        NonNullList<ItemStack> armor = inventory.armor;
        for (int i = 0; i < armor.size(); i++) {
            ItemStack stack = armor.get(i);
            if (!stack.isEmpty()) {
                result.add(toInventoryItem(36 + i, stack));
            }
        }

        // Offhand (slot 40)
        NonNullList<ItemStack> offhand = inventory.offhand;
        for (int i = 0; i < offhand.size(); i++) {
            ItemStack stack = offhand.get(i);
            if (!stack.isEmpty()) {
                result.add(toInventoryItem(40 + i, stack));
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
        if (mc == null || mc.level == null) {
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
        if (mc == null || mc.level == null) {
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

    /**
     * 扫描玩家周围指定半径内的实体。
     * <p>
     * 按类别过滤（monster / animal / villager / item / player），按距离排序。
     * 支持两种输出模式：
     * <ul>
     *   <li><b>summary</b>（默认）：按类别聚合统计 + 只返回前 8 个最重要的实体
     *   <li><b>full</b>：返回所有匹配实体的完整列表
     * </ul>
     *
     * @param mc         Minecraft 客户端实例
     * @param radius     扫描半径（格），最大 32
     * @param categories 实体类别过滤列表，{"all"} 表示不过滤
     * @param detail     输出精度："summary"（聚合）或 "full"（完整列表）
     * @return JSON 格式的实体列表字符串
     */
    public static String getNearbyEntities(Minecraft mc, int radius, List<String> categories,
                                            String detail) {
        JsonObject root = new JsonObject();

        if (mc.player == null || mc.level == null) {
            root.addProperty("error", "Not in a world");
            return root.toString();
        }

        int clampedRadius = Math.max(1, Math.min(radius, 32));
        boolean fullDetail = "full".equalsIgnoreCase(detail);

        Vec3 playerPos = mc.player.position();
        AABB boundingBox = mc.player.getBoundingBox().inflate(clampedRadius);

        JsonObject playerPosObj = new JsonObject();
        playerPosObj.addProperty("x", Math.round(playerPos.x * 10.0) / 10.0);
        playerPosObj.addProperty("y", Math.round(playerPos.y * 10.0) / 10.0);
        playerPosObj.addProperty("z", Math.round(playerPos.z * 10.0) / 10.0);
        root.add("player_pos", playerPosObj);
        root.addProperty("radius", clampedRadius);
        root.addProperty("detail", fullDetail ? "full" : "summary");

        boolean includeAll = categories == null || categories.isEmpty()
                || (categories.size() == 1 && "all".equalsIgnoreCase(categories.get(0)));
        Set<String> catSet = includeAll ? Set.of() : Set.copyOf(
                categories.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList());

        List<Entity> nearby = mc.level.getEntities(mc.player, boundingBox, entity -> {
            if (includeAll) return !entity.isSpectator();
            for (String cat : catSet) {
                switch (cat) {
                    case "monster":
                        if (entity instanceof Monster) return true;
                        break;
                    case "animal":
                        if (entity instanceof Animal) return true;
                        break;
                    case "villager":
                        if (entity instanceof AbstractVillager) return true;
                        break;
                    case "item":
                        if (entity instanceof ItemEntity) return true;
                        break;
                    case "player":
                        if (entity instanceof Player && entity != mc.player) return true;
                        break;
                }
            }
            return false;
        });

        // Sort by distance
        nearby.sort(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)));

        if (fullDetail) {
            // === Full mode: return all entities ===
            JsonArray entitiesArr = new JsonArray();
            for (Entity entity : nearby) {
                entitiesArr.add(buildEntityEntry(entity, mc.player, playerPos));
            }
            root.addProperty("total", entitiesArr.size());
            root.add("entities", entitiesArr);
        } else {
            // === Summary mode: aggregate by category + highlights ===
            buildEntitySummary(root, nearby, mc.player, playerPos);
        }

        return root.toString();
    }

    /** Build a single entity JSON entry (used by both summary and full modes). */
    private static JsonObject buildEntityEntry(Entity entity, Player localPlayer, Vec3 playerPos) {
        JsonObject obj = new JsonObject();
        ResourceLocation typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        obj.addProperty("type", typeKey != null ? typeKey.toString() : "unknown");
        obj.addProperty("name", entity.getName().getString());
        obj.addProperty("category", classifyEntity(entity, localPlayer));

        double dx = entity.getX() - playerPos.x;
        double dy = entity.getY() - playerPos.y;
        double dz = entity.getZ() - playerPos.z;
        double totalDist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        obj.addProperty("distance", Math.round(totalDist * 10.0) / 10.0);
        obj.addProperty("direction", computeDirection(dx, dy, dz));

        JsonObject posObj = new JsonObject();
        posObj.addProperty("x", Math.round(entity.getX() * 10.0) / 10.0);
        posObj.addProperty("y", Math.round(entity.getY() * 10.0) / 10.0);
        posObj.addProperty("z", Math.round(entity.getZ() * 10.0) / 10.0);
        obj.add("pos", posObj);

        if (entity instanceof LivingEntity living) {
            obj.addProperty("health", Math.round(living.getHealth() * 10.0) / 10.0);
            obj.addProperty("max_health", Math.round(living.getMaxHealth() * 10.0) / 10.0);
        }
        if (entity instanceof ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getItem();
            if (!stack.isEmpty()) {
                obj.addProperty("item", getItemKey(stack.getItem()).toString());
                obj.addProperty("item_count", stack.getCount());
                obj.addProperty("item_name", stack.getHoverName().getString());
            }
        }

        return obj;
    }

    /**
     * Build aggregated summary output for entities.
     * <p>
     * Produces two sections:
     * <ol>
     *   <li><b>summary</b> — per-category counts + per-type breakdown (e.g. "monster: 3, animal: 1")
     *   <li><b>highlights</b> — top 8 most important entities sorted by danger priority then distance
     * </ol>
     * Highlight priority: monster (dangerous) > player > villager > animal > item
     */
    private static void buildEntitySummary(JsonObject root, List<Entity> nearby,
                                            Player localPlayer, Vec3 playerPos) {
        // Category priority for highlight ordering (lower = more important)
        Map<String, Integer> priorityMap = Map.of(
                "monster", 0, "player", 1, "villager", 2, "animal", 3, "item", 4
        );

        // === Aggregation: count by category and type ===
        Map<String, Map<String, Integer>> categoryTypeCounts = new LinkedHashMap<>();
        Map<String, Integer> categoryTotals = new LinkedHashMap<>();
        List<JsonObject> entityEntries = new ArrayList<>();

        for (Entity entity : nearby) {
            String cat = classifyEntity(entity, localPlayer);
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();

            categoryTotals.merge(cat, 1, Integer::sum);
            categoryTypeCounts
                    .computeIfAbsent(cat, k -> new LinkedHashMap<>())
                    .merge(type, 1, Integer::sum);

            entityEntries.add(buildEntityEntry(entity, localPlayer, playerPos));
        }

        // Build summary JSON
        JsonObject summary = new JsonObject();
        for (String cat : List.of("monster", "player", "villager", "animal", "item")) {
            JsonObject catObj = new JsonObject();
            catObj.addProperty("total", categoryTotals.getOrDefault(cat, 0));
            JsonObject typesObj = new JsonObject();
            Map<String, Integer> typeCounts = categoryTypeCounts.get(cat);
            if (typeCounts != null) {
                typeCounts.forEach(typesObj::addProperty);
            }
            catObj.add("types", typesObj);
            summary.add(cat, catObj);
        }
        root.addProperty("total", entityEntries.size());
        root.add("summary", summary);

        // === Highlights: top 8 by priority then distance ===
        entityEntries.sort((a, b) -> {
            int pa = priorityMap.getOrDefault(a.get("category").getAsString(), 99);
            int pb = priorityMap.getOrDefault(b.get("category").getAsString(), 99);
            if (pa != pb) return Integer.compare(pa, pb);
            return Double.compare(a.get("distance").getAsDouble(), b.get("distance").getAsDouble());
        });

        JsonArray highlights = new JsonArray();
        int limit = Math.min(entityEntries.size(), 8);
        for (int i = 0; i < limit; i++) {
            highlights.add(entityEntries.get(i));
        }
        root.add("highlights", highlights);
    }

    /**
     * 扫描玩家周围指定半径内的关键方块。
     * <p>
     * 按类别过滤（ores / containers / spawners / portals / danger / crafting / beehive），
     * 使用球形遍历以保证距离准确。
     * 支持两种输出模式：
     * <ul>
     *   <li><b>summary</b>（默认）：按类别聚合统计 + 只返回前 8 个最重要的方块
     *   <li><b>full</b>：返回所有匹配方块的完整列表
     * </ul>
     *
     * @param mc         Minecraft 客户端实例
     * @param radius     扫描半径（格），最大 32
     * @param categories 方块类别过滤列表，{"all"} 表示不过滤
     * @param detail     输出精度："summary"（聚合）或 "full"（完整列表）
     * @return JSON 格式的方块列表字符串
     */
    public static String getNearbyBlocks(Minecraft mc, int radius, List<String> categories,
                                          String detail) {
        JsonObject root = new JsonObject();

        if (mc.player == null || mc.level == null) {
            root.addProperty("error", "Not in a world");
            return root.toString();
        }

        int clampedRadius = Math.max(1, Math.min(radius, 32));
        boolean fullDetail = "full".equalsIgnoreCase(detail);
        BlockPos playerPos = mc.player.blockPosition();

        JsonObject playerPosObj = new JsonObject();
        playerPosObj.addProperty("x", playerPos.getX());
        playerPosObj.addProperty("y", playerPos.getY());
        playerPosObj.addProperty("z", playerPos.getZ());
        root.add("player_pos", playerPosObj);
        root.addProperty("radius", clampedRadius);
        root.addProperty("detail", fullDetail ? "full" : "summary");

        boolean includeAll = categories == null || categories.isEmpty()
                || (categories.size() == 1 && "all".equalsIgnoreCase(categories.get(0)));
        Set<String> catSet = includeAll ? Set.of() : Set.copyOf(
                categories.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList());

        List<JsonObject> nearbyBlocks = new ArrayList<>();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int r2 = clampedRadius * clampedRadius;

        // Spherical iteration: horizontal disc, full vertical column
        for (int dx = -clampedRadius; dx <= clampedRadius; dx++) {
            for (int dz = -clampedRadius; dz <= clampedRadius; dz++) {
                if (dx * dx + dz * dz > r2) continue;
                for (int dy = -clampedRadius; dy <= clampedRadius; dy++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    mutable.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);
                    BlockState state = mc.level.getBlockState(mutable);
                    if (state.isAir()) continue;

                    if (!includeAll && !matchesBlockCategory(state, catSet)) continue;

                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    JsonObject obj = buildBlockEntry(state, mutable, playerPos, clampedRadius);
                    obj.addProperty("distance", Math.round(dist * 10.0) / 10.0);
                    obj.addProperty("horizontal_distance", Math.round(Math.sqrt(dx * dx + dz * dz) * 10.0) / 10.0);
                    nearbyBlocks.add(obj);
                }
            }
        }

        // Sort by distance
        nearbyBlocks.sort(Comparator.comparingDouble(o -> o.get("distance").getAsDouble()));

        if (fullDetail) {
            // === Full mode: return all blocks ===
            JsonArray blocksArr = new JsonArray();
            for (JsonObject block : nearbyBlocks) {
                blocksArr.add(block);
            }
            root.addProperty("total", blocksArr.size());
            root.add("blocks", blocksArr);
        } else {
            // === Summary mode: aggregate by category + highlights ===
            buildBlockSummary(root, nearbyBlocks);
        }

        return root.toString();
    }

    /**
     * Build aggregated summary output for blocks.
     * <p>
     * Produces two sections:
     * <ol>
     *   <li><b>summary</b> — per-category counts + per-type breakdown
     *   <li><b>highlights</b> — top 8 most important blocks sorted by category priority
     *        and distance (spawners > portals > danger > rare ores > containers > crafting > beehive)
     * </ol>
     */
    private static void buildBlockSummary(JsonObject root, List<JsonObject> nearbyBlocks) {
        // Category priority: spawner(0) > portal(1) > danger(2) > ores(3) >
        //                    containers(4) > crafting(5) > beehive(6)
        Map<String, Integer> catPriority = new LinkedHashMap<>();
        catPriority.put("spawners", 0);
        catPriority.put("portals", 1);
        catPriority.put("danger", 2);
        catPriority.put("ores", 3);
        catPriority.put("containers", 4);
        catPriority.put("crafting", 5);
        catPriority.put("beehive", 6);

        // Ore rarity score for sub-sorting within "ores" category
        Map<String, Integer> oreRarity = new HashMap<>();
        oreRarity.put("minecraft:diamond_ore", 10);
        oreRarity.put("minecraft:deepslate_diamond_ore", 10);
        oreRarity.put("minecraft:emerald_ore", 9);
        oreRarity.put("minecraft:deepslate_emerald_ore", 9);
        oreRarity.put("minecraft:ancient_debris", 8);
        oreRarity.put("minecraft:gold_ore", 7);
        oreRarity.put("minecraft:deepslate_gold_ore", 7);
        oreRarity.put("minecraft:nether_gold_ore", 7);
        oreRarity.put("minecraft:lapis_ore", 6);
        oreRarity.put("minecraft:deepslate_lapis_ore", 6);
        oreRarity.put("minecraft:redstone_ore", 5);
        oreRarity.put("minecraft:deepslate_redstone_ore", 5);
        oreRarity.put("minecraft:iron_ore", 4);
        oreRarity.put("minecraft:deepslate_iron_ore", 4);
        oreRarity.put("minecraft:copper_ore", 3);
        oreRarity.put("minecraft:deepslate_copper_ore", 3);
        oreRarity.put("minecraft:coal_ore", 2);
        oreRarity.put("minecraft:deepslate_coal_ore", 2);
        oreRarity.put("minecraft:nether_quartz_ore", 1);

        // === Aggregation: count by category and type ===
        Map<String, Map<String, Integer>> categoryTypeCounts = new LinkedHashMap<>();
        Map<String, Integer> categoryTotals = new LinkedHashMap<>();

        for (JsonObject block : nearbyBlocks) {
            String blockId = block.get("block").getAsString();
            // Get primary category (first in the categories array)
            String primaryCat = "other";
            if (block.has("categories") && block.getAsJsonArray("categories").size() > 0) {
                primaryCat = block.getAsJsonArray("categories").get(0).getAsString();
            }

            categoryTotals.merge(primaryCat, 1, Integer::sum);
            categoryTypeCounts
                    .computeIfAbsent(primaryCat, k -> new LinkedHashMap<>())
                    .merge(blockId, 1, Integer::sum);
        }

        // Build summary JSON
        JsonObject summary = new JsonObject();
        for (String cat : List.of("spawners", "portals", "danger", "ores",
                                   "containers", "crafting", "beehive")) {
            JsonObject catObj = new JsonObject();
            catObj.addProperty("total", categoryTotals.getOrDefault(cat, 0));
            JsonObject typesObj = new JsonObject();
            Map<String, Integer> typeCounts = categoryTypeCounts.get(cat);
            if (typeCounts != null) {
                typeCounts.forEach(typesObj::addProperty);
            }
            catObj.add("types", typesObj);
            summary.add(cat, catObj);
        }
        root.addProperty("total", nearbyBlocks.size());
        root.add("summary", summary);

        // === Highlights: top 8 by category priority + ore rarity + distance ===
        nearbyBlocks.sort((a, b) -> {
            String catA = getPrimaryCategory(a);
            String catB = getPrimaryCategory(b);
            int pa = catPriority.getOrDefault(catA, 99);
            int pb = catPriority.getOrDefault(catB, 99);
            if (pa != pb) return Integer.compare(pa, pb);

            // Within ores: sort by rarity first, then distance
            if ("ores".equals(catA) && "ores".equals(catB)) {
                String idA = a.get("block").getAsString();
                String idB = b.get("block").getAsString();
                int ra = oreRarity.getOrDefault(idA, 0);
                int rb = oreRarity.getOrDefault(idB, 0);
                if (ra != rb) return Integer.compare(rb, ra); // higher rarity first
            }

            // Within same category/rarity: sort by distance
            return Double.compare(a.get("distance").getAsDouble(), b.get("distance").getAsDouble());
        });

        JsonArray highlights = new JsonArray();
        int limit = Math.min(nearbyBlocks.size(), 8);
        for (int i = 0; i < limit; i++) {
            highlights.add(nearbyBlocks.get(i));
        }
        root.add("highlights", highlights);
    }

    /** Extract the primary (first) category from a block entry. */
    private static String getPrimaryCategory(JsonObject block) {
        if (block.has("categories") && block.getAsJsonArray("categories").size() > 0) {
            return block.getAsJsonArray("categories").get(0).getAsString();
        }
        return "other";
    }

    /** Classify an entity into a human-readable category string. */
    private static String classifyEntity(Entity entity, Player localPlayer) {
        if (entity instanceof Monster) return "monster";
        if (entity instanceof AbstractVillager) return "villager";
        if (entity instanceof Animal) return "animal";
        if (entity instanceof ItemEntity) return "item";
        if (entity instanceof Player && entity != localPlayer) return "player";
        return "other";
    }

    /**
     * Compute a human-readable direction string from relative coordinates.
     * Examples: "N", "NE", "SW-up", "W-down"
     */
    private static String computeDirection(double dx, double dy, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, dz)); // Minecraft: x=east, z=south
        String dir;
        if (angle >= -22.5 && angle < 22.5) dir = "S";
        else if (angle >= 22.5 && angle < 67.5) dir = "SE";
        else if (angle >= 67.5 && angle < 112.5) dir = "E";
        else if (angle >= 112.5 && angle < 157.5) dir = "NE";
        else if (angle >= 157.5 || angle < -157.5) dir = "N";
        else if (angle >= -157.5 && angle < -112.5) dir = "NW";
        else if (angle >= -112.5 && angle < -67.5) dir = "W";
        else dir = "SW";

        if (dy > 3.0) dir += "-up";
        else if (dy < -3.0) dir += "-down";

        return dir;
    }

    /** Check if a BlockState matches any of the requested block categories. */
    private static boolean matchesBlockCategory(BlockState state, Set<String> categories) {
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

        if (categories.contains("all")) return true;

        if (categories.contains("ores") && isOre(state)) return true;
        if (categories.contains("containers") && isContainer(blockId)) return true;
        if (categories.contains("spawners") && isSpawner(blockId)) return true;
        if (categories.contains("portals") && isPortal(blockId)) return true;
        if (categories.contains("danger") && isDanger(state, blockId)) return true;
        if (categories.contains("crafting") && isCraftingStation(blockId)) return true;
        if (categories.contains("beehive") && isBeehive(blockId)) return true;

        return false;
    }

    /** Build a JSON entry for a matched block. */
    private static JsonObject buildBlockEntry(BlockState state, BlockPos pos, BlockPos playerPos,
                                               int radius) {
        JsonObject obj = new JsonObject();
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        obj.addProperty("block", blockId);
        obj.addProperty("name", state.getBlock().getName().getString());

        int dx = pos.getX() - playerPos.getX();
        int dy = pos.getY() - playerPos.getY();
        int dz = pos.getZ() - playerPos.getZ();
        obj.addProperty("direction", computeDirection(dx, dy, dz));

        JsonObject posObj = new JsonObject();
        posObj.addProperty("x", pos.getX());
        posObj.addProperty("y", pos.getY());
        posObj.addProperty("z", pos.getZ());
        obj.add("pos", posObj);

        // Categories list for the matched block
        JsonArray cats = new JsonArray();
        if (isOre(state)) cats.add("ores");
        if (isContainer(blockId)) cats.add("containers");
        if (isSpawner(blockId)) cats.add("spawners");
        if (isPortal(blockId)) cats.add("portals");
        if (isDanger(state, blockId)) cats.add("danger");
        if (isCraftingStation(blockId)) cats.add("crafting");
        if (isBeehive(blockId)) cats.add("beehive");
        if (cats.isEmpty()) cats.add("other");
        obj.add("categories", cats);

        return obj;
    }

    private static boolean isOre(BlockState state) {
        return state.is(BlockTags.COAL_ORES)
                || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.COPPER_ORES)
                || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.REDSTONE_ORES)
                || state.is(BlockTags.EMERALD_ORES)
                || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.DIAMOND_ORES);
    }

    private static final Set<String> CONTAINER_BLOCKS = Set.of(
            "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel",
            "minecraft:ender_chest", "minecraft:hopper", "minecraft:dispenser",
            "minecraft:dropper"
    );

    private static boolean isContainer(String blockId) {
        return CONTAINER_BLOCKS.contains(blockId) || blockId.endsWith("_shulker_box");
    }

    private static final Set<String> SPAWNER_BLOCKS = Set.of(
            "minecraft:spawner", "minecraft:trial_spawner", "minecraft:vault"
    );

    private static boolean isSpawner(String blockId) {
        return SPAWNER_BLOCKS.contains(blockId);
    }

    private static final Set<String> PORTAL_BLOCKS = Set.of(
            "minecraft:nether_portal", "minecraft:end_portal",
            "minecraft:end_portal_frame", "minecraft:end_gateway"
    );

    private static boolean isPortal(String blockId) {
        return PORTAL_BLOCKS.contains(blockId);
    }

    private static final Set<String> DANGER_BLOCKS = Set.of(
            "minecraft:lava", "minecraft:water", "minecraft:powder_snow",
            "minecraft:fire", "minecraft:soul_fire", "minecraft:cactus",
            "minecraft:sweet_berry_bush", "minecraft:cobweb",
            "minecraft:magma_block", "minecraft:campfire", "minecraft:soul_campfire"
    );

    private static boolean isDanger(BlockState state, String blockId) {
        return DANGER_BLOCKS.contains(blockId);
    }

    private static final Set<String> CRAFTING_STATIONS = Set.of(
            "minecraft:crafting_table", "minecraft:furnace",
            "minecraft:blast_furnace", "minecraft:smoker",
            "minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil",
            "minecraft:enchanting_table", "minecraft:brewing_stand",
            "minecraft:smithing_table", "minecraft:grindstone",
            "minecraft:stonecutter", "minecraft:cartography_table",
            "minecraft:loom", "minecraft:fletching_table"
    );

    private static boolean isCraftingStation(String blockId) {
        return CRAFTING_STATIONS.contains(blockId);
    }

    private static final Set<String> BEEHIVE_BLOCKS = Set.of(
            "minecraft:beehive", "minecraft:bee_nest"
    );

    private static boolean isBeehive(String blockId) {
        return BEEHIVE_BLOCKS.contains(blockId);
    }

    /**
     * 螺旋采样搜索指定生物群系。
     * <p>
     * 以玩家为中心，步长 32 格（2 chunks）进行螺旋采样，最多 200 个采样点。
     * 支持精确生物群系名称匹配和模糊关键词匹配（如 "cold" 匹配 taiga/snow 等）。
     *
     * @param mc          Minecraft 客户端实例
     * @param targetBiome 目标生物群系名称或类型关键词
     * @param maxRadius   最大搜索半径（格），范围 32-1024
     * @return JSON 格式的搜索结果
     */
    public static String findBiome(Minecraft mc, String targetBiome, int maxRadius) {
        JsonObject root = new JsonObject();

        if (mc.player == null || mc.level == null) {
            root.addProperty("error", "Not in a world");
            return root.toString();
        }

        // Only search in overworld
        String dimension = mc.level.dimension().location().toString();
        if (!"minecraft:overworld".equals(dimension)) {
            root.addProperty("error", "Biome search only works in the Overworld. Current: " + dimension);
            root.addProperty("found", false);
            return root.toString();
        }

        int clampedRadius = Math.max(32, Math.min(maxRadius, 1024));
        int step = 32; // blocks between samples (2 chunks)
        BlockPos playerPos = mc.player.blockPosition();
        int sampleY = playerPos.getY();
        // Clamp Y to a reasonable range for biome sampling
        if (sampleY < -64) sampleY = -64;
        if (sampleY > 320) sampleY = 320;

        root.addProperty("target", targetBiome);

        JsonObject playerPosObj = new JsonObject();
        playerPosObj.addProperty("x", playerPos.getX());
        playerPosObj.addProperty("y", sampleY);
        playerPosObj.addProperty("z", playerPos.getZ());
        root.add("player_pos", playerPosObj);

        boolean isFuzzyKeyword = isFuzzyKeyword(targetBiome);
        List<String> matchKeywords = isFuzzyKeyword
                ? getFuzzyKeywords(targetBiome.toLowerCase(Locale.ROOT))
                : List.of();

        // Generate spiral sample points
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        List<SampleResult> samples = new ArrayList<>();
        int px = playerPos.getX();
        int pz = playerPos.getZ();
        int maxRings = clampedRadius / step;

        for (int ring = 0; ring <= maxRings; ring++) {
            int d = ring * step;
            // Top edge: left to right
            for (int dx = -d; dx <= d; dx += step) {
                addSample(mc, samples, mutable, px, sampleY, pz, dx, -d, targetBiome, isFuzzyKeyword, matchKeywords);
            }
            // Right edge: top to bottom (exclude corners already sampled)
            for (int dz = -d + step; dz <= d; dz += step) {
                addSample(mc, samples, mutable, px, sampleY, pz, d, dz, targetBiome, isFuzzyKeyword, matchKeywords);
            }
            // Bottom edge: right to left (exclude corners)
            for (int dx = d - step; dx >= -d; dx -= step) {
                addSample(mc, samples, mutable, px, sampleY, pz, dx, d, targetBiome, isFuzzyKeyword, matchKeywords);
            }
            // Left edge: bottom to top (exclude corners)
            for (int dz = d - step; dz > -d; dz -= step) {
                addSample(mc, samples, mutable, px, sampleY, pz, -d, dz, targetBiome, isFuzzyKeyword, matchKeywords);
            }

            if (samples.size() >= 200) break;
        }

        root.addProperty("sample_count", samples.size());

        if (samples.isEmpty()) {
            root.addProperty("found", false);
            root.addProperty("message", "No matching biome found within " + clampedRadius + " blocks.");
            return root.toString();
        }

        // Find nearest by actual distance
        SampleResult nearest = samples.get(0);
        double minDist = nearest.distance;
        for (SampleResult s : samples) {
            if (s.distance < minDist) {
                minDist = s.distance;
                nearest = s;
            }
        }

        JsonObject nearestObj = new JsonObject();
        nearestObj.addProperty("biome", nearest.biomeName);
        nearestObj.addProperty("distance", Math.round(nearest.distance * 10.0) / 10.0);
        nearestObj.addProperty("direction", nearest.direction);

        JsonObject nearestPos = new JsonObject();
        nearestPos.addProperty("x", nearest.pos.getX());
        nearestPos.addProperty("z", nearest.pos.getZ());
        nearestObj.add("pos", nearestPos);

        root.addProperty("found", true);
        root.add("nearest", nearestObj);

        // Alternatives (up to 2 additional distinct biome results at different positions)
        JsonArray altArr = new JsonArray();
        double altMinDist = Math.min(nearest.distance * 2.0, clampedRadius * 0.5);
        int altCount = 0;
        for (SampleResult s : samples) {
            if (s == nearest) continue;
            if (altCount >= 2) break;
            // Must be meaningfully far from nearest to be a real alternative
            if (Math.abs(s.pos.getX() - nearest.pos.getX()) < 32
                    && Math.abs(s.pos.getZ() - nearest.pos.getZ()) < 32) continue;
            if (s.distance > nearest.distance + clampedRadius * 0.5) continue;

            JsonObject altObj = new JsonObject();
            altObj.addProperty("biome", s.biomeName);
            altObj.addProperty("distance", Math.round(s.distance * 10.0) / 10.0);
            altObj.addProperty("direction", s.direction);
            JsonObject altPos = new JsonObject();
            altPos.addProperty("x", s.pos.getX());
            altPos.addProperty("z", s.pos.getZ());
            altObj.add("pos", altPos);
            altArr.add(altObj);
            altCount++;
        }
        root.add("alternatives", altArr);

        return root.toString();
    }

    /** Internal record for a single biome sample result. */
    private record SampleResult(BlockPos pos, String biomeName, double distance, String direction) {}

    private static void addSample(Minecraft mc, List<SampleResult> samples,
                                   BlockPos.MutableBlockPos mutable,
                                   int px, int sampleY, int pz,
                                   int dx, int dz,
                                   String targetBiome, boolean isFuzzy,
                                   List<String> fuzzyKeywords) {
        if (samples.size() >= 200) return;

        int sx = px + dx;
        int sz = pz + dz;

        // Clamp to world bounds
        if (sx < -30000000 || sx > 30000000 || sz < -30000000 || sz > 30000000) return;

        mutable.set(sx, sampleY, sz);
        var biomeHolder = mc.level.getBiome(mutable);
        String biomeName = biomeHolder.unwrapKey()
                .map(key -> key.location().toString())
                .orElse("");

        if (biomeName.isEmpty()) return;

        boolean match;
        if (isFuzzy) {
            String lower = biomeName.toLowerCase(Locale.ROOT);
            match = fuzzyKeywords.stream().anyMatch(lower::contains);
        } else {
            match = biomeName.equals("minecraft:" + targetBiome)
                    || biomeName.endsWith(":" + targetBiome);
        }

        if (match) {
            double dist = Math.sqrt(dx * dx + dz * dz);
            String dir = computeDirection(dx, 0, dz);
            samples.add(new SampleResult(mutable.immutable(), biomeName, dist, dir));
        }
    }

    /**
     * Check if the given target is a fuzzy keyword (type category) rather than a specific biome name.
     */
    private static boolean isFuzzyKeyword(String target) {
        return switch (target.toLowerCase(Locale.ROOT)) {
            case "cold", "warm", "hot", "ocean", "forest", "mountain",
                 "plains", "swamp", "jungle", "taiga", "mushroom", "desert",
                 "savanna", "badlands", "snow", "frozen", "ice", "river",
                 "dark", "birch", "bamboo", "mangrove", "cave", "lush",
                 "dripstone", "cherry", "meadow", "grove", "peak",
                 "beach", "stony", "windswept" -> true;
            default -> false;
        };
    }

    /**
     * Get the list of biome name substrings to match for a fuzzy keyword.
     * Each keyword maps to biome name fragments that would satisfy the user's intent.
     */
    private static List<String> getFuzzyKeywords(String keyword) {
        List<String> result = new ArrayList<>();
        switch (keyword) {
            case "desert":
                result.add("desert");
                break;
            case "cold", "snow", "frozen", "ice":
                result.add("snow");
                result.add("ice");
                result.add("frozen");
                result.add("taiga");
                result.add("grove");
                result.add("tundra");
                break;
            case "warm":
                result.add("desert");
                result.add("savanna");
                result.add("badlands");
                result.add("jungle");
                result.add("mangrove");
                break;
            case "hot":
                result.add("desert");
                result.add("badlands");
                break;
            case "ocean", "river":
                result.add("ocean");
                result.add("river");
                result.add("deep");
                result.add("lake");
                break;
            case "forest":
                result.add("forest");
                result.add("birch");
                result.add("dark");
                result.add("grove");
                result.add("woodland");
                result.add("jungle");
                break;
            case "jungle":
                result.add("jungle");
                result.add("bamboo");
                break;
            case "mountain":
                result.add("mountain");
                result.add("peak");
                result.add("slope");
                result.add("hill");
                result.add("highlands");
                result.add("cliff");
                break;
            case "plains", "meadow":
                result.add("plains");
                result.add("meadow");
                result.add("steppe");
                break;
            case "swamp", "mangrove":
                result.add("swamp");
                result.add("mangrove");
                break;
            case "taiga", "grove":
                result.add("taiga");
                result.add("grove");
                result.add("snow");
                break;
            case "mushroom":
                result.add("mushroom");
                break;
            case "savanna":
                result.add("savanna");
                break;
            case "badlands":
                result.add("badlands");
                break;
            case "birch":
                result.add("birch");
                break;
            case "dark":
                result.add("dark");
                result.add("roofed");
                break;
            case "bamboo":
                result.add("bamboo");
                result.add("jungle");
                break;
            case "cherry":
                result.add("cherry");
                break;
            case "beach":
                result.add("beach");
                result.add("shore");
                break;
            case "stony", "windswept":
                result.add("stony");
                result.add("windswept");
                break;
            case "cave", "lush", "dripstone":
                result.add("cave");
                result.add("lush");
                result.add("dripstone");
                break;
            default:
                // Exact match fallback — keyword IS the biome name
                result.add(keyword);
                break;
        }
        return result;
    }

    static RecipeType<?> getCraftingRecipeType() {
        return RecipeType.CRAFTING;
    }
}

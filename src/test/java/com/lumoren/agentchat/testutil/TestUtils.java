package com.lumoren.agentchat.testutil;

import com.lumoren.agentchat.model.InventoryItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.mockito.Mockito;

import java.util.List;

/**
 * 测试工具类，提供创建 Mock Minecraft 对象的静态工厂方法。
 * <p>
 * 所有方法均使用 Mockito.mock() 创建模拟对象，
 * 避免依赖 Minecraft 运行时的复杂初始化。
 */
public class TestUtils {

    /**
     * 创建一个模拟的 Minecraft 客户端实例。
     * <p>
     * 返回的 mock 实例可用于获取模拟的 player、level 等对象，
     * 所有方法均返回 Mockito 默认值。
     *
     * @return Mock Minecraft 实例
     */
    public static Minecraft createMockMinecraft() {
        return Mockito.mock(Minecraft.class);
    }

    /**
     * 创建一个模拟的本地玩家对象。
     * <p>
     * 使用 Mockito.mock() 直接创建 LocalPlayer 的模拟实例，
     * 不会执行构造函数逻辑。
     *
     * @param name 玩家名称
     * @param x    X 坐标
     * @param y    Y 坐标
     * @param z    Z 坐标
     * @return Mock LocalPlayer 实例
     */
    public static LocalPlayer createMockPlayer(String name, double x, double y, double z) {
        LocalPlayer player = Mockito.mock(LocalPlayer.class);
        Mockito.when(player.getName()).thenReturn(Component.literal(name));
        Mockito.when(player.getX()).thenReturn(x);
        Mockito.when(player.getY()).thenReturn(y);
        Mockito.when(player.getZ()).thenReturn(z);
        Mockito.when(player.getDisplayName()).thenReturn(Component.literal(name));
        return player;
    }

    /**
     * 创建一个模拟的玩家背包对象。
     * <p>
     * 返回的 Inventory mock 可在测试中替代真实的 Minecraft 背包。
     *
     * @param items 背包物品列表
     * @return Mock Inventory 实例
     */
    public static Inventory createMockInventory(List<InventoryItem> items) {
        return Mockito.mock(Inventory.class);
    }
}

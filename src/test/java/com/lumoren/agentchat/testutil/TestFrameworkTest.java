package com.lumoren.agentchat.testutil;

import com.lumoren.agentchat.model.InventoryItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试框架验证测试，验证 TestUtils 和 MockGameData 工具类能正常工作。
 * <p>
 * 确保测试基础设施中的工具方法按预期工作。
 */
public class TestFrameworkTest {

    /**
     * 验证 TestUtils.createMockPlayer() 返回非 null 对象。
     */
    @Test
    public void testCreateMockPlayerReturnsNonNull() {
        var player = TestUtils.createMockPlayer("Steve", 100.5, 64.0, -200.3);
        assertNotNull(player, "createMockPlayer 应返回非 null 的 mock 对象");
    }

    /**
     * 验证 MockGameData.getStandardInventory() 返回正确数量的物品。
     */
    @Test
    public void testStandardInventoryHasThreeItems() {
        List<InventoryItem> items = MockGameData.getStandardInventory();
        assertEquals(3, items.size(), "标准测试背包应包含 3 个物品");
    }

    /**
     * 验证 MockGameData.getStandardInventory() 包含钻石剑。
     */
    @Test
    public void testStandardInventoryContainsDiamondSword() {
        List<InventoryItem> items = MockGameData.getStandardInventory();
        boolean hasDiamondSword = items.stream()
                .anyMatch(item -> "minecraft:diamond_sword".equals(item.itemId()));
        assertTrue(hasDiamondSword, "标准测试背包应包含钻石剑");
    }
}

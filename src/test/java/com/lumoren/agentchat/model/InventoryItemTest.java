package com.lumoren.agentchat.model;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InventoryItem} record.
 */
class InventoryItemTest {

    @Test
    void recordStoresAllFields() {
        InventoryItem item = new InventoryItem(0, "minecraft:diamond_sword", "Diamond Sword", 1, 1561, 0);

        assertEquals(0, item.slot());
        assertEquals("minecraft:diamond_sword", item.itemId());
        assertEquals("Diamond Sword", item.displayName());
        assertEquals(1, item.count());
        assertEquals(1561, item.maxDamage());
        assertEquals(0, item.currentDamage());
    }

    @Test
    void recordEquality() {
        InventoryItem a = new InventoryItem(5, "minecraft:stone", "Stone", 64, 0, 0);
        InventoryItem b = new InventoryItem(5, "minecraft:stone", "Stone", 64, 0, 0);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void zeroMaxDamage_impliesNoDurability() {
        InventoryItem item = new InventoryItem(1, "minecraft:dirt", "Dirt", 64, 0, 0);
        assertEquals(0, item.maxDamage());
        assertEquals(0, item.currentDamage());
    }

    @Test
    void damagedItem_tracksCurrentDamage() {
        InventoryItem item = new InventoryItem(0, "minecraft:diamond_pickaxe", "Diamond Pickaxe", 1, 1561, 500);
        assertEquals(1561, item.maxDamage());
        assertEquals(500, item.currentDamage());
    }
}

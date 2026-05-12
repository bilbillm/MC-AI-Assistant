package com.lumoren.agentchat.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RecipeResult} record and nested records.
 */
class RecipeResultTest {

    @Test
    void recordStoresAllFields() {
        var ingredients = List.of(
                new RecipeResult.Ingredient("minecraft:diamond", 2),
                new RecipeResult.Ingredient("minecraft:stick", 1)
        );
        var output = new RecipeResult.ItemStack("minecraft:diamond_sword", 1);
        RecipeResult recipe = new RecipeResult("minecraft:diamond_sword", ingredients, output, "crafting");

        assertEquals("minecraft:diamond_sword", recipe.recipeId());
        assertEquals(2, recipe.input().size());
        assertEquals("minecraft:diamond", recipe.input().get(0).itemId());
        assertEquals(2, recipe.input().get(0).count());
        assertEquals("minecraft:diamond_sword", recipe.output().itemId());
        assertEquals(1, recipe.output().count());
        assertEquals("crafting", recipe.type());
    }

    @Test
    void ingredient_record() {
        var ingredient = new RecipeResult.Ingredient("minecraft:iron_ingot", 3);
        assertEquals("minecraft:iron_ingot", ingredient.itemId());
        assertEquals(3, ingredient.count());
    }

    @Test
    void ingredient_equality() {
        var a = new RecipeResult.Ingredient("minecraft:stone", 1);
        var b = new RecipeResult.Ingredient("minecraft:stone", 1);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void itemStack_record() {
        var stack = new RecipeResult.ItemStack("minecraft:furnace", 1);
        assertEquals("minecraft:furnace", stack.itemId());
        assertEquals(1, stack.count());
    }

    @Test
    void itemStack_equality() {
        var a = new RecipeResult.ItemStack("minecraft:stone", 64);
        var b = new RecipeResult.ItemStack("minecraft:stone", 64);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void smeltingRecipe_type() {
        var ingredients = List.of(new RecipeResult.Ingredient("minecraft:raw_iron", 1));
        var output = new RecipeResult.ItemStack("minecraft:iron_ingot", 1);
        RecipeResult recipe = new RecipeResult("minecraft:iron_ingot", ingredients, output, "smelting");

        assertEquals("smelting", recipe.type());
        assertEquals("minecraft:raw_iron", recipe.input().get(0).itemId());
    }

    @Test
    void recordEquality() {
        var ingredients = List.of(new RecipeResult.Ingredient("minecraft:log", 1));
        var output = new RecipeResult.ItemStack("minecraft:planks", 4);
        RecipeResult a = new RecipeResult("minecraft:planks", ingredients, output, "crafting");
        RecipeResult b = new RecipeResult("minecraft:planks", ingredients, output, "crafting");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}

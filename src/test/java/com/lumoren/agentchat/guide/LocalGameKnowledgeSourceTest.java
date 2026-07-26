package com.lumoren.agentchat.guide;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalGameKnowledgeSourceTest {
    @Test
    void query_recipePlanWithItemIdAddsRecipeEvidence() {
        LocalGameKnowledgeSource source = new LocalGameKnowledgeSource(
                (mc, itemId, recipe) -> new LocalGameKnowledgeSource.LookupEvidence(
                        GuideEvidence.SourceType.LOCAL_GAME,
                        "Recipe data for " + itemId,
                        "minecraft_recipe_manager:" + itemId,
                        true));

        List<GuideEvidence> evidence = source.query(null, GuideIntent.RECIPE_PLAN,
                "minecraft:piston 怎么做");

        assertTrue(evidence.stream().anyMatch(item ->
                item.title().equals("Recipe evidence: minecraft:piston")
                        && item.summary().contains("Recipe data")
                        && item.verified()));
    }

    @Test
    void query_itemHelpWithItemIdAddsRecipeAndUsageEvidence() {
        LocalGameKnowledgeSource source = new LocalGameKnowledgeSource((mc, itemId, recipe) ->
                new LocalGameKnowledgeSource.LookupEvidence(
                        GuideEvidence.SourceType.JEI,
                        (recipe ? "JEI recipe " : "JEI usage ") + itemId,
                        (recipe ? "jei:recipe:" : "jei:usage:") + itemId,
                        true));

        List<GuideEvidence> evidence = source.query(null, GuideIntent.ITEM_HELP,
                "minecraft:hopper 有什么用");

        assertTrue(evidence.stream().anyMatch(item ->
                item.sourceType() == GuideEvidence.SourceType.JEI
                        && item.title().equals("Recipe evidence: minecraft:hopper")));
        assertTrue(evidence.stream().anyMatch(item ->
                item.sourceType() == GuideEvidence.SourceType.JEI
                        && item.title().equals("Usage evidence: minecraft:hopper")));
    }

    @Test
    void query_noVanillaRecipeEvidenceIsExplicitlyUnverified() {
        LocalGameKnowledgeSource source = new LocalGameKnowledgeSource();

        List<GuideEvidence> evidence = source.query(null, GuideIntent.RECIPE_PLAN,
                "minecraft:stone 怎么做");

        GuideEvidence recipe = evidence.stream()
                .filter(item -> item.title().equals("Recipe evidence: minecraft:stone"))
                .findFirst()
                .orElseThrow();
        assertFalse(recipe.verified());
        assertTrue(recipe.summary().contains("No verified vanilla recipe"));
    }

    @Test
    void query_plainNamesDoNotInventRecipeEvidence() {
        LocalGameKnowledgeSource source = new LocalGameKnowledgeSource((mc, itemId, recipe) -> {
            throw new AssertionError("lookup should not be called for non-namespaced text");
        });

        List<GuideEvidence> evidence = source.query(null, GuideIntent.RECIPE_PLAN,
                "hopper 怎么做");

        assertEquals(2, evidence.size());
    }
}

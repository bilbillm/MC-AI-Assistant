package com.lumoren.agentchat.guide;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebKnowledgeSourceTest {
    @Test
    void query_disabledDoesNotSearch() {
        AtomicBoolean searched = new AtomicBoolean(false);
        WebKnowledgeSource source = new WebKnowledgeSource(() -> false, (mc, query) -> {
            searched.set(true);
            return "should not run";
        });

        List<GuideEvidence> evidence = source.query(null, GuideIntent.RECIPE_PLAN,
                "minecraft:piston 怎么做");

        assertFalse(searched.get());
        assertEquals(1, evidence.size());
        assertEquals("Web/wiki disabled", evidence.get(0).title());
    }

    @Test
    void query_enabledReturnsBoundedSearchEvidence() {
        AtomicReference<String> querySeen = new AtomicReference<>();
        WebKnowledgeSource source = new WebKnowledgeSource(() -> true, (mc, query) -> {
            querySeen.set(query);
            return "Search results:\n1. Piston - Minecraft Wiki\nA piston recipe and usage summary.";
        });

        List<GuideEvidence> evidence = source.query(null, GuideIntent.RECIPE_PLAN,
                "minecraft:piston 怎么做");

        assertEquals(1, evidence.size());
        assertEquals(GuideEvidence.SourceType.WEB, evidence.get(0).sourceType());
        assertTrue(evidence.get(0).verified());
        assertTrue(evidence.get(0).summary().contains("Piston"));
        assertTrue(querySeen.get().contains("minecraft:piston"));
        assertTrue(querySeen.get().contains("minecraft mod recipe"));
    }

    @Test
    void query_searchFailureIsUnverified() {
        WebKnowledgeSource source = new WebKnowledgeSource(() -> true,
                (mc, query) -> "Search unavailable: timeout");

        GuideEvidence evidence = source.query(null, GuideIntent.ITEM_HELP,
                "minecraft:hopper 有什么用").get(0);

        assertEquals("Web/wiki search unavailable", evidence.title());
        assertFalse(evidence.verified());
    }
}

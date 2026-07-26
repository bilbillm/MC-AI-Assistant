package com.lumoren.agentchat.guide.cache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuideTextIndexTest {

    @Test
    void cachesWithinGenerationAndBustsOnInvalidate() {
        GuideTextIndex index = new GuideTextIndex();
        AtomicInteger loads = new AtomicInteger();

        String first = index.cachedText("k", () -> {
            loads.incrementAndGet();
            return "text";
        });
        String second = index.cachedText("k", () -> {
            loads.incrementAndGet();
            return "text";
        });

        assertEquals("text", first);
        assertEquals("text", second);
        assertEquals(1, loads.get(), "second read must hit the cache");

        index.invalidateAll();
        index.cachedText("k", () -> {
            loads.incrementAndGet();
            return "text";
        });
        assertEquals(2, loads.get(), "invalidation must force a reload");
    }

    @Test
    void separateKeysAreIndependent() {
        GuideTextIndex index = new GuideTextIndex();
        index.cachedText("a", () -> "A");
        index.cachedText("b", () -> "B");
        assertEquals("A", index.cachedText("a", () -> "X"));
        assertEquals("B", index.cachedText("b", () -> "X"));
        assertEquals(2, index.size());
    }
}

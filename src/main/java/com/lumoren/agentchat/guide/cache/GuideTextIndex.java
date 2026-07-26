package com.lumoren.agentchat.guide.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Generation-scoped cache for expensive guide-text reads (resource-pack
 * scans, local guide file extraction). Entries are tagged with the current
 * generation; {@link #invalidateAll()} bumps the generation so every cached
 * entry becomes stale at once.
 * <p>
 * Invalidation triggers: Minecraft resource reload (pack content may change)
 * and world/instance switch (config/ftbquests files may belong to another
 * instance). Wiring lives in {@code ClientEventHandler} /
 * {@code ClientModEvents}.
 */
public final class GuideTextIndex {

    private static final GuideTextIndex INSTANCE = new GuideTextIndex();

    private final Map<String, Entry> textCache = new ConcurrentHashMap<>();
    private volatile long generation;

    public static GuideTextIndex getInstance() {
        return INSTANCE;
    }

    /**
     * Returns cached extracted text for {@code key}, or loads and caches it
     * under the current generation.
     */
    public String cachedText(String key, Supplier<String> loader) {
        long gen = generation;
        Entry entry = textCache.get(key);
        if (entry != null && entry.generation == gen) {
            return entry.text;
        }
        String text = loader.get();
        if (text != null) {
            textCache.put(key, new Entry(gen, text));
        }
        return text;
    }

    /**
     * Drops all cached text (resource reload, world or instance change).
     */
    public void invalidateAll() {
        generation++;
        textCache.clear();
    }

    /** Current cache size, exposed for tests/diagnostics. */
    public int size() {
        return textCache.size();
    }

    private record Entry(long generation, String text) {
    }
}

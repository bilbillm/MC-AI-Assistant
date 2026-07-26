package com.lumoren.agentchat.guide;

import com.lumoren.agentchat.guide.cache.GuideTextIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads guide/quest text from active resource packs and mod jars through Minecraft's ResourceManager.
 * <p>
 * Extraction results are cached in {@link GuideTextIndex} (invalidated on
 * resource reload or instance change). Patchouli entries are read from the
 * client's language folder first, falling back to {@code en_us}; other
 * language variants are skipped to avoid duplicate multilingual evidence.
 */
public class ResourceGuideTextKnowledgeSource implements GuideKnowledgeSource {
    private static final int MAX_RESULTS = 6;
    private static final int MAX_RESOURCES_PER_PREFIX = 200;

    @Override
    public boolean requiresMainThread() {
        return false;
    }

    @Override
    public boolean isAvailable(Minecraft mc) {
        return mc != null && mc.getResourceManager() != null;
    }

    @Override
    public List<GuideEvidence> query(Minecraft mc, GuideIntent intent, String userText) {
        if (!isAvailable(mc)) {
            return List.of();
        }
        List<String> terms = LocalGuideTextKnowledgeSource.searchTerms(userText);
        String langFolder = currentLangFolder(mc);
        List<GuideEvidence> results = new ArrayList<>();
        scanPrefix(mc, "patchouli_books", terms, langFolder, results);
        scanPrefix(mc, "ftbquests", terms, langFolder, results);
        return results.size() > MAX_RESULTS ? results.subList(0, MAX_RESULTS) : results;
    }

    private static void scanPrefix(Minecraft mc, String prefix, List<String> terms,
                                   String langFolder, List<GuideEvidence> results) {
        if (results.size() >= MAX_RESULTS) {
            return;
        }
        Map<ResourceLocation, Resource> resources = mc.getResourceManager()
                .listResources(prefix, ResourceGuideTextKnowledgeSource::isReadableGuideResource);

        // Language preference: current client language first, then en_us.
        // Other language variants of the same entry are skipped.
        List<Map.Entry<ResourceLocation, Resource>> preferred = new ArrayList<>();
        List<Map.Entry<ResourceLocation, Resource>> englishFallback = new ArrayList<>();
        List<Map.Entry<ResourceLocation, Resource>> neutral = new ArrayList<>();
        resources.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .limit(MAX_RESOURCES_PER_PREFIX)
                .forEach(entry -> {
                    String langSegment = langSegmentOf(entry.getKey().getPath());
                    if (langSegment == null) {
                        neutral.add(entry);
                    } else if (langSegment.equals(langFolder)) {
                        preferred.add(entry);
                    } else if ("en_us".equals(langSegment)) {
                        englishFallback.add(entry);
                    }
                    // other language variants skipped
                });

        Set<String> seenEntries = new HashSet<>();
        for (List<Map.Entry<ResourceLocation, Resource>> bucket
                : List.of(preferred, englishFallback, neutral)) {
            for (Map.Entry<ResourceLocation, Resource> entry : bucket) {
                if (results.size() >= MAX_RESULTS) {
                    return;
                }
                if (!seenEntries.add(langNeutralKey(entry.getKey().getPath()))) {
                    continue;
                }
                GuideEvidence evidence = readEvidence(entry.getKey(), entry.getValue(), terms);
                if (evidence != null) {
                    results.add(evidence);
                }
            }
        }
    }

    /**
     * Extracts the language folder segment from paths like
     * {@code patchouli_books/<book>/<lang>/...}; {@code null} when the path
     * has no language structure.
     */
    static String langSegmentOf(String path) {
        String[] segments = path.split("/");
        if (segments.length >= 3 && "patchouli_books".equals(segments[0])) {
            return segments[2].toLowerCase(Locale.ROOT);
        }
        return null;
    }

    /** Path key ignoring the language segment, for cross-language dedup. */
    private static String langNeutralKey(String path) {
        String[] segments = path.split("/");
        if (segments.length >= 4 && "patchouli_books".equals(segments[0])) {
            StringBuilder sb = new StringBuilder(segments[0]).append('/').append(segments[1]);
            for (int i = 3; i < segments.length; i++) {
                sb.append('/').append(segments[i]);
            }
            return sb.toString();
        }
        return path;
    }

    private static GuideEvidence readEvidence(ResourceLocation id, Resource resource,
                                              List<String> terms) {
        String cacheKey = "resource:" + resource.sourcePackId() + ":" + id;
        String text = GuideTextIndex.getInstance().cachedText(cacheKey, () -> readResourceText(id, resource));
        if (text == null || text.isBlank()) {
            return null;
        }
        if (LocalGuideTextKnowledgeSource.score(text, terms) <= 0 && !terms.isEmpty()) {
            return null;
        }
        GuideEvidence.SourceType type = classify(id);
        String summary = LocalGuideTextKnowledgeSource.summarize(text, terms);
        String title = id.toString();
        if (type == GuideEvidence.SourceType.PATCHOULI) {
            // Honest label: resource scan, not the Patchouli runtime API.
            title = "Patchouli resource: " + title;
        }
        return new GuideEvidence(type, title, summary,
                resource.sourcePackId() + ":" + id, true);
    }

    private static String readResourceText(ResourceLocation id, Resource resource) {
        try (var reader = resource.openAsReader()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
                if (sb.length() > 16_000) {
                    break;
                }
            }
            return LocalGuideTextKnowledgeSource.extractText(Path.of(id.getPath()), sb.toString());
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    private static String currentLangFolder(Minecraft mc) {
        try {
            Object languageManager = mc.getClass().getMethod("getLanguageManager").invoke(mc);
            Object selected = languageManager.getClass().getMethod("getSelected").invoke(languageManager);
            if (selected != null && !selected.toString().isBlank()) {
                return selected.toString().toLowerCase(Locale.ROOT);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fall through to en_us outside a live client.
        }
        return "en_us";
    }

    private static boolean isReadableGuideResource(ResourceLocation id) {
        String path = id.getPath().toLowerCase(Locale.ROOT);
        return path.endsWith(".json") || path.endsWith(".snbt")
                || path.endsWith(".txt") || path.endsWith(".md");
    }

    private static GuideEvidence.SourceType classify(ResourceLocation id) {
        String path = id.getPath().toLowerCase(Locale.ROOT);
        if (path.contains("patchouli_books")) {
            return GuideEvidence.SourceType.PATCHOULI;
        }
        if (path.contains("ftbquests")) {
            return GuideEvidence.SourceType.FTB_QUESTS;
        }
        return GuideEvidence.SourceType.LOCAL_GAME;
    }
}

package com.lumoren.agentchat.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads guide/quest text from active resource packs and mod jars through Minecraft's ResourceManager.
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
        List<GuideEvidence> results = new ArrayList<>();
        scanPrefix(mc, "patchouli_books", terms, results);
        scanPrefix(mc, "ftbquests", terms, results);
        return results.size() > MAX_RESULTS ? results.subList(0, MAX_RESULTS) : results;
    }

    private static void scanPrefix(Minecraft mc, String prefix, List<String> terms,
                                   List<GuideEvidence> results) {
        if (results.size() >= MAX_RESULTS) {
            return;
        }
        Map<ResourceLocation, Resource> resources = mc.getResourceManager()
                .listResources(prefix, ResourceGuideTextKnowledgeSource::isReadableGuideResource);
        resources.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .limit(MAX_RESOURCES_PER_PREFIX)
                .forEach(entry -> {
                    if (results.size() >= MAX_RESULTS) {
                        return;
                    }
                    GuideEvidence evidence = readEvidence(entry.getKey(), entry.getValue(), terms);
                    if (evidence != null) {
                        results.add(evidence);
                    }
                });
    }

    private static GuideEvidence readEvidence(ResourceLocation id, Resource resource,
                                              List<String> terms) {
        try (var reader = resource.openAsReader()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
                if (sb.length() > 16_000) {
                    break;
                }
            }
            String text = LocalGuideTextKnowledgeSource.extractText(Path.of(id.getPath()), sb.toString());
            if (text.isBlank()) {
                return null;
            }
            if (LocalGuideTextKnowledgeSource.score(text, terms) <= 0 && !terms.isEmpty()) {
                return null;
            }
            GuideEvidence.SourceType type = classify(id);
            String summary = LocalGuideTextKnowledgeSource.summarize(text, terms);
            return new GuideEvidence(type, id.toString(), summary,
                    resource.sourcePackId() + ":" + id, true);
        } catch (IOException | RuntimeException e) {
            return null;
        }
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

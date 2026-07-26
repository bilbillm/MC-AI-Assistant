package com.lumoren.agentchat.guide;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reads common modpack guide/quest data files without adding hard mod dependencies.
 */
public class LocalGuideTextKnowledgeSource implements GuideKnowledgeSource {
    private static final int MAX_FILES_PER_ROOT = 160;
    private static final int MAX_RESULTS = 6;
    private static final Set<String> EXTENSIONS = Set.of(".json", ".snbt", ".txt", ".md");
    private final Path rootOverride;

    public LocalGuideTextKnowledgeSource() {
        this(null);
    }

    LocalGuideTextKnowledgeSource(Path rootOverride) {
        this.rootOverride = rootOverride;
    }

    @Override
    public boolean requiresMainThread() {
        return false;
    }

    @Override
    public boolean isAvailable(Minecraft mc) {
        Path root = resolveGameDir(mc);
        return root != null && Files.isDirectory(root);
    }

    @Override
    public List<GuideEvidence> query(Minecraft mc, GuideIntent intent, String userText) {
        Path root = resolveGameDir(mc);
        if (root == null || !Files.isDirectory(root)) {
            return List.of();
        }
        List<String> terms = searchTerms(userText);
        List<GuideEvidence> results = new ArrayList<>();
        for (Path candidateRoot : candidateRoots(root)) {
            if (!Files.isDirectory(candidateRoot)) {
                continue;
            }
            scanRoot(candidateRoot, terms, results);
            if (results.size() >= MAX_RESULTS) {
                break;
            }
        }
        return results.size() > MAX_RESULTS ? results.subList(0, MAX_RESULTS) : results;
    }

    private static void scanRoot(Path root, List<String> terms, List<GuideEvidence> results) {
        try (Stream<Path> stream = Files.walk(root, 8)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(LocalGuideTextKnowledgeSource::hasSupportedExtension)
                    .limit(MAX_FILES_PER_ROOT)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            for (Path file : files) {
                if (results.size() >= MAX_RESULTS) {
                    return;
                }
                GuideEvidence evidence = readEvidence(root, file, terms);
                if (evidence != null) {
                    results.add(evidence);
                }
            }
        } catch (IOException ignored) {
            // Ignore unreadable guide roots; this source is best-effort.
        }
    }

    private static GuideEvidence readEvidence(Path root, Path file, List<String> terms) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String text = extractText(file, content);
            if (text.isBlank()) {
                return null;
            }
            int score = score(text, terms);
            if (score <= 0 && !terms.isEmpty()) {
                return null;
            }
            GuideEvidence.SourceType sourceType = classify(file);
            String title = root.relativize(file).toString().replace('\\', '/');
            String summary = summarize(text, terms);
            return new GuideEvidence(sourceType, title, summary, file.toString(), true);
        } catch (RuntimeException | IOException e) {
            return null;
        }
    }

    static String extractText(Path file, String content) {
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".json")) {
            try {
                JsonElement element = JsonParser.parseString(content);
                List<String> values = new ArrayList<>();
                collectJsonText(element, values);
                return String.join("\n", values);
            } catch (RuntimeException ignored) {
                return stripSyntax(content);
            }
        }
        return stripSyntax(content);
    }

    private static void collectJsonText(JsonElement element, List<String> values) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            if (value.length() >= 3 && value.length() <= 500) {
                values.add(value);
            }
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectJsonText(child, values);
            }
            return;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (String key : List.of("title", "subtitle", "name", "description", "text", "body", "entries", "pages", "tasks")) {
                if (obj.has(key)) {
                    collectJsonText(obj.get(key), values);
                }
            }
        }
    }

    private static String stripSyntax(String content) {
        return content.replaceAll("[{}\\[\\]\",:=]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    static List<String> searchTerms(String userText) {
        if (userText == null || userText.isBlank()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        for (String raw : userText.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}_:.-]+")) {
            if (raw.length() >= 2 && !isStopTerm(raw)) {
                terms.add(raw);
            }
        }
        return terms.stream().distinct().limit(8).toList();
    }

    private static boolean isStopTerm(String value) {
        return Set.of("怎么", "什么", "为什么", "这个", "那个", "the", "this", "that", "what", "why", "how", "next")
                .contains(value);
    }

    static int score(String text, List<String> terms) {
        if (terms.isEmpty()) {
            return 1;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (lower.contains(term)) {
                score++;
            }
        }
        return score;
    }

    static String summarize(String text, List<String> terms) {
        String compact = text.replaceAll("\\s+", " ").strip();
        if (compact.length() <= 260) {
            return compact;
        }
        String lower = compact.toLowerCase(Locale.ROOT);
        int start = 0;
        for (String term : terms) {
            int index = lower.indexOf(term);
            if (index >= 0) {
                start = Math.max(0, index - 80);
                break;
            }
        }
        int end = Math.min(compact.length(), start + 260);
        return (start > 0 ? "..." : "") + compact.substring(start, end) + (end < compact.length() ? "..." : "");
    }

    private static List<Path> candidateRoots(Path root) {
        return List.of(
                root.resolve("config").resolve("ftbquests"),
                root.resolve("config").resolve("ftbquests").resolve("quests"),
                root.resolve("kubejs").resolve("data"),
                root.resolve("openloader").resolve("data"),
                root.resolve("patchouli_books"),
                root.resolve("data")
        );
    }

    private static GuideEvidence.SourceType classify(Path file) {
        String value = file.toString().toLowerCase(Locale.ROOT).replace('\\', '/');
        if (value.contains("ftbquests")) {
            return GuideEvidence.SourceType.FTB_QUESTS;
        }
        if (value.contains("patchouli")) {
            return GuideEvidence.SourceType.PATCHOULI;
        }
        return GuideEvidence.SourceType.LOCAL_GAME;
    }

    private static boolean hasSupportedExtension(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private Path resolveGameDir(Minecraft mc) {
        if (rootOverride != null) {
            return rootOverride;
        }
        if (mc == null || mc.gameDirectory == null) {
            return null;
        }
        return mc.gameDirectory.toPath();
    }
}

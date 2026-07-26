package com.lumoren.agentchat.guide;

import com.lumoren.agentchat.ai.GameDataAccess;
import com.lumoren.agentchat.model.InventoryItem;
import com.lumoren.agentchat.model.Project;
import com.lumoren.agentchat.model.Task;
import com.lumoren.agentchat.model.TaskStatus;
import com.lumoren.agentchat.persistence.ProjectManager;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ModpackGuideService {
    private final List<GuideKnowledgeSource> sources;

    public ModpackGuideService() {
        this(List.of(
                new LocalGameKnowledgeSource(),
                new FtbQuestsKnowledgeSource(),
                new PatchouliKnowledgeSource(),
                new ResourceGuideTextKnowledgeSource(),
                new LocalGuideTextKnowledgeSource(),
                new WebKnowledgeSource()
        ));
    }

    public ModpackGuideService(List<GuideKnowledgeSource> sources) {
        this.sources = List.copyOf(sources);
    }

    public GuideIntent detectIntent(String text) {
        String value = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (containsAny(value, "为什么", "why", "不工作", "not working", "doesn't work",
                "does not work", "没运行", "没工作", "不能运行", "无法运行")) {
            return GuideIntent.MACHINE_DIAGNOSIS;
        }
        if (containsAny(value, "下一步", "next", "what now", "干什么", "做什么", "卡住", "stuck")) {
            return GuideIntent.WHAT_NEXT;
        }
        if (containsAny(value, "有什么用", "用途", "what is", "what does", "usage", "use for")) {
            return GuideIntent.ITEM_HELP;
        }
        if (containsAny(value, "怎么做", "怎么合成", "配方", "recipe", "材料", "缺什么", "missing")
                || containsWord(value, "craft")) {
            return GuideIntent.RECIPE_PLAN;
        }
        return GuideIntent.GENERAL_CHAT;
    }

    public boolean shouldEnhance(String text, boolean guideMode) {
        return guideMode || detectIntent(text) != GuideIntent.GENERAL_CHAT;
    }

    public GuideContext buildContext(Minecraft mc, String userText) {
        return buildContext(mc, userText, safeWebKnowledgeEnabled());
    }

    /**
     * Synchronous full aggregation. Kept for tool-side use (e.g. modpack_guide)
     * where no coordinator request is in flight; the chat screen should prefer
     * the asynchronous pipeline via
     * {@link #collectMainThreadSnapshot}, {@link #queryBackgroundSources},
     * {@link #queryWebSources} and {@link #assembleContext}.
     */
    public GuideContext buildContext(Minecraft mc, String userText, boolean webEnabled) {
        GuideSnapshot snapshot = collectMainThreadSnapshot(mc, userText, webEnabled);
        List<GuideEvidence> background = queryBackgroundSources(snapshot, mc, userText);
        List<GuideEvidence> web = webEvidence(snapshot, mc, userText, background);
        return assembleContext(snapshot, background, web);
    }

    /**
     * Main-thread phase: fast immutable snapshot plus evidence from sources
     * that must read live game state (JEI/recipe lookups, FTB reflection).
     * Target budget &lt; 16 ms; no file IO and no HTTP here.
     */
    public GuideSnapshot collectMainThreadSnapshot(Minecraft mc, String userText, boolean webEnabled) {
        GuideIntent intent = detectIntent(userText);
        List<GuideEvidence> mainEvidence = new ArrayList<>();
        for (GuideKnowledgeSource source : sources) {
            if (source.requiresMainThread() && !(source instanceof WebKnowledgeSource)) {
                mainEvidence.addAll(source.query(mc, intent, userText));
            }
        }
        java.nio.file.Path gameDir = mc != null && mc.gameDirectory != null
                ? mc.gameDirectory.toPath() : null;
        return new GuideSnapshot(intent, languageTag(mc), projectSummary(), localSummary(mc),
                installedModsSummary(), targetBlockSummary(mc, intent), mainEvidence,
                gameDir, webEnabled);
    }

    /**
     * Background phase: file/resource scanning sources. Must not read live
     * player/level state; only the snapshot, files and loaded resources.
     */
    public List<GuideEvidence> queryBackgroundSources(GuideSnapshot snapshot, Minecraft mc, String userText) {
        List<GuideEvidence> evidence = new ArrayList<>();
        for (GuideKnowledgeSource source : sources) {
            if (!source.requiresMainThread() && !(source instanceof WebKnowledgeSource)) {
                evidence.addAll(source.query(mc, snapshot.intent(), userText));
            }
        }
        return evidence;
    }

    /**
     * Web phase: only runs when enabled and local evidence is insufficient.
     * When disabled but a web source is registered, returns the explicit
     * "web disabled" evidence so the prompt can state the boundary.
     */
    public List<GuideEvidence> webEvidence(GuideSnapshot snapshot, Minecraft mc, String userText,
                                           List<GuideEvidence> currentEvidence) {
        List<GuideKnowledgeSource> webSources = webSources();
        if (webSources.isEmpty()) {
            return List.of();
        }
        if (!snapshot.webEnabled()) {
            return List.of(WebKnowledgeSource.disabledEvidence());
        }
        if (!needsWebEvidence(snapshot.intent(), combined(snapshot.mainThreadEvidence(), currentEvidence),
                snapshot.targetBlockSummary())) {
            return List.of();
        }
        List<GuideEvidence> evidence = new ArrayList<>();
        for (GuideKnowledgeSource source : webSources) {
            evidence.addAll(source.query(mc, snapshot.intent(), userText));
        }
        return evidence;
    }

    /**
     * Final assembly: merge (priority, dedup, budget) into a prompt-ready context.
     */
    public GuideContext assembleContext(GuideSnapshot snapshot, List<GuideEvidence> backgroundEvidence,
                                        List<GuideEvidence> webEvidence) {
        List<GuideEvidence> all = new ArrayList<>(snapshot.mainThreadEvidence());
        if (backgroundEvidence != null) {
            all.addAll(backgroundEvidence);
        }
        if (webEvidence != null) {
            all.addAll(webEvidence);
        }
        return new GuideContext(snapshot.intent(), snapshot.languageTag(), snapshot.projectSummary(),
                snapshot.localSummary(), snapshot.installedModsSummary(), snapshot.targetBlockSummary(),
                GuideEvidenceMerger.merge(all));
    }

    private List<GuideKnowledgeSource> webSources() {
        List<GuideKnowledgeSource> result = new ArrayList<>();
        for (GuideKnowledgeSource source : sources) {
            if (source instanceof WebKnowledgeSource) {
                result.add(source);
            }
        }
        return result;
    }

    private static List<GuideEvidence> combined(List<GuideEvidence> a, List<GuideEvidence> b) {
        List<GuideEvidence> all = new ArrayList<>(a);
        if (b != null) {
            all.addAll(b);
        }
        return all;
    }

    public String buildGuidePrompt(Minecraft mc, String userText, boolean guideMode) {
        return buildGuidePrompt(mc, userText, guideMode, safeWebKnowledgeEnabled());
    }

    public String buildGuidePrompt(Minecraft mc, String userText, boolean guideMode, boolean webEnabled) {
        GuideContext context = buildContext(mc, userText, webEnabled);
        return context.toSystemPrompt(guideMode, webEnabled);
    }

    private static boolean safeWebKnowledgeEnabled() {
        try {
            return com.lumoren.agentchat.config.ConfigManager.getInstance().isWebKnowledgeEnabled();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWord(String value, String needle) {
        int index = value.indexOf(needle);
        while (index >= 0) {
            boolean leftBoundary = index == 0 || !isAsciiWord(value.charAt(index - 1));
            int end = index + needle.length();
            boolean rightBoundary = end >= value.length() || !isAsciiWord(value.charAt(end));
            if (leftBoundary && rightBoundary) {
                return true;
            }
            index = value.indexOf(needle, index + 1);
        }
        return false;
    }

    private static boolean isAsciiWord(char value) {
        return (value >= 'a' && value <= 'z')
                || (value >= '0' && value <= '9')
                || value == '_';
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean needsWebEvidence(GuideIntent intent, List<GuideEvidence> evidence,
                                            String targetBlockSummary) {
        return switch (intent) {
            case RECIPE_PLAN -> evidence.stream().noneMatch(ModpackGuideService::isVerifiedRecipeEvidence);
            case ITEM_HELP -> evidence.stream().noneMatch(ModpackGuideService::isVerifiedUsageEvidence);
            case MACHINE_DIAGNOSIS -> targetBlockSummary == null || targetBlockSummary.isBlank()
                    || evidence.stream().noneMatch(ModpackGuideService::isVerifiedGuidebookEvidence);
            case WHAT_NEXT -> evidence.stream().noneMatch(item ->
                    item.verified() && item.sourceType() == GuideEvidence.SourceType.FTB_QUESTS);
            case GENERAL_CHAT -> false;
        };
    }

    /**
     * Recipe need is only satisfied by recipe-specific evidence — unrelated
     * quest/guidebook text must not suppress the web fallback.
     */
    private static boolean isVerifiedRecipeEvidence(GuideEvidence evidence) {
        return evidence.verified()
                && (evidence.title().startsWith("Recipe evidence:")
                || evidence.sourceType() == GuideEvidence.SourceType.JEI);
    }

    /**
     * Usage need is only satisfied by usage-specific evidence.
     */
    private static boolean isVerifiedUsageEvidence(GuideEvidence evidence) {
        return evidence.verified()
                && (evidence.title().startsWith("Usage evidence:")
                || evidence.sourceType() == GuideEvidence.SourceType.JEI);
    }

    private static boolean isVerifiedGuidebookEvidence(GuideEvidence evidence) {
        return evidence.verified()
                && (evidence.sourceType() == GuideEvidence.SourceType.FTB_QUESTS
                || evidence.sourceType() == GuideEvidence.SourceType.PATCHOULI);
    }

    private static String languageTag(Minecraft mc) {
        if (mc != null) {
            try {
                Object languageManager = mc.getClass().getMethod("getLanguageManager").invoke(mc);
                Object selected = languageManager.getClass().getMethod("getSelected").invoke(languageManager);
                if (selected != null && !selected.toString().isBlank()) {
                    return selected.toString().replace('_', '-');
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Fall back to the host locale outside a live Minecraft client.
            }
        }
        try {
            return Locale.getDefault().toLanguageTag();
        } catch (Exception e) {
            return "und";
        }
    }

    private static String projectSummary() {
        ProjectManager pm = ProjectManager.getInstance();
        Project project = pm != null ? pm.getActiveProject() : null;
        if (project == null) {
            return "No active project.";
        }
        long done = project.getTasks().stream().filter(t -> t.status() == TaskStatus.DONE).count();
        StringBuilder sb = new StringBuilder();
        sb.append(project.getName()).append(" (").append(done).append("/")
                .append(project.getTasks().size()).append(" done)\n");
        for (Task task : project.getTasks()) {
            sb.append("- [").append(task.status()).append("] ").append(task.description()).append("\n");
        }
        return sb.toString().strip();
    }

    private static String installedModsSummary() {
        try {
            var mods = ModList.get().getMods();
            List<String> entries = new ArrayList<>();
            boolean hasJei = false;
            boolean hasFtbQuests = false;
            boolean hasPatchouli = false;
            for (Object mod : mods) {
                String id = modString(mod, "getModId");
                if ("minecraft".equals(id) || "neoforge".equals(id)) {
                    continue;
                }
                if ("jei".equals(id)) {
                    hasJei = true;
                } else if ("ftbquests".equals(id)) {
                    hasFtbQuests = true;
                } else if ("patchouli".equals(id)) {
                    hasPatchouli = true;
                }
                String name = modString(mod, "getDisplayName");
                entries.add(name == null || name.isBlank() || name.equals(id) ? id : id + " (" + name + ")");
            }
            entries.sort(String.CASE_INSENSITIVE_ORDER);
            StringBuilder sb = new StringBuilder();
            sb.append("Third-party mods: ").append(entries.size()).append("\n");
            sb.append("Key integrations: JEI=").append(hasJei)
                    .append(", FTB Quests=").append(hasFtbQuests)
                    .append(", Patchouli=").append(hasPatchouli).append("\n");
            int limit = Math.min(24, entries.size());
            for (int i = 0; i < limit; i++) {
                sb.append("- ").append(entries.get(i)).append("\n");
            }
            if (entries.size() > limit) {
                sb.append("- ... ").append(entries.size() - limit).append(" more mods");
            }
            return sb.toString().strip();
        } catch (RuntimeException e) {
            return "Installed mod list unavailable outside a loaded NeoForge runtime.";
        }
    }

    private static String modString(Object mod, String methodName) {
        try {
            Object value = mod.getClass().getMethod(methodName).invoke(mod);
            return value == null ? "" : value.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }

    private static String localSummary(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) {
            return "Not in a world.";
        }
        StringBuilder sb = new StringBuilder();
        var ctx = GameDataAccess.getPlayerContext(mc);
        var world = GameDataAccess.getWorldState(mc);
        sb.append("Player: ").append(ctx.playerName()).append(", dimension: ").append(ctx.dimension())
                .append(", position: ").append(Math.round(ctx.x())).append(", ")
                .append(Math.round(ctx.y())).append(", ").append(Math.round(ctx.z())).append("\n");
        sb.append("Health: ").append(ctx.health()).append(", hunger: ").append(ctx.hunger())
                .append(", biome: ").append(world.biome()).append("\n");
        List<InventoryItem> inventory = ctx.inventory();
        // Merge by item id: the model needs totals and key materials, not raw slots
        Map<String, Integer> merged = new java.util.LinkedHashMap<>();
        for (InventoryItem item : inventory) {
            merged.merge(item.itemId(), item.count(), Integer::sum);
        }
        sb.append("Inventory non-empty slots: ").append(inventory.size())
                .append(", distinct items: ").append(merged.size()).append("\n");
        int limit = Math.min(12, merged.size());
        List<Map.Entry<String, Integer>> top = merged.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .toList();
        for (Map.Entry<String, Integer> entry : top) {
            sb.append("- ").append(entry.getKey()).append(" x").append(entry.getValue()).append("\n");
        }
        if (merged.size() > limit) {
            sb.append("- ... ").append(merged.size() - limit).append(" more item types");
        }
        return sb.toString().strip();
    }

    private static String targetBlockSummary(Minecraft mc, GuideIntent intent) {
        if (intent != GuideIntent.MACHINE_DIAGNOSIS) {
            return null;
        }
        if (mc == null || mc.player == null || mc.level == null) {
            return "No world is active.";
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return "The player is not currently looking at a block.";
        }
        var pos = hit.getBlockPos();
        var state = mc.level.getBlockState(pos);
        var blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        StringBuilder sb = new StringBuilder();
        sb.append(state.getBlock().getName().getString()).append(" (").append(blockId).append(") at ")
                .append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ())
                .append(" in ").append(mc.level.dimension().location()).append("\n");
        var blockEntity = mc.level.getBlockEntity(pos);
        if (blockEntity != null) {
            var blockEntityTypeId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
            sb.append("Block entity: ")
                    .append(blockEntityTypeId != null ? blockEntityTypeId : "unknown").append("\n");
        } else {
            sb.append("Block entity: none\n");
        }
        if (!state.getProperties().isEmpty()) {
            sb.append("State: ");
            boolean first = true;
            for (var property : state.getProperties()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(property.getName()).append("=").append(state.getValue(property));
            }
        }
        return sb.toString();
    }
}

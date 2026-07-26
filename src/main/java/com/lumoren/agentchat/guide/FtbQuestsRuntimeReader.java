package com.lumoren.agentchat.guide;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Reflection bridge for FTB Quests. This keeps FTB Quests optional while still
 * using its runtime quest/progress data when the mod is present.
 */
final class FtbQuestsRuntimeReader {
    private static final int MAX_EVIDENCE = 6;
    private static final int MAX_QUESTS_SCANNED = 240;
    private static final java.util.concurrent.atomic.AtomicBoolean BRIDGE_FAILURE_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean();

    List<GuideEvidence> query(Minecraft mc, GuideIntent intent, String userText) {
        if (mc == null || mc.player == null) {
            return List.of(unverified("FTB Quests installed",
                    "FTB Quests is installed, but no active player/world is available, so quest progress cannot be verified yet."));
        }
        try {
            Object questFile = currentClientQuestFile();
            Object teamData = teamData(questFile, mc.player);
            return readFromQuestFile(questFile, teamData, intent, userText);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Log once per session; do not spam on every guide request.
            if (BRIDGE_FAILURE_LOGGED.compareAndSet(false, true)) {
                com.lumoren.agentchat.AgentChat.LOGGER.debug(
                        "FTB Quests reflection bridge failed; falling back to quest files/resources", e);
            }
            return List.of(unverified("FTB Quests bridge unavailable",
                    "FTB Quests is installed, but its runtime quest API could not be read safely. Falling back to local quest files/resources."));
        }
    }

    List<GuideEvidence> readFromQuestFile(Object questFile, Object teamData,
                                          GuideIntent intent, String userText) {
        if (questFile == null) {
            return List.of(unverified("FTB Quests quest file unavailable",
                    "FTB Quests did not expose a client quest file yet."));
        }
        List<String> terms = LocalGuideTextKnowledgeSource.searchTerms(userText);
        List<QuestSnapshot> snapshots = snapshots(questFile, teamData);
        if (snapshots.isEmpty()) {
            return List.of(unverified("FTB Quests loaded",
                    "FTB Quests is present, but no readable quest chapters/tasks were found from runtime data."));
        }

        List<QuestSnapshot> selected = snapshots.stream()
                .filter(q -> q.visible)
                .filter(q -> matchesIntent(q, intent, terms))
                .sorted(snapshotComparator(intent))
                .limit(MAX_EVIDENCE)
                .toList();

        if (selected.isEmpty()) {
            selected = snapshots.stream()
                    .filter(q -> q.visible)
                    .sorted(snapshotComparator(intent))
                    .limit(Math.min(3, MAX_EVIDENCE))
                    .toList();
        }

        List<GuideEvidence> evidence = new ArrayList<>();
        for (QuestSnapshot snapshot : selected) {
            evidence.add(snapshot.toEvidence());
        }
        evidence.add(0, overview(snapshots));
        return evidence.size() > MAX_EVIDENCE ? evidence.subList(0, MAX_EVIDENCE) : evidence;
    }

    private static Object currentClientQuestFile() throws ReflectiveOperationException {
        Class<?> apiClass = Class.forName("dev.ftb.mods.ftbquests.api.FTBQuestsAPI");
        Object api = apiClass.getMethod("api").invoke(null);
        return invoke(api, "getQuestFile", true);
    }

    private static Object teamData(Object questFile, Object player) throws ReflectiveOperationException {
        Object optional = invoke(questFile, "getTeamData", player);
        Object value = unwrapOptional(optional);
        if (value != null) {
            return value;
        }
        Class<?> teamDataClass = Class.forName("dev.ftb.mods.ftbquests.quest.TeamData");
        return invokeStatic(teamDataClass, "get", player);
    }

    private static List<QuestSnapshot> snapshots(Object questFile, Object teamData) {
        List<QuestSnapshot> snapshots = new ArrayList<>();
        for (Object chapter : chapters(questFile)) {
            String chapterTitle = firstNonBlank(rawString(chapter, "getRawTitle"),
                    componentString(invokeQuiet(chapter, "getTitle")),
                    rawString(chapter, "getFilename"),
                    idString(chapter));
            int chapterProgress = intValue(invokeQuiet(teamData, "getRelativeProgress", chapter), -1);
            for (Object quest : iterable(invokeQuiet(chapter, "getQuests"))) {
                if (snapshots.size() >= MAX_QUESTS_SCANNED) {
                    return snapshots;
                }
                snapshots.add(readQuest(chapterTitle, chapterProgress, quest, teamData));
            }
        }
        return snapshots;
    }

    private static List<Object> chapters(Object questFile) {
        List<Object> chapters = new ArrayList<>();
        for (Object group : iterable(invokeQuiet(questFile, "getChapterGroups"))) {
            chapters.addAll(iterable(invokeQuiet(group, "getChapters")));
        }
        if (!chapters.isEmpty()) {
            return chapters;
        }
        for (Object chapter : iterable(invokeQuiet(questFile, "getAllChapters"))) {
            chapters.add(chapter);
        }
        return chapters;
    }

    private static QuestSnapshot readQuest(String chapterTitle, int chapterProgress,
                                           Object quest, Object teamData) {
        String title = firstNonBlank(rawString(quest, "getRawTitle"),
                componentString(invokeQuiet(quest, "getTitle")),
                idString(quest),
                "Unnamed quest");
        String subtitle = rawString(quest, "getRawSubtitle");
        String description = joinStrings(invokeQuiet(quest, "getRawDescription"));
        String taskSummary = taskSummary(quest, teamData);
        boolean completed = booleanValue(invokeQuiet(teamData, "isCompleted", quest), false);
        boolean started = booleanValue(invokeQuiet(teamData, "isStarted", quest), false);
        boolean visible = booleanValue(invokeQuiet(quest, "isVisible", teamData), true);
        int progress = intValue(invokeQuiet(teamData, "getRelativeProgress", quest), completed ? 100 : 0);
        String id = idString(quest);
        return new QuestSnapshot(chapterTitle, chapterProgress, title, subtitle,
                description, taskSummary, completed, started, visible, progress, id);
    }

    private static String taskSummary(Object quest, Object teamData) {
        List<String> parts = new ArrayList<>();
        for (Object task : iterable(invokeQuiet(quest, "getTasks"))) {
            if (parts.size() >= 3) {
                break;
            }
            String title = firstNonBlank(rawString(task, "getRawTitle"),
                    componentString(invokeQuiet(task, "getTitle")),
                    task.getClass().getSimpleName());
            long progress = longValue(invokeQuiet(teamData, "getProgress", task), -1L);
            if (progress >= 0) {
                parts.add(title + " progress=" + progress);
            } else {
                parts.add(title);
            }
        }
        return String.join("; ", parts);
    }

    private static boolean matchesIntent(QuestSnapshot quest, GuideIntent intent, List<String> terms) {
        if (!terms.isEmpty() && LocalGuideTextKnowledgeSource.score(quest.searchText(), terms) <= 0) {
            return false;
        }
        if (intent == GuideIntent.WHAT_NEXT) {
            return !quest.completed;
        }
        return true;
    }

    private static Comparator<QuestSnapshot> snapshotComparator(GuideIntent intent) {
        Comparator<QuestSnapshot> base = Comparator
                .comparing((QuestSnapshot q) -> q.completed)
                .thenComparing(q -> !q.started)
                .thenComparingInt(q -> -q.progress)
                .thenComparing(q -> q.chapterTitle.toLowerCase(Locale.ROOT))
                .thenComparing(q -> q.questTitle.toLowerCase(Locale.ROOT));
        if (intent == GuideIntent.WHAT_NEXT) {
            return base;
        }
        return Comparator.comparing((QuestSnapshot q) -> q.completed)
                .thenComparing(q -> q.chapterTitle.toLowerCase(Locale.ROOT))
                .thenComparing(q -> q.questTitle.toLowerCase(Locale.ROOT));
    }

    private static GuideEvidence overview(List<QuestSnapshot> snapshots) {
        long visible = snapshots.stream().filter(q -> q.visible).count();
        long completed = snapshots.stream().filter(q -> q.visible && q.completed).count();
        long started = snapshots.stream().filter(q -> q.visible && q.started && !q.completed).count();
        String summary = "Runtime quest progress: " + completed + "/" + visible
                + " visible quests completed";
        if (started > 0) {
            summary += ", " + started + " started but unfinished";
        }
        return new GuideEvidence(GuideEvidence.SourceType.FTB_QUESTS,
                "FTB Quests runtime progress", summary, "ftbquests:runtime", true);
    }

    private static GuideEvidence unverified(String title, String summary) {
        return new GuideEvidence(GuideEvidence.SourceType.FTB_QUESTS,
                title, summary, "ftbquests", false);
    }

    private record QuestSnapshot(String chapterTitle, int chapterProgress, String questTitle,
                                 String subtitle, String description, String taskSummary,
                                 boolean completed, boolean started, boolean visible,
                                 int progress, String id) {
        GuideEvidence toEvidence() {
            List<String> facts = new ArrayList<>();
            facts.add("Chapter: " + chapterTitle + (chapterProgress >= 0 ? " (" + chapterProgress + "%)" : ""));
            facts.add("Status: " + (completed ? "completed" : started ? "started" : "not started")
                    + ", progress " + progress + "%");
            if (!subtitle.isBlank()) {
                facts.add(subtitle);
            }
            if (!description.isBlank()) {
                facts.add(description);
            }
            if (!taskSummary.isBlank()) {
                facts.add("Tasks: " + taskSummary);
            }
            String summary = LocalGuideTextKnowledgeSource.summarize(String.join(" ", facts), List.of());
            return new GuideEvidence(GuideEvidence.SourceType.FTB_QUESTS,
                    "FTB: " + chapterTitle + " / " + questTitle,
                    summary, "ftbquests:" + id, true);
        }

        String searchText() {
            return String.join(" ", chapterTitle, questTitle, subtitle, description, taskSummary);
        }
    }

    private static String rawString(Object target, String method) {
        Object value = invokeQuiet(target, method);
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            return s.strip();
        }
        return joinStrings(value);
    }

    private static String componentString(Object value) {
        if (value == null) {
            return "";
        }
        Object text = invokeQuiet(value, "getString");
        return text instanceof String s ? s.strip() : "";
    }

    private static String idString(Object value) {
        String code = rawString(value, "getCodeString");
        if (!code.isBlank()) {
            return code;
        }
        Object id = invokeQuiet(value, "getId");
        return id == null ? "" : String.valueOf(id);
    }

    private static String joinStrings(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Collection<?> collection) {
            List<String> parts = new ArrayList<>();
            for (Object element : collection) {
                String text = element instanceof String s ? s : componentString(element);
                if (!text.isBlank()) {
                    parts.add(text.strip());
                }
            }
            return String.join(" ", parts);
        }
        return value instanceof String s ? s.strip() : "";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }

    private static List<Object> iterable(Object value) {
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            for (Object item : iterable) {
                if (item != null) {
                    result.add(item);
                }
            }
            return result;
        }
        return List.of();
    }

    private static Object unwrapOptional(Object value) {
        if (value instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        return value;
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean b ? b : fallback;
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private static long longValue(Object value, long fallback) {
        return value instanceof Number n ? n.longValue() : fallback;
    }

    private static Object invokeQuiet(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            return invoke(target, name, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Object invoke(Object target, String name, Object... args)
            throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), name, args);
        if (method == null) {
            Field field = findField(target.getClass(), name);
            if (field != null) {
                return field.get(target);
            }
            throw new NoSuchMethodException(target.getClass().getName() + "#" + name);
        }
        return method.invoke(target, args);
    }

    private static Object invokeStatic(Class<?> cls, String name, Object... args)
            throws ReflectiveOperationException {
        Method method = findMethod(cls, name, args);
        if (method == null) {
            throw new NoSuchMethodException(cls.getName() + "#" + name);
        }
        return method.invoke(null, args);
    }

    private static Method findMethod(Class<?> cls, String name, Object[] args) {
        for (Method method : cls.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
                continue;
            }
            if (argsMatch(method.getParameterTypes(), args)) {
                return method;
            }
        }
        return null;
    }

    private static boolean argsMatch(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            if (args[i] != null && !wrap(parameterTypes[i]).isAssignableFrom(args[i].getClass())) {
                return false;
            }
        }
        return true;
    }

    private static Field findField(Class<?> cls, String name) {
        for (Class<?> current = cls; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getField(name);
                if (Modifier.isPublic(field.getModifiers())) {
                    return field;
                }
            } catch (NoSuchFieldException ignored) {
                // Try superclass.
            }
        }
        return null;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }
}

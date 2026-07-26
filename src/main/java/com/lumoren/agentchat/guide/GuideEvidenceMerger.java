package com.lumoren.agentchat.guide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges guide evidence from all knowledge sources: stable source-priority
 * ordering, deduplication, and a hard prompt budget.
 * <ul>
 *   <li>Priority: LOCAL_GAME / JEI &gt; FTB_QUESTS &gt; PATCHOULI &gt; WEB</li>
 *   <li>Verified evidence sorts before unverified within the same source type</li>
 *   <li>Duplicates collapse on (source type, source id/url, title)</li>
 *   <li>Budget: at most {@link #MAX_EVIDENCE} entries, each summary truncated
 *       to {@link #MAX_SUMMARY_CHARS} chars, total summary chars capped at
 *       {@link #MAX_TOTAL_CHARS}</li>
 * </ul>
 */
public final class GuideEvidenceMerger {

    public static final int MAX_EVIDENCE = 6;
    public static final int MAX_SUMMARY_CHARS = 500;
    public static final int MAX_TOTAL_CHARS = 3000;

    private GuideEvidenceMerger() {
    }

    public static List<GuideEvidence> merge(List<GuideEvidence> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return List.of();
        }
        List<GuideEvidence> sorted = new ArrayList<>();
        for (GuideEvidence item : evidence) {
            if (item != null) {
                sorted.add(item);
            }
        }
        sorted.sort((a, b) -> {
            int bySource = Integer.compare(sourcePriority(a.sourceType()), sourcePriority(b.sourceType()));
            if (bySource != 0) {
                return bySource;
            }
            return Boolean.compare(!a.verified(), !b.verified());
        });

        Map<String, GuideEvidence> unique = new LinkedHashMap<>();
        for (GuideEvidence item : sorted) {
            String key = item.sourceType() + "|"
                    + nullToEmpty(item.sourceIdOrUrl()) + "|"
                    + nullToEmpty(item.title());
            unique.putIfAbsent(key, item);
        }

        List<GuideEvidence> result = new ArrayList<>();
        int totalChars = 0;
        for (GuideEvidence item : unique.values()) {
            if (result.size() >= MAX_EVIDENCE) {
                break;
            }
            GuideEvidence truncated = truncateSummary(item);
            int summaryLength = truncated.summary() == null ? 0 : truncated.summary().length();
            if (totalChars + summaryLength > MAX_TOTAL_CHARS && !result.isEmpty()) {
                break;
            }
            totalChars += summaryLength;
            result.add(truncated);
        }
        return List.copyOf(result);
    }

    static int sourcePriority(GuideEvidence.SourceType type) {
        if (type == null) {
            return 99;
        }
        return switch (type) {
            case LOCAL_GAME, JEI -> 0;
            case FTB_QUESTS -> 1;
            case PATCHOULI -> 2;
            case WEB -> 3;
        };
    }

    private static GuideEvidence truncateSummary(GuideEvidence item) {
        String summary = item.summary();
        if (summary == null || summary.length() <= MAX_SUMMARY_CHARS) {
            return item;
        }
        return new GuideEvidence(item.sourceType(), item.title(),
                summary.substring(0, MAX_SUMMARY_CHARS - 3) + "...",
                item.sourceIdOrUrl(), item.verified());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

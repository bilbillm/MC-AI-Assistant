package com.lumoren.agentchat.guide;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideEvidenceMergerTest {

    private static GuideEvidence ev(GuideEvidence.SourceType type, String title, String summary,
                                    String sourceId, boolean verified) {
        return new GuideEvidence(type, title, summary, sourceId, verified);
    }

    @Test
    void ordersBySourcePriority() {
        List<GuideEvidence> merged = GuideEvidenceMerger.merge(List.of(
                ev(GuideEvidence.SourceType.WEB, "w", "s", "w", true),
                ev(GuideEvidence.SourceType.PATCHOULI, "p", "s", "p", true),
                ev(GuideEvidence.SourceType.LOCAL_GAME, "l", "s", "l", true),
                ev(GuideEvidence.SourceType.FTB_QUESTS, "f", "s", "f", true),
                ev(GuideEvidence.SourceType.JEI, "j", "s", "j", true)));

        assertEquals(List.of(
                GuideEvidence.SourceType.LOCAL_GAME,
                GuideEvidence.SourceType.JEI,
                GuideEvidence.SourceType.FTB_QUESTS,
                GuideEvidence.SourceType.PATCHOULI,
                GuideEvidence.SourceType.WEB),
                merged.stream().map(GuideEvidence::sourceType).toList());
    }

    @Test
    void verifiedSortsBeforeUnverifiedWithinSameSource() {
        List<GuideEvidence> merged = GuideEvidenceMerger.merge(List.of(
                ev(GuideEvidence.SourceType.WEB, "bad", "s", "a", false),
                ev(GuideEvidence.SourceType.WEB, "good", "s", "b", true)));

        assertEquals("good", merged.get(0).title());
        assertEquals("bad", merged.get(1).title());
    }

    @Test
    void deduplicatesSameSourceIdAndTitle() {
        List<GuideEvidence> merged = GuideEvidenceMerger.merge(List.of(
                ev(GuideEvidence.SourceType.PATCHOULI, "Entry", "one", "pack:entry", true),
                ev(GuideEvidence.SourceType.PATCHOULI, "Entry", "two", "pack:entry", true)));

        assertEquals(1, merged.size());
        assertEquals("one", merged.get(0).summary());
    }

    @Test
    void sameTitleDifferentSourcesNotMerged() {
        List<GuideEvidence> merged = GuideEvidenceMerger.merge(List.of(
                ev(GuideEvidence.SourceType.FTB_QUESTS, "Same", "s", "ftb:1", true),
                ev(GuideEvidence.SourceType.PATCHOULI, "Same", "s", "patch:1", true)));

        assertEquals(2, merged.size());
    }

    @Test
    void enforcesMaxEntryBudget() {
        List<GuideEvidence> input = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            input.add(ev(GuideEvidence.SourceType.LOCAL_GAME, "t" + i, "s", "id" + i, true));
        }
        assertEquals(GuideEvidenceMerger.MAX_EVIDENCE, GuideEvidenceMerger.merge(input).size());
    }

    @Test
    void truncatesLongSummaries() {
        String longSummary = "x".repeat(2000);
        List<GuideEvidence> merged = GuideEvidenceMerger.merge(List.of(
                ev(GuideEvidence.SourceType.LOCAL_GAME, "t", longSummary, "id", true)));

        assertEquals(1, merged.size());
        assertTrue(merged.get(0).summary().length() <= GuideEvidenceMerger.MAX_SUMMARY_CHARS);
        assertTrue(merged.get(0).summary().endsWith("..."));
    }

    @Test
    void enforcesTotalCharacterBudget() {
        List<GuideEvidence> input = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            input.add(ev(GuideEvidence.SourceType.LOCAL_GAME, "t" + i,
                    "x".repeat(GuideEvidenceMerger.MAX_SUMMARY_CHARS), "id" + i, true));
        }
        List<GuideEvidence> merged = GuideEvidenceMerger.merge(input);

        int total = merged.stream().mapToInt(item -> item.summary().length()).sum();
        assertTrue(total <= GuideEvidenceMerger.MAX_TOTAL_CHARS);
    }

    @Test
    void nullAndEmptyInputYieldEmpty() {
        assertTrue(GuideEvidenceMerger.merge(null).isEmpty());
        assertTrue(GuideEvidenceMerger.merge(List.of()).isEmpty());
    }
}

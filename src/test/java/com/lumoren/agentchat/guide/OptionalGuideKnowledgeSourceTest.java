package com.lumoren.agentchat.guide;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionalGuideKnowledgeSourceTest {
    @Test
    void ftbUnavailableEvidenceIsExplicitAndUnverified() {
        GuideEvidence evidence = FtbQuestsKnowledgeSource.unavailableEvidence();

        assertEquals(GuideEvidence.SourceType.FTB_QUESTS, evidence.sourceType());
        assertTrue(evidence.title().contains("unavailable"));
        assertFalse(evidence.verified());
    }

    @Test
    void patchouliUnavailableEvidenceIsExplicitAndUnverified() {
        GuideEvidence evidence = PatchouliKnowledgeSource.unavailableEvidence();

        assertEquals(GuideEvidence.SourceType.PATCHOULI, evidence.sourceType());
        assertTrue(evidence.title().contains("unavailable"));
        assertFalse(evidence.verified());
    }

    @Test
    void patchouliSourceReturnsMatchingBookTextFromReaders() {
        PatchouliKnowledgeSource source = new PatchouliKnowledgeSource(() -> true, List.of(
                fakeSource(new GuideEvidence(
                        GuideEvidence.SourceType.LOCAL_GAME,
                        "Local note",
                        "not a patchouli entry",
                        "local",
                        true)),
                fakeSource(new GuideEvidence(
                        GuideEvidence.SourceType.PATCHOULI,
                        "manual/entries/power/generator.json",
                        "The basic generator accepts coal and outputs FE.",
                        "pack:manual/entries/power/generator.json",
                        true))
        ));

        List<GuideEvidence> evidence = source.query(null, GuideIntent.MACHINE_DIAGNOSIS, "generator FE");

        assertEquals(1, evidence.size());
        assertEquals(GuideEvidence.SourceType.PATCHOULI, evidence.get(0).sourceType());
        assertTrue(evidence.get(0).summary().contains("outputs FE"));
        assertTrue(evidence.get(0).verified());
    }

    @Test
    void patchouliInstalledWithoutReadableTextIsUnverified() {
        PatchouliKnowledgeSource source = new PatchouliKnowledgeSource(() -> true, List.of());

        GuideEvidence evidence = source.query(null, GuideIntent.ITEM_HELP, "mana lens").get(0);

        assertEquals(GuideEvidence.SourceType.PATCHOULI, evidence.sourceType());
        assertTrue(evidence.title().contains("no matching page text"));
        assertFalse(evidence.verified());
    }

    private static GuideKnowledgeSource fakeSource(GuideEvidence evidence) {
        return new GuideKnowledgeSource() {
            @Override
            public boolean isAvailable(net.minecraft.client.Minecraft mc) {
                return true;
            }

            @Override
            public List<GuideEvidence> query(net.minecraft.client.Minecraft mc, GuideIntent intent, String userText) {
                return List.of(evidence);
            }
        };
    }
}

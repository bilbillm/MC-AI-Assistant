package com.lumoren.agentchat.guide;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalGuideTextKnowledgeSourceTest {
    @TempDir
    Path tempDir;

    @Test
    void query_readsPatchouliJsonTextFromCommonDataPaths() throws Exception {
        Path entry = tempDir.resolve("data/example/patchouli_books/manual/en_us/entries/power/generator.json");
        Files.createDirectories(entry.getParent());
        Files.writeString(entry, """
                {
                  "name": "Basic Generator",
                  "pages": [
                    {"type": "patchouli:text", "text": "The basic generator accepts coal and outputs FE for early machines."}
                  ]
                }
                """);
        LocalGuideTextKnowledgeSource source = new LocalGuideTextKnowledgeSource(tempDir);

        var evidence = source.query(null, GuideIntent.MACHINE_DIAGNOSIS, "basic generator FE");

        assertFalse(evidence.isEmpty());
        assertEquals(GuideEvidence.SourceType.PATCHOULI, evidence.get(0).sourceType());
        assertTrue(evidence.get(0).summary().contains("basic generator"));
        assertTrue(evidence.get(0).summary().contains("FE"));
    }

    @Test
    void query_readsFtbQuestFilesFromConfig() throws Exception {
        Path quest = tempDir.resolve("config/ftbquests/quests/chapters/getting_started.snbt");
        Files.createDirectories(quest.getParent());
        Files.writeString(quest, """
                title: "Getting Started"
                subtitle: "Craft a hand crank before building the millstone"
                """);
        LocalGuideTextKnowledgeSource source = new LocalGuideTextKnowledgeSource(tempDir);

        var evidence = source.query(null, GuideIntent.WHAT_NEXT, "millstone");

        assertFalse(evidence.isEmpty());
        assertEquals(GuideEvidence.SourceType.FTB_QUESTS, evidence.get(0).sourceType());
        assertTrue(evidence.get(0).summary().contains("millstone"));
    }

    @Test
    void extractText_collectsNestedPatchouliStrings() {
        String text = LocalGuideTextKnowledgeSource.extractText(Path.of("patchouli_books/manual/en_us/entries/a.json"), """
                {
                  "name": "Steam Dynamo",
                  "pages": [
                    {"text": "Water and fuel are required before the dynamo outputs FE."}
                  ]
                }
                """);

        assertTrue(text.contains("Steam Dynamo"));
        assertTrue(text.contains("dynamo outputs FE"));
    }
}

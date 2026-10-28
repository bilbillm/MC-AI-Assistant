package com.lumoren.agentchat.ai;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemPromptContractTest {
    @Test
    void systemPrompt_instructsModpackGuideWorkflow() throws Exception {
        String prompt = loadPrompt();

        assertTrue(prompt.contains("modpack_guide"));
        assertTrue(prompt.contains("inspect_target_block"));
        assertTrue(prompt.contains("open_item_recipe"));
        assertTrue(prompt.contains("open_item_usage"));
        assertTrue(prompt.contains("FTB Quests/Patchouli"));
        assertTrue(prompt.contains("verified local data first"));
        assertTrue(prompt.contains("cannot verify"));
        assertTrue(prompt.contains("Do not fill missing facts with guesses"));
        assertTrue(prompt.contains("short and action-oriented"));
    }

    private static String loadPrompt() throws Exception {
        try (InputStream in = SystemPromptContractTest.class.getResourceAsStream(
                "/assets/agentchat/system_prompt.txt")) {
            assertNotNull(in, "system prompt resource must be packaged");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

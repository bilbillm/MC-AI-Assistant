package com.lumoren.agentchat.guide;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModpackGuideServiceTest {
    private final ModpackGuideService service = new ModpackGuideService();

    @Test
    void detectIntent_recognizesWhatNext() {
        assertEquals(GuideIntent.WHAT_NEXT, service.detectIntent("我现在下一步干什么"));
    }

    @Test
    void detectIntent_recognizesRecipePlan() {
        assertEquals(GuideIntent.RECIPE_PLAN, service.detectIntent("这个机器怎么做，需要什么材料"));
    }

    @Test
    void detectIntent_recognizesEnglishCraftAsWordOnly() {
        assertEquals(GuideIntent.RECIPE_PLAN, service.detectIntent("how do I craft create:shaft"));
        assertEquals(GuideIntent.GENERAL_CHAT, service.detectIntent("tell me about minecraft:hopper"));
    }

    @Test
    void detectIntent_recognizesMachineDiagnosis() {
        assertEquals(GuideIntent.MACHINE_DIAGNOSIS, service.detectIntent("为什么这个机器不工作"));
    }

    @Test
    void detectIntent_recognizesItemHelp() {
        assertEquals(GuideIntent.ITEM_HELP, service.detectIntent("minecraft:hopper 有什么用"));
    }

    @Test
    void shouldEnhance_enhancesGuideModeGeneralChat() {
        assertTrue(service.shouldEnhance("hello", true));
    }

    @Test
    void shouldEnhance_enhancesNaturalGuideFlowsOutsideGuideMode() {
        assertTrue(service.shouldEnhance("下一步干什么", false));
        assertTrue(service.shouldEnhance("这个怎么合成", false));
        assertTrue(service.shouldEnhance("为什么机器不工作", false));
        assertTrue(service.shouldEnhance("这个有什么用", false));
    }

    @Test
    void buildContext_preservesInjectedSourceOrderAndLabels() {
        ModpackGuideService custom = new ModpackGuideService(List.of(
                fakeSource(GuideEvidence.SourceType.LOCAL_GAME, "Local"),
                fakeSource(GuideEvidence.SourceType.FTB_QUESTS, "Quest"),
                fakeSource(GuideEvidence.SourceType.PATCHOULI, "Book"),
                fakeSource(GuideEvidence.SourceType.WEB, "Web")
        ));

        GuideContext context = custom.buildContext(null, "下一步干什么");

        assertEquals(GuideIntent.WHAT_NEXT, context.intent());
        assertEquals(4, context.evidence().size());
        assertEquals(GuideEvidence.SourceType.LOCAL_GAME, context.evidence().get(0).sourceType());
        assertEquals(GuideEvidence.SourceType.FTB_QUESTS, context.evidence().get(1).sourceType());
        assertEquals(GuideEvidence.SourceType.PATCHOULI, context.evidence().get(2).sourceType());
        assertEquals(GuideEvidence.SourceType.WEB, context.evidence().get(3).sourceType());
    }

    @Test
    void buildContext_deduplicatesRepeatedEvidence() {
        GuideKnowledgeSource duplicate = fakeSource(GuideEvidence.SourceType.PATCHOULI, "Book");
        ModpackGuideService custom = new ModpackGuideService(List.of(duplicate, duplicate));

        GuideContext context = custom.buildContext(null, "basic generator");

        assertEquals(1, context.evidence().size());
        assertEquals("Book", context.evidence().get(0).title());
    }

    @Test
    void guidePrompt_includesUnavailableEvidence() {
        ModpackGuideService custom = new ModpackGuideService(List.of(
                new GuideKnowledgeSource() {
                    @Override
                    public boolean isAvailable(net.minecraft.client.Minecraft mc) {
                        return true;
                    }

                    @Override
                    public List<GuideEvidence> query(net.minecraft.client.Minecraft mc, GuideIntent intent, String text) {
                        return List.of(new GuideEvidence(
                                GuideEvidence.SourceType.WEB,
                                "Web/wiki disabled",
                                "Web knowledge is disabled in config.",
                                "config:webKnowledgeEnabled=false",
                                true));
                    }
                }
        ));

        String prompt = custom.buildGuidePrompt(null, "普通聊天", false, false);

        assertTrue(prompt.contains("Web/wiki disabled"));
        assertTrue(prompt.contains("config:webKnowledgeEnabled=false"));
    }

    @Test
    void guidePrompt_explicitWebDisabledOverridesDefaultWebSource() {
        ModpackGuideService custom = new ModpackGuideService(List.of(new WebKnowledgeSource()));

        String prompt = custom.buildGuidePrompt(null, "minecraft:piston 怎么做", true, false);

        assertTrue(prompt.contains("Web/wiki knowledge allowed: false"));
        assertTrue(prompt.contains("Web/wiki disabled"));
        assertTrue(prompt.contains("config:webKnowledgeEnabled=false"));
    }

    @Test
    void guidePrompt_includesConcreteRecipeEvidenceForNamespacedItem() {
        ModpackGuideService custom = new ModpackGuideService(List.of(new LocalGameKnowledgeSource()));

        String prompt = custom.buildGuidePrompt(null, "minecraft:piston 怎么做", true, false);

        assertTrue(prompt.contains("Recipe evidence: minecraft:piston"));
        assertTrue(prompt.contains("minecraft_recipe_manager:minecraft:piston"));
    }

    @Test
    void guidePrompt_usesWebWhenLocalRecipeEvidenceIsInsufficient() {
        WebKnowledgeSource web = new WebKnowledgeSource(() -> true,
                (mc, query) -> "Search results:\n1. Wiki source\nA modpack recipe explanation.");
        ModpackGuideService custom = new ModpackGuideService(List.of(new LocalGameKnowledgeSource(), web));

        String prompt = custom.buildGuidePrompt(null, "minecraft:piston 怎么做", true, true);

        assertTrue(prompt.contains("Recipe evidence: minecraft:piston"));
        assertTrue(prompt.contains("Web search snippets:"));
        assertTrue(prompt.contains("Wiki source"));
    }

    @Test
    void guidePrompt_skipsWebWhenVerifiedRecipeEvidenceExists() {
        GuideKnowledgeSource verifiedRecipe = new GuideKnowledgeSource() {
            @Override
            public boolean isAvailable(net.minecraft.client.Minecraft mc) {
                return true;
            }

            @Override
            public List<GuideEvidence> query(net.minecraft.client.Minecraft mc, GuideIntent intent, String text) {
                return List.of(new GuideEvidence(GuideEvidence.SourceType.LOCAL_GAME,
                        "Recipe evidence: minecraft:piston",
                        "Verified recipe data",
                        "minecraft_recipe_manager:minecraft:piston",
                        true));
            }
        };
        WebKnowledgeSource web = new WebKnowledgeSource(() -> true, (mc, query) -> {
            throw new AssertionError("web search should not run when recipe evidence is verified");
        });
        ModpackGuideService custom = new ModpackGuideService(List.of(verifiedRecipe, web));

        String prompt = custom.buildGuidePrompt(null, "minecraft:piston 怎么做", true, true);

        assertTrue(prompt.contains("Verified recipe data"));
        assertFalse(prompt.contains("Web search snippets:"));
    }

    @Test
    void guidePrompt_includesInstalledModsSection() {
        ModpackGuideService custom = new ModpackGuideService(List.of());

        String prompt = custom.buildGuidePrompt(null, "下一步干什么", true, false);

        assertTrue(prompt.contains("Installed Mods:"));
        assertTrue(prompt.contains("Installed mod list"));
    }

    private static GuideKnowledgeSource fakeSource(GuideEvidence.SourceType type, String title) {
        return new GuideKnowledgeSource() {
            @Override
            public boolean isAvailable(net.minecraft.client.Minecraft mc) {
                return true;
            }

            @Override
            public List<GuideEvidence> query(net.minecraft.client.Minecraft mc, GuideIntent intent, String text) {
                return List.of(new GuideEvidence(type, title, "summary", title.toLowerCase(), true));
            }
        };
    }
}

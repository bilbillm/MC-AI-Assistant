package com.lumoren.agentchat.guide;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public class PatchouliKnowledgeSource implements GuideKnowledgeSource {
    private static final int MAX_RESULTS = 6;
    private final BooleanSupplier availabilityOverride;
    private final List<GuideKnowledgeSource> textSources;

    public PatchouliKnowledgeSource() {
        this(null, List.of(new ResourceGuideTextKnowledgeSource(), new LocalGuideTextKnowledgeSource()));
    }

    PatchouliKnowledgeSource(BooleanSupplier availabilityOverride, List<GuideKnowledgeSource> textSources) {
        this.availabilityOverride = availabilityOverride;
        this.textSources = textSources == null ? List.of() : List.copyOf(textSources);
    }

    @Override
    public boolean requiresMainThread() {
        return false;
    }

    @Override
    public boolean isAvailable(Minecraft mc) {
        if (availabilityOverride != null) {
            return availabilityOverride.getAsBoolean();
        }
        try {
            return ModList.get() != null && ModList.get().isLoaded("patchouli");
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public List<GuideEvidence> query(Minecraft mc, GuideIntent intent, String userText) {
        if (!isAvailable(mc)) {
            return List.of(unavailableEvidence());
        }
        List<GuideEvidence> evidence = new ArrayList<>();
        for (GuideKnowledgeSource source : textSources) {
            try {
                for (GuideEvidence item : source.query(mc, intent, userText)) {
                    if (item.sourceType() == GuideEvidence.SourceType.PATCHOULI) {
                        evidence.add(item);
                        if (evidence.size() >= MAX_RESULTS) {
                            return evidence;
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                // Keep Patchouli optional and best-effort.
            }
        }
        if (!evidence.isEmpty()) {
            return evidence;
        }
        return List.of(new GuideEvidence(GuideEvidence.SourceType.PATCHOULI,
                "Patchouli installed, no matching page text",
                "Patchouli is installed, but no readable matching book entry was found for this question. Ask for the relevant book entry or use web/wiki fallback if enabled.",
                "patchouli", false));
    }

    static GuideEvidence unavailableEvidence() {
        return new GuideEvidence(GuideEvidence.SourceType.PATCHOULI,
                "Patchouli unavailable",
                "Patchouli is not installed or not readable in this instance, so guidebook pages cannot be verified from Patchouli data.",
                "patchouli", false);
    }
}

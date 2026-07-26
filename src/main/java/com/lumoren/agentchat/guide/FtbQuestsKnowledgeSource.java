package com.lumoren.agentchat.guide;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

import java.util.List;

public class FtbQuestsKnowledgeSource implements GuideKnowledgeSource {
    private final FtbQuestsRuntimeReader runtimeReader;

    public FtbQuestsKnowledgeSource() {
        this(new FtbQuestsRuntimeReader());
    }

    FtbQuestsKnowledgeSource(FtbQuestsRuntimeReader runtimeReader) {
        this.runtimeReader = runtimeReader;
    }

    @Override
    public boolean isAvailable(Minecraft mc) {
        try {
            return ModList.get() != null && ModList.get().isLoaded("ftbquests");
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public List<GuideEvidence> query(Minecraft mc, GuideIntent intent, String userText) {
        if (!isAvailable(mc)) {
            return List.of(unavailableEvidence());
        }
        return runtimeReader.query(mc, intent, userText);
    }

    static GuideEvidence unavailableEvidence() {
        return new GuideEvidence(GuideEvidence.SourceType.FTB_QUESTS,
                "FTB Quests unavailable",
                "FTB Quests is not installed or not readable in this instance, so quest progression cannot be verified from FTB data.",
                "ftbquests", false);
    }
}

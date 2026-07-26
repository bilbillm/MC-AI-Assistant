package com.lumoren.agentchat.guide;

public record GuideEvidence(
        SourceType sourceType,
        String title,
        String summary,
        String sourceIdOrUrl,
        boolean verified
) {
    public enum SourceType {
        LOCAL_GAME,
        JEI,
        FTB_QUESTS,
        PATCHOULI,
        WEB
    }
}

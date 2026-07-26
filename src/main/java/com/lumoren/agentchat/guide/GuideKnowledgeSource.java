package com.lumoren.agentchat.guide;

import net.minecraft.client.Minecraft;

import java.util.List;

public interface GuideKnowledgeSource {
    boolean isAvailable(Minecraft mc);

    List<GuideEvidence> query(Minecraft mc, GuideIntent intent, String userText);

    /**
     * Whether {@link #query} must run on the Minecraft main thread because it
     * reads live game state (player, level, registries, JEI/FTB runtime).
     * Sources that only read files or the network should return {@code false}
     * so the guide coordinator can run them on a background executor.
     */
    default boolean requiresMainThread() {
        return true;
    }
}

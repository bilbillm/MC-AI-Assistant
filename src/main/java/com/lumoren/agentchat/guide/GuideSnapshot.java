package com.lumoren.agentchat.guide;

import java.nio.file.Path;
import java.util.List;

/**
 * Immutable snapshot of everything the guide pipeline is allowed to read off
 * the Minecraft main thread. Collected synchronously on the main thread
 * (target budget &lt; 16 ms); background stages must never touch
 * {@code Minecraft}, player, level, or registries — only this snapshot,
 * files, and the network.
 *
 * @param intent               detected guide intent
 * @param languageTag          client language tag (e.g. zh-CN)
 * @param projectSummary       active project summary text
 * @param localSummary         player/world/inventory summary text
 * @param installedModsSummary installed mod summary text
 * @param targetBlockSummary   looked-at block summary (diagnosis intents only)
 * @param mainThreadEvidence   evidence that had to be gathered on the main thread
 * @param gameDirectory        game directory for file-based sources
 * @param webEnabled           whether web knowledge is enabled for this request
 */
public record GuideSnapshot(
        GuideIntent intent,
        String languageTag,
        String projectSummary,
        String localSummary,
        String installedModsSummary,
        String targetBlockSummary,
        List<GuideEvidence> mainThreadEvidence,
        Path gameDirectory,
        boolean webEnabled
) {
    public GuideSnapshot {
        mainThreadEvidence = mainThreadEvidence == null ? List.of() : List.copyOf(mainThreadEvidence);
    }
}

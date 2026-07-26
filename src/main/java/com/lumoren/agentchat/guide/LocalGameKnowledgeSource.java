package com.lumoren.agentchat.guide;

import com.lumoren.agentchat.AgentChat;
import com.lumoren.agentchat.ai.GameDataAccess;
import com.lumoren.agentchat.model.RecipeResult;
import com.lumoren.agentchat.tools.RecipeTool;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalGameKnowledgeSource implements GuideKnowledgeSource {
    private static final Pattern ITEM_ID_PATTERN =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private final RecipeEvidenceReader recipeEvidenceReader;

    public LocalGameKnowledgeSource() {
        this(LocalGameKnowledgeSource::readRecipeEvidence);
    }

    LocalGameKnowledgeSource(RecipeEvidenceReader recipeEvidenceReader) {
        this.recipeEvidenceReader = recipeEvidenceReader;
    }

    @Override
    public boolean isAvailable(Minecraft mc) {
        return mc != null && mc.player != null;
    }

    @Override
    public List<GuideEvidence> query(Minecraft mc, GuideIntent intent, String userText) {
        List<GuideEvidence> result = new ArrayList<>();
        result.add(new GuideEvidence(GuideEvidence.SourceType.LOCAL_GAME,
                "Player/world context",
                "Inventory, position, world state, nearby blocks, and installed mod list can be queried with local tools.",
                "client runtime", true));
        result.add(new GuideEvidence(AgentChat.hasJei()
                        ? GuideEvidence.SourceType.JEI : GuideEvidence.SourceType.LOCAL_GAME,
                AgentChat.hasJei() ? "JEI runtime" : "Vanilla recipe fallback",
                AgentChat.hasJei()
                        ? "JEI is available for recipe and usage lookups."
                        : "JEI is not available; recipe lookups will use the vanilla recipe manager where possible.",
                AgentChat.hasJei() ? "jei" : "minecraft recipe manager", true));
        result.addAll(itemEvidence(mc, intent, userText));
        return result;
    }

    private List<GuideEvidence> itemEvidence(Minecraft mc, GuideIntent intent, String userText) {
        String itemId = firstItemId(userText);
        if (itemId == null) {
            return List.of();
        }
        List<GuideEvidence> evidence = new ArrayList<>();
        if (intent == GuideIntent.RECIPE_PLAN || intent == GuideIntent.ITEM_HELP) {
            LookupEvidence recipe = recipeEvidenceReader.lookup(mc, ResourceLocation.parse(itemId), true);
            evidence.add(new GuideEvidence(recipe.sourceType(),
                    "Recipe evidence: " + itemId,
                    recipe.summary(),
                    recipe.sourceId(),
                    recipe.verified()));
        }
        if (intent == GuideIntent.ITEM_HELP) {
            LookupEvidence usage = recipeEvidenceReader.lookup(mc, ResourceLocation.parse(itemId), false);
            evidence.add(new GuideEvidence(usage.sourceType(),
                    "Usage evidence: " + itemId,
                    usage.summary(),
                    usage.sourceId(),
                    usage.verified()));
        }
        return evidence;
    }

    private static String firstItemId(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = ITEM_ID_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        return matcher.find() ? matcher.group() : null;
    }

    private static LookupEvidence readRecipeEvidence(Minecraft mc, ResourceLocation itemId, boolean recipe) {
        boolean jei = AgentChat.hasJei();
        GuideEvidence.SourceType sourceType = jei ? GuideEvidence.SourceType.JEI : GuideEvidence.SourceType.LOCAL_GAME;
        String sourceId = (jei ? "jei:" : "minecraft_recipe_manager:") + itemId;
        if (jei) {
            String result = recipe ? RecipeTool.lookupRecipesJEI(itemId) : RecipeTool.lookupUsagesJEI(itemId);
            if (result != null && !result.isBlank()) {
                return new LookupEvidence(sourceType, compact(result), sourceId, true);
            }
            return new LookupEvidence(sourceType,
                    "JEI did not return verified " + (recipe ? "recipe" : "usage")
                            + " data for " + itemId + ".",
                    sourceId, false);
        }

        List<RecipeResult> results = recipe
                ? GameDataAccess.getRecipesForOutput(mc, itemId)
                : GameDataAccess.getUsagesForInput(mc, itemId);
        if (!results.isEmpty()) {
            return new LookupEvidence(sourceType,
                    compact(RecipeResult.toJsonArray(results).toString()),
                    sourceId, true);
        }
        return new LookupEvidence(sourceType,
                "No verified vanilla " + (recipe ? "recipe" : "usage")
                        + " found for " + itemId + ".",
                sourceId, false);
    }

    private static String compact(String text) {
        String value = text.strip().replaceAll("\\s+", " ");
        return value.length() > 800 ? value.substring(0, 797) + "..." : value;
    }

    interface RecipeEvidenceReader {
        LookupEvidence lookup(Minecraft mc, ResourceLocation itemId, boolean recipe);
    }

    record LookupEvidence(GuideEvidence.SourceType sourceType, String summary,
                          String sourceId, boolean verified) {}
}

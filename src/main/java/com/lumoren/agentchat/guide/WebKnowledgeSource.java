package com.lumoren.agentchat.guide;

import com.lumoren.agentchat.config.ConfigManager;
import com.google.gson.JsonObject;
import com.lumoren.agentchat.tools.WebSearchTool;

import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.function.BooleanSupplier;

public class WebKnowledgeSource implements GuideKnowledgeSource {
    private final BooleanSupplier webKnowledgeEnabled;
    private final SearchProvider searchProvider;

    public WebKnowledgeSource() {
        this(WebKnowledgeSource::configWebKnowledgeEnabled,
                (mc, query) -> {
                    JsonObject args = new JsonObject();
                    args.addProperty("query", query);
                    args.addProperty("deep", false);
                    return new WebSearchTool().execute(mc, args.toString()).content();
                });
    }

    WebKnowledgeSource(BooleanSupplier webKnowledgeEnabled, SearchProvider searchProvider) {
        this.webKnowledgeEnabled = webKnowledgeEnabled;
        this.searchProvider = searchProvider;
    }

    @Override
    public boolean requiresMainThread() {
        return false;
    }

    @Override
    public boolean isAvailable(Minecraft mc) {
        return webKnowledgeEnabled.getAsBoolean();
    }

    @Override
    public List<GuideEvidence> query(Minecraft mc, GuideIntent intent, String userText) {
        if (!isAvailable(mc)) {
            return List.of(disabledEvidence());
        }
        String query = searchQuery(intent, userText);
        try {
            String result = searchProvider.search(mc, query);
            if (result == null || result.isBlank()
                    || result.startsWith("Search unavailable")
                    || result.startsWith("No results found")) {
                return List.of(new GuideEvidence(GuideEvidence.SourceType.WEB,
                        "Web/wiki search unavailable",
                        "Web knowledge is enabled, but no usable web/wiki result was found for: " + query,
                        "web_search:" + query, false));
            }
            return List.of(new GuideEvidence(GuideEvidence.SourceType.WEB,
                    "Web/wiki search: " + query,
                    compact(result),
                    "web_search:" + query, true));
        } catch (RuntimeException e) {
            return List.of(new GuideEvidence(GuideEvidence.SourceType.WEB,
                    "Web/wiki search failed",
                    "Web knowledge is enabled, but search failed: " + e.getMessage(),
                    "web_search:" + query, false));
        }
    }

    private static boolean configWebKnowledgeEnabled() {
        try {
            return ConfigManager.getInstance().isWebKnowledgeEnabled();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static String searchQuery(GuideIntent intent, String userText) {
        String text = userText == null || userText.isBlank()
                ? "Minecraft modpack guide" : userText.strip();
        String suffix = switch (intent) {
            case RECIPE_PLAN -> " minecraft mod recipe";
            case MACHINE_DIAGNOSIS -> " minecraft modded machine not working";
            case ITEM_HELP -> " minecraft mod item usage";
            case WHAT_NEXT -> " minecraft modpack progression guide";
            default -> " minecraft";
        };
        return text + suffix;
    }

    private static String compact(String text) {
        String value = text.strip().replaceAll("\\s+", " ");
        if (value.length() <= 700) {
            return value;
        }
        return value.substring(0, 697) + "...";
    }

    public static GuideEvidence disabledEvidence() {
        return new GuideEvidence(GuideEvidence.SourceType.WEB,
                "Web/wiki disabled",
                "Web knowledge is disabled in config; only local game data should be treated as verified.",
                "config:webKnowledgeEnabled=false", true);
    }

    interface SearchProvider {
        String search(Minecraft mc, String query);
    }
}

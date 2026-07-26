package com.lumoren.agentchat.guide;

import com.lumoren.agentchat.config.ConfigManager;
import com.google.gson.JsonObject;
import com.lumoren.agentchat.tools.WebSearchTool;

import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebKnowledgeSource implements GuideKnowledgeSource {
    private static final long CACHE_TTL_MS = 5 * 60_000;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private final Map<String, CachedResult> queryCache = new ConcurrentHashMap<>();

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
        String cacheKey = intent + "|" + query;
        CachedResult cached = queryCache.get(cacheKey);
        if (cached != null && System.currentTimeMillis() - cached.timestampMillis < CACHE_TTL_MS) {
            // Cache hits keep the original source/verified semantics.
            return cached.evidence;
        }
        List<GuideEvidence> result = doQuery(mc, query);
        queryCache.put(cacheKey, new CachedResult(System.currentTimeMillis(), result));
        return result;
    }

    private List<GuideEvidence> doQuery(Minecraft mc, String query) {
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
            // v1 policy: search snippets only — pages are not fetched in the
            // automatic chain, so the content is labeled as snippets and the
            // top result URL is exposed for the card to link.
            String topUrl = firstUrl(result);
            return List.of(new GuideEvidence(GuideEvidence.SourceType.WEB,
                    "Web search snippets: " + query,
                    "Search snippets only (result pages were not fetched; treat as leads, not verified mechanics):\n"
                            + compact(result),
                    topUrl != null ? topUrl : "web_search:" + query, true));
        } catch (RuntimeException e) {
            return List.of(new GuideEvidence(GuideEvidence.SourceType.WEB,
                    "Web/wiki search failed",
                    "Web knowledge is enabled, but search failed: " + e.getMessage(),
                    "web_search:" + query, false));
        }
    }

    private static String firstUrl(String text) {
        Matcher matcher = URL_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : null;
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

    private record CachedResult(long timestampMillis, List<GuideEvidence> evidence) {
    }

    interface SearchProvider {
        String search(Minecraft mc, String query);
    }
}

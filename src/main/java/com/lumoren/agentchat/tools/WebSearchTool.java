package com.lumoren.agentchat.tools;

import com.google.gson.JsonObject;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tool for searching the web via DuckDuckGo HTML search (no API key required).
 * <p>
 * Returns the top 3-5 search results with title, URL, and snippet.
 * Supports a {@code deep} parameter that fetches and reads the actual page content
 * of the top 3 results instead of just snippets.
 */
public class WebSearchTool implements GameTool {

    private static final String NAME = "web_search";
    private static final String DESCRIPTION =
            "Search the web for Minecraft information. Returns search results as text.";
    private static final String DDG_HTML_URL = "https://html.duckduckgo.com/html/?q=";
    private static final String BING_URL = "https://www.bing.com/search?q=";

    // Patterns for extracting results from search HTML
    // Generic approach: extract all <a href> links and surrounding text
    private static final Pattern LINK_PATTERN =
            Pattern.compile("<a[^>]+href=\"(https?://[^\"]+)\"[^>]*>([^<]*(?:<[^/][^>]*>[^<]*</[^>]+>)?[^<]*)</a>",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern TEXT_BLOCK =
            Pattern.compile("<(?:p|div|span|li)[^>]*>([^<]{20,})</(?:p|div|span|li)>",
                    Pattern.CASE_INSENSITIVE);

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject queryProp = new JsonObject();
        queryProp.addProperty("type", "string");
        queryProp.addProperty("description", "The search query");
        properties.add("query", queryProp);

        JsonObject deepProp = new JsonObject();
        deepProp.addProperty("type", "boolean");
        deepProp.addProperty("description",
                "If true, fetch and read the actual content of top 3 result pages instead of just snippets. Slower but more detailed.");
        properties.add("deep", deepProp);

        parameters.add("properties", properties);

        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("query");
        parameters.add("required", required);

        return new ToolDefinition(NAME, DESCRIPTION, parameters);
    }

    @Override
    public ToolResult execute(Minecraft mc, String arguments) {
        try {
            // Parse the query from arguments JSON
            String query = extractQuery(arguments);
            if (query == null || query.isBlank()) {
                return new ToolResult(NAME, "Error: search query is required");
            }

            boolean deep = extractDeep(arguments);

            // Encode and search
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            if (deep) {
                // Deep mode: fetch actual page content from top results
                List<SearchResult> results = doSearch(client, DDG_HTML_URL + encodedQuery);
                if (results == null || results.isEmpty()) {
                    results = doSearch(client, BING_URL + encodedQuery);
                }
                if (results != null && !results.isEmpty()) {
                    return new ToolResult(NAME, deepSearch(results, query));
                }
                // Fall through to standard search if deep search produced no results
            }

            // Standard (non-deep) search
            String result = trySearch(client, DDG_HTML_URL + encodedQuery, query);
            if (result.startsWith("Search unavailable")) {
                result = trySearch(client, BING_URL + encodedQuery, query);
            }
            return new ToolResult(NAME, result);

        } catch (Exception e) {
            return new ToolResult(NAME, "Search unavailable: " + e.getMessage());
        }
    }

    /**
     * Perform the HTTP request and parse results. Returns the raw list of
     * {@link SearchResult} objects (may be empty on failure).
     */
    private List<SearchResult> doSearch(HttpClient client, String searchUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return new ArrayList<>();
            }

            return parseResults(response.body());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Try to search with a given URL, return formatted results or error message.
     */
    private String trySearch(HttpClient client, String searchUrl, String query) {
        List<SearchResult> results = doSearch(client, searchUrl);

        if (results.isEmpty()) {
            return "No results found for: " + query;
        }

        return formatResults(results);
    }

    /**
     * Deep search: for each of the top 3 results with a URL, fetch the actual
     * page content, extract the readable body text, and return combined content.
     */
    private String deepSearch(List<SearchResult> results, String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("Deep search results for: ").append(query).append("\n\n");

        int count = 0;
        for (SearchResult r : results) {
            if (r.url().isBlank()) {
                continue;
            }
            if (count >= 3) {
                break;
            }

            sb.append("=== ").append(count + 1).append(". ").append(r.title()).append(" ===\n");
            sb.append("URL: ").append(r.url()).append("\n");

            try {
                String content = WebContentFetcher.fetchAndExtract(r.url(), 10);
                sb.append(content).append("\n\n");
            } catch (Exception e) {
                sb.append("[Failed to fetch: ").append(e.getMessage()).append("]\n\n");
            }
            count++;
        }

        if (count == 0) {
            return formatResults(results); // fall back to snippets
        }

        return sb.toString().trim();
    }

    /**
     * Extract the "query" field from JSON arguments.
     */
    private String extractQuery(String arguments) {
        try {
            JsonObject args = new com.google.gson.Gson().fromJson(arguments, JsonObject.class);
            if (args != null && args.has("query")) {
                return args.get("query").getAsString();
            }
        } catch (Exception e) {
            // Fall through to return null
        }
        return null;
    }

    /**
     * Extract the "deep" field from JSON arguments. Returns false if absent.
     */
    private boolean extractDeep(String arguments) {
        try {
            JsonObject args = new com.google.gson.Gson().fromJson(arguments, JsonObject.class);
            if (args != null && args.has("deep")) {
                return args.get("deep").getAsBoolean();
            }
        } catch (Exception e) {
            // Fall through to return false
        }
        return false;
    }

    /**
     * Parse DuckDuckGo HTML response into search results.
     */
    private List<SearchResult> parseResults(String html) {
        List<SearchResult> results = new ArrayList<>();

        // Extract all external links with text content
        Matcher linkMatcher = LINK_PATTERN.matcher(html);
        while (linkMatcher.find() && results.size() < 8) {
            String href = linkMatcher.group(1);
            String text = stripHtml(linkMatcher.group(2));
            
            // Skip navigation/internal links and very short text
            if (href.contains("duckduckgo.com") || href.contains("bing.com") 
                    || href.contains("microsoft.com/bing") || text.length() < 5) {
                continue;
            }
            
            // Try to get a snippet from surrounding HTML context
            int linkEnd = linkMatcher.end();
            int snippetStart = html.indexOf("<", linkEnd);
            String snippet = "";
            if (snippetStart > linkEnd) {
                String between = html.substring(linkEnd, snippetStart).trim();
                if (between.length() > 10) {
                    snippet = stripHtml(between);
                }
            }
            
            results.add(new SearchResult(text, href, snippet));
        }

        // If no results from links, try extracting text blocks
        if (results.isEmpty()) {
            Matcher textMatcher = TEXT_BLOCK.matcher(html);
            while (textMatcher.find() && results.size() < 5) {
                String text = stripHtml(textMatcher.group(1));
                if (text.length() > 30) {
                    results.add(new SearchResult(text.substring(0, 60) + "...", "", text));
                }
            }
        }

        // If still empty, the search engine likely blocked us — return raw text preview
        if (results.isEmpty()) {
            String visible = stripHtml(html).trim();
            if (visible.length() > 50) {
                results.add(new SearchResult("Search results (text only)", "", 
                    visible.substring(0, Math.min(300, visible.length())) + "..."));
            }
        }

        return results;
    }

    /**
     * Remove HTML tags from a string.
     */
    private String stripHtml(String html) {
        String text = html.replaceAll("<[^>]*>", "");
        text = WebContentFetcher.decodeHtmlEntities(text);
        return text.trim();
    }

    /**
     * Format results as human-readable text.
     */
    private String formatResults(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("Search results:\n\n");
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append(i + 1).append(". ").append(r.title).append("\n");
            sb.append("   ").append(r.snippet).append("\n");
            sb.append("   ").append(r.url).append("\n\n");
        }
        return sb.toString().trim();
    }

    /**
     * Internal record for a parsed search result.
     */
    private record SearchResult(String title, String url, String snippet) {
    }
}

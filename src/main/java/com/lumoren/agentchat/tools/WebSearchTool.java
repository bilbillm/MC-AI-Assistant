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
 */
public class WebSearchTool implements GameTool {

    private static final String NAME = "web_search";
    private static final String DESCRIPTION =
            "Search the web for Minecraft information. Returns search results as text.";
    private static final String DDG_HTML_URL = "https://html.duckduckgo.com/html/?q=";

    // Patterns for extracting results from DuckDuckGo HTML
    private static final Pattern RESULT_LINK_PATTERN =
            Pattern.compile("<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern RESULT_SNIPPET_PATTERN =
            Pattern.compile("<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

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

            // Encode and search
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = DDG_HTML_URL + encodedQuery;

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return new ToolResult(NAME, "Search unavailable (HTTP " + response.statusCode() + ")");
            }

            String html = response.body();
            List<SearchResult> results = parseResults(html);

            if (results.isEmpty()) {
                return new ToolResult(NAME, "No results found for: " + query);
            }

            return new ToolResult(NAME, formatResults(results));

        } catch (Exception e) {
            return new ToolResult(NAME, "Search unavailable: " + e.getMessage());
        }
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
     * Parse DuckDuckGo HTML response into search results.
     */
    private List<SearchResult> parseResults(String html) {
        List<SearchResult> results = new ArrayList<>();

        // Find all result links
        Matcher linkMatcher = RESULT_LINK_PATTERN.matcher(html);
        List<String[]> links = new ArrayList<>();
        while (linkMatcher.find()) {
            String href = stripHtml(linkMatcher.group(1));
            String title = stripHtml(linkMatcher.group(2));
            links.add(new String[]{title, href});
        }

        // Find all snippets
        Matcher snippetMatcher = RESULT_SNIPPET_PATTERN.matcher(html);
        List<String> snippets = new ArrayList<>();
        while (snippetMatcher.find()) {
            snippets.add(stripHtml(snippetMatcher.group(1)));
        }

        // Pair them up (max 5)
        int count = Math.min(Math.min(links.size(), snippets.size()), 5);
        for (int i = 0; i < count; i++) {
            results.add(new SearchResult(links.get(i)[0], links.get(i)[1], snippets.get(i)));
        }

        return results;
    }

    /**
     * Remove HTML tags from a string.
     */
    private String stripHtml(String html) {
        return html.replaceAll("<[^>]*>", "").replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"").replaceAll("&#x27;", "'")
                .trim();
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

package com.lumoren.agentchat.tools;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static utility for fetching web page content and extracting readable body text.
 * <p>
 * Designed for the {@code deep} parameter of {@link WebSearchTool} — fetches
 * actual page content instead of relying solely on search snippets.
 * Uses only {@code java.net.http.HttpClient} and {@code java.util.regex} (no OkHttp, no JSoup).
 */
public final class WebContentFetcher {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Matches script / style / noscript blocks (including their inner content). */
    private static final Pattern SCRIPT_STYLE =
            Pattern.compile("<(script|style|noscript)[^>]*>.*?</\\1>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Matches any HTML tag. */
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");

    /** Matches 3+ consecutive whitespace characters. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s{3,}");

    /** Matches common HTML entities. */
    private static final Pattern HTML_ENTITY = Pattern.compile("&[a-z]+;|&#[0-9]+;");

    /** Default maximum characters after extraction. */
    private static final int MAX_CONTENT_CHARS = 3000;

    /** Pattern to find structured content containers. */
    private static final Pattern MAIN_CONTENT =
            Pattern.compile("<(main|article)\\b[^>]*>(.*?)</\\1>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern DIV_CONTENT =
            Pattern.compile("<div\\s[^>]*\\b(?:id|class)\\s*=\\s*\"content\"[^>]*>(.*?)</div>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Pattern for nav/header/footer elements (stripped as boilerplate). */
    private static final Pattern BOILERPLATE =
            Pattern.compile("<(nav|header|footer)\\b[^>]*>.*?</\\1>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Maximum response body size (5 MB) to prevent OOM on giant pages. */
    private static final long MAX_RESPONSE_BYTES = 5 * 1024 * 1024; // 5 MB

    /** Matches private/internal IP ranges for SSRF prevention. */
    private static final Pattern PRIVATE_IP = Pattern.compile(
            "^(10\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|192\\.168\\.|169\\.254\\.|0\\.0\\.0\\.0)");

    private WebContentFetcher() {
        // utility class
    }

    /**
     * Fetch a URL and extract readable body text.
     *
     * @param url            the URL to fetch
     * @param timeoutSeconds connection/read timeout in seconds
     * @return extracted body text, truncated to {@link #MAX_CONTENT_CHARS}
     */
    public static String fetchAndExtract(String url, int timeoutSeconds) {
        String html = httpGet(url, timeoutSeconds);
        return extractBody(html, MAX_CONTENT_CHARS);
    }

    /**
     * Extract main content from an HTML string.
     * <ol>
     *   <li>Find {@code <main>}, {@code <article>}, or {@code <div id="content">} /
     *       {@code <div class="content">} — extract inner HTML of the first match.</li>
     *   <li>Fall back to full HTML if no structured container found.</li>
     *   <li>Remove {@code <script>}, {@code <style>}, {@code <noscript>} blocks.</li>
     *   <li>Remove {@code <nav>}, {@code <header>}, {@code <footer>} elements.</li>
     *   <li>Strip all remaining HTML tags.</li>
     *   <li>Decode common HTML entities.</li>
     *   <li>Collapse multiple whitespace/newlines into single space.</li>
     *   <li>Truncate to {@code maxChars} with "..." suffix if cut.</li>
     * </ol>
     *
     * @param html     raw HTML string
     * @param maxChars maximum characters in the result
     * @return cleaned, readable text content
     */
    public static String extractBody(String html, int maxChars) {
        if (html == null || html.isBlank()) {
            return "";
        }

        // 1. Try to find structured content containers
        String body = html;
        Matcher m = MAIN_CONTENT.matcher(html);
        if (!m.find()) {
            m = DIV_CONTENT.matcher(html);
        }
        if (m.find()) {
            body = m.group(2);
        }
        // else fall back to full HTML

        // 2. Remove script/style/noscript blocks
        body = SCRIPT_STYLE.matcher(body).replaceAll("");

        // 3. Remove nav/header/footer boilerplate
        body = BOILERPLATE.matcher(body).replaceAll("");

        // 4. Strip all remaining HTML tags
        body = HTML_TAG.matcher(body).replaceAll("");

        // 5. Decode common HTML entities
        body = decodeHtmlEntities(body);

        // 6. Collapse multiple whitespace/newlines into single space
        body = body.replace('\n', ' ').replace('\r', ' ');
        body = WHITESPACE.matcher(body).replaceAll(" ");

        // 7. Trim
        body = body.trim();

        // 8. Truncate to maxChars with "..." suffix if cut
        if (body.length() > maxChars) {
            body = body.substring(0, maxChars) + "...";
        }

        return body;
    }

    /**
     * Decode common HTML entities in text.
     */
    public static String decodeHtmlEntities(String text) {
        return text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#x27;", "'");
    }

    /**
     * Perform an HTTP GET request and return the response body as a string.
     *
     * @param url            the URL to fetch
     * @param timeoutSeconds connection/read timeout in seconds
     * @return response body string
     * @throws RuntimeException on any HTTP or networking error
     */
    public static String httpGet(String url, int timeoutSeconds) {
        if (isInternalUrl(url)) {
            throw new RuntimeException("Internal URL blocked: " + url);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept",
                            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .GET()
                    .build();

            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());

            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (contentLength > MAX_RESPONSE_BYTES) {
                throw new RuntimeException("Response too large: " + contentLength
                        + " bytes (max " + MAX_RESPONSE_BYTES + ")");
            }

            if (response.statusCode() != 200) {
                throw new RuntimeException("HTTP " + response.statusCode());
            }

            return response.body();
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Fetch failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check whether a URL targets an internal/private network address.
     * <p>
     * Blocks localhost, loopback addresses, and RFC 1918 / link-local IP ranges
     * to prevent Server-Side Request Forgery (SSRF) attacks.
     *
     * @param urlString the URL to check
     * @return {@code true} if the URL is internal or malformed
     */
    private static boolean isInternalUrl(String urlString) {
        try {
            URI uri = new URI(urlString);
            String host = uri.getHost();
            if (host == null) return true;
            String h = host.toLowerCase();
            if (h.equals("localhost") || h.equals("127.0.0.1")
                    || h.equals("0.0.0.0") || h.equals("[::1]")) return true;
            if (PRIVATE_IP.matcher(h).find()) return true;
            return false;
        } catch (Exception e) {
            return true; // Reject malformed URLs
        }
    }
}

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
            // Redirects are followed manually so every hop is re-validated
            // against the SSRF rules below.
            .followRedirects(HttpClient.Redirect.NEVER)
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

    /** Maximum response body size (1 MB) — enforced while streaming, not just via Content-Length. */
    private static final long MAX_RESPONSE_BYTES = 1024 * 1024; // 1 MB

    /** Maximum manually-followed redirect hops. */
    private static final int MAX_REDIRECTS = 3;

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
        return fetchAndExtract(url, timeoutSeconds, "en-US,en;q=0.9");
    }

    /**
     * Fetch a URL with a preferred language and extract readable body text.
     */
    public static String fetchAndExtract(String url, int timeoutSeconds, String acceptLanguage) {
        String html = httpGet(url, timeoutSeconds, acceptLanguage);
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
        return httpGet(url, timeoutSeconds, "en-US,en;q=0.9");
    }

    /**
     * Perform an HTTP GET request with an explicit Accept-Language header.
     * <p>
     * SSRF hardening: only http/https URLs whose host publicly resolves are
     * fetched; redirects are followed manually (max {@link #MAX_REDIRECTS})
     * with each target re-validated; the body is read as a stream with a hard
     * byte cap instead of trusting Content-Length.
     */
    public static String httpGet(String url, int timeoutSeconds, String acceptLanguage) {
        String current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            URI uri = validatePublicUrl(current);
            HttpResponse<java.io.InputStream> response;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .header("User-Agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Accept",
                                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language",
                                acceptLanguage == null || acceptLanguage.isBlank()
                                        ? "en-US,en;q=0.9" : acceptLanguage)
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .GET()
                        .build();
                response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException("Fetch failed: " + e.getMessage(), e);
            }

            int status = response.statusCode();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                String location = response.headers().firstValue("Location").orElse(null);
                closeQuietly(response.body());
                if (location == null || location.isBlank()) {
                    throw new RuntimeException("Redirect without Location header (HTTP " + status + ")");
                }
                current = uri.resolve(location).toString();
                continue;
            }
            if (status != 200) {
                closeQuietly(response.body());
                throw new RuntimeException("HTTP " + status);
            }
            try (java.io.InputStream in = response.body()) {
                return readBounded(in, MAX_RESPONSE_BYTES);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed reading response body: " + e.getMessage(), e);
            }
        }
        throw new RuntimeException("Too many redirects (max " + MAX_REDIRECTS + ")");
    }

    /**
     * Reads at most {@code maxBytes} from the stream; throws when the body
     * exceeds the cap (aborts the download instead of buffering it fully).
     */
    private static String readBounded(java.io.InputStream in, long maxBytes) throws java.io.IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(chunk)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new RuntimeException("Response body exceeds " + maxBytes + " bytes; download aborted");
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void closeQuietly(java.io.InputStream in) {
        try {
            if (in != null) {
                in.close();
            }
        } catch (java.io.IOException ignored) {
            // best effort
        }
    }

    /**
     * Validates that a URL is safe to fetch: http/https scheme, a real host,
     * and DNS resolution that does not point at loopback, private, link-local,
     * unique-local-IPv6, or multicast addresses.
     *
     * @return the validated URI
     * @throws RuntimeException when the URL fails any check
     */
    static URI validatePublicUrl(String urlString) {
        URI uri;
        try {
            uri = new URI(urlString);
        } catch (Exception e) {
            throw new RuntimeException("URL blocked (malformed): " + urlString);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new RuntimeException("URL blocked (only http/https allowed): " + urlString);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new RuntimeException("URL blocked (no host): " + urlString);
        }
        if (host.equalsIgnoreCase("localhost")) {
            throw new RuntimeException("URL blocked (local host): " + urlString);
        }
        java.net.InetAddress[] addresses;
        try {
            addresses = java.net.InetAddress.getAllByName(host);
        } catch (java.net.UnknownHostException e) {
            throw new RuntimeException("URL blocked (unresolvable host): " + urlString);
        }
        for (java.net.InetAddress address : addresses) {
            if (isPrivateAddress(address)) {
                throw new RuntimeException("URL blocked (resolves to a private/internal address): " + urlString);
            }
        }
        return uri;
    }

    static boolean isPrivateAddress(java.net.InetAddress address) {
        if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                || address.isLinkLocalAddress() || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        // IPv6 unique-local addresses (fc00::/7) are not covered by the checks above
        if (address instanceof java.net.Inet6Address) {
            byte[] bytes = address.getAddress();
            return (bytes[0] & 0xfe) == 0xfc;
        }
        return false;
    }
}

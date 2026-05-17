package com.lumoren.agentchat.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WebContentFetcher} — covers {@code extractBody()}
 * and {@code httpGet()} with a local stub server.
 * <p>
 * Pure Java tests — no Minecraft runtime required.
 */
class WebContentFetcherTest {

    // ========================================================================
    // extractBody tests
    // ========================================================================

    @Test
    void testExtractMainTag() {
        String html = "<html><body><main>Hello World</main></body></html>";
        String result = WebContentFetcher.extractBody(html, 500);
        assertTrue(result.contains("Hello World"));
    }

    @Test
    void testExtractArticleTag() {
        String html = "<article>Deep Research Findings</article>";
        String result = WebContentFetcher.extractBody(html, 500);
        assertTrue(result.contains("Deep Research Findings"));
    }

    @Test
    void testNoContainerFallback() {
        String html = "<body><p>Some text</p><script>ignore me</script></body>";
        String result = WebContentFetcher.extractBody(html, 500);
        assertTrue(result.contains("Some text"));
        assertFalse(result.contains("ignore"));
    }

    @Test
    void testScriptRemoved() {
        String html = "<body><p>visible</p><script>hidden()</script></body>";
        String result = WebContentFetcher.extractBody(html, 500);
        assertTrue(result.contains("visible"));
        assertFalse(result.contains("hidden"));
    }

    @Test
    void testStyleRemoved() {
        String html = "<style>body { color: red; }</style><p>Hello</p>";
        String result = WebContentFetcher.extractBody(html, 500);
        assertTrue(result.contains("Hello"));
        assertFalse(result.contains("color"));
    }

    @Test
    void testNoscriptRemoved() {
        String html = "<noscript>JS disabled</noscript><p>content</p>";
        String result = WebContentFetcher.extractBody(html, 500);
        assertTrue(result.contains("content"));
        assertFalse(result.contains("disabled"));
    }

    @Test
    void testNavHeaderFooterStripped() {
        String html = "<nav>menu</nav><main>real content</main><footer>bye</footer>";
        String result = WebContentFetcher.extractBody(html, 500);
        assertTrue(result.contains("real content"));
        assertFalse(result.contains("menu"));
        assertFalse(result.contains("bye"));
    }

    @Test
    void testHeaderStripped() {
        String html = "<header>site header with nav</header><p>body text</p>";
        String result = WebContentFetcher.extractBody(html, 500);
        assertTrue(result.contains("body text"));
        assertFalse(result.contains("site header"));
    }

    @Test
    void testHtmlEntityDecodingAmp() {
        String html = "<main>Rock &amp; Roll</main>";
        String result = WebContentFetcher.extractBody(html, 500);
        assertTrue(result.contains("&"));
        assertFalse(result.contains("&amp;"));
    }

    @Test
    void testHtmlEntityDecodingLtGt() {
        String html = "<main>if x &lt; 10 &amp;&amp; y &gt; 5</main>";
        String result = WebContentFetcher.extractBody(html, 500);
        assertTrue(result.contains("<"));
        assertTrue(result.contains(">"));
        assertFalse(result.contains("&lt;"));
        assertFalse(result.contains("&gt;"));
    }

    @Test
    void testHtmlEntityDecodingQuot() {
        String html = "<main>She said &quot;hello&quot;</main>";
        String result = WebContentFetcher.extractBody(html, 500);
        assertTrue(result.contains("\""));
        assertFalse(result.contains("&quot;"));
    }

    @Test
    void testHtmlEntityDecodingAposNumeric() {
        String html = "<main>it&#39;s working&#x27;s fine</main>";
        String result = WebContentFetcher.extractBody(html, 500);
        assertTrue(result.contains("'"));
        assertFalse(result.contains("&#39;"));
        assertFalse(result.contains("&#x27;"));
    }

    @Test
    void testWhitespaceCollapseNewlines() {
        String html = "<main>line1\n\n\nline2</main>";
        String result = WebContentFetcher.extractBody(html, 500);
        // Three newlines → three spaces → collapsed to single space
        assertTrue(result.contains("line1 line2"));
        assertFalse(result.contains("\n"));
    }

    @Test
    void testWhitespaceCollapseMultipleSpaces() {
        String html = "<main>word1   word2     word3</main>";
        String result = WebContentFetcher.extractBody(html, 500);
        // 3+ spaces collapsed to single space → "word1 word2 word3"
        assertEquals("word1 word2 word3", result);
    }

    @Test
    void testTruncationLongContent() {
        String html = "<main>" + "A".repeat(5000) + "</main>";
        String result = WebContentFetcher.extractBody(html, 100);
        assertTrue(result.length() <= 103); // 100 + "..."
        assertTrue(result.endsWith("..."));
    }

    @Test
    void testTruncationExactLength() {
        String html = "<main>Hello World</main>";
        String result = WebContentFetcher.extractBody(html, 5);
        assertTrue(result.length() <= 8); // 5 + "..."
        assertTrue(result.endsWith("..."));
    }

    @Test
    void testNoTruncationWhenShort() {
        String html = "<main>Short text</main>";
        String result = WebContentFetcher.extractBody(html, 500);
        assertFalse(result.endsWith("..."));
    }

    @Test
    void testEmptyHtml() {
        String result = WebContentFetcher.extractBody("", 500);
        assertEquals("", result);
    }

    @Test
    void testBlankHtml() {
        String result = WebContentFetcher.extractBody("   \n  \t  ", 500);
        assertEquals("", result);
    }

    @Test
    void testNullHtml() {
        String result = WebContentFetcher.extractBody(null, 500);
        assertEquals("", result);
    }

    @Test
    void testTrimWhitespace() {
        String html = "<main>\n  Hello World  \n</main>";
        String result = WebContentFetcher.extractBody(html, 500);
        // Newlines become spaces, 3+ spaces collapsed, then trimmed
        assertEquals("Hello World", result);
    }

    // ========================================================================
    // httpGet tests — SSRF protection blocks localhost/private IPs
    // ========================================================================

    @Test
    void testHttpGetLocalhostBlocked() {
        assertThrows(RuntimeException.class, () ->
                WebContentFetcher.httpGet("http://localhost:8080/ok", 5));
    }

    @Test
    void testHttpGetLoopbackBlocked() {
        assertThrows(RuntimeException.class, () ->
                WebContentFetcher.httpGet("http://127.0.0.1:8080/ok", 5));
    }

    @Test
    void testHttpGetPrivateIpBlocked() {
        assertThrows(RuntimeException.class, () ->
                WebContentFetcher.httpGet("http://192.168.1.1/ok", 5));
    }

    @Test
    void testHttpGetInvalidUrlThrows() {
        assertThrows(RuntimeException.class, () ->
                WebContentFetcher.httpGet("not-a-valid-url-!!!", 5));
    }
}

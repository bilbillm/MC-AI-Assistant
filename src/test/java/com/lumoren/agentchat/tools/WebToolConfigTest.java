package com.lumoren.agentchat.tools;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebToolConfigTest {
    @Test
    void webSearch_respectsDisabledWebKnowledgeConfig() {
        var result = new WebSearchTool(() -> false)
                .execute(null, "{\"query\":\"minecraft piston\"}");

        assertTrue(result.content().contains("disabled"));
        assertTrue(result.content().contains("no web search was performed"));
    }

    @Test
    void readWebpage_respectsDisabledWebKnowledgeConfig() {
        var result = new ReadWebpageTool(() -> false)
                .execute(null, "{\"url\":\"https://example.com\"}");

        assertTrue(result.content().contains("disabled"));
        assertTrue(result.content().contains("no webpage was fetched"));
    }

    @Test
    void webSearch_acceptLanguagePrefersChineseForChineseClient() {
        assertEquals("zh-CN,zh;q=0.9,en;q=0.7",
                WebSearchTool.acceptLanguage("zh_cn"));
    }

    @Test
    void webSearch_acceptLanguagePrefersEnglishForEnglishClient() {
        assertEquals("en-US,en;q=0.9,zh-CN;q=0.4,zh;q=0.3",
                WebSearchTool.acceptLanguage("en_us"));
    }

    @Test
    void webSearch_acceptLanguageKeepsOtherClientLanguageWithEnglishFallback() {
        assertEquals("ja-jp,ja;q=0.9,en;q=0.7",
                WebSearchTool.acceptLanguage("ja_jp"));
    }

    @Test
    void readWebpage_passesPreferredLanguageToFetcher() {
        Locale original = Locale.getDefault();
        AtomicReference<String> capturedLanguage = new AtomicReference<>();
        try {
            Locale.setDefault(Locale.CHINA);
            ReadWebpageTool tool = new ReadWebpageTool(() -> true, (url, timeoutSeconds, acceptLanguage) -> {
                capturedLanguage.set(acceptLanguage);
                return "ok";
            });

            var result = tool.execute(null, "{\"url\":\"https://example.com\"}");

            assertEquals("ok", result.content());
            assertEquals("zh-CN,zh;q=0.9,en;q=0.7", capturedLanguage.get());
        } finally {
            Locale.setDefault(original);
        }
    }
}

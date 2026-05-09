package com.lumoren.agentchat.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {
    @Test
    void testDefaultBaseUrl() {
        assertEquals("https://api.openai.com/v1", Config.BASE_URL.get());
    }

    @Test
    void testDefaultModel() {
        assertEquals("gpt-4o-mini", Config.MODEL.get());
    }

    @Test
    void testDefaultTemperature() {
        assertEquals(0.7, Config.TEMPERATURE.get(), 0.001);
    }

    @Test
    void testDefaultMaxTokens() {
        assertEquals(1024, Config.MAX_TOKENS.get());
    }

    @Test
    void testConfigManagerSingleton() {
        ConfigManager cm1 = ConfigManager.getInstance();
        ConfigManager cm2 = ConfigManager.getInstance();
        assertSame(cm1, cm2);
    }
}

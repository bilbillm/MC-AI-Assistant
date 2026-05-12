package com.lumoren.agentchat.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AI-related exception classes.
 */
class ApiExceptionTest {

    // ========== ApiAuthException ==========

    @Test
    void apiAuthException_withMessage() {
        ApiAuthException ex = new ApiAuthException("Invalid API key");
        assertEquals("Invalid API key", ex.getMessage());
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void apiAuthException_withMessageAndCause() {
        Throwable cause = new RuntimeException("network error");
        ApiAuthException ex = new ApiAuthException("Auth failed", cause);
        assertEquals("Auth failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    // ========== ApiRateLimitException ==========

    @Test
    void apiRateLimitException_withMessage() {
        ApiRateLimitException ex = new ApiRateLimitException("Rate limited after 3 retries");
        assertEquals("Rate limited after 3 retries", ex.getMessage());
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void apiRateLimitException_withMessageAndCause() {
        Throwable cause = new RuntimeException("HTTP 429");
        ApiRateLimitException ex = new ApiRateLimitException("Rate limited", cause);
        assertEquals("Rate limited", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    // ========== ApiTimeoutException ==========

    @Test
    void apiTimeoutException_withMessage() {
        ApiTimeoutException ex = new ApiTimeoutException("Request timed out");
        assertEquals("Request timed out", ex.getMessage());
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void apiTimeoutException_withMessageAndCause() {
        Throwable cause = new java.net.http.HttpTimeoutException("Timeout");
        ApiTimeoutException ex = new ApiTimeoutException("Request timed out", cause);
        assertEquals("Request timed out", ex.getMessage());
        assertSame(cause, ex.getCause());
    }
}

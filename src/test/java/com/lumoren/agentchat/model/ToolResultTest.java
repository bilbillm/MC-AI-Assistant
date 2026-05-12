package com.lumoren.agentchat.model;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ToolResult} record.
 */
class ToolResultTest {

    @Test
    void recordStoresValues() {
        ToolResult result = new ToolResult("call_123", "{\"result\": \"found\"}");
        assertEquals("call_123", result.toolCallId());
        assertEquals("{\"result\": \"found\"}", result.content());
    }

    @Test
    void recordEquality() {
        ToolResult a = new ToolResult("call_1", "content");
        ToolResult b = new ToolResult("call_1", "content");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void recordAllowsNullContent() {
        ToolResult result = new ToolResult("call_1", null);
        assertEquals("call_1", result.toolCallId());
        assertNull(result.content());
    }

    @Test
    void toString_includesFields() {
        ToolResult result = new ToolResult("call_1", "data");
        String str = result.toString();
        assertTrue(str.contains("call_1"), "toString should contain toolCallId");
        assertTrue(str.contains("data"), "toString should contain content");
    }
}

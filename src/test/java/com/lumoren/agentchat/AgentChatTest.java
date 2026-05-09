package com.lumoren.agentchat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentChatTest {
    @Test
    void testModId() {
        assertEquals("agentchat", AgentChat.MODID);
    }

    @Test
    void testModIdNotEmpty() {
        assertNotNull(AgentChat.MODID);
        assertFalse(AgentChat.MODID.isEmpty());
    }
}

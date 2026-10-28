package com.lumoren.agentchat.tools;

import com.lumoren.agentchat.model.ActionCard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectTargetBlockToolTest {
    @Test
    void execute_withoutWorldReturnsDiagnosisCard() {
        var result = new InspectTargetBlockTool().execute(null, "{}");

        ActionCard card = ActionCard.fromToolResultContent(result.content());

        assertNotNull(card);
        assertEquals(ActionCard.Type.DIAGNOSIS, card.type());
        assertEquals("No world", card.title());
        assertTrue(card.summary().contains("not in a world"));
    }
}

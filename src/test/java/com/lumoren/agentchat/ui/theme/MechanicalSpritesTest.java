package com.lumoren.agentchat.ui.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechanicalSpritesTest {

    @Test
    void allSpritesExistOnClasspath() {
        for (var location : MechanicalSprites.ALL) {
            String path = "/assets/" + location.getNamespace() + "/" + location.getPath();
            assertNotNull(MechanicalSprites.class.getResourceAsStream(path),
                    "missing sprite resource: " + path);
        }
    }

    @Test
    void spinnerFramesAreDistinct() {
        for (int i = 0; i < MechanicalSprites.SPINNER_GEARS.length; i++) {
            for (int j = i + 1; j < MechanicalSprites.SPINNER_GEARS.length; j++) {
                assertTrue(!MechanicalSprites.SPINNER_GEARS[i].equals(MechanicalSprites.SPINNER_GEARS[j]),
                        "spinner frames must be distinct textures");
            }
        }
    }
}

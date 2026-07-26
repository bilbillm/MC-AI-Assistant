package com.lumoren.agentchat.ui.widget;

import com.lumoren.agentchat.ui.theme.MechanicalSprites;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Non-interactive mechanical panel: 9-slice plate with optional corner rivets.
 * Decoration only — it never takes focus, clicks, or text ownership.
 */
public class MechanicalPanelWidget extends AbstractWidget {

    private final ResourceLocation texture;
    private final boolean rivets;

    public MechanicalPanelWidget(int x, int y, int width, int height,
                                 ResourceLocation texture, boolean rivets) {
        super(x, y, width, height, Component.empty());
        this.texture = texture;
        this.rivets = rivets;
        this.active = false;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        MechanicalSprites.blitPanel(g, texture, getX(), getY(), getWidth(), getHeight());
        if (rivets) {
            int inset = 3;
            MechanicalSprites.blitRivet(g, getX() + inset, getY() + inset);
            MechanicalSprites.blitRivet(g, getX() + getWidth() - inset - 4, getY() + inset);
            MechanicalSprites.blitRivet(g, getX() + inset, getY() + getHeight() - inset - 4);
            MechanicalSprites.blitRivet(g, getX() + getWidth() - inset - 4,
                    getY() + getHeight() - inset - 4);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // decorative, nothing to narrate
    }
}

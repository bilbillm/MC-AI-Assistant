package com.lumoren.agentchat.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A non-interactive text label widget.
 * Renders text through the standard widget pipeline (via super.render())
 * ensuring crisp text on par with vanilla buttons.
 */
public class LabelWidget extends AbstractWidget {

    private final Font font;
    private final String text;
    private final int color;

    public LabelWidget(int x, int y, String text, int color, Font font) {
        super(x, y, font.width(text), font.lineHeight, Component.literal(text));
        this.font = font;
        this.text = text;
        this.color = color;
        this.active = false;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(font, text, getX(), getY(), color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}

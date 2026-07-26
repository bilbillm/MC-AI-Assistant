package com.lumoren.agentchat.ui.widget;

import com.lumoren.agentchat.ui.theme.GuideTheme;
import com.lumoren.agentchat.ui.theme.MechanicalSprites;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Mechanical push button: 9-slice metal plate, 1 px press edge, no gradients.
 * Variants follow the theme's interaction-metal rules.
 */
public class MechanicalButton extends AbstractWidget {

    public enum Variant {
        BRASS(MechanicalSprites.BUTTON_BRASS, GuideTheme.TEXT_ON_BRASS, GuideTheme.BRASS_HIGHLIGHT),
        COPPER(MechanicalSprites.BUTTON_COPPER, GuideTheme.TEXT_PRIMARY, GuideTheme.BRASS_HIGHLIGHT),
        DANGER(MechanicalSprites.BUTTON_DANGER, GuideTheme.TEXT_PRIMARY, GuideTheme.DANGER_RED),
        IRON(MechanicalSprites.TAB_SEGMENT, GuideTheme.TEXT_PRIMARY, GuideTheme.STEEL_EDGE);

        final ResourceLocation texture;
        final int textColor;
        final int hoverEdgeColor;

        Variant(ResourceLocation texture, int textColor, int hoverEdgeColor) {
            this.texture = texture;
            this.textColor = textColor;
            this.hoverEdgeColor = hoverEdgeColor;
        }
    }

    private final Font font;
    private final Variant variant;
    private final OnPress onPress;

    public MechanicalButton(Font font, int x, int y, int width, int height,
                            Component message, Variant variant, OnPress onPress) {
        super(x, y, width, height, message);
        this.font = font;
        this.variant = variant;
        this.onPress = onPress;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ResourceLocation texture = this.active ? variant.texture : MechanicalSprites.TAB_SEGMENT;
        MechanicalSprites.blitPanel(g, texture, getX(), getY(), getWidth(), getHeight());
        if (this.active && isHoveredOrFocused()) {
            // 1 px highlight just inside the top press edge
            g.fill(getX() + 2, getY() + 2, getX() + getWidth() - 2, getY() + 3, variant.hoverEdgeColor);
        }
        int color = this.active ? variant.textColor : GuideTheme.TEXT_DIM;
        int textWidth = font.width(getMessage());
        g.drawString(font, getMessage(),
                getX() + (getWidth() - textWidth) / 2,
                getY() + (getHeight() - 8) / 2, color);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (this.active && onPress != null) {
            onPress.onPress(this);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    @FunctionalInterface
    public interface OnPress {
        void onPress(MechanicalButton button);
    }
}

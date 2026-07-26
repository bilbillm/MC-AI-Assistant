package com.lumoren.agentchat.ui.widget;

import com.lumoren.agentchat.ui.theme.GuideTheme;
import com.lumoren.agentchat.ui.theme.MechanicalSprites;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Icon-only mechanical button. Visible icon is 12x12 but the click target is
 * at least 20x20 GUI px. Every icon button carries a tooltip that doubles as
 * its narration text.
 */
public class MechanicalIconButton extends AbstractWidget {

    private final Font font;
    private final ResourceLocation icon;
    private final int hoverEdgeColor;
    private final OnPress onPress;

    public MechanicalIconButton(Font font, int x, int y, int width, int height,
                                ResourceLocation icon, Component tooltip,
                                int hoverEdgeColor, OnPress onPress) {
        super(x, y, Math.max(width, GuideTheme.ICON_BUTTON_SIZE),
                Math.max(height, GuideTheme.ICON_BUTTON_SIZE), tooltip);
        this.font = font;
        this.icon = icon;
        this.hoverEdgeColor = hoverEdgeColor;
        this.onPress = onPress;
        setTooltip(Tooltip.create(tooltip));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        MechanicalSprites.blitPanel(g,
                this.active ? MechanicalSprites.TAB_SEGMENT : MechanicalSprites.PANEL_INSET,
                getX(), getY(), getWidth(), getHeight());
        if (this.active && isHoveredOrFocused()) {
            g.fill(getX() + 2, getY() + 2, getX() + getWidth() - 2, getY() + 3, hoverEdgeColor);
        }
        int iconX = getX() + (getWidth() - GuideTheme.ICON_SIZE) / 2;
        int iconY = getY() + (getHeight() - GuideTheme.ICON_SIZE) / 2;
        if (this.active) {
            MechanicalSprites.blitIcon(g, icon, iconX, iconY);
        } else {
            // disabled: dimmed icon substitute — inset dot
            g.fill(iconX + 3, iconY + 3, iconX + 9, iconY + 9, GuideTheme.STEEL_EDGE);
        }
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
        void onPress(MechanicalIconButton button);
    }
}

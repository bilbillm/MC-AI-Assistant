package com.lumoren.agentchat.ui.widget;

import com.lumoren.agentchat.ui.theme.GuideTheme;
import com.lumoren.agentchat.ui.theme.MechanicalSprites;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Mechanical lever toggle for boolean settings. State is shown through BOTH
 * knob position and color (verdigris = on, steel = off) — never through
 * hardcoded ON/OFF text.
 */
public class MechanicalToggleWidget extends AbstractWidget {

    private boolean value;
    private final Consumer<Boolean> onChange;

    public MechanicalToggleWidget(int x, int y, boolean initial,
                                  Component tooltip, Consumer<Boolean> onChange) {
        super(x, y, 26, 12, tooltip);
        this.value = initial;
        this.onChange = onChange;
        setTooltip(Tooltip.create(tooltip));
    }

    public boolean value() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (!this.active) {
            return;
        }
        this.value = !this.value;
        if (onChange != null) {
            onChange.accept(this.value);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // inset track
        MechanicalSprites.blitNineSlice(g, MechanicalSprites.PANEL_INSET,
                getX(), getY(), getWidth(), getHeight(), 24, 24, 8);
        // knob: square plate, verdigris when on, steel when off; position shifts
        int knobColor = value ? GuideTheme.VERDIGRIS : GuideTheme.STEEL_EDGE;
        int knobX = value ? getX() + getWidth() - 10 : getX() + 2;
        g.fill(knobX, getY() + 2, knobX + 8, getY() + getHeight() - 2, GuideTheme.IRON_EDGE_DARK);
        g.fill(knobX + 1, getY() + 3, knobX + 7, getY() + getHeight() - 3, knobColor);
        if (isHoveredOrFocused() && this.active) {
            g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + 2,
                    GuideTheme.BRASS_HIGHLIGHT);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}

package com.lumoren.agentchat.ui.widget;

import com.lumoren.agentchat.ui.theme.GuideTheme;
import com.lumoren.agentchat.ui.theme.MechanicalSprites;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Compact guide source status rail: one badge per knowledge source showing
 * availability. Status semantics: verdigris = available, steel = disabled /
 * unavailable, spinning gear = a guide request is collecting evidence.
 * Text lives inside this widget's render pass, as required.
 */
public class SourceRailWidget extends AbstractWidget {

    public enum Status {
        AVAILABLE(GuideTheme.VERDIGRIS),
        DISABLED(GuideTheme.STEEL_EDGE);

        final int dotColor;

        Status(int dotColor) {
            this.dotColor = dotColor;
        }
    }

    public record SourceStatus(String label, Status status) {
    }

    private final Font font;
    private List<SourceStatus> sources = List.of();
    private boolean loading;

    public SourceRailWidget(Font font, int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        this.font = font;
        this.active = false;
    }

    public void setSources(List<SourceStatus> sources) {
        this.sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int yCenter = getY() + getHeight() / 2;
        for (SourceStatus source : sources) {
            if (loading) {
                MechanicalSprites.blitIcon(g,
                        MechanicalSprites.spinnerFrame(System.currentTimeMillis()),
                        x, yCenter - 6);
            } else {
                g.fill(x + 2, yCenter - 4, x + 10, yCenter + 4, 0x40000000);
                g.fill(x + 3, yCenter - 3, x + 9, yCenter + 3, source.status().dotColor);
            }
            x += 12;
            g.drawString(font, source.label(), x, yCenter - 4, GuideTheme.TEXT_DIM);
            x += font.width(source.label()) + 10;
            if (x > getX() + getWidth() - 40) {
                break;
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // status rail, decorative
    }
}

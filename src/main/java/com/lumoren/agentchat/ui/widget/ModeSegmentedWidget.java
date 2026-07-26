package com.lumoren.agentchat.ui.widget;

import com.lumoren.agentchat.ui.theme.GuideTheme;
import com.lumoren.agentchat.ui.theme.MechanicalSprites;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Two-segment Chat/Guide mode control. The selected segment shows its own
 * state (copper for Chat, brass for Guide) — the label always names the
 * CURRENT mode, never the mode you would switch to.
 */
public class ModeSegmentedWidget extends AbstractWidget {

    public enum Mode {
        CHAT(GuideTheme.COPPER),
        GUIDE(GuideTheme.BRASS);

        final int accentColor;

        Mode(int accentColor) {
            this.accentColor = accentColor;
        }
    }

    private final Font font;
    private final Component chatLabel;
    private final Component guideLabel;
    private final Consumer<Mode> onChange;
    private Mode selected;

    public ModeSegmentedWidget(Font font, int x, int y, int width, int height,
                               Component chatLabel, Component guideLabel,
                               Mode initial, Consumer<Mode> onChange) {
        super(x, y, width, height, Component.empty());
        this.font = font;
        this.chatLabel = chatLabel;
        this.guideLabel = guideLabel;
        this.selected = initial;
        this.onChange = onChange;
    }

    public Mode selected() {
        return selected;
    }

    public void setSelected(Mode mode) {
        this.selected = mode;
    }

    /**
     * Maps a local x coordinate to a segment index.
     *
     * @return 0 for the left half, 1 for the right half, -1 when out of range
     */
    static int segmentAt(int localX, int width) {
        if (localX < 0 || localX >= width) {
            return -1;
        }
        return localX < width / 2 ? 0 : 1;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        int index = segmentAt((int) (mouseX - getX()), getWidth());
        Mode mode = index == 0 ? Mode.CHAT : index == 1 ? Mode.GUIDE : null;
        if (mode != null && mode != selected) {
            selected = mode;
            if (onChange != null) {
                onChange.accept(mode);
            }
        }
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int half = getWidth() / 2;
        renderSegment(g, getX(), half, chatLabel, Mode.CHAT,
                mouseX >= getX() && mouseX < getX() + half);
        renderSegment(g, getX() + half, getWidth() - half, guideLabel, Mode.GUIDE,
                mouseX >= getX() + half && mouseX < getX() + getWidth());
    }

    private void renderSegment(GuiGraphics g, int x, int width, Component label, Mode mode,
                               boolean hovered) {
        MechanicalSprites.blitPanel(g, MechanicalSprites.TAB_SEGMENT, x, getY(), width, getHeight());
        if (selected == mode) {
            // 2 px accent bar at the bottom of the selected segment
            g.fill(x + 2, getY() + getHeight() - 3, x + width - 2, getY() + getHeight() - 1,
                    mode.accentColor);
        } else if (hovered && this.active) {
            g.fill(x + 2, getY() + 2, x + width - 2, getY() + 3, GuideTheme.STEEL_EDGE);
        }
        int color = selected == mode ? GuideTheme.TEXT_PRIMARY : GuideTheme.TEXT_DIM;
        int textWidth = font.width(label);
        g.drawString(font, label, x + (width - textWidth) / 2,
                getY() + (getHeight() - 8) / 2 - (selected == mode ? 1 : 0), color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}

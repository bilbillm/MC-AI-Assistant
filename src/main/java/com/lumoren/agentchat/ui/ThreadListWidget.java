package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.i18n.I18nHelper;
import com.lumoren.agentchat.i18n.I18nKeys;
import com.lumoren.agentchat.model.ConversationThread;
import com.lumoren.agentchat.ui.theme.GuideTheme;
import com.lumoren.agentchat.ui.theme.MechanicalSprites;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Collapsible thread list sidebar / overlay drawer, mechanical skin.
 * Items are independent widgets. Collapse/expand notifies parent via
 * callback for layout rebuild. Bounds are driven by the screen's layout
 * calculator, so the same widget serves as docked panel, rail, and drawer.
 */
public class ThreadListWidget extends AbstractWidget {

    private static final int HEADER_HEIGHT = 20;
    private static final int ITEM_HEIGHT = ThreadItemWidget.getItemHeight();

    private final List<ConversationThread> threads = new ArrayList<>();
    private String currentThreadId;
    private boolean collapsed;
    private Font font;
    private final Consumer<ConversationThread> onSelect;
    private final Runnable onToggle;

    private final List<ThreadItemWidget> itemWidgets = new ArrayList<>();
    private int scroll;

    public ThreadListWidget(Consumer<ConversationThread> onSelect, Runnable onToggle) {
        super(8, 8, GuideTheme.SIDEBAR_WIDTH, 100, Component.empty());
        this.onSelect = onSelect;
        this.onToggle = onToggle;
        this.collapsed = false;
    }

    public void refresh(List<ConversationThread> threads, String currentId, Font font) {
        this.threads.clear();
        this.threads.addAll(threads);
        this.currentThreadId = currentId;
        this.font = font;
        rebuildItemWidgets();
    }

    public void setBounds(int x, int y, int width, int height) {
        setX(x);
        setY(y);
        setWidth(width);
        setHeight(height);
    }

    public boolean isCollapsed() { return collapsed; }
    public int getScroll() { return scroll; }
    public void setScroll(int s) { this.scroll = Math.max(0, s); }

    public void setCollapsed(boolean c) {
        setCollapsed(c, true);
    }

    /** Set collapsed state. If notify=true, trigger onToggle callback. */
    public void setCollapsed(boolean c, boolean notify) {
        if (this.collapsed != c) {
            this.collapsed = c;
            rebuildItemWidgets();
            if (notify && onToggle != null) onToggle.run();
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (font == null) return;
        int px = getX(), py = getY(), pw = getWidth(), ph = getHeight();

        if (collapsed) {
            renderRail(graphics, mouseX, mouseY, px, py, pw, ph);
            return;
        }

        MechanicalSprites.blitPanel(graphics, MechanicalSprites.PANEL_IRON, px, py, pw, ph);

        // Header
        String header = I18nHelper.translateToString(I18nKeys.SIDEBAR_HISTORY);
        graphics.drawString(font, header, px + 8, py + 6, GuideTheme.TEXT_DIM);
        graphics.fill(px + 4, py + HEADER_HEIGHT, px + pw - 4, py + HEADER_HEIGHT + 1,
                GuideTheme.STEEL_EDGE);
        boolean hoverToggle = mouseX >= px + pw - 18 && mouseX <= px + pw - 2
                && mouseY >= py + 2 && mouseY <= py + HEADER_HEIGHT;
        if (hoverToggle) {
            graphics.fill(px + pw - 18, py + 2, px + pw - 2, py + HEADER_HEIGHT, GuideTheme.IRON_RAISED);
        }
        MechanicalSprites.blitIcon(graphics, MechanicalSprites.ICON_CHEVRON_LEFT,
                px + pw - 15, py + 4);

        // Items
        int itemY = py + HEADER_HEIGHT + 4;
        int visibleH = ph - HEADER_HEIGHT - 6;
        int totalH = threads.size() * ITEM_HEIGHT;
        int maxScroll = Math.max(0, totalH - visibleH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        graphics.enableScissor(px, itemY, px + pw, py + ph - 2);
        int y = itemY - scroll;
        for (ThreadItemWidget w : itemWidgets) {
            w.setX(px + 4);
            w.setY(y);
            w.setWidth(pw - 8);
            w.render(graphics, mouseX, mouseY, partialTick);
            y += ITEM_HEIGHT;
        }
        graphics.disableScissor();
    }

    private void renderRail(GuiGraphics graphics, int mx, int my, int px, int py, int pw, int ph) {
        MechanicalSprites.blitNineSlice(graphics, MechanicalSprites.TAB_SEGMENT,
                px, py, pw, Math.min(ph, 48), 24, 24, 8);
        boolean hovered = mx >= px && mx <= px + pw && my >= py && my <= py + Math.min(ph, 48);
        if (hovered) {
            graphics.fill(px + 2, py + 2, px + pw - 2, py + 3, GuideTheme.BRASS_HIGHLIGHT);
        }
        MechanicalSprites.blitIcon(graphics, MechanicalSprites.ICON_CHEVRON_RIGHT,
                px + (pw - GuideTheme.ICON_SIZE) / 2, py + 8);
        MechanicalSprites.blitIcon(graphics, MechanicalSprites.ICON_HISTORY,
                px + (pw - GuideTheme.ICON_SIZE) / 2, py + 26);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !visible) return false;
        int px = getX(), py = getY(), pw = getWidth();

        if (collapsed) {
            if (mx >= px && mx <= px + pw && my >= py && my <= py + Math.min(getHeight(), 48)) {
                setCollapsed(false);
                return true;
            }
            return false;
        }
        if (mx >= px + pw - 18 && mx <= px + pw - 2
                && my >= py + 2 && my <= py + HEADER_HEIGHT) {
            setCollapsed(true);
            return true;
        }

        // Item click — compute positions from scroll, not widget state
        int itemY = py + HEADER_HEIGHT + 4;
        int itemEnd = itemY - scroll;
        for (int i = 0; i < threads.size(); i++) {
            int iy = itemEnd + i * ITEM_HEIGHT;
            if (mx >= px + 4 && mx <= px + pw - 4
                    && my >= iy && my <= iy + ITEM_HEIGHT - 2) {
                if (onSelect != null) onSelect.accept(threads.get(i));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (collapsed || !visible) return false;
        if (mx >= getX() && mx <= getX() + getWidth()
                && my >= getY() && my <= getY() + getHeight()) {
            int visibleH = getHeight() - HEADER_HEIGHT - 6;
            int totalH = threads.size() * ITEM_HEIGHT;
            int maxScroll = Math.max(0, totalH - visibleH);
            scroll -= (int) (sy * 20);
            scroll = Math.max(0, Math.min(scroll, maxScroll));
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}

    private void rebuildItemWidgets() {
        itemWidgets.clear();
        for (ConversationThread t : threads) {
            itemWidgets.add(new ThreadItemWidget(0, 0, getWidth() - 8, t,
                    t.getId().equals(currentThreadId), font));
        }
    }
}

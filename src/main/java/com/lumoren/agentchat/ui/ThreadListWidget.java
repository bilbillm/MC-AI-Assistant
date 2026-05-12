package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.model.ConversationThread;
import com.lumoren.agentchat.ui.theme.ChatColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Collapsible thread list sidebar. Items are independent widgets.
 * Collapse/expand notifies parent via callback for layout rebuild.
 */
public class ThreadListWidget extends AbstractWidget {

    private static final int PANEL_WIDTH = 180;
    private static final int TOGGLE_WIDTH = 18;
    private static final int HEADER_HEIGHT = 18;
    private static final int ITEM_HEIGHT = ThreadItemWidget.getItemHeight();

    private final List<ConversationThread> threads = new ArrayList<>();
    private String currentThreadId;
    private boolean collapsed;
    private Font font;
    private Consumer<ConversationThread> onSelect;
    private Runnable onToggle;

    private final List<ThreadItemWidget> itemWidgets = new ArrayList<>();
    private int scroll;

    public ThreadListWidget(Consumer<ConversationThread> onSelect, Runnable onToggle) {
        super(8, 8, PANEL_WIDTH, 100, Component.empty());
        this.onSelect = onSelect;
        this.onToggle = onToggle;
        this.collapsed = false;
    }

    public void refresh(List<ConversationThread> threads, String currentId, int screenHeight, Font font) {
        this.threads.clear();
        this.threads.addAll(threads);
        this.currentThreadId = currentId;
        this.font = font;
        setHeight(screenHeight - 16);
        rebuildItemWidgets();
    }

    public boolean isCollapsed() { return collapsed; }
    public int getEffectiveWidth() { return collapsed ? TOGGLE_WIDTH : PANEL_WIDTH; }
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
        int px = getX(), py = getY(), pw = PANEL_WIDTH, ph = getHeight();

        if (collapsed) {
            renderCollapsed(graphics, mouseX, mouseY, px, py);
            return;
        }

        graphics.fill(px, py, px + pw, py + ph, ChatColors.BG_SIDEBAR);
        graphics.fill(px + pw, py, px + pw + 1, py + ph, ChatColors.SEPARATOR_SIDEBAR);

        // Header
        graphics.drawString(font, Component.literal("对话历史"), px + 6, py + 3, ChatColors.TEXT_DIM);
        boolean hoverToggle = mouseX >= px + pw - TOGGLE_WIDTH && mouseX <= px + pw
                && mouseY >= py && mouseY <= py + HEADER_HEIGHT;
        graphics.drawString(font, "<", px + pw - 14, py + 3,
                hoverToggle ? ChatColors.TEXT_PRIMARY : ChatColors.TEXT_DIM);

        // Items
        int itemY = py + HEADER_HEIGHT + 2;
        int visibleH = ph - HEADER_HEIGHT - 2;
        int totalH = threads.size() * ITEM_HEIGHT;
        int maxScroll = Math.max(0, totalH - visibleH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        graphics.enableScissor(px, itemY, px + pw, py + ph);
        int y = itemY - scroll;
        for (ThreadItemWidget w : itemWidgets) {
            w.setX(px + 2);
            w.setY(y);
            w.setWidth(pw - 4);
            w.render(graphics, mouseX, mouseY, partialTick);
            y += ITEM_HEIGHT;
        }
        graphics.disableScissor();
    }

    private void renderCollapsed(GuiGraphics graphics, int mx, int my, int px, int py) {
        graphics.fill(px, py, px + TOGGLE_WIDTH, py + 40, ChatColors.BG_SIDEBAR);
        graphics.fill(px + TOGGLE_WIDTH - 1, py, px + TOGGLE_WIDTH, py + 40, ChatColors.SEPARATOR_SIDEBAR);
        int color = (mx >= px && mx <= px + TOGGLE_WIDTH && my >= py && my <= py + 40)
                ? ChatColors.TEXT_PRIMARY : ChatColors.TEXT_DIM;
        graphics.drawString(font, ">", px + 4, py + 12, color);
        graphics.drawString(font, ">", px + 4, py + 24, color);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        int px = getX(), py = getY();

        if (collapsed) {
            if (mx >= px && mx <= px + TOGGLE_WIDTH && my >= py && my <= py + 40) {
                setCollapsed(false);
                return true;
            }
            return false;
        }
        if (mx >= px + PANEL_WIDTH - TOGGLE_WIDTH && mx <= px + PANEL_WIDTH
                && my >= py && my <= py + HEADER_HEIGHT) {
            setCollapsed(true);
            return true;
        }

        // Item click — compute positions from scroll, not widget state
        int itemY = py + HEADER_HEIGHT + 2;
        int itemEnd = itemY - scroll;
        for (int i = 0; i < threads.size(); i++) {
            int iy = itemEnd + i * ITEM_HEIGHT;
            if (mx >= px + 2 && mx <= px + PANEL_WIDTH - 2
                    && my >= iy && my <= iy + ITEM_HEIGHT - 2) {
                if (onSelect != null) onSelect.accept(threads.get(i));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (collapsed) return false;
        if (mx >= getX() && mx <= getX() + PANEL_WIDTH
                && my >= getY() && my <= getY() + getHeight()) {
            int visibleH = getHeight() - HEADER_HEIGHT - 2;
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
            itemWidgets.add(new ThreadItemWidget(0, 0, PANEL_WIDTH - 4, t,
                    t.getId().equals(currentThreadId), font));
        }
    }
}

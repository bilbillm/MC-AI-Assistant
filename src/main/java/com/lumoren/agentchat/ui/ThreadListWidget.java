package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.i18n.I18nHelper;
import com.lumoren.agentchat.model.ConversationThread;
import com.lumoren.agentchat.persistence.ConversationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Scrollable thread list sidebar panel. Rendered through widget pipeline for crisp text.
 * Supports collapse/expand via toggle button.
 */
public class ThreadListWidget extends AbstractWidget {

    private static final int PANEL_WIDTH = 180;
    private static final int ITEM_HEIGHT = 28;
    private static final int TOGGLE_SIZE = 14;

    private List<ConversationThread> threads = List.of();
    private String currentThreadId;
    private int scroll;
    private boolean collapsed;
    private Font font;
    private Consumer<ConversationThread> onSelect;

    public ThreadListWidget(Consumer<ConversationThread> onSelect) {
        super(8, 8, PANEL_WIDTH, 100, Component.empty());
        this.onSelect = onSelect;
        this.collapsed = false;
        this.active = false;
    }

    public void refresh(List<ConversationThread> threads, String currentId, int screenHeight, Font font) {
        this.threads = threads;
        this.currentThreadId = currentId;
        this.font = font;
        setHeight(screenHeight - 16);
    }

    public void setCollapsed(boolean c) { this.collapsed = c; }
    public boolean isCollapsed() { return collapsed; }
    public int getEffectiveWidth() { return collapsed ? TOGGLE_SIZE + 4 : PANEL_WIDTH; }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (font == null) return;
        int px = getX(), py = getY(), pw = getWidth(), ph = getHeight();

        if (collapsed) {
            // Small toggle tab
            graphics.fill(px, py, px + TOGGLE_SIZE + 4, py + 40, 0xCC1F2937);
            graphics.fill(px + TOGGLE_SIZE + 3, py, px + TOGGLE_SIZE + 4, py + 40, 0xFF374151);
            String arrow = isHoveringToggle(mouseX, mouseY) ? "▶" : "▶";
            // Note: vanilla MC 1.21.1 may not render ▶ well; use ">" instead
            graphics.drawString(font, ">", px + 3, py + 12, 0xFF9CA3AF);
            graphics.drawString(font, ">", px + 1, py + 24, 0xFF9CA3AF);
            return;
        }

        // Panel background
        graphics.fill(px, py, px + pw, py + ph, 0xCC1F2937);
        graphics.fill(px + pw, py, px + pw + 1, py + ph, 0xFF374151);

        // Header row: title + collapse button
        graphics.drawString(font, Component.literal("对话历史"), px + 6, py + 4, 0xFF9CA3AF);
        String toggleLabel = isHoveringToggle(mouseX, mouseY) ? "◀" : "◀";
        graphics.drawString(font, "<", px + pw - 16, py + 4, 0xFF9CA3AF);

        // Thread items
        int itemY = py + 22;
        int visibleH = ph - 24;
        int totalH = threads.size() * ITEM_HEIGHT;
        int maxScroll = Math.max(0, totalH - visibleH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        graphics.enableScissor(px, itemY, px + pw, py + ph);
        int y = itemY - scroll;
        for (ConversationThread t : threads) {
            boolean isCurrent = t.getId().equals(currentThreadId);
            int bg = 0;
            if (isCurrent) bg = 0xFF374151;
            else if (mouseX >= px && mouseX <= px + pw && mouseY >= y && mouseY <= y + ITEM_HEIGHT - 2)
                bg = 0xFF4B5563;
            if (bg != 0) graphics.fill(px + 2, y, px + pw - 2, y + ITEM_HEIGHT - 2, bg);

            String name = t.getName();
            if (font.width(name) > pw - 20) name = font.plainSubstrByWidth(name, pw - 24) + "..";
            graphics.drawString(font, name, px + 8, y + 2, isCurrent ? 0xFFFFFFFF : 0xFFD1D5DB);
            graphics.drawString(font, t.messageCount() + " msgs", px + 8, y + 15, 0xFF6B7280);
            y += ITEM_HEIGHT;
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        int px = getX(), py = getY(), pw = getWidth();

        // Toggle button
        if (collapsed) {
            if (mx >= px && mx <= px + TOGGLE_SIZE + 4 && my >= py && my <= py + 40) {
                collapsed = false;
                return true;
            }
            return false;
        }
        if (mx >= px + pw - 16 && mx <= px + pw && my >= py + 4 && my <= py + 16) {
            collapsed = true;
            return true;
        }

        // Thread items
        if (mx >= px && mx <= px + pw && my >= py + 22 && my <= py + getHeight()) {
            int y = py + 22 - scroll;
            for (ConversationThread t : threads) {
                if (my >= y && my <= y + ITEM_HEIGHT - 2) {
                    if (onSelect != null) onSelect.accept(t);
                    return true;
                }
                y += ITEM_HEIGHT;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (collapsed) return false;
        int px = getX(), py = getY();
        if (mx >= px && mx <= px + getWidth() && my >= py && my <= py + getHeight()) {
            int visibleH = getHeight() - 24;
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

    private boolean isHoveringToggle(int mx, int my) {
        int px = getX(), py = getY(), pw = getWidth();
        if (collapsed) {
            return mx >= px && mx <= px + TOGGLE_SIZE + 4 && my >= py && my <= py + 40;
        }
        return mx >= px + pw - 16 && mx <= px + pw && my >= py + 4 && my <= py + 16;
    }
}

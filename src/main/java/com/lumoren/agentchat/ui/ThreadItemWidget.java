package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.model.ConversationThread;
import com.lumoren.agentchat.ui.theme.ChatColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A single thread item in the conversation sidebar.
 * Renders through widget pipeline, self-handles hover highlight.
 */
public class ThreadItemWidget extends AbstractWidget {

    private static final int ITEM_HEIGHT = 28;

    private final ConversationThread thread;
    private final boolean isCurrent;
    private final Font font;

    public ThreadItemWidget(int x, int y, int width, ConversationThread thread,
                            boolean isCurrent, Font font) {
        super(x, y, width, ITEM_HEIGHT, Component.empty());
        this.thread = thread;
        this.isCurrent = isCurrent;
        this.font = font;
        this.active = false;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int px = getX(), py = getY(), pw = getWidth();
        boolean hovered = isMouseOver(mouseX, mouseY);

        // Background
        if (isCurrent) {
            graphics.fill(px, py, px + pw, py + ITEM_HEIGHT - 2, ChatColors.THREAD_ITEM_HOVER);
        } else if (hovered) {
            graphics.fill(px, py, px + pw, py + ITEM_HEIGHT - 2, 0xCC_334155);
            // Left accent bar on hover
            graphics.fill(px, py + 2, px + 2, py + ITEM_HEIGHT - 4, ChatColors.TEXT_ACCENT);
        }

        // Name (truncate if too long)
        String name = thread.getName();
        if (font.width(name) > pw - 14) {
            name = font.plainSubstrByWidth(name, pw - 18) + "..";
        }
        int textColor = isCurrent ? ChatColors.TEXT_PRIMARY : ChatColors.TEXT_SECONDARY;
        graphics.drawString(font, name, px + 6, py + 2, textColor);

        // Message count
        graphics.drawString(font, thread.messageCount() + " msgs",
                px + 6, py + 15, ChatColors.TEXT_DIM);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}

    public ConversationThread getThread() { return thread; }
    public static int getItemHeight() { return ITEM_HEIGHT; }
}

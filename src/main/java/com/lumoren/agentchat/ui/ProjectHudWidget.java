package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.i18n.I18nHelper;
import com.lumoren.agentchat.i18n.I18nKeys;
import com.lumoren.agentchat.model.Project;
import com.lumoren.agentchat.model.Task;
import com.lumoren.agentchat.model.TaskStatus;
import com.lumoren.agentchat.persistence.ProjectManager;
import com.lumoren.agentchat.ui.theme.ChatColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.List;

/**
 * Draggable HUD widget displaying the active project's name, progress, and
 * task list. Has two modes:
 * <ul>
 *   <li><b>Compact</b> — project name, progress fraction, and current step</li>
 *   <li><b>Expanded</b> — full task list with status icons and scrolling</li>
 * </ul>
 *
 * <p>All rendering goes through {@link #renderWidget(GuiGraphics, int, int, float)},
 * never via direct {@code drawString()} outside this class.
 */
public class ProjectHudWidget extends AbstractWidget {

    // ── Layout constants ──
    private static final int COMPACT_WIDTH = 220;
    private static final int COMPACT_HEIGHT = 28;
    private static final int EXPANDED_WIDTH = 260;
    private static final int EXPANDED_HEADER_HEIGHT = 14;
    private static final int TASK_LINE_HEIGHT = 12;
    private static final int TASK_INDENT = 4;
    private static final int MAX_VISIBLE_TASKS = 8;
    private static final int TEXT_PAD = 4;
    private static final int COLLAPSE_BTN_SIZE = 12;

    // ── Persistent position (survives widget re-creation) ──
    private static int persistentOffsetX = 200;
    private static int persistentOffsetY = 10;

    // ── Mutable state per instance ──
    private boolean expanded;
    private int scroll;
    private boolean dragging;
    private boolean wasDrag;
    private int dragOffX, dragOffY;

    // ── Cached font reference ──
    private Font font;

    public ProjectHudWidget() {
        super(persistentOffsetX, persistentOffsetY, COMPACT_WIDTH, COMPACT_HEIGHT, Component.empty());
        this.expanded = false;
        this.scroll = 0;
        this.dragging = false;
        this.wasDrag = false;
    }

    // ========================================================================
    // Rendering
    // ========================================================================

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (font == null) {
            font = Minecraft.getInstance().font;
        }

        Project project = (ProjectManager.getInstance() != null)
                ? ProjectManager.getInstance().getActiveProject()
                : null;

        setX(persistentOffsetX);
        setY(persistentOffsetY);

        if (project == null) {
            setWidth(COMPACT_WIDTH);
            setHeight(COMPACT_HEIGHT);
            renderNoProject(graphics);
            return;
        }

        if (expanded) {
            List<Task> tasks = project.getTasks();
            int visibleTasks = Math.min(tasks.size(), MAX_VISIBLE_TASKS);
            setWidth(EXPANDED_WIDTH);
            setHeight(EXPANDED_HEADER_HEIGHT + 1 + visibleTasks * TASK_LINE_HEIGHT + 2);
            renderExpanded(graphics, project, mouseX, mouseY);
        } else {
            setWidth(COMPACT_WIDTH);
            setHeight(COMPACT_HEIGHT);
            renderCompact(graphics, project, mouseX, mouseY);
        }
    }

    /**
     * Renders the "empty" state when no active project exists.
     */
    private void renderNoProject(GuiGraphics graphics) {
        int px = getX(), py = getY(), pw = getWidth(), ph = getHeight();
        fillBackground(graphics, px, py, pw, ph);

        String msg = I18nHelper.translateToString(I18nKeys.PROJECT_HUD_NO_PROJECT);
        int cx = px + (pw - font.width(msg)) / 2;
        int cy = py + (ph - font.lineHeight) / 2;
        graphics.drawString(font, msg, cx, cy, ChatColors.TEXT_DIM);
    }

    // ── Compact mode ──

    /**
     * Shows project name + progress on line 1, current step on line 2.
     */
    private void renderCompact(GuiGraphics graphics, Project project, int mx, int my) {
        int px = getX(), py = getY(), pw = getWidth(), ph = getHeight();
        fillBackground(graphics, px, py, pw, ph);

        // Line 1: name + progress
        List<Task> tasks = project.getTasks();
        long done = tasks.stream().filter(t -> t.status() == TaskStatus.DONE).count();
        long total = tasks.size();
        String progress = I18nHelper.translateToString(I18nKeys.PROJECT_HUD_PROGRESS,
                project.getName(), done, total);
        graphics.drawString(font, progress, px + TEXT_PAD, py + 3, ChatColors.TEXT_PRIMARY);

        // Line 2: current step (first non-DONE task) or "all done"
        Task current = tasks.stream()
                .filter(t -> t.status() != TaskStatus.DONE)
                .findFirst()
                .orElse(null);

        if (current != null) {
            String step = current.description();
            int maxW = pw - TEXT_PAD * 2 - 4;
            if (font.width(step) > maxW) {
                step = font.plainSubstrByWidth(step, maxW - font.width("..")) + "..";
            }
            graphics.drawString(font, step, px + TEXT_PAD, py + 15, ChatColors.TEXT_ACCENT);
        } else if (total > 0) {
            graphics.drawString(font, "\u2713 All done!", px + TEXT_PAD, py + 15, ChatColors.TASK_DONE);
        } else {
            graphics.drawString(font, "No tasks yet", px + TEXT_PAD, py + 15, ChatColors.TEXT_DIM);
        }
    }

    // ── Expanded mode ──

    /**
     * Shows the full task list with status icons and colours.
     * DONE → green strikethrough, BLOCKED → red, current → bold blue,
     * PENDING → normal text.
     */
    private void renderExpanded(GuiGraphics graphics, Project project, int mx, int my) {
        int px = getX(), py = getY(), pw = getWidth(), ph = getHeight();
        fillBackground(graphics, px, py, pw, ph);

        List<Task> tasks = project.getTasks();
        long done = tasks.stream().filter(t -> t.status() == TaskStatus.DONE).count();
        long total = tasks.size();

        // ── Header ──
        String header = I18nHelper.translateToString(I18nKeys.PROJECT_HUD_PROGRESS,
                project.getName(), done, total);
        graphics.drawString(font, header, px + TEXT_PAD, py + 2, ChatColors.TEXT_PRIMARY);

        // Collapse button
        int btnX = px + pw - COLLAPSE_BTN_SIZE - 2;
        int btnY = py + 1;
        boolean hoverCollapse = mx >= btnX && mx <= btnX + COLLAPSE_BTN_SIZE
                && my >= btnY && my <= btnY + COLLAPSE_BTN_SIZE;
        graphics.drawString(font, "\u2212", btnX + 2, btnY,
                hoverCollapse ? ChatColors.TEXT_PRIMARY : ChatColors.TEXT_DIM);

        int separatorY = py + EXPANDED_HEADER_HEIGHT;
        graphics.fill(px + TEXT_PAD, separatorY, px + pw - TEXT_PAD, separatorY + 1, ChatColors.HUD_BORDER);

        // ── Task list with scissor ──
        int listY = separatorY + 2;
        int listH = ph - (listY - py);
        int totalListH = tasks.size() * TASK_LINE_HEIGHT;
        int maxScroll = Math.max(0, totalListH - listH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        graphics.enableScissor(px + 1, listY, px + pw - 1, py + ph - 1);

        int yOff = listY - scroll;
        Task firstActive = tasks.stream()
                .filter(t -> t.status() != TaskStatus.DONE)
                .findFirst()
                .orElse(null);

        for (Task task : tasks) {
            if (yOff + TASK_LINE_HEIGHT < listY && yOff < py + ph) {
                // visible in scissor region
            }
            renderTaskLine(graphics, task, px + TEXT_PAD, yOff, pw - TEXT_PAD * 2,
                    task == firstActive);
            yOff += TASK_LINE_HEIGHT;
        }

        graphics.disableScissor();

        // ── Scroll indicator ──
        if (maxScroll > 0) {
            float progress = (float) scroll / maxScroll;
            int indicatorH = Math.max(8, listH * listH / totalListH);
            int indicatorY = listY + (int) (progress * (listH - indicatorH));
            int indicatorX = px + pw - 3;
            graphics.fill(indicatorX, indicatorY, indicatorX + 2, indicatorY + indicatorH, ChatColors.SCROLLBAR_THUMB);
        }
    }

    /**
     * Renders a single task line with status icon, description, and colour.
     */
    private void renderTaskLine(GuiGraphics graphics, Task task, int x, int y, int maxW, boolean isActive) {
        String icon;
        int color;
        boolean strikethrough = false;

        switch (task.status()) {
            case DONE -> {
                icon = "\u2713 ";  // check mark
                color = ChatColors.TASK_DONE;
                strikethrough = true;
            }
            case BLOCKED -> {
                icon = "\u26A0 ";  // warning
                color = ChatColors.TASK_BLOCKED;
            }
            case IN_PROGRESS -> {
                icon = "\u25B6 ";  // play
                color = isActive ? ChatColors.TASK_ACTIVE : ChatColors.TEXT_PRIMARY;
            }
            default -> { // PENDING
                icon = "\u25CB ";  // circle
                color = isActive ? ChatColors.TASK_ACTIVE : ChatColors.TEXT_PRIMARY;
            }
        }

        String desc = task.description();
        int iconW = font.width(icon);
        int maxDescW = maxW - iconW;
        if (font.width(desc) > maxDescW) {
            desc = font.plainSubstrByWidth(desc, maxDescW - font.width("..")) + "..";
        }

        MutableComponent line = Component.literal(icon + desc);

        if (strikethrough) {
            line.withStyle(Style.EMPTY
                    .withColor(TextColor.fromRgb(color))
                    .withStrikethrough(true));
        } else if (isActive) {
            line.withStyle(Style.EMPTY
                    .withColor(TextColor.fromRgb(ChatColors.TASK_ACTIVE))
                    .withBold(true));
        } else {
            line.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
        }

        // Use default colour = color so non-styled text substrings render correctly
        graphics.drawString(font, line, x, y, color);
    }

    // ── Common helpers ──

    /**
     * Fills the widget background and draws a 1-px border.
     */
    private void fillBackground(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, ChatColors.HUD_BG);
        // Top
        graphics.fill(x, y, x + w, y + 1, ChatColors.HUD_BORDER);
        // Bottom
        graphics.fill(x, y + h - 1, x + w, y + h, ChatColors.HUD_BORDER);
        // Left
        graphics.fill(x, y, x + 1, y + h, ChatColors.HUD_BORDER);
        // Right
        graphics.fill(x + w - 1, y, x + w, y + h, ChatColors.HUD_BORDER);
    }

    // ========================================================================
    // Mouse interaction
    // ========================================================================

    /**
     * Records the start of a potential drag. Does NOT toggle expand here —
     * that happens on release only if no drag occurred.
     */
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !isMouseOver(mx, my)) return false;

        // Check collapse button hit in expanded mode
        if (expanded) {
            int px = getX(), py = getY(), pw = getWidth();
            int btnX = px + pw - COLLAPSE_BTN_SIZE - 2;
            int btnY = py + 1;
            if (mx >= btnX && mx <= btnX + COLLAPSE_BTN_SIZE
                    && my >= btnY && my <= btnY + COLLAPSE_BTN_SIZE) {
                expanded = false;
                scroll = 0;
                return true;
            }
        }

        dragging = true;
        wasDrag = false;
        dragOffX = (int) (mx - persistentOffsetX);
        dragOffY = (int) (my - persistentOffsetY);
        return true;
    }

    /**
     * Updates widget position while dragging.
     */
    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging || button != 0) return false;
        wasDrag = true;

        int newX = (int) mx - dragOffX;
        int newY = (int) my - dragOffY;

        // Clamp to screen bounds (optional safety)
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        persistentOffsetX = Math.max(0, Math.min(newX, screenW - getWidth()));
        persistentOffsetY = Math.max(0, Math.min(newY, screenH - getHeight()));

        return true;
    }

    /**
     * On release: toggles expand/collapse if no drag happened.
     */
    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button != 0) return false;
        if (dragging) {
            dragging = false;
            if (!wasDrag && isMouseOver(mx, my)) {
                expanded = !expanded;
                scroll = 0;
            }
            return true;
        }
        return false;
    }

    /**
     * Scrolls the task list in expanded mode.
     */
    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (!expanded || !isMouseOver(mx, my)) return false;

        Project project = (ProjectManager.getInstance() != null)
                ? ProjectManager.getInstance().getActiveProject()
                : null;
        if (project == null) return false;

        int listH = getHeight() - EXPANDED_HEADER_HEIGHT - 4;
        int totalH = project.getTasks().size() * TASK_LINE_HEIGHT;
        int maxScroll = Math.max(0, totalH - listH);
        scroll -= (int) (scrollY * 16);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        return true;
    }

    // ========================================================================
    // Narration (required by AbstractWidget)
    // ========================================================================

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // Not narrated — HUD element
    }

    // ========================================================================
    // Static accessors for position persistence
    // ========================================================================

    public static int getStoredOffsetX() {
        return persistentOffsetX;
    }

    public static int getStoredOffsetY() {
        return persistentOffsetY;
    }

    public static void setStoredOffset(int x, int y) {
        persistentOffsetX = x;
        persistentOffsetY = y;
    }

    public boolean isExpanded() {
        return expanded;
    }
}

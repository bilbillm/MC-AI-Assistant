package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.AgentChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Renders the {@link ProjectHudWidget} on the game screen via
 * {@link RenderGuiEvent.Post} (fires after the entire vanilla GUI has
 * been drawn). Handles mouse click/drag via GLFW state queries and
 * forwards scroll events from the Forge bus.
 */
@EventBusSubscriber(modid = AgentChat.MODID, value = Dist.CLIENT)
public class ProjectHudOverlay {

    private static ProjectHudWidget widget;
    private static boolean wasLeftDown;
    private static double lastMouseX;
    private static double lastMouseY;

    // ──────────────────────────────────────────────────────────────────────
    // Render — fires on the FORGE bus after all vanilla GUI layers
    // ──────────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        GuiGraphics guiGraphics = event.getGuiGraphics();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(
                Minecraft.getInstance().isPaused());

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        if (widget == null) {
            widget = new ProjectHudWidget();
        }

        // ── Scaled mouse coordinates ──
        double guiScale = mc.getWindow().getGuiScale();
        double mX = mc.mouseHandler.xpos() / guiScale;
        double mY = mc.mouseHandler.ypos() / guiScale;

        // ── Forward mouse press / drag / release via GLFW ──
        long handle = mc.getWindow().getWindow();
        boolean leftDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT)
                == GLFW.GLFW_PRESS;

        if (leftDown && !wasLeftDown) {
            widget.mouseClicked(mX, mY, 0);
        } else if (leftDown && wasLeftDown) {
            double dx = mX - lastMouseX;
            double dy = mY - lastMouseY;
            widget.mouseDragged(mX, mY, 0, dx, dy);
        } else if (!leftDown && wasLeftDown) {
            widget.mouseReleased(mX, mY, 0);
        }

        wasLeftDown = leftDown;
        lastMouseX = mX;
        lastMouseY = mY;

        // ── Render widget ──
        widget.render(guiGraphics, (int) mX, (int) mY, partialTick);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Mouse scroll forwarding
    // ──────────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (widget == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        // The event provides raw-window mouse coordinates; scale them
        double guiScale = mc.getWindow().getGuiScale();
        double mX = event.getMouseX() / guiScale;
        double mY = event.getMouseY() / guiScale;

        if (widget.mouseScrolled(mX, mY, event.getScrollDeltaX(), event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }
}

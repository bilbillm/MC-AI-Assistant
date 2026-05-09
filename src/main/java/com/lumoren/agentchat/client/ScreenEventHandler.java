package com.lumoren.agentchat.client;

import com.lumoren.agentchat.AgentChat;
import com.lumoren.agentchat.ui.AIChatSidebarPanel;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Event subscriber that hooks into screen lifecycle events to add the
 * AI chat sidebar panel to the inventory screen.
 * <p>
 * Subscribes to the GAME (Forge) event bus for screen events.
 * <ul>
 *   <li>{@link ScreenEvent.Init.Post} — adds sidebar widgets to {@link InventoryScreen}</li>
 *   <li>{@link ScreenEvent.Render.Post} — renders the sidebar background and message list</li>
 * </ul>
 */
@EventBusSubscriber(modid = AgentChat.MODID, value = Dist.CLIENT)
public class ScreenEventHandler {

    /** The active sidebar panel instance, or null if not shown. */
    private static AIChatSidebarPanel activePanel;

    /**
     * After an InventoryScreen initializes, add the AI chat sidebar panel
     * with its widgets (input field, buttons).
     *
     * @param event the screen init event
     */
    @SubscribeEvent
    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen inventoryScreen) {
            AIChatSidebarPanel panel = new AIChatSidebarPanel();
            panel.init(inventoryScreen, inventoryScreen.getMinecraft().font, event::addListener);
            activePanel = panel;
        }
    }

    /**
     * After a screen renders, draw the sidebar panel overlay if active.
     *
     * @param event the screen render event
     */
    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (activePanel != null && event.getScreen() instanceof InventoryScreen) {
            activePanel.render(
                    event.getGuiGraphics(),
                    event.getMouseX(),
                    event.getMouseY(),
                    event.getPartialTick()
            );
        }
    }

    /**
     * Called when the InventoryScreen is closed to clean up the panel.
     * This is handled via {@link ScreenEvent.Closing}.
     *
     * @param event the screen closing event
     */
    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof InventoryScreen) {
            activePanel = null;
        }
    }

    /**
     * @return the active sidebar panel, or null if not visible
     */
    public static AIChatSidebarPanel getActivePanel() {
        return activePanel;
    }

    /**
     * @return true if the sidebar panel is currently active
     */
    public static boolean hasActivePanel() {
        return activePanel != null && activePanel.isInitialized();
    }
}

package com.lumoren.agentchat.client;

import com.lumoren.agentchat.AgentChat;
import com.lumoren.agentchat.ui.AIChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * GAME-bus event subscriber for client-side tick handling.
 * <p>
 * Processes the key binding press each tick:
 * <ul>
 *   <li>No screen → open full-screen {@link AIChatScreen}</li>
 *   <li>InventoryScreen → toggle sidebar input focus</li>
 *   <li>Other screen → open AI chat overlay</li>
 * </ul>
 */
@EventBusSubscriber(modid = AgentChat.MODID, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ClientModEvents.OPEN_CHAT_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                // No screen open → open full-screen chat
                mc.setScreen(new AIChatScreen());
            } else if (mc.screen instanceof InventoryScreen) {
                // In inventory → toggle sidebar input focus
                if (ScreenEventHandler.hasActivePanel()) {
                    var input = ScreenEventHandler.getActivePanel().getInputField();
                    if (input != null) {
                        input.setFocused(!input.isFocused());
                        if (input.isFocused()) {
                            input.setCursorPosition(input.getValue().length());
                        }
                    }
                }
            } else {
                // Other screen → open AI chat (overrides current screen)
                mc.setScreen(new AIChatScreen());
            }
        }
    }
}

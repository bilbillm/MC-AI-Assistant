package com.lumoren.agentchat.client;

import com.lumoren.agentchat.AgentChat;
import com.lumoren.agentchat.persistence.ConversationManager;
import com.lumoren.agentchat.ui.AIChatScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.nio.file.Path;

/**
 * GAME-bus event subscriber for client-side events.
 * Initializes conversation persistence on world load.
 */
@EventBusSubscriber(modid = AgentChat.MODID, value = Dist.CLIENT)
public class ClientEventHandler {

    private static boolean initialized;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ClientModEvents.OPEN_CHAT_KEY.consumeClick()) {
            Minecraft.getInstance().setScreen(new AIChatScreen());
        }
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!initialized && event.getLevel().isClientSide()) {
            Path saveDir = Minecraft.getInstance().gameDirectory.toPath().resolve("agentchat-conversations");
            ConversationManager.initialize(saveDir);
            initialized = true;
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (initialized && event.getLevel().isClientSide()) {
            ConversationManager.shutdown();
            initialized = false;
        }
    }
}

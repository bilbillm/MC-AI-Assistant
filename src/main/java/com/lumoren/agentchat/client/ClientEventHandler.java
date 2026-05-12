package com.lumoren.agentchat.client;

import com.lumoren.agentchat.AgentChat;
import com.lumoren.agentchat.persistence.ConversationManager;
import com.lumoren.agentchat.persistence.ProjectManager;
import com.lumoren.agentchat.config.ConfigManager;
import com.lumoren.agentchat.ui.AIChatScreen;
import com.lumoren.agentchat.client.ProjectProgressTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
    private static int tickCounter;
    private static ProjectProgressTracker tracker;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ClientModEvents.OPEN_CHAT_KEY.consumeClick()) {
            Minecraft.getInstance().setScreen(new AIChatScreen());
        }

        if (tickCounter++ >= ConfigManager.getInstance().getProjectTrackInterval()) {
            tickCounter = 0;
            ProjectManager pm = ProjectManager.getInstance();
            if (pm != null && pm.getActiveProject() != null) {
                if (tracker == null) {
                    tracker = new ProjectProgressTracker();
                    // Wire auto-completion: save project after tasks are marked done
                    final ProjectManager fPm = pm;
                    tracker.setOnProgressChanged(() -> fPm.saveProject(fPm.getActiveProject()));
                }
                // Pass interval=1 — ClientEventHandler already gates by tick counter
                tracker.onClientTick(pm.getActiveProject(), Minecraft.getInstance(), 1);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!initialized && event.getLevel().isClientSide()) {
            Path saveDir = Minecraft.getInstance().gameDirectory.toPath().resolve("agentchat-conversations");
            ConversationManager.initialize(saveDir);
            ProjectManager.initialize(saveDir);
            initialized = true;
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (initialized && event.getLevel().isClientSide()) {
            // Close the chat screen before shutdown to trigger save
            // This prevents data loss from in-flight AI responses
            Minecraft mc = Minecraft.getInstance();
            Screen current = mc.screen;
            if (current instanceof AIChatScreen) {
                current.onClose();
            }
            ProjectManager.shutdown();
            ConversationManager.shutdown();
            initialized = false;
        }
    }
}

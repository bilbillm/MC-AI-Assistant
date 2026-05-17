package com.lumoren.agentchat.client;

import com.lumoren.agentchat.AgentChat;
import com.lumoren.agentchat.i18n.I18nKeys;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * MOD-bus event subscriber for client-side key binding registration.
 * <p>
 * Registers the backtick/grave accent key mapping for opening the
 * AI chat screen.
 * <p>
 * The actual key press handling is in {@link ClientEventHandler}.
 */
@EventBusSubscriber(modid = AgentChat.MODID, value = Dist.CLIENT)
public class ClientModEvents {

    /** The key binding: backtick/grave accent (`). */
    public static final KeyMapping OPEN_CHAT_KEY = new KeyMapping(
            I18nKeys.KEY_OPEN_CHAT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_GRAVE_ACCENT,
            I18nKeys.KEY_CATEGORY
    );

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CHAT_KEY);
    }
}

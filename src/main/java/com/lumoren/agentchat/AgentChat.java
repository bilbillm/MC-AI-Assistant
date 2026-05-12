package com.lumoren.agentchat;

import com.lumoren.agentchat.ui.ConfigScreen;
import com.mojang.logging.LogUtils;
import mezz.jei.api.runtime.IJeiRuntime;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;

import java.util.function.Supplier;

@Mod(AgentChat.MODID)
public class AgentChat {
    public static final String MODID = "agentchat";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static ModContainer MOD_CONTAINER;

    /**
     * JEI runtime reference, set by {@link com.lumoren.agentchat.client.JeiBridge}
     * when JEI is installed. {@code null} when JEI is absent.
     */
    public static IJeiRuntime jeiRuntime = null;

    /**
     * @return {@code true} when JEI is installed and its runtime is available
     */
    public static boolean hasJei() {
        return jeiRuntime != null;
    }

    public AgentChat(IEventBus modEventBus, ModContainer modContainer) {
        AgentChat.MOD_CONTAINER = modContainer;
        LOGGER.info("MC-AI-Assistant (AgentChat) initializing...");
        modContainer.registerConfig(ModConfig.Type.CLIENT, com.lumoren.agentchat.config.Config.SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (Supplier<IConfigScreenFactory>) () -> (mc, parent) -> new ConfigScreen(parent));
    }
}

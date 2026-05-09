package com.lumoren.agentchat;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(AgentChat.MODID)
public class AgentChat {
    public static final String MODID = "agentchat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AgentChat(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("MC-AI-Assistant (AgentChat) initializing...");
        modContainer.registerConfig(ModConfig.Type.CLIENT, com.lumoren.agentchat.config.Config.SPEC);
    }
}

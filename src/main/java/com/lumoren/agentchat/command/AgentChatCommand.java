package com.lumoren.agentchat.command;

import com.lumoren.agentchat.AgentChat;
import com.lumoren.agentchat.client.ClientServiceManager;
import com.lumoren.agentchat.config.ConfigManager;
import com.lumoren.agentchat.i18n.I18nHelper;
import com.lumoren.agentchat.i18n.I18nKeys;
import com.lumoren.agentchat.model.ConversationThread;
import com.lumoren.agentchat.persistence.ConversationManager;
import com.lumoren.agentchat.ui.ConfigScreen;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * {@code /agentchat} 客户端命令注册与处理。
 * <p>
 * 子命令：
 * <ul>
 *   <li>{@code /agentchat config} — 打开配置界面</li>
 *   <li>{@code /agentchat key set <key>} — 设置 API Key</li>
 *   <li>{@code /agentchat clear} — 清空当前对话</li>
 *   <li>{@code /agentchat threads} — 列出所有对话线程</li>
 *   <li>{@code /agentchat thread new <name>} — 新建线程（名称可选）</li>
 *   <li>{@code /agentchat thread switch <id>} — 切换到指定线程</li>
 * </ul>
 */
@EventBusSubscriber(modid = AgentChat.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class AgentChatCommand {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(buildCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("agentchat")
                .then(Commands.literal("config")
                        .executes(ctx -> {
                            Minecraft.getInstance().execute(() -> {
                                Screen current = Minecraft.getInstance().screen;
                                Minecraft.getInstance().setScreen(new ConfigScreen(current));
                            });
                            return 1;
                        }))
                .then(Commands.literal("key")
                        .then(Commands.literal("set")
                                .then(Commands.argument("key", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String key = StringArgumentType.getString(ctx, "key");
                                            ConfigManager.getInstance().getSecrets().saveApiKey(key);
                                            ClientServiceManager.reset();
                                            sendFeedback(I18nKeys.COMMAND_KEY_SET);
                                            return 1;
                                        }))))
                .then(Commands.literal("clear")
                        .executes(ctx -> {
                            ConversationManager manager = getManager();
                            if (manager != null) {
                                manager.getCurrentThread().clearMessages();
                                sendFeedback(I18nKeys.COMMAND_CLEAR_SUCCESS);
                            } else {
                                sendFeedbackRaw("No active conversation manager");
                            }
                            return 1;
                        }))
                .then(Commands.literal("threads")
                        .executes(ctx -> {
                            ConversationManager manager = getManager();
                            if (manager != null) {
                                List<ConversationThread> threads = manager.getAllThreads();
                                sendFeedbackRaw(I18nHelper.translateToString(I18nKeys.COMMAND_THREADS_HEADER, threads.size()));
                                for (ConversationThread t : threads) {
                                    String marker = (manager.getCurrentThread() == t) ? "[*] " : "    ";
                                    sendFeedbackRaw(marker + I18nHelper.translateToString(I18nKeys.COMMAND_THREADS_ENTRY,
                                            t.getId().substring(0, Math.min(8, t.getId().length())),
                                            t.getName(),
                                            t.messageCount()));
                                }
                            } else {
                                sendFeedbackRaw("No active conversation manager");
                            }
                            return 1;
                        }))
                .then(Commands.literal("thread")
                        .then(Commands.literal("new")
                                .executes(ctx -> {
                                    ConversationManager manager = getManager();
                                    if (manager != null) {
                                        ConversationThread t = manager.createThread();
                                        sendFeedbackRaw(I18nHelper.translateToString(I18nKeys.COMMAND_THREAD_CREATED, t.getName(), t.getId()));
                                    } else {
                                        sendFeedbackRaw("No active conversation manager");
                                    }
                                    return 1;
                                })
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            ConversationManager manager = getManager();
                                            if (manager != null) {
                                                String name = StringArgumentType.getString(ctx, "name");
                                                ConversationThread t = manager.createThread(name);
                                                sendFeedbackRaw(I18nHelper.translateToString(I18nKeys.COMMAND_THREAD_CREATED, t.getName(), t.getId()));
                                            } else {
                                                sendFeedbackRaw("No active conversation manager");
                                            }
                                            return 1;
                                        })))
                        .then(Commands.literal("switch")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ConversationManager manager = getManager();
                                            if (manager != null) {
                                                String id = StringArgumentType.getString(ctx, "id");
                                                boolean success = manager.switchThread(id);
                                                if (success) {
                                                    sendFeedbackRaw(I18nHelper.translateToString(I18nKeys.COMMAND_THREAD_SWITCHED,
                                                            manager.getCurrentThread().getName()));
                                                } else {
                                                    sendFeedbackRaw(I18nHelper.translateToString(I18nKeys.COMMAND_THREAD_NOT_FOUND, id));
                                                }
                                            } else {
                                                sendFeedbackRaw("No active conversation manager");
                                            }
                                            return 1;
                                        }))));
    }

    /**
     * 获取当前世界的 ConversationManager，若未初始化则尝试用默认路径初始化。
     * <p>
     * 注意：若单例已被 {@link ClientEventHandler} 初始化，则不再覆盖，
     * 避免 fallback 路径覆盖真实世界路径导致数据丢失。
     */
    private static ConversationManager getManager() {
        ConversationManager manager = ConversationManager.getInstance();
        if (manager != null) {
            return manager; // Already initialized by world load — don't overwrite
        }
        Minecraft mc = Minecraft.getInstance();
        Path worldPath = null;
        if (mc.getSingleplayerServer() != null) {
            worldPath = mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        }
        if (worldPath != null) {
            ConversationManager.initialize(worldPath);
        } else {
            // Fallback: no world loaded yet, use temp path (will be overwritten on world load)
            ConversationManager.initialize(Paths.get(".").toAbsolutePath().resolve("agentchat_fallback"));
        }
        return ConversationManager.getInstance();
    }

    private static void sendFeedback(String key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(I18nHelper.translate(key));
        }
    }

    private static void sendFeedbackRaw(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(text));
        }
    }
}

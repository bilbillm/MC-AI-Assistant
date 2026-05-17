package com.lumoren.agentchat.client;

import com.lumoren.agentchat.ai.AIChatService;
import com.lumoren.agentchat.ai.OpenAICompatClient;
import com.lumoren.agentchat.ai.ToolCallDispatcher;
import com.lumoren.agentchat.config.ConfigManager;
import com.lumoren.agentchat.tools.GameInfoTool;
import com.lumoren.agentchat.tools.InventoryTool;
import com.lumoren.agentchat.tools.MaterialCalculatorTool;
import com.lumoren.agentchat.tools.ItemEncyclopediaTool;
import com.lumoren.agentchat.tools.ModListTool;
import com.lumoren.agentchat.tools.PositionTool;
import com.lumoren.agentchat.tools.ProjectTool;
import com.lumoren.agentchat.tools.ReadWebpageTool;
import com.lumoren.agentchat.tools.RecipeTool;
import com.lumoren.agentchat.tools.ToolRegistry;
import com.lumoren.agentchat.tools.UsageTool;
import com.lumoren.agentchat.tools.WebSearchTool;
import com.lumoren.agentchat.tools.WorldStateTool;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.function.Consumer;

/**
 * Singleton service manager that provides shared instances of {@link AIChatService}
 * and its dependencies to UI components.
 * <p>
 * Initialized lazily on first access. All tools are registered during initialization.
 */
public final class ClientServiceManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static AIChatService chatService;

    private ClientServiceManager() {
    }

    /**
     * Get or create the shared {@link AIChatService} instance.
     * <p>
     * Creates the service with all registered tools on first call.
     *
     * @return the shared AIChatService instance (never null)
     */
    public static synchronized AIChatService getChatService() {
        if (chatService == null) {
            chatService = createChatService();
        }
        return chatService;
    }

    /**
     * Reinitialize the chat service (e.g. after config changes).
     */
    public static synchronized void reset() {
        chatService = null;
    }

    /**
     * Create and configure the full AIChatService with tooling.
     */
    private static AIChatService createChatService() {
        ToolRegistry toolRegistry = createToolRegistry();
        ToolCallDispatcher dispatcher = new ToolCallDispatcher(toolRegistry);

        // Use Minecraft's main thread executor for streaming callbacks
        Consumer<Runnable> mainThreadExecutor = r -> {
            if (net.minecraft.client.Minecraft.getInstance() != null) {
                net.minecraft.client.Minecraft.getInstance().execute(r);
            } else {
                r.run();
            }
        };

        OpenAICompatClient client = new OpenAICompatClient(
                ConfigManager.getInstance(),
                mainThreadExecutor
        );

        LOGGER.info("AIChatService initialized with {} tools: {}",
                toolRegistry.size(),
                toolRegistry.getAll().stream().map(t -> t.getName()).toList());

        return new AIChatService(client, dispatcher, toolRegistry);
    }

    /**
     * Create and register all available game tools.
     */
    private static ToolRegistry createToolRegistry() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new InventoryTool());
        registry.register(new ItemEncyclopediaTool());
        registry.register(new PositionTool());
        registry.register(new RecipeTool());
        registry.register(new UsageTool());
        registry.register(new WorldStateTool());
        registry.register(new WebSearchTool());
        registry.register(new ModListTool());
        registry.register(new GameInfoTool());
        registry.register(new ProjectTool());
        registry.register(new ReadWebpageTool());
        registry.register(new MaterialCalculatorTool());
        return registry;
    }
}

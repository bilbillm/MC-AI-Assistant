package com.lumoren.agentchat.ai;

import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.persistence.ProjectManager;
import com.lumoren.agentchat.tools.ToolRegistry;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * High-level service that orchestrates the full AI conversation loop with
 * OpenAI function calling support.
 * <p>
 * The loop works as follows:
 * <ol>
 *   <li>Send user message + history to AI (non-streaming) to detect tool calls</li>
 *   <li>If tool calls are returned, execute them via {@link ToolCallDispatcher}</li>
 *   <li>Add tool results to conversation history</li>
 *   <li>Repeat steps 1-3 until AI returns plain text (max {@value #MAX_ROUNDS} rounds)</li>
 *   <li>Stream the final text response to the user via {@link ChatCallback}</li>
 * </ol>
 */
public class AIChatService {

    private final OpenAICompatClient client;
    private final ToolCallDispatcher dispatcher;
    private final ToolRegistry toolRegistry;
    private static final int MAX_ROUNDS = 20;
    private volatile boolean cancelled;

    private static final String SYSTEM_PROMPT =
        "You are an AI assistant embedded in Minecraft. You have access to the player's game state "
        + "through tools and can help with planning, crafting, exploration, and modded gameplay.\n\n"
        + "## Available Tools\n"
        + "- get_inventory — Player's 36-slot inventory with item IDs and counts\n"
        + "- item_info — Item details: max stack, rarity, food properties\n"
        + "- lookup_recipe — Crafting recipes for an item (JEI-powered if installed)\n"
        + "- get_player_status — Position, dimension, health, hunger\n"
        + "- get_world_state — Time, weather, difficulty, biome\n"
        + "- web_search — Search the web for Minecraft information\n"
        + "- list_mods — List all installed mods in this instance\n"
        + "- game_info — Minecraft version, loader type, mod count\n"
        + "- manage_project — Create, track, and update player projects\n\n"
        + "## Project System\n"
        + "When a player says '我要做...' or 'I want to make...', use manage_project "
        + "(action='create_project') to break down their goal into 3-8 concrete steps. "
        + "Each step needs: description, type (CRAFT/GATHER/GO_TO/USE/KILL/PLAN), "
        + "and items array with minecraft:itemId and count where applicable.\n"
        + "Use manage_project (action='get_status') to check progress, "
        + "action='mark_done' to complete steps, action='cancel' to remove the project.\n"
        + "The game auto-detects item-based progress; you can also mark steps done manually.\n\n"
        + "## Guidelines\n"
        + "- Be concise. Use tools proactively — check inventory, recipes, and game state "
        + "before answering.\n"
        + "- When the player has a goal, create a project to track it step-by-step.\n"
        + "- Use web_search for information beyond your knowledge (mod mechanics, updates).\n"
        + "- Use list_mods and game_info to understand the player's environment.\n"
        + "- Explain what you're doing before calling tools, then give natural responses.";

    /**
     * Callback interface for receiving chat events.
     */
    public interface ChatCallback {
        /** Called for each streamed token in the final response. */
        void onToken(String token);

        /** Called for reasoning/thinking tokens (DeepSeek thinking mode). */
        default void onReasoningToken(String token) {}

        /** Called when a tool is about to be executed. */
        void onThinking(String status);

        /** Called when AI requests a tool execution (before the tool runs). */
        default void onToolCall(String name, String arguments) {}

        /** Called when a tool execution completes with results. */
        default void onToolResult(String result) {}

        /** Called when an error occurs. */
        void onError(String error);

        /** Called when the full conversation is complete. */
        void onComplete(String fullResponse);
    }

    public AIChatService(OpenAICompatClient client, ToolCallDispatcher dispatcher, ToolRegistry toolRegistry) {
        this.client = client;
        this.dispatcher = dispatcher;
        this.toolRegistry = toolRegistry;
    }

    /**
     * Send a message and run the full function-calling conversation loop.
     * <p>
     * This method is non-blocking. All network and tool execution happens
     * asynchronously and results are delivered via the {@link ChatCallback}.
     *
     * @param userInput the user's input message
     * @param history   previous conversation messages (excludes system message)
     * @param callback  callback for receiving tokens, status updates, and the final response
     */
    public void sendMessage(String userInput, List<ChatMessage> history, ChatCallback callback) {
        cancelled = false;
        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(ChatMessage.system(SYSTEM_PROMPT));

        // Inject project context if active project exists
        ProjectManager pm = ProjectManager.getInstance();
        if (pm != null && pm.getActiveProject() != null) {
            conversation.add(ProjectPlanningService.buildProjectContextMessage(pm.getActiveProject()));
        }

        // Filter out display-only tool messages (no tool_call_id) from UI history
        // StreamingChatRenderer creates these for visual feedback but API rejects them
        for (ChatMessage h : history) {
            if (!("tool".equals(h.role()) && h.toolCallId() == null)) {
                conversation.add(h);
            }
        }
        conversation.add(ChatMessage.user(userInput));

        runConversationLoop(conversation, callback, 0);
    }

    /**
     * Cancel any in-flight conversation request. Prevents callbacks from dispatching
     * after the cancel point. The caller must also abort the {@link ChatCallback}
     * (e.g. {@code StreamingChatRenderer.abort()}) to prevent stale state.
     */
    public void cancel() {
        cancelled = true;
    }

    private void runConversationLoop(List<ChatMessage> conversation, ChatCallback callback, int round) {
        if (round >= MAX_ROUNDS) {
            // Forced output: add a last system message telling AI to wrap up
            conversation.add(ChatMessage.system(
                "You have reached the tool call limit. DO NOT request any more tools. "
                + "Based on ALL the information gathered above, give your final answer "
                + "as plain conversation text. Do NOT output JSON, tool calls, or data dumps. "
                + "Just answer the player's original question naturally."
            ));
            // Do a final non-streaming completion WITHOUT tools to force a natural response
            client.chatCompletion(conversation, null)
                .thenAccept(summary -> {
                    if (cancelled) return;
                    callback.onThinking("Wrapping up...");
                    callback.onToken(summary);
                    callback.onComplete(summary);
                })
                .exceptionally(throwable -> {
                    Throwable cause = unwrap(throwable);
                    callback.onError(cause.getMessage());
                    return null;
                });
            return;
        }

        List<ToolDefinition> tools = toolRegistry.getToolDefinitions();

        client.chatCompletionWithTools(conversation, tools)
            .thenAccept(response -> {
                if (cancelled) return;

                if (response.toolCalls() != null && !response.toolCalls().isEmpty()) {
                    // AI requested tool calls - add assistant message to history and execute tools
                    conversation.add(response);

                    for (ChatMessage.ToolCall tc : response.toolCalls()) {
                        callback.onThinking("querying " + tc.function().name() + "...");
                        callback.onToolCall(tc.function().name(), tc.function().arguments());
                    }

                    List<ChatMessage> toolResults = dispatcher.executeToolCalls(
                        response.toolCalls(), Minecraft.getInstance()
                    );
                    conversation.addAll(toolResults);

                    for (ChatMessage tr : toolResults) {
                        callback.onToolResult(tr.content());
                    }

                    // Continue the loop with updated history
                    runConversationLoop(conversation, callback, round + 1);
                } else {
                    // No tool calls - stream the final text response to the user
                    client.chatCompletionStreaming(conversation, null, new OpenAICompatClient.StreamCallback() {
                        private final StringBuilder fullResponse = new StringBuilder();

                        @Override
                        public void onToken(String token) {
                            if (cancelled) return;
                            // Filter out DeepSeek tool call XML fragments leaking into stream
                            if (isToolCallFragment(token)) return;
                            callback.onToken(token);
                            fullResponse.append(token);
                        }

                        @Override
                        public void onReasoningToken(String token) {
                            if (cancelled) return;
                            callback.onReasoningToken(token);
                        }

                        @Override
                        public void onComplete() {
                            if (cancelled) return;
                            callback.onComplete(fullResponse.toString());
                        }

                        @Override
                        public void onError(Throwable error) {
                            if (cancelled) return;
                            callback.onError(error.getMessage());
                        }
                    });
                }
            })
            .exceptionally(throwable -> {
                Throwable cause = unwrap(throwable);
                callback.onError(cause.getMessage());
                return null;
            });
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof java.util.concurrent.CompletionException
                || throwable instanceof java.util.concurrent.ExecutionException) {
            return throwable.getCause() != null ? throwable.getCause() : throwable;
        }
        return throwable;
    }

    /** Filter out DeepSeek tool call XML fragments that leak into streaming content. */
    private static boolean isToolCallFragment(String token) {
        if (token == null || token.isEmpty()) return false;
        // DeepSeek uses various markers for tool call XML in streaming content
        return token.contains("▌") || token.contains("<DSML|") || token.contains("|tool_calls>")
            || token.contains("|invoke") || token.contains("|parameter")
            || token.contains("</invoke>") || token.contains("</parameter>")
            || token.contains("</tool_calls>");
    }
}

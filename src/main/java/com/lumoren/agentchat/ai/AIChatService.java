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
    private static final int MAX_ROUNDS = 5;
    private volatile boolean cancelled;

    private static final String SYSTEM_PROMPT =
        "You are a helpful Minecraft assistant. You can use tools to query the player's inventory, "
        + "recipes, position, world state, and item information. Always be concise and helpful. "
        + "When using tools, briefly explain what you're doing.";

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
            // Forced output: inject system instruction to stop using tools and summarize
            conversation.add(ChatMessage.system(
                "Tool call limit reached. Based on all the information gathered so far, "
                + "provide your best answer NOW without calling any more tools."
            ));
            // Do a final non-streaming completion to get the summary text
            client.chatCompletion(conversation, null)
                .thenAccept(summary -> {
                    if (cancelled) return;
                    callback.onThinking("Summarizing...");
                    callback.onToken("(Tool call limit reached — showing best available answer)\n\n");
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
}

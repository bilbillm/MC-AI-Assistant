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

    private static final String SYSTEM_PROMPT = loadSystemPrompt();

    private static String loadSystemPrompt() {
        try (var in = AIChatService.class.getResourceAsStream(
                "/assets/agentchat/system_prompt.txt")) {
            if (in != null) {
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            System.err.println("[AgentChat] Failed to load system prompt: " + e.getMessage());
        }
        // Fallback
        return "You are a helpful Minecraft assistant. Use tools to query the player's inventory, recipes, position, world state, and item information.";
    }

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
                    ToolCallFilter xmlFilter = new ToolCallFilter();
                    client.chatCompletionStreaming(conversation, null, new OpenAICompatClient.StreamCallback() {
                        private final StringBuilder fullResponse = new StringBuilder();

                        @Override
                        public void onToken(String token) {
                            if (cancelled) return;
                            // Filter out DeepSeek tool call XML fragments (multi-token spans)
                            String filtered = xmlFilter.filter(token);
                            if (filtered != null) {
                                callback.onToken(filtered);
                                fullResponse.append(filtered);
                            }
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

    /**
     * State-machine filter that suppresses DeepSeek tool call XML fragments
     * from streaming output. Tool call XML spans multiple tokens — a simple
     * per-token check is insufficient.
     */
    private static class ToolCallFilter {
        private boolean inToolCall;
        private final StringBuilder buffer = new StringBuilder();

        /**
         * Feed a token through the filter. Returns the token if it should be
         * displayed, or null if it should be suppressed.
         */
        String filter(String token) {
            if (token == null || token.isEmpty()) return token;

            if (!inToolCall) {
                // Check for tool call start markers
                if (token.contains("▌") || token.contains("<DSML|") || token.contains("|tool_calls>")
                        || token.contains("|invoke") || token.contains("|parameter")) {
                    inToolCall = true;
                    buffer.setLength(0);
                    buffer.append(token);
                    // Check if the tool call also ends within this token
                    if (token.contains("</tool_calls>") || token.contains("|tool_calls")) {
                        inToolCall = false;
                    }
                    return null;
                }
                return token;
            } else {
                // Inside a tool call block — buffer and suppress
                buffer.append(token);
                if (token.contains("</tool_calls>") || token.contains("|tool_calls")) {
                    inToolCall = false;
                }
                return null;
            }
        }
    }
}

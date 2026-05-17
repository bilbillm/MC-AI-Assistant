package com.lumoren.agentchat.ai;

import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.persistence.ConversationManager;
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
    private static volatile boolean debugMode = false;

    public static boolean isDebugMode() { return debugMode; }

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

        /** Called when the assistant outputs text alongside tool calls (non-streaming). */
        default void onAssistantMessage(String text) {}

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

        // Debug mode toggle — handled locally, not sent to AI
        if (userInput.strip().equalsIgnoreCase("debug mode")) {
            debugMode = true;
            callback.onToken("[DEBUG MODE ENABLED]\n");
            callback.onToken("Available debug capabilities:\n");
            callback.onToken("- Tool testing: call any tool with raw args\n");
            callback.onToken("- Project inspection: view internal state\n");
            callback.onToken("- Inventory simulation: pretend to have items\n");
            callback.onToken("- Conversation dump: see raw API messages\n");
            callback.onToken("- Performance timing: tool call durations\n");
            callback.onToken("Say 'debug off' to exit.\n");
            callback.onComplete("[DEBUG MODE ENABLED]");
            return;
        }
        if (userInput.strip().equalsIgnoreCase("debug off")) {
            debugMode = false;
            callback.onToken("[DEBUG MODE DISABLED]\n");
            callback.onComplete("[DEBUG MODE DISABLED]");
            return;
        }

        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(ChatMessage.system(SYSTEM_PROMPT));

        // Inject project context if active project exists
        ProjectManager pm = ProjectManager.getInstance();
        if (pm != null && pm.getActiveProject() != null) {
            conversation.add(ProjectPlanningService.buildProjectContextMessage(pm.getActiveProject()));
        }

        // Inject debug mode instructions if active
        if (debugMode) {
            String debugPrompt = "\n\n## DEBUG MODE ACTIVE\n"
                + "You are in debug mode. You have these additional capabilities:\n"
                + "- Call tools with ANY arguments (even invalid ones) and I'll show the raw result including errors\n"
                + "- Ask me to 'show project state' and I'll dump the full project/task internals\n"
                + "- Ask me to 'simulate inventory: item1 x5, item2 x3' and I'll pretend those items exist\n"
                + "- Ask me to 'show conversation' and I'll dump the raw messages being sent to the API\n"
                + "- Ask me to 'show performance' and I'll show timing for recent tool calls\n"
                + "- Be verbose about what you're doing — explain every step\n"
                + "- This is for testing the mod, not for real gameplay — be thorough";
            conversation.add(ChatMessage.system(debugPrompt));
        }

        // Filter out display-only tool messages (no tool_call_id) from UI history
        // StreamingChatRenderer creates these for visual feedback but API rejects them
        for (ChatMessage h : history) {
            if (!("tool".equals(h.role()) && h.toolCallId() == null)) {
                conversation.add(h);
            }
        }
        conversation.add(ChatMessage.user(userInput));

        // Log user message to session log
        var mgr = ConversationManager.getInstance();
        if (mgr != null && mgr.getCurrentThread() != null) {
            SessionLogger.logUser(mgr.getCurrentThread().getId(), userInput);
        }

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
                runOnMainThread(() -> {
                    if (cancelled) return;

                    if (response.toolCalls() != null && !response.toolCalls().isEmpty()) {
                        // AI requested tool calls - add assistant message to history and execute tools
                        conversation.add(response);

                        // Show content text that appears between tool calls
                        if (response.content() != null && !response.content().isBlank()) {
                            callback.onAssistantMessage(response.content());
                        }

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
                                if (xmlFilter.justExitedToolCall()) {
                                    callback.onToolCall("🔧 tool", "processing...");
                                }
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
                                String finalText = fullResponse.toString();
                                callback.onComplete(finalText);
                                // Log streamed response to session log
                                var mgr = ConversationManager.getInstance();
                                if (mgr != null && mgr.getCurrentThread() != null) {
                                    SessionLogger.logStreamComplete(mgr.getCurrentThread().getId(), finalText);
                                }
                            }

                            @Override
                            public void onError(Throwable error) {
                                if (cancelled) return;
                                callback.onError(error.getMessage());
                            }
                        });
                    }
                });
            })
            .exceptionally(throwable -> {
                Throwable cause = unwrap(throwable);
                callback.onError(cause.getMessage());
                return null;
            });
    }

    /**
     * Run a task on the Minecraft main/render thread if available, otherwise
     * execute directly (for unit tests or headless environments).
     */
    private static void runOnMainThread(Runnable task) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(task);
        } else {
            task.run();
        }
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
        private boolean inInvoke;   // inside <DSML|invoke>...</DSML|invoke> — suppress
        private boolean justExited;  // just exited an invoke block — fire onToolCall once
        private final StringBuilder buffer = new StringBuilder();

        /**
         * Feed a token through the filter. Returns the token (or its non-invoke prefix)
         * if it should be displayed, or null if entirely suppressed.
         * <p>
         * If a token CONTAINS an invoke start marker preceded by text, the text before
         * the marker is returned while the invoke portion is suppressed.
         */
        String filter(String token) {
            if (token == null || token.isEmpty()) return token;

            if (!inInvoke) {
                int invokeAt = token.indexOf("|invoke");
                if (invokeAt >= 0) {
                    // Enter invoke — suppress from the marker onward
                    String before = invokeAt > 0 ? token.substring(0, invokeAt) : null;
                    inInvoke = true;
                    buffer.setLength(0);
                    buffer.append(token.substring(invokeAt));
                    if (token.contains("</invoke>") || token.contains("</DSML|invoke>")) {
                        inInvoke = false;
                        justExited = true;
                    }
                    // Return text BEFORE the invoke marker (may be null if invoke starts at position 0)
                    return (before != null && !before.isBlank()) ? before : null;
                }
                return token;
            } else {
                buffer.append(token);
                if (token.contains("</invoke>") || token.contains("</DSML|invoke>")
                        || token.contains("</tool_calls>") || token.contains("</DSML|tool_calls>")) {
                    inInvoke = false;
                    justExited = true;
                }
                return null;
            }
        }

        boolean justExitedToolCall() {
            boolean result = justExited;
            justExited = false;
            return result;
        }
    }
}

package com.lumoren.agentchat.ai;

import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.tools.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration-style tests for {@link AIChatService}.
 */
class AIChatServiceTest {

    private OpenAICompatClient client;
    private ToolCallDispatcher dispatcher;
    private ToolRegistry toolRegistry;
    private AIChatService service;

    @BeforeEach
    void setUp() {
        client = mock(OpenAICompatClient.class);
        dispatcher = mock(ToolCallDispatcher.class);
        toolRegistry = mock(ToolRegistry.class);

        when(toolRegistry.getToolDefinitions()).thenReturn(List.of());

        service = new AIChatService(client, dispatcher, toolRegistry);
    }

    // Helper to mock a successful streaming response
    private void mockStreamingResponse(String text) {
        doAnswer(invocation -> {
            OpenAICompatClient.StreamCallback cb = invocation.getArgument(2);
            for (String token : text.split("(?<=\\s)|(?=\\s)")) {
                if (!token.isEmpty()) {
                    cb.onToken(token);
                }
            }
            cb.onComplete();
            return null;
        }).when(client).chatCompletionStreaming(anyList(), isNull(), any(OpenAICompatClient.StreamCallback.class));
    }

    // Helper to capture callback results
    private static class CallbackCollector implements AIChatService.ChatCallback {
        final List<String> tokens = new ArrayList<>();
        final List<String> thinkings = new ArrayList<>();
        final CompletableFuture<String> completed = new CompletableFuture<>();
        final CompletableFuture<String> errored = new CompletableFuture<>();

        @Override
        public void onToken(String token) {
            tokens.add(token);
        }

        @Override
        public void onThinking(String status) {
            thinkings.add(status);
        }

        @Override
        public void onError(String error) {
            errored.complete(error);
        }

        @Override
        public void onComplete(String fullResponse) {
            completed.complete(fullResponse);
        }
    }

    // ========== 1. Simple text response (no tool calls) ==========

    @Test
    void simpleTextResponse_callbackReceivesOnComplete() throws Exception {
        when(client.chatCompletionWithTools(anyList(), anyList()))
            .thenReturn(CompletableFuture.completedFuture(ChatMessage.assistant("Hello world")));

        mockStreamingResponse("Hello world");

        CallbackCollector cb = new CallbackCollector();
        service.sendMessage("Hi", List.of(), cb);

        String result = cb.completed.get(5, TimeUnit.SECONDS);
        assertEquals("Hello world", result);
        assertFalse(cb.tokens.isEmpty());
        assertTrue(String.join("", cb.tokens).contains("Hello"));
    }

    // ========== 2. Tool call round-trip ==========

    @Test
    void withToolCall_dispatcherExecutesThenReturnsFinalText() throws Exception {
        ChatMessage.ToolCall toolCall = new ChatMessage.ToolCall(
            "call_1", new ChatMessage.FunctionCall("get_inventory", "{}")
        );
        ChatMessage toolCallResponse = new ChatMessage("assistant", "", null, List.of(toolCall), null);
        ChatMessage finalResponse = ChatMessage.assistant("You have 3 items");

        when(client.chatCompletionWithTools(anyList(), anyList()))
            .thenReturn(CompletableFuture.completedFuture(toolCallResponse))
            .thenReturn(CompletableFuture.completedFuture(finalResponse));

        when(dispatcher.executeToolCalls(anyList(), any()))
            .thenReturn(List.of(ChatMessage.tool("call_1", "Diamond Sword, Bread x5, Torch x64")));

        mockStreamingResponse("You have 3 items");

        CallbackCollector cb = new CallbackCollector();
        service.sendMessage("What's in my inventory?", List.of(), cb);

        String result = cb.completed.get(5, TimeUnit.SECONDS);
        assertEquals("You have 3 items", result);
        assertEquals(1, cb.thinkings.size());
        assertEquals("querying get_inventory...", cb.thinkings.get(0));
    }

    // ========== 3. Max rounds exceeded ==========

    @Test
    void maxRoundsExceeded_forcesCompletion() throws Exception {
        ChatMessage.ToolCall toolCall = new ChatMessage.ToolCall(
            "call_1", new ChatMessage.FunctionCall("get_inventory", "{}")
        );
        ChatMessage toolCallResponse = new ChatMessage("assistant", "", null, List.of(toolCall), null);

        when(client.chatCompletionWithTools(anyList(), anyList()))
            .thenReturn(CompletableFuture.completedFuture(toolCallResponse));

        when(dispatcher.executeToolCalls(anyList(), any()))
            .thenReturn(List.of(ChatMessage.tool("call_1", "result")));

        // After max rounds, forces a final non-tool completion
        when(client.chatCompletion(anyList(), isNull()))
            .thenReturn(CompletableFuture.completedFuture("Your inventory contains 3 iron ingots."));

        CallbackCollector cb = new CallbackCollector();
        service.sendMessage("What's in my inventory?", List.of(), cb);

        String response = cb.completed.get(5, TimeUnit.SECONDS);
        assertTrue(response.contains("iron ingots"), "Expected forced summary output, got: " + response);
    }

    // ========== 4. Error handling - API error bubbles up ==========

    @Test
    void apiError_bubblesUpToOnError() throws Exception {
        when(client.chatCompletionWithTools(anyList(), anyList()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Network timeout")));

        CallbackCollector cb = new CallbackCollector();
        service.sendMessage("Hi", List.of(), cb);

        String error = cb.errored.get(5, TimeUnit.SECONDS);
        assertTrue(error.contains("Network timeout"));
    }

    // ========== 5. System message included ==========

    @Test
    void systemMessageIncludedInConversation() throws Exception {
        ArgumentCaptor<List<ChatMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);

        when(client.chatCompletionWithTools(messagesCaptor.capture(), anyList()))
            .thenReturn(CompletableFuture.completedFuture(ChatMessage.assistant("Hello")));

        mockStreamingResponse("Hello");

        CallbackCollector cb = new CallbackCollector();
        service.sendMessage("Hi", List.of(), cb);

        cb.completed.get(5, TimeUnit.SECONDS);

        List<ChatMessage> firstCallMessages = messagesCaptor.getValue();
        assertFalse(firstCallMessages.isEmpty());
        assertEquals("system", firstCallMessages.get(0).role());
        assertTrue(firstCallMessages.get(0).content().contains("AI assistant"));
    }
}

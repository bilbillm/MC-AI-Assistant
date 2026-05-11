package com.lumoren.agentchat.ai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.lumoren.agentchat.config.ConfigManager;
import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.model.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OpenAICompatClient 的单元测试。
 * <p>
 * 覆盖请求构建、响应解析、SSE 流式解析、各种错误码和重试逻辑。
 */
class OpenAICompatClientTest {

    private ConfigManager config;
    private OpenAICompatClient.HttpSender mockSender;
    private Gson gson;
    private OpenAICompatClient client;

    @BeforeEach
    void setUp() {
        config = mock(ConfigManager.class);
        when(config.getApiKey()).thenReturn("sk-test-key");
        when(config.getBaseUrl()).thenReturn("https://api.openai.com/v1");
        when(config.getModel()).thenReturn("gpt-4o-mini");
        when(config.getTemperature()).thenReturn(0.7);
        when(config.getMaxTokens()).thenReturn(1024);
        when(config.getTimeout()).thenReturn(30);
        when(config.hasApiKey()).thenReturn(true);

        mockSender = mock(OpenAICompatClient.HttpSender.class);
        gson = new Gson();
        client = new OpenAICompatClient(mockSender, config, gson, Executors.newSingleThreadExecutor(), null,
            ms -> CompletableFuture.completedFuture(null));
    }

    // ========== 请求构建测试 ==========

    @Test
    void buildRequestBody_includesMessagesAndTools() {
        List<ChatMessage> messages = List.of(
            ChatMessage.user("Hello"),
            ChatMessage.assistant("Hi there"),
            ChatMessage.tool("call_123", "{\"result\":\"ok\"}")
        );

        JsonObject params = new JsonObject();
        params.addProperty("type", "string");
        List<ToolDefinition> tools = List.of(new ToolDefinition("query_item", "Query item", params));

        JsonObject body = client.buildRequestBody(messages, tools, false);

        assertEquals("gpt-4o-mini", body.get("model").getAsString());
        assertEquals(0.7, body.get("temperature").getAsDouble(), 0.001);
        assertEquals(1024, body.get("max_tokens").getAsInt());
        assertFalse(body.get("stream").getAsBoolean());

        assertTrue(body.has("messages"));
        assertTrue(body.get("messages").isJsonArray());
        var msgs = body.getAsJsonArray("messages");
        assertEquals(3, msgs.size());
        assertEquals("user", msgs.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("Hello", msgs.get(0).getAsJsonObject().get("content").getAsString());
        assertEquals("assistant", msgs.get(1).getAsJsonObject().get("role").getAsString());
        assertEquals("tool", msgs.get(2).getAsJsonObject().get("role").getAsString());
        assertEquals("call_123", msgs.get(2).getAsJsonObject().get("tool_call_id").getAsString());

        assertTrue(body.has("tools"));
        var toolArray = body.getAsJsonArray("tools");
        assertEquals(1, toolArray.size());
        assertEquals("function", toolArray.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("query_item", toolArray.get(0).getAsJsonObject().getAsJsonObject("function").get("name").getAsString());
    }

    @Test
    void buildRequestBody_assistantWithToolCalls_hasNullOrEmptyContent() {
        var function = new ChatMessage.FunctionCall("get_time", "{}");
        var toolCall = new ChatMessage.ToolCall("call_999", function);
        ChatMessage msg = new ChatMessage("assistant", "", null, List.of(toolCall), null);

        JsonObject body = client.buildRequestBody(List.of(msg), null, false);
        var msgs = body.getAsJsonArray("messages");
        assertEquals(1, msgs.size());
        // Content should be empty string (not null) — some APIs reject null
        assertEquals("", msgs.get(0).getAsJsonObject().get("content").getAsString());
        assertTrue(msgs.get(0).getAsJsonObject().has("tool_calls"));
    }

    @Test
    void chatCompletion_sendsRequestWithCorrectHeadersAndUrl() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"choices\":[{\"message\":{\"content\":\"\"}}]}");
        when(mockSender.sendAsync(any(), any())).thenReturn((CompletableFuture) CompletableFuture.completedFuture(mockResponse));

        client.chatCompletion(List.of(ChatMessage.user("Hello")), null).get();

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockSender).sendAsync(requestCaptor.capture(), any());

        HttpRequest request = requestCaptor.getValue();
        assertEquals(URI.create("https://api.openai.com/v1/chat/completions"), request.uri());
        assertEquals("POST", request.method());
        String authHeader = request.headers().firstValue("Authorization").orElse("");
        assertTrue(authHeader.startsWith("Bearer "), "Authorization header should start with 'Bearer '");
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElse(""));
    }

    // ========== 非流式响应测试 ==========

    @Test
    void chatCompletion_parsesNonStreamingResponse() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"choices\":[{\"message\":{\"content\":\"Hello world\"}}]}");
        when(mockSender.sendAsync(any(), any())).thenReturn((CompletableFuture) CompletableFuture.completedFuture(mockResponse));

        String result = client.chatCompletion(List.of(ChatMessage.user("Hello")), null).get();

        assertEquals("Hello world", result);
    }

    @Test
    void chatCompletion_emptyChoices_returnsEmptyString() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"choices\":[]}");
        when(mockSender.sendAsync(any(), any())).thenReturn((CompletableFuture) CompletableFuture.completedFuture(mockResponse));

        String result = client.chatCompletion(List.of(ChatMessage.user("Hello")), null).get();

        assertEquals("", result);
    }

    // ========== SSE 流式解析测试 ==========

    @Test
    void chatCompletionStreaming_parsesSseTokens() throws Exception {
        HttpResponse<Stream<String>> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(Stream.of(
            "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}",
            "data: {\"choices\":[{\"delta\":{\"content\":\" \"}}]}",
            "data: {\"choices\":[{\"delta\":{\"content\":\"world\"}}]}",
            "data: [DONE]"
        ));
        when(mockSender.sendAsync(any(), any())).thenReturn((CompletableFuture) CompletableFuture.completedFuture(mockResponse));

        List<String> tokens = new ArrayList<>();
        CompletableFuture<Void> completed = new CompletableFuture<>();

        client.chatCompletionStreaming(List.of(ChatMessage.user("Hello")), null, new OpenAICompatClient.StreamCallback() {
            @Override
            public void onToken(String token) {
                tokens.add(token);
            }

            @Override
            public void onComplete() {
                completed.complete(null);
            }

            @Override
            public void onError(Throwable error) {
                completed.completeExceptionally(error);
            }
        });

        completed.get(5, TimeUnit.SECONDS);

        assertEquals(List.of("Hello", " ", "world"), tokens);
    }

    // ========== 错误处理测试 ==========

    @Test
    void chatCompletion_401throwsApiAuthException() {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(401);
        when(mockSender.sendAsync(any(), any())).thenReturn((CompletableFuture) CompletableFuture.completedFuture(mockResponse));

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            client.chatCompletion(List.of(ChatMessage.user("Hello")), null).get();
        });

        assertInstanceOf(ApiAuthException.class, ex.getCause());
    }

    @Test
    void chatCompletion_429retriesThenThrowsApiRateLimitException() {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(429);
        when(mockSender.sendAsync(any(), any())).thenReturn((CompletableFuture) CompletableFuture.completedFuture(mockResponse));

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            client.chatCompletion(List.of(ChatMessage.user("Hello")), null).get();
        });

        assertInstanceOf(ApiRateLimitException.class, ex.getCause());
        verify(mockSender, times(4)).sendAsync(any(), any());
    }

    @Test
    void chatCompletion_500retriesOnce() throws Exception {
        HttpResponse<String> errorResponse = mock(HttpResponse.class);
        when(errorResponse.statusCode()).thenReturn(500);

        HttpResponse<String> successResponse = mock(HttpResponse.class);
        when(successResponse.statusCode()).thenReturn(200);
        when(successResponse.body()).thenReturn("{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}");

        when(mockSender.sendAsync(any(), any()))
            .thenReturn((CompletableFuture) CompletableFuture.completedFuture(errorResponse))
            .thenReturn((CompletableFuture) CompletableFuture.completedFuture(successResponse));

        String result = client.chatCompletion(List.of(ChatMessage.user("Hello")), null).get();

        assertEquals("OK", result);
        verify(mockSender, times(2)).sendAsync(any(), any());
    }

    @Test
    void chatCompletion_timeoutThrowsApiTimeoutException() {
        when(mockSender.sendAsync(any(), any()))
            .thenReturn(CompletableFuture.failedFuture(new HttpTimeoutException("timeout")));

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            client.chatCompletion(List.of(ChatMessage.user("Hello")), null).get();
        });

        assertInstanceOf(ApiTimeoutException.class, ex.getCause());
    }

    @Test
    void chatCompletion_noApiKeyThrowsApiAuthException() {
        when(config.hasApiKey()).thenReturn(false);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            client.chatCompletion(List.of(ChatMessage.user("Hello")), null).get();
        });

        assertInstanceOf(ApiAuthException.class, ex.getCause());
    }

    @Test
    void chatCompletionStreaming_noApiKeyCallsOnError() {
        when(config.hasApiKey()).thenReturn(false);

        List<Throwable> errors = new ArrayList<>();
        client.chatCompletionStreaming(List.of(ChatMessage.user("Hello")), null, new OpenAICompatClient.StreamCallback() {
            @Override public void onToken(String token) {}
            @Override public void onComplete() {}
            @Override public void onError(Throwable error) { errors.add(error); }
        });

        assertEquals(1, errors.size());
        assertInstanceOf(ApiAuthException.class, errors.get(0));
    }
}

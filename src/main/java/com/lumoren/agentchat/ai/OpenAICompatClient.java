package com.lumoren.agentchat.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.lumoren.agentchat.config.ConfigManager;
import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.model.ToolDefinition;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * OpenAI 兼容的 HTTP 客户端，支持非阻塞请求和 SSE 流式响应。
 * <p>
 * 使用 Java 21 内置的 {@link HttpClient}，具备自动重试、错误处理和流式解析能力。
 */
public class OpenAICompatClient {

    private final HttpSender httpSender;
    private final ConfigManager config;
    private final Gson gson;
    private final Executor executor;
    private final Consumer<Runnable> mainThreadExecutor;
    private final Function<Long, CompletableFuture<Void>> delayer;

    @FunctionalInterface
    interface HttpSender {
        <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler);
    }

    public interface StreamCallback {
        void onToken(String token);
        void onComplete();
        void onError(Throwable error);
    }

    public OpenAICompatClient(ConfigManager config) {
        this(
            HttpClient.newHttpClient()::sendAsync,
            config,
            new Gson(),
            ForkJoinPool.commonPool(),
            null,
            ms -> CompletableFuture.runAsync(
                () -> {},
                CompletableFuture.delayedExecutor(ms, TimeUnit.MILLISECONDS)
            )
        );
    }

    public OpenAICompatClient(ConfigManager config, Consumer<Runnable> mainThreadExecutor) {
        this(
            HttpClient.newHttpClient()::sendAsync,
            config,
            new Gson(),
            ForkJoinPool.commonPool(),
            mainThreadExecutor,
            ms -> CompletableFuture.runAsync(
                () -> {},
                CompletableFuture.delayedExecutor(ms, TimeUnit.MILLISECONDS)
            )
        );
    }

    OpenAICompatClient(HttpSender httpSender, ConfigManager config, Gson gson, Executor executor,
                       Consumer<Runnable> mainThreadExecutor, Function<Long, CompletableFuture<Void>> delayer) {
        this.httpSender = httpSender;
        this.config = config;
        this.gson = gson;
        this.executor = executor;
        this.mainThreadExecutor = mainThreadExecutor;
        this.delayer = delayer;
    }

    /**
     * 发送非流式聊天补全请求，返回完整的回复文本。
     *
     * @param messages 消息列表
     * @param tools    工具定义列表（可为 null）
     * @return 包含回复文本的 CompletableFuture
     */
    public CompletableFuture<String> chatCompletion(List<ChatMessage> messages, List<ToolDefinition> tools) {
        if (!config.hasApiKey()) {
            return CompletableFuture.failedFuture(new ApiAuthException("API key not configured"));
        }

        JsonObject body = buildRequestBody(messages, tools, false);
        HttpRequest request = buildRequest(body);

        return sendWithRetry(request, HttpResponse.BodyHandlers.ofString(), 0)
            .thenApply(response -> {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                JsonArray choices = json.getAsJsonArray("choices");
                if (choices == null || choices.isEmpty()) {
                    return "";
                }
                JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                if (message == null) {
                    return "";
                }
                JsonElement content = message.get("content");
                return content != null && !content.isJsonNull() ? content.getAsString() : "";
            });
    }

    /**
     * Sends a non-streaming chat completion request and returns the full assistant message,
     * including any tool calls.
     *
     * @param messages message list
     * @param tools    tool definition list (may be null)
     * @return CompletableFuture containing the assistant ChatMessage
     */
    public CompletableFuture<ChatMessage> chatCompletionWithTools(List<ChatMessage> messages, List<ToolDefinition> tools) {
        if (!config.hasApiKey()) {
            return CompletableFuture.failedFuture(new ApiAuthException("API key not configured"));
        }

        JsonObject body = buildRequestBody(messages, tools, false);
        HttpRequest request = buildRequest(body);

        return sendWithRetry(request, HttpResponse.BodyHandlers.ofString(), 0)
            .thenApply(response -> {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                JsonArray choices = json.getAsJsonArray("choices");
                if (choices == null || choices.isEmpty()) {
                    return ChatMessage.assistant("");
                }
                JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                if (message == null) {
                    return ChatMessage.assistant("");
                }

                JsonElement content = message.get("content");
                String contentStr = content != null && !content.isJsonNull() ? content.getAsString() : "";

                JsonArray toolCallsArray = message.getAsJsonArray("tool_calls");
                List<ChatMessage.ToolCall> toolCalls = null;
                if (toolCallsArray != null && !toolCallsArray.isEmpty()) {
                    toolCalls = new ArrayList<>();
                    for (JsonElement tcElem : toolCallsArray) {
                        JsonObject tcObj = tcElem.getAsJsonObject();
                        String id = tcObj.has("id") ? tcObj.get("id").getAsString() : "";
                        JsonObject funcObj = tcObj.getAsJsonObject("function");
                        String name = funcObj.has("name") ? funcObj.get("name").getAsString() : "";
                        String arguments = funcObj.has("arguments") ? funcObj.get("arguments").getAsString() : "";
                        toolCalls.add(new ChatMessage.ToolCall(id, new ChatMessage.FunctionCall(name, arguments)));
                    }
                }

                return new ChatMessage("assistant", contentStr, null, toolCalls, null);
            });
    }

    /**
     * 发送流式聊天补全请求，通过回调逐字接收 token。
     *
     * @param messages 消息列表
     * @param tools    工具定义列表（可为 null）
     * @param callback 流式回调
     */
    public void chatCompletionStreaming(List<ChatMessage> messages, List<ToolDefinition> tools, StreamCallback callback) {
        if (!config.hasApiKey()) {
            callback.onError(new ApiAuthException("API key not configured"));
            return;
        }

        JsonObject body = buildRequestBody(messages, tools, true);
        HttpRequest request = buildRequest(body);

        sendWithRetry(request, HttpResponse.BodyHandlers.ofLines(), 0)
            .thenAcceptAsync(response -> {
                try (Stream<String> lines = response.body()) {
                    lines.forEach(line -> {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if ("[DONE]".equals(data.trim())) {
                                return;
                            }
                            try {
                                JsonObject json = gson.fromJson(data, JsonObject.class);
                                JsonArray choices = json.getAsJsonArray("choices");
                                if (choices != null && !choices.isEmpty()) {
                                    JsonObject delta = choices.get(0).getAsJsonObject().getAsJsonObject("delta");
                                    if (delta != null) {
                                        JsonElement content = delta.get("content");
                                        if (content != null && !content.isJsonNull()) {
                                            String token = content.getAsString();
                                            if (token != null && !token.isEmpty()) {
                                                dispatchToMainThread(() -> callback.onToken(token));
                                            }
                                        }
                                    }
                                }
                            } catch (JsonSyntaxException e) {
                                // 跳过格式错误的 SSE 行
                            }
                        }
                    });
                    dispatchToMainThread(callback::onComplete);
                }
            }, executor)
            .exceptionally(throwable -> {
                Throwable cause = unwrap(throwable);
                dispatchToMainThread(() -> callback.onError(cause));
                return null;
            });
    }

    JsonObject buildRequestBody(List<ChatMessage> messages, List<ToolDefinition> tools, boolean stream) {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.getModel());
        body.addProperty("temperature", config.getTemperature());
        body.addProperty("max_tokens", config.getMaxTokens());
        body.addProperty("stream", stream);

        JsonArray messagesArray = new JsonArray();
        for (ChatMessage msg : messages) {
            messagesArray.add(toRequestMessage(msg));
        }
        body.add("messages", messagesArray);

        if (tools != null && !tools.isEmpty()) {
            JsonArray toolsArray = new JsonArray();
            for (ToolDefinition tool : tools) {
                toolsArray.add(tool.toJsonObject());
            }
            body.add("tools", toolsArray);
        }

        return body;
    }

    private JsonObject toRequestMessage(ChatMessage msg) {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", msg.role());

        if (msg.content() != null && !msg.content().isEmpty()) {
            obj.addProperty("content", msg.content());
        } else if ("assistant".equals(msg.role()) && msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
            obj.add("content", JsonNull.INSTANCE);
        } else {
            obj.addProperty("content", "");
        }

        if (msg.toolCallId() != null) {
            obj.addProperty("tool_call_id", msg.toolCallId());
        }

        if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
            JsonArray toolCallsArray = new JsonArray();
            for (ChatMessage.ToolCall tc : msg.toolCalls()) {
                JsonObject tcObj = new JsonObject();
                tcObj.addProperty("id", tc.id());
                tcObj.addProperty("type", "function");
                JsonObject funcObj = new JsonObject();
                funcObj.addProperty("name", tc.function().name());
                funcObj.addProperty("arguments", tc.function().arguments());
                tcObj.add("function", funcObj);
                toolCallsArray.add(tcObj);
            }
            obj.add("tool_calls", toolCallsArray);
        }

        return obj;
    }

    private HttpRequest buildRequest(JsonObject body) {
        String url = config.getBaseUrl();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.endsWith("/chat/completions")) {
            url = url + "/chat/completions";
        }
        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + config.getApiKey())
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(config.getTimeout()))
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
            .build();
    }

    private <T> CompletableFuture<HttpResponse<T>> sendWithRetry(HttpRequest request,
                                                                  HttpResponse.BodyHandler<T> handler,
                                                                  int retryCount) {
        return httpSender.sendAsync(request, handler)
            .thenCompose(response -> handleResponse(response, request, handler, retryCount))
            .handle((response, throwable) -> {
                if (throwable != null) {
                    Throwable cause = unwrap(throwable);
                    if (cause instanceof java.net.http.HttpTimeoutException) {
                        throw new ApiTimeoutException("Request timed out", cause);
                    }
                    if (cause instanceof RuntimeException) {
                        throw (RuntimeException) cause;
                    }
                    throw new RuntimeException(cause);
                }
                return response;
            });
    }

    private <T> CompletableFuture<HttpResponse<T>> handleResponse(HttpResponse<T> response,
                                                                   HttpRequest request,
                                                                   HttpResponse.BodyHandler<T> handler,
                                                                   int retryCount) {
        int status = response.statusCode();
        if (status == 401) {
            return CompletableFuture.failedFuture(new ApiAuthException("Invalid API key (HTTP 401)"));
        }
        if (status == 429) {
            if (retryCount < 3) {
                long delayMs = 1000L * (1L << retryCount);
                return sleepAsync(delayMs).thenCompose(v -> sendWithRetry(request, handler, retryCount + 1));
            }
            return CompletableFuture.failedFuture(new ApiRateLimitException("Rate limited after " + retryCount + " retries"));
        }
        if (status >= 500) {
            if (retryCount < 1) {
                return sleepAsync(500).thenCompose(v -> sendWithRetry(request, handler, retryCount + 1));
            }
        }
        if (status >= 400) {
            return CompletableFuture.failedFuture(new RuntimeException("API error: HTTP " + status));
        }
        return CompletableFuture.completedFuture(response);
    }

    private CompletableFuture<Void> sleepAsync(long delayMs) {
        return delayer.apply(delayMs);
    }

    private void dispatchToMainThread(Runnable runnable) {
        if (mainThreadExecutor != null) {
            mainThreadExecutor.accept(runnable);
        } else {
            runnable.run();
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException || throwable instanceof ExecutionException) {
            return throwable.getCause() != null ? throwable.getCause() : throwable;
        }
        return throwable;
    }
}

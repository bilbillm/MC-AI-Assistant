package com.lumoren.agentchat.ai;

/**
 * 当 API 速率限制触发且重试耗尽时抛出（HTTP 429）。
 */
public class ApiRateLimitException extends RuntimeException {
    public ApiRateLimitException(String message) {
        super(message);
    }

    public ApiRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}

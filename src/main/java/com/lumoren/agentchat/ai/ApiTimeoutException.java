package com.lumoren.agentchat.ai;

/**
 * 当 HTTP 请求超时时抛出。
 */
public class ApiTimeoutException extends RuntimeException {
    public ApiTimeoutException(String message) {
        super(message);
    }

    public ApiTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}

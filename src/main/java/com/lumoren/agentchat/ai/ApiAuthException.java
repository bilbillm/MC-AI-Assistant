package com.lumoren.agentchat.ai;

/**
 * 当 API 认证失败时抛出（HTTP 401 或 API 密钥未配置）。
 * 此异常不会触发重试。
 */
public class ApiAuthException extends RuntimeException {
    public ApiAuthException(String message) {
        super(message);
    }

    public ApiAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}

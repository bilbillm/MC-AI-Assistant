package com.lumoren.agentchat.model;

/**
 * 工具调用执行结果记录。
 * <p>
 * 对应 OpenAI API 中 tool 角色的 content 消息结构，
 * 用于将函数执行结果返回给 AI 模型。
 *
 * @param toolCallId 关联的工具调用 ID（与 ChatMessage 中的 tool_call_id 对应）
 * @param content    工具执行返回的文本结果
 */
public record ToolResult(
        String toolCallId,
        String content
) {}

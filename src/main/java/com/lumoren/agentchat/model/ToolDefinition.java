package com.lumoren.agentchat.model;

import com.google.gson.JsonObject;

/**
 * OpenAI Function Calling 的工具定义记录。
 * <p>
 * 用于向 AI 模型注册可调用工具，遵循 OpenAI 的 function calling 格式：
 * <pre>
 * {
 *   "type": "function",
 *   "function": {
 *     "name": "tool_name",
 *     "description": "工具描述",
 *     "parameters": { ... JSON Schema ... }
 *   }
 * }
 * </pre>
 *
 * @param name       工具名称（如 "query_item"）
 * @param description 工具功能描述（影响 AI 是否选择调用此工具）
 * @param parameters  JSON Schema 格式的参数定义
 */
public record ToolDefinition(
        String name,
        String description,
        JsonObject parameters
) {

    /**
     * 转换为 OpenAI 兼容的 JSON 对象。
     *
     * @return 包含 type 和 function 字段的 JsonObject
     */
    public JsonObject toJsonObject() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "function");

        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("description", description);
        function.add("parameters", parameters);

        obj.add("function", function);
        return obj;
    }
}

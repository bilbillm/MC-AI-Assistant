package com.lumoren.agentchat.model;

import com.google.gson.JsonObject;

import java.util.UUID;

/**
 * A user-facing action attached to an {@link ActionCard}.
 *
 * @param id      stable action ID for UI callbacks
 * @param label   short display label
 * @param kind    action category
 * @param payload JSON parameters needed to execute the action
 */
public record AssistantAction(
        String id,
        String label,
        Kind kind,
        JsonObject payload
) {
    public AssistantAction {
        id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        payload = payload == null ? new JsonObject() : payload;
    }

    public enum Kind {
        OPEN_RECIPE,
        OPEN_USAGE,
        MARK_TASK_DONE,
        MARK_TASK_ACTIVE,
        SET_CURRENT_TASK,
        SEARCH_MORE,
        CANCEL_PROJECT,
        PIN_TASK
    }

    public JsonObject toJsonObject() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("label", label);
        obj.addProperty("kind", kind.name());
        obj.add("payload", payload);
        return obj;
    }

    /**
     * Parses an action from JSON, failing closed: unknown, missing, or malformed
     * kinds yield {@code null} so the caller can drop the action instead of
     * accidentally mapping it onto an executable kind.
     *
     * @return the parsed action, or {@code null} when it must be ignored
     */
    public static AssistantAction fromJsonObject(JsonObject obj) {
        if (obj == null) {
            return null;
        }
        Kind kind = obj.has("kind") && !obj.get("kind").isJsonNull()
                ? parseKind(obj.get("kind").getAsString()) : null;
        if (kind == null) {
            return null;
        }
        String id = obj.has("id") && !obj.get("id").isJsonNull()
                ? obj.get("id").getAsString() : null;
        String label = obj.has("label") && !obj.get("label").isJsonNull()
                ? obj.get("label").getAsString() : "";
        JsonObject payload = obj.has("payload") && obj.get("payload").isJsonObject()
                ? obj.getAsJsonObject("payload") : new JsonObject();
        return new AssistantAction(id, label, kind, payload);
    }

    private static Kind parseKind(String value) {
        try {
            return Kind.valueOf(value);
        } catch (RuntimeException e) {
            return null;
        }
    }
}

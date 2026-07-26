package com.lumoren.agentchat.ui.action;

import com.google.gson.JsonObject;
import com.lumoren.agentchat.model.AssistantAction;

/**
 * Per-kind payload schema checks for {@link AssistantAction}.
 * Validation is fail-closed: an action that does not carry the payload its
 * kind requires is reported invalid and must not execute.
 */
public final class AssistantActionValidator {

    private AssistantActionValidator() {
    }

    public record ValidationResult(boolean valid, String errorKey) {
        private static final ValidationResult OK = new ValidationResult(true, null);

        public static ValidationResult ok() {
            return OK;
        }

        public static ValidationResult invalid(String errorKey) {
            return new ValidationResult(false, errorKey);
        }
    }

    public static ValidationResult validate(AssistantAction action) {
        if (action == null || action.kind() == null) {
            return ValidationResult.invalid(com.lumoren.agentchat.i18n.I18nKeys.ACTION_ERROR_UNKNOWN_ACTION);
        }
        JsonObject payload = action.payload();
        return switch (action.kind()) {
            case OPEN_RECIPE, OPEN_USAGE -> hasItemTarget(payload)
                    ? ValidationResult.ok()
                    : ValidationResult.invalid(com.lumoren.agentchat.i18n.I18nKeys.ACTION_ERROR_MISSING_ITEM);
            case MARK_TASK_DONE, MARK_TASK_ACTIVE, SET_CURRENT_TASK, PIN_TASK -> hasTaskIndex(payload)
                    ? ValidationResult.ok()
                    : ValidationResult.invalid(com.lumoren.agentchat.i18n.I18nKeys.ACTION_ERROR_MISSING_TASK);
            case SEARCH_MORE -> hasQuery(payload)
                    ? ValidationResult.ok()
                    : ValidationResult.invalid(com.lumoren.agentchat.i18n.I18nKeys.ACTION_ERROR_MISSING_QUERY);
            case CANCEL_PROJECT -> ValidationResult.ok();
        };
    }

    static String itemTarget(JsonObject payload) {
        String itemName = stringField(payload, "item_name");
        if (!itemName.isBlank()) {
            return itemName;
        }
        String itemId = stringField(payload, "item_id");
        if (!itemId.isBlank()) {
            return itemId;
        }
        return stringField(payload, "itemId");
    }

    static String query(JsonObject payload) {
        return stringField(payload, "query");
    }

    static int taskIndex(JsonObject payload) {
        if (payload == null || !payload.has("task_index") || payload.get("task_index").isJsonNull()) {
            return -1;
        }
        try {
            return payload.get("task_index").getAsInt();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static boolean hasItemTarget(JsonObject payload) {
        return !itemTarget(payload).isBlank();
    }

    private static boolean hasQuery(JsonObject payload) {
        return !query(payload).isBlank();
    }

    private static boolean hasTaskIndex(JsonObject payload) {
        return taskIndex(payload) >= 1;
    }

    private static String stringField(JsonObject payload, String name) {
        if (payload == null || !payload.has(name) || payload.get(name).isJsonNull()) {
            return "";
        }
        try {
            return payload.get(name).getAsString().strip();
        } catch (RuntimeException e) {
            return "";
        }
    }
}

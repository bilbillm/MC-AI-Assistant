package com.lumoren.agentchat.ui.action;

/**
 * Outcome of executing an {@link com.lumoren.agentchat.model.AssistantAction}.
 * The UI must rely on this explicit result instead of guessing from exceptions.
 *
 * @param status  execution status
 * @param message localized, player-visible message (may be empty for ignored no-ops)
 */
public record ActionExecutionResult(Status status, String message) {

    public enum Status {
        SUCCESS,
        FAILURE,
        IGNORED
    }

    public boolean success() {
        return status == Status.SUCCESS;
    }

    public static ActionExecutionResult success(String message) {
        return new ActionExecutionResult(Status.SUCCESS, message);
    }

    public static ActionExecutionResult failure(String message) {
        return new ActionExecutionResult(Status.FAILURE, message);
    }

    public static ActionExecutionResult ignored(String message) {
        return new ActionExecutionResult(Status.IGNORED, message);
    }
}

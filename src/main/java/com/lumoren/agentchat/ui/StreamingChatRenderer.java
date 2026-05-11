package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.ai.AIChatService;
import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.model.ConversationThread;

/**
 * Manages incremental streaming of AI responses and bridges between
 * {@link AIChatService.ChatCallback} and the UI rendering layer.
 * <p>
 * Lifecycle for one message:
 * <ol>
 *   <li>{@link #onThinking(String)} — AI is computing or running a tool</li>
 *   <li>{@link #onToken(String)} — tokens arrive incrementally</li>
 *   <li>{@link #onComplete(String)} — finalize the AI message in history</li>
 *   <li>{@link #onError(String)} — something went wrong</li>
 * </ol>
 * <p>
 * The UI can query {@link #getCurrentContent()} to render the in-flight
 * response and {@link #getStatusText()} for status indicators like
 * "AI is thinking..." or "querying inventory...".
 */
public class StreamingChatRenderer implements AIChatService.ChatCallback {

    private final ConversationThread thread;
    private final Runnable onUpdate;

    private final StringBuilder currentContent = new StringBuilder();
    private final StringBuilder reasoningContent = new StringBuilder();
    private String statusText;
    private String errorText;
    private boolean streaming;
    private boolean completed;
    private boolean hasError;

    /**
     * @param thread the conversation thread to append completed messages to
     * @param onUpdate callback invoked after each state change to trigger re-render
     */
    public StreamingChatRenderer(ConversationThread thread, Runnable onUpdate) {
        this.thread = thread;
        this.onUpdate = onUpdate;
        this.streaming = false;
        this.completed = false;
        this.hasError = false;
    }

    /**
     * Start tracking a new streaming response.
     */
    public void startStreaming() {
        this.currentContent.setLength(0);
        this.reasoningContent.setLength(0);
        this.statusText = null; // no "thinking" placeholder — reasoning content replaces it
        this.errorText = null;
        this.streaming = true;
        this.completed = false;
        this.hasError = false;
        notifyUpdate();
    }

    // ==================== ChatCallback implementation ====================

    @Override
    public void onToken(String token) {
        if (!streaming) {
            startStreaming();
        }
        currentContent.append(token);
        this.statusText = null;
        notifyUpdate();
    }

    @Override
    public void onReasoningToken(String token) {
        if (!streaming) {
            startStreaming();
        }
        reasoningContent.append(token);
        notifyUpdate();
    }

    @Override
    public void onThinking(String status) {
        // Strip trailing dots so animated dots are clean
        this.statusText = status.replaceAll("\\.+$", "");
        notifyUpdate();
    }

    @Override
    public void onError(String error) {
        this.errorText = error;
        this.hasError = true;
        this.streaming = false;
        this.completed = true;
        this.statusText = null; // clear thinking/status so only red error shows

        // Add error message to conversation thread
        thread.addMessage(new ChatMessage("error", error, null, null, null));
        notifyUpdate();
    }

    @Override
    public void onComplete(String fullResponse) {
        this.streaming = false;
        this.completed = true;
        this.statusText = null;

        // Add the completed assistant message with reasoning content
        String reasoning = reasoningContent.length() > 0 ? reasoningContent.toString() : null;
        thread.addMessage(ChatMessage.assistant(fullResponse, reasoning));
        notifyUpdate();
    }

    // ==================== State queries ====================

    /**
     * @return the currently accumulated streaming content (incomplete AI response)
     */
    public String getCurrentContent() {
        return currentContent.toString();
    }

    /** @return the currently accumulated reasoning content (DeepSeek thinking) */
    public String getReasoningContent() {
        return reasoningContent.toString();
    }

    /** @return true if there is reasoning content to display */
    public boolean hasReasoningContent() {
        return reasoningContent.length() > 0;
    }

    /**
     * @return current status text with animated dots (e.g. "AI is thinking." → ".." → "...")
     */
    public String getStatusText() {
        if (statusText == null) return null;
        // Animate dots: cycle through "", ".", "..", "..." every 500ms
        int dotCount = (int) (System.currentTimeMillis() % 2000 / 500);
        return statusText + ".".repeat(dotCount);
    }

    /**
     * @return error message if streaming failed, or null
     */
    public String getErrorText() {
        return errorText;
    }

    /**
     * @return true if tokens are currently being received
     */
    public boolean isStreaming() {
        return streaming;
    }

    /**
     * @return true if the streaming has completed (either successfully or with error)
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * @return true if the streaming ended with an error
     */
    public boolean hasError() {
        return hasError;
    }

    /**
     * @return true if there is accumulated streaming content to display
     */
    public boolean hasStreamingContent() {
        return streaming && currentContent.length() > 0;
    }

    /**
     * @return true if a status message should be shown (loading / thinking)
     */
    public boolean hasStatus() {
        return statusText != null && !statusText.isEmpty();
    }

    // ==================== Internal ====================

    private void notifyUpdate() {
        if (onUpdate != null) {
            onUpdate.run();
        }
    }
}

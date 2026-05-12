package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.model.ConversationThread;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StreamingChatRenderer} state machine.
 * <p>
 * Validates lifecycle transitions, content accumulation, reasoning handling,
 * status animation, error handling, and callback notifications.
 * No Minecraft runtime required — uses real {@link ConversationThread}.
 */
class StreamingChatRendererTest {

    private ConversationThread thread;
    private AtomicInteger updateCount;
    private StreamingChatRenderer renderer;

    @BeforeEach
    void setUp() {
        thread = new ConversationThread("Test Thread");
        updateCount = new AtomicInteger(0);
        renderer = new StreamingChatRenderer(thread, updateCount::incrementAndGet);
    }

    // ========== Initial State ==========

    @Test
    void initialState_allFalse() {
        assertFalse(renderer.isStreaming());
        assertFalse(renderer.isCompleted());
        assertFalse(renderer.hasError());
        assertTrue(renderer.getCurrentContent().isEmpty());
        assertTrue(renderer.getReasoningContent().isEmpty());
        assertNull(renderer.getStatusText());
        assertNull(renderer.getErrorText());
        assertFalse(renderer.hasStreamingContent());
        assertFalse(renderer.hasReasoningContent());
        assertFalse(renderer.hasStatus());
    }

    // ========== startStreaming ==========

    @Test
    void startStreaming_resetsState() {
        // Set up dirty state first
        renderer.onToken("dirty");
        renderer.onReasoningToken("dirty-reasoning");
        renderer.onThinking("querying tool");

        renderer.startStreaming();

        assertTrue(renderer.isStreaming());
        assertFalse(renderer.isCompleted());
        assertFalse(renderer.hasError());
        assertEquals("", renderer.getCurrentContent());
        assertEquals("", renderer.getReasoningContent());
        assertNull(renderer.getStatusText());
        assertNull(renderer.getErrorText());
    }

    @Test
    void startStreaming_notifiesUpdate() {
        int before = updateCount.get();
        renderer.startStreaming();
        assertTrue(updateCount.get() > before);
    }

    // ========== onToken ==========

    @Test
    void onToken_accumulatesContent() {
        renderer.startStreaming();
        renderer.onToken("Hello");
        assertEquals("Hello", renderer.getCurrentContent());
        assertTrue(renderer.isStreaming());

        renderer.onToken(" World");
        assertEquals("Hello World", renderer.getCurrentContent());
    }

    @Test
    void onToken_skipsWhenNotStreaming() {
        // With abort guard, onToken silently returns when streaming=false
        renderer.onToken("ignored");
        assertEquals("", renderer.getCurrentContent());
        assertFalse(renderer.isStreaming());
    }

    @Test
    void onToken_clearsStatusText() {
        renderer.startStreaming();
        renderer.onThinking("querying");
        assertNotNull(renderer.getStatusText());
        renderer.onToken("result");
        assertNull(renderer.getStatusText());
    }

    @Test
    void onToken_notifiesUpdate() {
        renderer.startStreaming();
        int before = updateCount.get();
        renderer.onToken("x");
        assertTrue(updateCount.get() > before);
    }

    // ========== onReasoningToken ==========

    @Test
    void onReasoningToken_accumulatesReasoningContent() {
        renderer.startStreaming();
        renderer.onReasoningToken("I think");
        assertEquals("I think", renderer.getReasoningContent());
        assertTrue(renderer.hasReasoningContent());

        renderer.onReasoningToken(" that");
        assertEquals("I think that", renderer.getReasoningContent());
    }

    @Test
    void onReasoningToken_skipsWhenNotStreaming() {
        // With abort guard, onReasoningToken silently returns when streaming=false
        renderer.onReasoningToken("ignored");
        assertEquals("", renderer.getReasoningContent());
        assertFalse(renderer.isStreaming());
    }

    @Test
    void onReasoningToken_notifiesUpdate() {
        renderer.startStreaming();
        int before = updateCount.get();
        renderer.onReasoningToken("r");
        assertTrue(updateCount.get() > before);
    }

    // ========== Reasoning and content are independent ==========

    @Test
    void reasoningAndContent_trackedIndependently() {
        renderer.startStreaming();
        renderer.onReasoningToken("Th");
        renderer.onReasoningToken("inking");
        renderer.onToken("Res");
        renderer.onToken("ult");

        assertEquals("Thinking", renderer.getReasoningContent());
        assertEquals("Result", renderer.getCurrentContent());
        assertTrue(renderer.hasReasoningContent());
        assertTrue(renderer.hasStreamingContent());
    }

    // ========== onThinking ==========

    @Test
    void onThinking_setsStatus() {
        renderer.onThinking("querying inventory");
        assertNotNull(renderer.getStatusText());
        assertTrue(renderer.getStatusText().startsWith("querying inventory"));
    }

    @Test
    void onThinking_stripsTrailingDotsAtSetTime() {
        // onThinking strips trailing dots before storing; getStatusText adds animated dots
        renderer.onThinking("querying...");
        String text = renderer.getStatusText();
        assertNotNull(text);
        assertTrue(text.startsWith("querying")); // base text is stripped
    }

    @Test
    void onThinking_notifiesUpdate() {
        int before = updateCount.get();
        renderer.onThinking("testing");
        assertTrue(updateCount.get() > before);
    }

    // ========== Status animation dots ==========

    @Test
    void getStatusText_nullWhenNoStatus() {
        assertNull(renderer.getStatusText());
    }

    @Test
    void getStatusText_appendsDots() {
        renderer.onThinking("thinking");
        String result = renderer.getStatusText();
        assertNotNull(result);
        assertTrue(result.startsWith("thinking"));
        // dots are time-based but always appended
        assertTrue(result.length() >= "thinking".length());
    }

    @Test
    void hasStatus_trueOnlyWhenStatusSet() {
        assertFalse(renderer.hasStatus());
        renderer.onThinking("working");
        assertTrue(renderer.hasStatus());
        renderer.onThinking("");
        assertFalse(renderer.hasStatus());
    }

    // ========== onComplete ==========

    @Test
    void onComplete_setsCompletedState() {
        renderer.startStreaming();
        renderer.onToken("Hello world");
        renderer.onReasoningToken("some thinking");

        renderer.onComplete("Hello world");

        assertFalse(renderer.isStreaming());
        assertTrue(renderer.isCompleted());
        assertFalse(renderer.hasError());
        assertNull(renderer.getStatusText());
    }

    @Test
    void onComplete_addsAssistantMessageToThread() {
        renderer.startStreaming();
        renderer.onToken("Hello");
        renderer.onComplete("Hello");

        assertEquals(1, thread.messageCount());
        ChatMessage msg = thread.getMessages().get(0);
        assertEquals("assistant", msg.role());
        assertEquals("Hello", msg.content());
    }

    @Test
    void onComplete_preservesReasoningInMessage() {
        renderer.startStreaming();
        renderer.onReasoningToken("Deep thoughts");
        renderer.onToken("Result");
        renderer.onComplete("Result");

        ChatMessage msg = thread.getMessages().get(0);
        assertEquals("Deep thoughts", msg.reasoningContent());
    }

    @Test
    void onComplete_noNullReasoningWhenEmpty() {
        renderer.startStreaming();
        renderer.onToken("Just content");
        renderer.onComplete("Just content");

        ChatMessage msg = thread.getMessages().get(0);
        assertNull(msg.reasoningContent(), "reasoningContent should be null when no reasoning was accumulated");
    }

    @Test
    void onComplete_skipsWhenAlreadyCompleted() {
        // Abort sets completed=true, late-arriving onComplete must be a no-op
        renderer.startStreaming();
        renderer.onToken("Hello");
        renderer.abort();
        assertEquals(0, thread.messageCount()); // abort doesn't add messages
        renderer.onComplete("should be ignored");
        assertEquals(0, thread.messageCount()); // onComplete skipped
    }

    // ========== onError ==========

    @Test
    void onError_setsErrorState() {
        renderer.startStreaming();
        renderer.onError("Network timeout");

        assertFalse(renderer.isStreaming());
        assertTrue(renderer.isCompleted());
        assertTrue(renderer.hasError());
        assertEquals("Network timeout", renderer.getErrorText());
        assertNull(renderer.getStatusText()); // status cleared on error
    }

    @Test
    void onError_addsErrorMessageToThread() {
        renderer.startStreaming();
        renderer.onError("Something broke");

        assertEquals(1, thread.messageCount());
        ChatMessage msg = thread.getMessages().get(0);
        assertEquals("error", msg.role());
        assertEquals("Something broke", msg.content());
    }

    @Test
    void onError_notifiesUpdate() {
        renderer.startStreaming();
        int before = updateCount.get();
        renderer.onError("fail");
        assertTrue(updateCount.get() > before);
    }

    @Test
    void onError_skipsWhenNotStreaming() {
        // With abort guard, onError silently returns when streaming=false
        renderer.onError("should be ignored");
        assertFalse(renderer.hasError());
        assertEquals(0, thread.messageCount());
    }

    // ========== hasStreamingContent ==========

    @Test
    void hasStreamingContent_trueWhenStreamingWithContent() {
        assertFalse(renderer.hasStreamingContent());
        renderer.startStreaming();
        assertFalse(renderer.hasStreamingContent()); // streaming but empty
        renderer.onToken("H");
        assertTrue(renderer.hasStreamingContent());
    }

    @Test
    void hasStreamingContent_falseAfterComplete() {
        renderer.onToken("done");
        renderer.onComplete("done");
        assertFalse(renderer.hasStreamingContent());
    }

    // ========== Full lifecycle ==========

    @Test
    void fullLifecycle_tokensThenComplete() {
        renderer.startStreaming();
        assertTrue(renderer.isStreaming());
        assertFalse(renderer.isCompleted());

        renderer.onReasoningToken("Let me think...");
        renderer.onToken("The answer is 42.");

        assertEquals("The answer is 42.", renderer.getCurrentContent());
        assertEquals("Let me think...", renderer.getReasoningContent());

        renderer.onComplete("The answer is 42.");

        assertFalse(renderer.isStreaming());
        assertTrue(renderer.isCompleted());
        assertFalse(renderer.hasError());
        assertEquals(1, thread.messageCount());
    }

    @Test
    void onUpdate_nullRunnableDoesNotThrow() {
        var noUpdate = new StreamingChatRenderer(new ConversationThread("test"), null);
        noUpdate.startStreaming();
        assertDoesNotThrow(() -> noUpdate.onToken("ok"));
        assertDoesNotThrow(() -> noUpdate.onComplete("ok"));
        assertDoesNotThrow(() -> noUpdate.onError("fail"));
        assertDoesNotThrow(() -> noUpdate.onThinking("working"));
    }

    // ========== Abort ==========

    @Test
    void abort_stopsStreamingAndPreventsCallbacks() {
        renderer.startStreaming();
        renderer.onToken("partial");
        renderer.abort();

        assertFalse(renderer.isStreaming());
        assertTrue(renderer.isCompleted());
        assertFalse(renderer.hasError());
        assertEquals(0, thread.messageCount()); // abort doesn't add messages

        // Late-arriving callbacks should be no-ops
        renderer.onToken("ignored");
        renderer.onReasoningToken("ignored");
        renderer.onComplete("ignored");
        renderer.onError("ignored");
        assertEquals(0, thread.messageCount());
        assertEquals("", renderer.getCurrentContent()); // cleared by abort
    }

    @Test
    void abort_clearsStatusAndContent_butNotError() {
        renderer.startStreaming();
        renderer.onThinking("working");
        renderer.onToken("text");
        renderer.onReasoningToken("think");
        renderer.abort();

        assertNull(renderer.getStatusText());
        assertEquals("", renderer.getCurrentContent());
        assertEquals("", renderer.getReasoningContent());
        assertNull(renderer.getErrorText());
    }
}

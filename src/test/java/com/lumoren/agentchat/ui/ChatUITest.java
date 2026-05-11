package com.lumoren.agentchat.ui;

import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.model.ConversationThread;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for chat UI components ({@link ChatMessageWidget}, {@link MessageListWidget},
 * {@link StreamingChatRenderer}).
 * <p>
 * These tests verify structural correctness and state management using
 * mocked Minecraft rendering dependencies.
 */
@ExtendWith(MockitoExtension.class)
class ChatUITest {

    @Mock
    private GuiGraphics graphics;
    @Mock
    private Font font;

    @BeforeEach
    void setUp() {
        lenient().when(font.lineHeight).thenReturn(9);
        lenient().when(font.wordWrapHeight(anyString(), anyInt())).thenReturn(18);
    }

    // ==================== ChatMessageWidget Tests ====================

    @Test
    void testChatMessageWidgetUserRole() {
        ChatMessage msg = ChatMessage.user("Hello, AI!");
        ChatMessageWidget widget = new ChatMessageWidget(msg, 200, null);
        assertTrue(widget.isUser());
        assertFalse(widget.isError());
        assertEquals(msg, widget.getMessage());
    }

    @Test
    void testChatMessageWidgetAssistantRole() {
        ChatMessage msg = ChatMessage.assistant("Hello, player!");
        ChatMessageWidget widget = new ChatMessageWidget(msg, 200, null);
        assertFalse(widget.isUser());
        assertFalse(widget.isError());
    }

    @Test
    void testChatMessageWidgetErrorRole() {
        ChatMessage msg = new ChatMessage("error", "Something went wrong", null, null, null);
        ChatMessageWidget widget = new ChatMessageWidget(msg, 200, null);
        assertTrue(widget.isError());
        assertFalse(widget.isUser());
    }

    @Test
    void testChatMessageWidgetHeight() {
        ChatMessage msg = ChatMessage.assistant("Test message");
        ChatMessageWidget widget = new ChatMessageWidget(msg, 200, null);
        assertTrue(widget.getHeight(font, 200) > 0, "Widget height should be positive");
    }

    @Test
    void testChatMessageWidgetMinWidth() {
        ChatMessage msg = ChatMessage.user("Hi");
        ChatMessageWidget widget = new ChatMessageWidget(msg, 10, null); // below minimum
        assertTrue(widget.getHeight(font, 10) > 0, "Should handle very narrow widths");
    }

    @Test
    void testChatMessageWidgetRenderDoesNotThrow() {
        ChatMessage msg = ChatMessage.assistant("Render test");
        ChatMessageWidget widget = new ChatMessageWidget(msg, 200, null);
        assertDoesNotThrow(() -> widget.render(graphics, 0, 0, 200, font));
    }

    @Test
    void testChatMessageWidgetUserRenderDoesNotThrow() {
        ChatMessage msg = ChatMessage.user("User message");
        ChatMessageWidget widget = new ChatMessageWidget(msg, 200, null);
        assertDoesNotThrow(() -> widget.render(graphics, 0, 0, 200, font));
    }

    @Test
    void testChatMessageWidgetErrorRenderDoesNotThrow() {
        ChatMessage msg = new ChatMessage("error", "Error message", null, null, null);
        ChatMessageWidget widget = new ChatMessageWidget(msg, 200, null);
        assertDoesNotThrow(() -> widget.render(graphics, 0, 0, 200, font));
    }

    @Test
    void testChatMessageWidgetMultipleMessages() {
        ChatMessage msg1 = ChatMessage.user("User text");
        ChatMessage msg2 = ChatMessage.assistant("AI response");
        ChatMessage msg3 = new ChatMessage("error", "Error", null, null, null);

        ChatMessageWidget w1 = new ChatMessageWidget(msg1, 200, null);
        ChatMessageWidget w2 = new ChatMessageWidget(msg2, 200, null);
        ChatMessageWidget w3 = new ChatMessageWidget(msg3, 200, null);

        assertTrue(w1.isUser());
        assertFalse(w2.isUser());
        assertTrue(w3.isError());
    }

    // ==================== StreamingChatRenderer Tests ====================

    @Test
    void testStreamingRendererInitialState() {
        ConversationThread thread = new ConversationThread("test");
        StreamingChatRenderer renderer = new StreamingChatRenderer(thread, () -> {});

        assertFalse(renderer.isStreaming());
        assertFalse(renderer.isCompleted());
        assertFalse(renderer.hasError());
        assertFalse(renderer.hasStreamingContent());
        assertFalse(renderer.hasStatus());
        assertNull(renderer.getStatusText());
        assertEquals("", renderer.getCurrentContent());
    }

    @Test
    void testStreamingRendererOnToken() {
        ConversationThread thread = new ConversationThread("test");
        StreamingChatRenderer renderer = new StreamingChatRenderer(thread, () -> {});

        renderer.onToken("Hello");
        assertTrue(renderer.isStreaming());
        assertEquals("Hello", renderer.getCurrentContent());

        renderer.onToken(" world");
        assertEquals("Hello world", renderer.getCurrentContent());
    }

    @Test
    void testStreamingRendererOnComplete() {
        ConversationThread thread = new ConversationThread("test");
        StreamingChatRenderer renderer = new StreamingChatRenderer(thread, () -> {});

        renderer.startStreaming();
        renderer.onToken("Final response");
        renderer.onComplete("Final response");

        assertFalse(renderer.isStreaming());
        assertTrue(renderer.isCompleted());
        assertEquals(1, thread.getMessages().size(), "Thread should have the AI message");
        assertEquals("assistant", thread.getMessages().get(0).role());
    }

    @Test
    void testStreamingRendererOnError() {
        ConversationThread thread = new ConversationThread("test");
        StreamingChatRenderer renderer = new StreamingChatRenderer(thread, () -> {});

        renderer.onError("API Error");

        assertTrue(renderer.hasError());
        assertFalse(renderer.isStreaming());
        assertEquals("API Error", renderer.getErrorText());
        assertEquals(1, thread.getMessages().size(), "Thread should have error message");
        assertEquals("error", thread.getMessages().get(0).role());
    }

    @Test
    void testStreamingRendererOnThinking() {
        ConversationThread thread = new ConversationThread("test");
        StreamingChatRenderer renderer = new StreamingChatRenderer(thread, () -> {});

        renderer.onThinking("querying inventory...");
        assertTrue(renderer.hasStatus());
        assertTrue(renderer.getStatusText().startsWith("querying inventory"));
    }

    @Test
    void testStreamingRendererStartStreaming() {
        ConversationThread thread = new ConversationThread("test");
        StreamingChatRenderer renderer = new StreamingChatRenderer(thread, () -> {});

        renderer.startStreaming();
        assertTrue(renderer.isStreaming());
        assertEquals("", renderer.getCurrentContent());

        renderer.onToken("token");
        assertTrue(renderer.hasStreamingContent());
    }

    @Test
    void testStreamingRendererOnUpdateCalled() {
        final boolean[] updated = {false};
        ConversationThread thread = new ConversationThread("test");
        StreamingChatRenderer renderer = new StreamingChatRenderer(thread, () -> updated[0] = true);

        renderer.onToken("test");
        assertTrue(updated[0], "onUpdate callback should be invoked");
    }

    @Test
    void testStreamingRendererMultipleTokens() {
        ConversationThread thread = new ConversationThread("test");
        StreamingChatRenderer renderer = new StreamingChatRenderer(thread, () -> {});

        renderer.onToken("Token 1 ");
        renderer.onToken("Token 2 ");
        renderer.onToken("Token 3");

        assertEquals("Token 1 Token 2 Token 3", renderer.getCurrentContent());
    }

    @Test
    void testStreamingRendererOnTokenWithoutStartStreaming() {
        ConversationThread thread = new ConversationThread("test");
        StreamingChatRenderer renderer = new StreamingChatRenderer(thread, () -> {});

        // onToken should auto-start streaming
        renderer.onToken("auto");
        assertTrue(renderer.isStreaming());
    }

    @Test
    void testStreamingRendererStatusClearedOnToken() {
        ConversationThread thread = new ConversationThread("test");
        StreamingChatRenderer renderer = new StreamingChatRenderer(thread, () -> {});

        renderer.onThinking("thinking...");
        assertTrue(renderer.hasStatus());

        renderer.onToken("response");
        assertFalse(renderer.hasStatus(), "Status should be cleared when token arrives");
    }

    // ==================== ConversationThread + Streaming Integration ====================

    @Test
    void testFullConversationFlow() {
        ConversationThread thread = new ConversationThread("test");
        StreamingChatRenderer renderer = new StreamingChatRenderer(thread, () -> {});

        // User message
        thread.addMessage(ChatMessage.user("Build a house"));
        assertEquals(1, thread.getMessages().size());

        // Simulate streaming AI response
        renderer.startStreaming();
        renderer.onToken("I'll ");
        renderer.onToken("help ");
        renderer.onToken("you!");
        renderer.onComplete("I'll help you!");

        assertEquals(2, thread.getMessages().size(), "Thread should have user msg + AI response");
        assertEquals("assistant", thread.getMessages().get(1).role());
        assertEquals("I'll help you!", thread.getMessages().get(1).content());
    }
}

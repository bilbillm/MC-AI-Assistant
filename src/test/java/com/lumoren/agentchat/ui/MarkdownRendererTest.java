package com.lumoren.agentchat.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MarkdownRenderer}.
 * <p>
 * Tests verify correct Markdown parsing into Minecraft {@link Component} trees
 * with appropriate {@link Style} annotations. All tests run without a Minecraft
 * runtime (Component/Style are pure data classes).
 */
class MarkdownRendererTest {

    @Test
    void testPlainText() {
        Component result = MarkdownRenderer.render("Hello world");
        assertEquals("Hello world", result.getString());
    }

    @Test
    void testNullInput() {
        Component result = MarkdownRenderer.render(null);
        assertEquals("", result.getString());
    }

    @Test
    void testBlankInput() {
        Component result = MarkdownRenderer.render("   ");
        assertEquals("", result.getString());
    }

    @Test
    void testEmptyInput() {
        Component result = MarkdownRenderer.render("");
        assertEquals("", result.getString());
    }

    @Test
    void testBold() {
        Component result = MarkdownRenderer.render("Hello **world** here");
        // "Hello ", "world" (bold), " here" — we get the concatenated string
        assertEquals("Hello world here", result.getString());

        // Verify the bold sibling
        MutableComponent mc = (MutableComponent) result;
        var siblings = mc.getSiblings();
        assertEquals(3, siblings.size(), "Should have 3 parts: Hello, world(bold), here");

        assertTrue(siblings.get(1).getStyle().isBold(), "Middle part should be bold");
    }

    @Test
    void testItalic() {
        Component result = MarkdownRenderer.render("Hello *world* here");
        assertEquals("Hello world here", result.getString());

        MutableComponent mc = (MutableComponent) result;
        var siblings = mc.getSiblings();
        assertEquals(3, siblings.size());

        assertTrue(siblings.get(1).getStyle().isItalic(), "Middle part should be italic");
    }

    @Test
    void testBoldAndItalic() {
        Component result = MarkdownRenderer.render("**bold** and *italic*");
        MutableComponent mc = (MutableComponent) result;
        var siblings = mc.getSiblings();

        // 5 parts: "bold"(bold), " and ", "italic"(italic)
        assertTrue(siblings.get(0).getStyle().isBold(), "bold part should be bold");
        assertTrue(siblings.get(2).getStyle().isItalic(), "italic part should be italic");
    }

    @Test
    void testInlineCode() {
        Component result = MarkdownRenderer.render("Use `code` here");
        MutableComponent mc = (MutableComponent) result;
        var siblings = mc.getSiblings();

        assertEquals(3, siblings.size(), "Should have 3 parts: Use, code(mono), here");
        // Inline code gets dark gray color
        assertEquals(ChatFormatting.DARK_GRAY, siblings.get(1).getStyle().getColor());
    }

    @Test
    void testBoldStarAtBoundaryNotConfusedWithItalic() {
        // *** should not create empty bold then italic
        Component result = MarkdownRenderer.render("a *** b");
        // This should just treat *** as characters if parsing fails gracefully
        assertNotNull(result);
    }

    @Test
    void testBulletList() {
        Component result = MarkdownRenderer.render("- Item one\n- Item two");
        String text = result.getString();
        assertTrue(text.contains("\u2022"), "Should contain bullet character");
        assertTrue(text.contains("Item one"), "Should contain first item");
        assertTrue(text.contains("Item two"), "Should contain second item");
    }

    @Test
    void testCodeBlock() {
        String code = "```\nline1\nline2\n```";
        Component result = MarkdownRenderer.render(code);
        String text = result.getString();
        assertTrue(text.contains("line1"), "Code block should contain line1");
        assertTrue(text.contains("line2"), "Code block should contain line2");

        MutableComponent mc = (MutableComponent) result;
        var siblings = mc.getSiblings();
        // Code block text should have dark gray color
        for (var sibling : siblings) {
            if (sibling.getString().contains("line1")) {
                assertEquals(ChatFormatting.DARK_GRAY, sibling.getStyle().getColor(),
                        "Code block text should be dark gray");
                break;
            }
        }
    }

    @Test
    void testCodeBlockWithLanguageHint() {
        // Language hint after ``` should be ignored but code still rendered
        String code = "```java\nSystem.out.println();\n```";
        Component result = MarkdownRenderer.render(code);
        assertTrue(result.getString().contains("System.out.println();"));
    }

    @Test
    void testParagraphSeparation() {
        Component result = MarkdownRenderer.render("First paragraph\n\nSecond paragraph");
        String text = result.getString();
        assertTrue(text.contains("First paragraph"), "Should contain first paragraph");
        assertTrue(text.contains("Second paragraph"), "Should contain second paragraph");
    }

    @Test
    void testNewlinesWithinParagraph() {
        Component result = MarkdownRenderer.render("Line one\nLine two");
        assertTrue(result.getString().contains("Line one"));
        assertTrue(result.getString().contains("Line two"));
    }

    @Test
    void testMixedFormatting() {
        Component result = MarkdownRenderer.render("**bold** and `code` and *italic*");
        MutableComponent mc = (MutableComponent) result;
        var siblings = mc.getSiblings();

        assertEquals(7, siblings.size(), "bold + ' and ' + code + ' and ' + italic");
        assertTrue(siblings.get(0).getStyle().isBold());
        assertEquals(ChatFormatting.DARK_GRAY, siblings.get(2).getStyle().getColor());
        assertTrue(siblings.get(4).getStyle().isItalic());
    }

    @Test
    void testAsteriskBullet() {
        Component result = MarkdownRenderer.render("* Item");
        assertTrue(result.getString().contains("\u2022"), "Bullet should be rendered");
        assertTrue(result.getString().contains("Item"), "Content should be preserved");
    }

    @Test
    void testInlineCodeWithinBold() {
        // Not explicitly required, but shouldn't crash
        Component result = MarkdownRenderer.render("**outer `inner` end**");
        assertNotNull(result);
        assertTrue(result.getString().contains("outer"));
        assertTrue(result.getString().contains("inner"));
    }

    @Test
    void testRenderInlineFormattingDirect() {
        // Test the package-private helper directly
        MutableComponent result = MarkdownRenderer.renderInlineFormatting("**bold** normal *italic*");
        var siblings = result.getSiblings();
        assertEquals(5, siblings.size());
        assertTrue(siblings.get(0).getStyle().isBold());
        assertTrue(siblings.get(2).getStyle().isItalic());
    }
}

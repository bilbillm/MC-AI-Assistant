package com.lumoren.agentchat.ui;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;

/**
 * Lightweight Markdown-to-Component converter.
 * <p>
 * Converts a subset of Markdown formatting into Minecraft {@link Component} trees
 * with appropriate {@link Style} annotations (bold, italic, code formatting).
 * <p>
 * Supported syntax:
 * <ul>
 *   <li>{@code **bold**} — bold text</li>
 *   <li>{@code *italic*} — italic text</li>
 *   <li>{@code ~~strikethrough~~} — strikethrough text</li>
 *   <li>{@code `code`} — inline code (gray)</li>
 *   <li>{@code ```code block```} — fenced code block (dark gray)</li>
 *   <li>{@code [text](url)} — clickable hyperlinks (blue, underlined)</li>
 *   <li>{@code - item} / {@code * item} — bullet list items</li>
 *   <li>{@code 1. item} — numbered list items</li>
 *   <li>{@code > text} — blockquotes (gray left border)</li>
 *   <li>{@code # text}, {@code ## text}, {@code ### text} — headers (bold, gold)</li>
 *   <li>Paragraphs separated by double newlines</li>
 * </ul>
 */
public final class MarkdownRenderer {

    private MarkdownRenderer() {
    }

    /**
     * Convert Markdown text to a Minecraft {@link Component}.
     *
     * @param markdown input Markdown text (may be null or blank)
     * @return rendered Component tree (never null)
     */
    public static Component render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return Component.literal("");
        }

        String[] lines = markdown.split("\n", -1);
        MutableComponent result = Component.literal("");
        boolean inCodeBlock = false;
        StringBuilder codeBlockBuffer = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Toggle code block fence
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    // End code block
                    if (!result.getSiblings().isEmpty() || result.getString().length() > 0) {
                        result.append(Component.literal("\n"));
                    }
                    result.append(renderCodeBlock(codeBlockBuffer.toString()));
                    codeBlockBuffer.setLength(0);
                    inCodeBlock = false;
                } else {
                    inCodeBlock = true;
                    codeBlockBuffer.setLength(0);
                }
                // Add a newline separator after the code block (handled at next append)
                if (i < lines.length - 1) {
                    // Don't add trailing newline — will be added by next line content
                }
                continue;
            }

            if (inCodeBlock) {
                // Accumulate code block content preserving blank lines
                if (codeBlockBuffer.length() > 0) {
                    codeBlockBuffer.append("\n");
                }
                codeBlockBuffer.append(line);
                continue;
            }

            // Normal line processing
            MutableComponent lineComponent;
            if (trimmed.startsWith("### ")) {
                lineComponent = renderHeader(line, 3);
            } else if (trimmed.startsWith("## ")) {
                lineComponent = renderHeader(line, 2);
            } else if (trimmed.startsWith("# ")) {
                lineComponent = renderHeader(line, 1);
            } else if (trimmed.startsWith("> ")) {
                lineComponent = renderBlockquote(line);
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                lineComponent = renderBulletLine(line);
            } else if (trimmed.matches("^\\d+\\.\\s.*")) {
                lineComponent = renderNumberedLine(line);
            } else {
                lineComponent = renderInlineFormatting(line);
            }

            // Append with paragraph separation
            if (!result.getSiblings().isEmpty() || result.getString().length() > 0) {
                result.append(Component.literal("\n"));
            }
            result.append(lineComponent);
        }

        // Handle unclosed code block at end
        if (inCodeBlock && codeBlockBuffer.length() > 0) {
            result.append(Component.literal("\n"));
            result.append(renderCodeBlock(codeBlockBuffer.toString()));
        }

        return result;
    }

    /**
     * Render a code block as monospace-styled gray text lines.
     */
    private static MutableComponent renderCodeBlock(String content) {
        MutableComponent block = Component.literal("");
        String[] codeLines = content.split("\n", -1);
        for (int i = 0; i < codeLines.length; i++) {
            MutableComponent codeLine = Component.literal(codeLines[i])
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY));
            block.append(codeLine);
            if (i < codeLines.length - 1) {
                block.append(Component.literal("\n"));
            }
        }
        return block;
    }

    /**
     * Render a bullet list item.
     */
    private static MutableComponent renderBulletLine(String line) {
        // Find the content after "- " or "* "
        int contentStart = 2;
        if (line.length() <= contentStart) {
            return renderInlineFormatting(line);
        }
        // Keep the bullet prefix in the rendered output
        String prefix = "\u2022 "; // Unicode bullet
        String content = line.substring(contentStart);
        MutableComponent result = Component.literal(prefix)
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY));
        result.append(renderInlineFormatting(content));
        return result;
    }

    /**
     * Render a numbered list item (e.g., "1. text", "2. text").
     */
    private static MutableComponent renderNumberedLine(String line) {
        int dotSpace = line.indexOf(". ");
        if (dotSpace == -1) {
            return renderInlineFormatting(line);
        }
        String prefix = line.substring(0, dotSpace + 2); // e.g., "1. "
        String content = line.substring(dotSpace + 2);
        MutableComponent result = Component.literal(prefix)
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY));
        result.append(renderInlineFormatting(content));
        return result;
    }

    /**
     * Render a blockquote line with a gray left border bar (&#124; prefix).
     */
    private static MutableComponent renderBlockquote(String line) {
        String content = line.length() > 2 ? line.substring(2) : "";
        MutableComponent result = Component.literal("| ")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY));
        result.append(renderInlineFormatting(content)
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)));
        return result;
    }

    /**
     * Render a header line with bold + gold color.
     */
    private static MutableComponent renderHeader(String line, int level) {
        int contentStart = level + 1; // Skip "# ", "## ", or "### "
        String content = line.length() > contentStart ? line.substring(contentStart) : "";
        return Component.literal(content)
                .withStyle(Style.EMPTY
                        .withBold(true)
                        .withColor(ChatFormatting.GOLD));
    }

    /**
     * Parse inline formatting markers and build a styled Component.
     * Handles **bold**, *italic*, and {@code `code`} inline syntax.
     */
    static MutableComponent renderInlineFormatting(String text) {
        MutableComponent result = Component.literal("");
        int i = 0;
        int len = text.length();

        while (i < len) {
            char c = text.charAt(i);

            // **bold** — double asterisk
            if (c == '*' && i + 1 < len && text.charAt(i + 1) == '*') {
                int end = findEndTag(text, i + 2, "**");
                if (end != -1) {
                    String inner = text.substring(i + 2, end);
                    result.append(Component.literal(inner)
                            .withStyle(Style.EMPTY.withBold(true)));
                    i = end + 2;
                    continue;
                }
            }

            // [text](url) — clickable link
            if (c == '[') {
                int closeBracket = text.indexOf(']', i + 1);
                if (closeBracket != -1 && closeBracket + 1 < len
                        && text.charAt(closeBracket + 1) == '(') {
                    int closeParen = text.indexOf(')', closeBracket + 2);
                    if (closeParen != -1) {
                        String linkText = text.substring(i + 1, closeBracket);
                        String url = text.substring(closeBracket + 2, closeParen);
                        MutableComponent link = Component.literal(linkText)
                                .withStyle(Style.EMPTY
                                        .withColor(TextColor.fromRgb(0x3B82F6))
                                        .withUnderlined(true)
                                        .withClickEvent(new ClickEvent(
                                                ClickEvent.Action.OPEN_URL, url)));
                        result.append(link);
                        i = closeParen + 1;
                        continue;
                    }
                }
            }

            // *italic* — single asterisk (not part of **)
            if (c == '*' && (i + 1 >= len || text.charAt(i + 1) != '*')) {
                int end = findEndTag(text, i + 1, "*");
                if (end != -1 && (end + 1 >= len || text.charAt(end + 1) != '*')) {
                    String inner = text.substring(i + 1, end);
                    result.append(Component.literal(inner)
                            .withStyle(Style.EMPTY.withItalic(true)));
                    i = end + 1;
                    continue;
                }
            }

            // ~~strikethrough~~
            if (c == '~' && i + 1 < len && text.charAt(i + 1) == '~') {
                int end = findEndTag(text, i + 2, "~~");
                if (end != -1) {
                    String inner = text.substring(i + 2, end);
                    result.append(Component.literal(inner)
                            .withStyle(Style.EMPTY.withStrikethrough(true)));
                    i = end + 2;
                    continue;
                }
            }

            // `code` — backtick inline
            if (c == '`') {
                int end = findEndTag(text, i + 1, "`");
                if (end != -1) {
                    String inner = text.substring(i + 1, end);
                    result.append(Component.literal(inner)
                            .withStyle(Style.EMPTY
                                    .withColor(ChatFormatting.DARK_GRAY)));
                    i = end + 1;
                    continue;
                }
            }

            // Regular character
            result.append(Component.literal(String.valueOf(c)));
            i++;
        }

        return result;
    }

    /**
     * Find the next occurrence of a tag starting from {@code startPos}.
     *
     * @return the start index of the tag, or -1 if not found
     */
    private static int findEndTag(String text, int startPos, String tag) {
        int index = text.indexOf(tag, startPos);
        // Skip empty tags like **** or ** **
        if (index == startPos) {
            // Try again after the empty tag
            return findEndTag(text, startPos + tag.length(), tag);
        }
        return index;
    }
}

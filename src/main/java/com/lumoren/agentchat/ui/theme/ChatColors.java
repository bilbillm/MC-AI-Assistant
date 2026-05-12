package com.lumoren.agentchat.ui.theme;

/**
 * Centralized color palette for the chat UI.
 * Inspired by Create mod's clean, flat visual style.
 */
public final class ChatColors {
    private ChatColors() {}

    // ── Backgrounds ──
    public static final int BG_OVERLAY       = 0xCC_0F172A;  // dark blue-black overlay
    public static final int BG_SIDEBAR       = 0xCC_1E293B;  // sidebar panel
    public static final int BG_SIDEBAR_ITEM  = 0xFF_1E293B;  // sidebar item hover

    // ── Chat bubbles ──
    public static final int BUBBLE_USER_BG   = 0xFF_2563EB;  // blue
    public static final int BUBBLE_AI_BG     = 0xFF_334155;  // dark gray
    public static final int BUBBLE_ERROR_BG  = 0xFF_7F1D1D;  // dark red

    // ── Avatars ──
    public static final int AVATAR_USER_BG   = 0xFF_1D4ED8;  // darker blue
    public static final int AVATAR_AI_BG     = 0xFF_475569;  // mid gray
    public static final int AVATAR_TEXT      = 0xFF_FFFFFF;  // white

    // ── Text ──
    public static final int TEXT_PRIMARY     = 0xFF_F1F5F9;  // main text
    public static final int TEXT_SECONDARY   = 0xFF_94A3B8;  // muted
    public static final int TEXT_DIM         = 0xFF_64748B;  // dim/hint
    public static final int TEXT_ACCENT      = 0xFF_60A5FA;  // light blue accent
    public static final int TEXT_ERROR       = 0xFF_F87171;  // red error
    public static final int TEXT_REASONING   = 0xFF_94A3B8;  // reasoning toggle

    // ── Separators ──
    public static final int SEPARATOR        = 0xFF_1E293B;  // subtle border
    public static final int SEPARATOR_SIDEBAR = 0xFF_334155; // sidebar edge

    // ── Scrollbar ──
    public static final int SCROLLBAR_BG     = 0x33_000000;
    public static final int SCROLLBAR_THUMB  = 0x88_94A3B8;

    // ── Status / Thinking ──
    public static final int STATUS_TEXT      = 0xFF_94A3B8;  // "AI is thinking..."
    public static final int THREAD_ITEM_HOVER = 0xFF_334155;

    // ── Project HUD ──
    /** HUD widget background */
    public static final int HUD_BG           = 0xCC_1E293B;
    /** HUD widget border */
    public static final int HUD_BORDER       = 0xCC_334155;
    /** Task completed (green) */
    public static final int TASK_DONE        = 0xFF_22C55E;
    /** Task blocked (red) */
    public static final int TASK_BLOCKED     = 0xFF_EF4444;
    /** Task active / current step (blue) */
    public static final int TASK_ACTIVE      = 0xFF_3B82F6;
}

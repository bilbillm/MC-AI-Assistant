package com.lumoren.agentchat.ui.theme;

/**
 * Semantic color and metric tokens for the mechanical (Create-workshop-inspired)
 * UI. Widgets must reference these tokens instead of hardcoding colors, so the
 * theme stays consistent and adjustable in one place.
 * <p>
 * Visual rules enforced here: cast-iron dark base, brass for primary
 * interaction, copper for secondary, verdigris only for verified/success
 * states, red only for danger/diagnosis. No gradients, no blur, no glow.
 */
public final class GuideTheme {

    private GuideTheme() {
    }

    // ==================== Base surfaces ====================
    /** World dimming scrim behind the main screen. */
    public static final int SCRIM = 0xD9141614;
    /** Main panel cast-iron base. */
    public static final int IRON_BASE = 0xF2232622;
    /** Raised surface: toolbars, hover faces, assistant bubbles. */
    public static final int IRON_RAISED = 0xFF30352F;
    /** Inset surface: input console, scroll areas. */
    public static final int IRON_INSET = 0xFF191C18;
    /** Outer outline / bottom edge. */
    public static final int IRON_EDGE_DARK = 0xFF0E100D;
    /** Inner separators, disabled edges. */
    public static final int STEEL_EDGE = 0xFF596057;

    // ==================== Interaction metals ====================
    /** Primary interaction: Guide mode, focus, main buttons. */
    public static final int BRASS = 0xFFD2A84D;
    /** Brass hover/focus top edge. */
    public static final int BRASS_HIGHLIGHT = 0xFFFFD77A;
    /** Secondary interaction: Chat mode, user bubble edge, secondary buttons. */
    public static final int COPPER = 0xFFB96542;
    /** Verified / success / FTB-Patchouli status. */
    public static final int VERDIGRIS = 0xFF4C9B8C;
    /** Web source accent — reserved for source badges so the UI is not all brass. */
    public static final int WEB_BLUE = 0xFF65A6D8;
    /** Errors, diagnosis warnings, destructive hover. */
    public static final int DANGER_RED = 0xFFC44D3F;

    // ==================== Text ====================
    public static final int TEXT_PRIMARY = 0xFFF3EFD9;
    public static final int TEXT_SECONDARY = 0xFFB8BDAE;
    public static final int TEXT_DIM = 0xFF7D8478;
    /** Text/icons drawn on brass surfaces. */
    public static final int TEXT_ON_BRASS = 0xFF171912;

    // ==================== Metrics ====================
    public static final int PADDING = 8;
    public static final int TITLE_BAR_HEIGHT = 24;
    public static final int INPUT_BAR_HEIGHT = 28;
    public static final int ICON_SIZE = 12;
    public static final int ICON_BUTTON_SIZE = 20;
    public static final int RIVET_SIZE = 4;
    public static final int SCROLLBAR_WIDTH = 6;

    /** Sidebar width for the wide layout tier. */
    public static final int SIDEBAR_WIDTH = 148;
    /** Collapsed mechanical rail width. */
    public static final int SIDEBAR_RAIL_WIDTH = 22;
    /** Layout tier breakpoints (GUI px width). */
    public static final int WIDTH_COMPACT = 480;
    public static final int WIDTH_NARROW = 320;
}

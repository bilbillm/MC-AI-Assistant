package com.lumoren.agentchat.integration;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Optional bridge to Xaero's Minimap mod for automatic waypoint creation.
 * <p>
 * When Xaero's Minimap is installed, this bridge allows our scanning tools
 * (find_biome, nearby_blocks, nearby_entities) to automatically create
 * waypoints on the player's minimap for discovered points of interest.
 * <p>
 * If Xaero is not installed, all methods silently no-op. The mod works
 * fully without Xaero — this is purely an enhancement.
 * <p>
 * Uses compileOnly dependency — all Xaero class references are guarded
 * by availability checks and try-catch blocks.
 */
public final class XaeroWaypointBridge {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "xaerominimap";

    private static boolean available = false;
    private static boolean initialized = false;

    private XaeroWaypointBridge() {
    }

    /**
     * Initialize the bridge. Must be called after mod loading is complete.
     * Safe to call multiple times — only checks once.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;
        available = ModList.get().isLoaded(MOD_ID);
        if (available) {
            LOGGER.info("[AgentChat] Xaero's Minimap detected — waypoint integration enabled");
        } else {
            LOGGER.info("[AgentChat] Xaero's Minimap not installed — waypoint integration disabled");
        }
    }

    /**
     * @return true if Xaero's Minimap is installed and loaded
     */
    public static boolean isAvailable() {
        if (!initialized) init();
        return available;
    }

    /**
     * Create a waypoint on the player's minimap.
     * <p>
     * No-ops silently if Xaero is not installed, the session hasn't initialized yet,
     * or any error occurs.
     *
     * @param mc     Minecraft client instance
     * @param x      block X coordinate
     * @param y      block Y coordinate
     * @param z      block Z coordinate
     * @param name   waypoint display name (e.g. "Biome: Desert")
     * @param symbol 1-2 character symbol for the minimap icon (e.g. "B" for biome)
     */
    public static void addWaypoint(Minecraft mc, int x, int y, int z,
                                    String name, String symbol) {
        if (!isAvailable()) return;
        try {
            addWaypointImpl(x, y, z, name, symbol);
        } catch (NoClassDefFoundError | Exception e) {
            LOGGER.debug("[AgentChat] Failed to add Xaero waypoint: {}", e.getMessage());
        }
    }

    /**
     * Create a waypoint with a specific color.
     */
    public static void addWaypoint(Minecraft mc, int x, int y, int z,
                                    String name, String symbol, String colorName) {
        if (!isAvailable()) return;
        try {
            addWaypointImpl(x, y, z, name, symbol, colorName);
        } catch (NoClassDefFoundError | Exception e) {
            LOGGER.debug("[AgentChat] Failed to add Xaero waypoint: {}", e.getMessage());
        }
    }

    /**
     * Get the current dimension directory name (used by Xaero for waypoint storage).
     *
     * @param mc Minecraft client instance
     * @return dimension directory name, or empty string if unavailable
     */
    @SuppressWarnings("unused")
    public static String getCurrentDimensionId(Minecraft mc) {
        if (!isAvailable() || mc.level == null) return "";
        try {
            var session = xaero.hud.minimap.BuiltInHudModules.MINIMAP.getCurrentSession();
            if (session == null) return "";
            return session.getDimensionHelper()
                    .getDimensionDirectoryName(mc.level.dimension()).toString();
        } catch (NoClassDefFoundError | Exception e) {
            return "";
        }
    }

    // === Private implementation methods (separated to isolate Xaero class loading) ===

    private static void addWaypointImpl(int x, int y, int z, String name, String symbol) {
        addWaypointImpl(x, y, z, name, symbol, null);
    }

    private static void addWaypointImpl(int x, int y, int z, String name,
                                         String symbol, String colorName) {
        var session = xaero.hud.minimap.BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) {
            LOGGER.debug("[AgentChat] Xaero session not initialized yet, skipping waypoint");
            return;
        }

        var worldManager = session.getWorldManager();
        if (worldManager == null) return;

        var world = worldManager.getCurrentWorld();
        if (world == null) return;

        var waypointSet = world.getCurrentWaypointSet();
        if (waypointSet == null) return;

        // Resolve color
        xaero.hud.minimap.waypoint.WaypointColor color;
        if (colorName != null) {
            color = resolveColor(colorName);
        } else {
            color = xaero.hud.minimap.waypoint.WaypointColor.GREEN;
        }

        // Build display name: truncate to reasonable length
        String displayName = name.length() > 32 ? name.substring(0, 29) + "..." : name;
        // Symbol: first 2 chars of symbol, uppercase
        String wpSymbol = (symbol.length() > 2 ? symbol.substring(0, 2) : symbol).toUpperCase();

        var waypoint = new xaero.common.minimap.waypoints.Waypoint(
                x, y, z,
                displayName,
                wpSymbol,
                color,
                xaero.hud.minimap.waypoint.WaypointPurpose.NORMAL
        );

        waypointSet.add(waypoint);

        LOGGER.debug("[AgentChat] Xaero waypoint created: '{}' at ({}, {}, {})",
                displayName, x, y, z);
    }

    /**
     * Resolve a color name string to a Xaero WaypointColor.
     * Maps common color names to their Xaero enum values.
     */
    private static xaero.hud.minimap.waypoint.WaypointColor resolveColor(String name) {
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "red" -> xaero.hud.minimap.waypoint.WaypointColor.RED;
            case "blue" -> xaero.hud.minimap.waypoint.WaypointColor.BLUE;
            case "yellow" -> xaero.hud.minimap.waypoint.WaypointColor.YELLOW;
            case "green" -> xaero.hud.minimap.waypoint.WaypointColor.GREEN;
            case "aqua", "cyan" -> xaero.hud.minimap.waypoint.WaypointColor.AQUA;
            case "gold", "orange" -> xaero.hud.minimap.waypoint.WaypointColor.GOLD;
            case "purple" -> xaero.hud.minimap.waypoint.WaypointColor.PURPLE;
            case "white" -> xaero.hud.minimap.waypoint.WaypointColor.WHITE;
            case "black" -> xaero.hud.minimap.waypoint.WaypointColor.BLACK;
            case "gray", "grey" -> xaero.hud.minimap.waypoint.WaypointColor.GRAY;
            default -> xaero.hud.minimap.waypoint.WaypointColor.GREEN;
        };
    }
}

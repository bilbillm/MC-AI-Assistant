package com.lumoren.agentchat.ai;

/**
 * Built-in test suite for the AI to run in debug mode.
 * Each test is a self-contained instruction the AI executes via tools.
 */
public final class TestSuite {
    private TestSuite() {}

    public static final String TEST_SUITE_PROMPT =
        """
        ## AUTOMATED TEST SUITE
        You are now running the built-in test suite. Execute each test below by calling the
        specified tools. After each test, report: [PASS] or [FAIL] with a brief reason.
        Do not skip any test. Report a summary at the end.

        ### Test 1: Inventory Tool
        Call get_inventory. Verify: response is a JSON array. Each item has 'slot', 'item_id',
        'count' fields. Report PASS if at least one item is returned or if empty array is valid.

        ### Test 2: Item Info Tool
        Call item_info with item_name="stick". Verify: response contains "Item:" or
        "Max Stack:" or "Rarity:". Report PASS if valid item info returned.

        ### Test 3: Recipe Tool
        Call lookup_recipe with item_name="stick". Verify: response contains recipe data
        or a sensible "no recipe found" message. Report PASS if tool responds without error.

        ### Test 4: Position Tool
        Call get_player_status. Verify: response contains position data (x/y/z coordinates
        or "unknown"). Report PASS if tool responds without error.

        ### Test 5: World State Tool
        Call get_world_state. Verify: response contains time/weather/difficulty info.
        Report PASS if tool responds without error.

        ### Test 6: Mod List Tool
        Call list_mods. Verify: response lists at least "minecraft" and "agentchat" in
        the mod list. Report PASS if both are found.

        ### Test 7: Game Info Tool
        Call game_info. Verify: response contains Minecraft version and loader type
        (should be "NeoForge"). Report PASS if version info is present.

        ### Test 8: Web Search Tool
        Call web_search with query="Minecraft diamond sword". Verify: response contains
        search results OR a clear "unavailable" message. Either is valid. Report PASS if
        tool responds without crash.

        ### Test 9: Project Create
        Call manage_project with action="create_project", project_name="Test Project",
        tasks=[{"description":"Step 1: test","type":"PLAN","items":[]}].
        Verify: response contains "created" or "steps". Report PASS if project was created.

        ### Test 10: Project Status
        Call manage_project with action="get_status". Verify: response shows "Test Project"
        with progress. Report PASS if the project from Test 9 appears.

        ### Test 11: Project Mark Done
        Call manage_project with action="mark_done", task_index=1. Verify: response says
        task was marked done. Report PASS if mark_done succeeds.

        ### Test 12: Project Cancel
        Call manage_project with action="cancel". Verify: response confirms cancellation.
        Report PASS if project was cancelled.

        ### Test 13: Markdown Rendering
        Output this markdown in your response: "**bold** *italic* `code` [link](https://example.com)".
        The user will visually verify rendering. Report: [VISUAL CHECK] — user must confirm.

        ### Test 14: Multi-Tool Chain
        Call get_inventory, then item_info for the first item found in inventory.
        Verify: no errors in either call. Report PASS if both tools worked sequentially.

        ### Test 15: Error Handling
        Call item_info with item_name="nonexistent_item_xyz_123". Verify: tool returns
        "Unknown" or error message — NOT a crash. Report PASS if error is handled gracefully.

        ## SUMMARY
        After running all tests, output: "Tests: X/15 PASS, Y/15 FAIL, Z/15 SKIP"
        """;
}

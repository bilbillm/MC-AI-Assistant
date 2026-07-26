package com.lumoren.agentchat.ui.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModeSegmentedWidgetTest {

    @Test
    void segmentAtSplitsWidthInHalves() {
        assertEquals(0, ModeSegmentedWidget.segmentAt(0, 100));
        assertEquals(0, ModeSegmentedWidget.segmentAt(49, 100));
        assertEquals(1, ModeSegmentedWidget.segmentAt(50, 100));
        assertEquals(1, ModeSegmentedWidget.segmentAt(99, 100));
        assertEquals(-1, ModeSegmentedWidget.segmentAt(-1, 100));
        assertEquals(-1, ModeSegmentedWidget.segmentAt(100, 100));
    }

    @Test
    void segmentAtHandlesOddWidths() {
        assertEquals(0, ModeSegmentedWidget.segmentAt(39, 81));
        assertEquals(1, ModeSegmentedWidget.segmentAt(40, 81));
    }
}

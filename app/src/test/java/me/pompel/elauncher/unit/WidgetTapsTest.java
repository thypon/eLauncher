package me.pompel.elauncher.unit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import me.pompel.elauncher.widget.WidgetTaps;

public class WidgetTapsTest {

    @Test
    public void parsesKnownCodes() {
        assertEquals(WidgetTaps.OFF, WidgetTaps.from(0));
        assertEquals(WidgetTaps.ON, WidgetTaps.from(1));
        assertEquals(WidgetTaps.DOUBLE_TAP, WidgetTaps.from(2));
    }

    @Test
    public void unknownCodeFallsBackToOn() {
        assertEquals(WidgetTaps.ON, WidgetTaps.from(3));
        assertEquals(WidgetTaps.ON, WidgetTaps.from(-1));
    }

    @Test
    public void codesRoundTrip() {
        for (WidgetTaps taps : WidgetTaps.values()) {
            assertEquals(taps, WidgetTaps.from(taps.code()));
        }
    }

    @Test
    public void nextCyclesThroughAllModes() {
        assertEquals(WidgetTaps.DOUBLE_TAP, WidgetTaps.ON.next());
        assertEquals(WidgetTaps.OFF, WidgetTaps.DOUBLE_TAP.next());
        assertEquals(WidgetTaps.ON, WidgetTaps.OFF.next());
    }
}

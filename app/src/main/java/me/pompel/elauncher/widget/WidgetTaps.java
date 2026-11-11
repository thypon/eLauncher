// Ported from Olauncher (https://github.com/tanujnotes/Olauncher), GPL-3.0.
// Modified for eLauncher: eInk (no crossfade), Java, single-column board.
package me.pompel.elauncher.widget;

/** Whether a widget's own views receive taps, mirroring Olauncher's WidgetTaps. */
public enum WidgetTaps {
    /** The widget gets every touch, like in any launcher. */
    ON,
    /** Swipes, long press and single taps go to the home screen; a double tap clicks the widget. */
    DOUBLE_TAP,
    /** Every touch goes to the home screen, as if the widget weren't there. */
    OFF;

    /** Parses a stored code: 0 = off, 2 = double tap, anything else = on. */
    public static WidgetTaps from(int code) {
        if (code == 0) return OFF;
        if (code == 2) return DOUBLE_TAP;
        return ON;
    }

    /** The code stored for this mode in the persisted widget list. */
    public int code() {
        return this == OFF ? 0 : this == DOUBLE_TAP ? 2 : 1;
    }

    /** The setting after this one when its switch is tapped: on, double tap, off, and around again. */
    public WidgetTaps next() {
        WidgetTaps[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}

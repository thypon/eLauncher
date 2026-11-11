// Ported from Olauncher (https://github.com/tanujnotes/Olauncher), GPL-3.0.
// Modified for eLauncher: eInk (no crossfade), Java, single-column board
// (no width/top: every widget fills the board width in a vertical stack).
package me.pompel.elauncher.widget;

/** A widget placed on the eBoard, identified by its bound app widget id. */
public final class WidgetEntry {
    private final int id;
    private final int heightDp;
    private final WidgetTaps taps;

    public WidgetEntry(int id, int heightDp, WidgetTaps taps) {
        this.id = id;
        this.heightDp = heightDp;
        this.taps = taps == null ? WidgetTaps.ON : taps;
    }

    /** The bound app widget id. */
    public int id() {
        return id;
    }

    /** Row height in dp. */
    public int heightDp() {
        return heightDp;
    }

    /** How touches on the widget are handled. */
    public WidgetTaps taps() {
        return taps;
    }

    public WidgetEntry withHeight(int newHeightDp) {
        return new WidgetEntry(id, newHeightDp, taps);
    }

    public WidgetEntry withTaps(WidgetTaps newTaps) {
        return new WidgetEntry(id, heightDp, newTaps);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WidgetEntry)) return false;
        WidgetEntry other = (WidgetEntry) o;
        return id == other.id && heightDp == other.heightDp && taps == other.taps;
    }

    @Override
    public int hashCode() {
        return 31 * id + heightDp;
    }

    @Override
    public String toString() {
        return id + ":" + heightDp + ":" + taps.code();
    }
}

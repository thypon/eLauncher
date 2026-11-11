// Ported from Olauncher (https://github.com/tanujnotes/Olauncher), GPL-3.0.
// Modified for eLauncher: eInk (no crossfade), Java, single-column board;
// migrates the legacy BoardStore keys (widget_ids / widget_heights).
package me.pompel.elauncher.widget;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persists the ordered widget list of the eBoard in the Olauncher format:
 * "appWidgetId:heightDp:tapsCode" entries separated by ';' (taps 1 = on,
 * 2 = double tap, 0 = off; extra fields after taps are ignored, mirroring
 * the Olauncher parser). Lives in the same prefs file the legacy BoardStore
 * used, so an existing board migrates in place. */
public final class WidgetPrefs {
    /** SharedPreferences file the board layout is kept in (legacy BoardStore name). */
    public static final String PREFS_FILE = "eboard_widgets";
    /** Key holding the Olauncher-format widget list. */
    public static final String KEY_WIDGETS = "home_widgets";
    /** Legacy BoardStore keys, read once for migration then removed. */
    public static final String LEGACY_KEY_IDS = "widget_ids";
    public static final String LEGACY_KEY_SIZES = "widget_heights";
    /** Default row height in dp when nothing is stored (legacy BoardStore default). */
    public static final int DEFAULT_HEIGHT_DP = 200;
    /** Row height range in dp, matching the old board's resize clamps. */
    public static final int MIN_HEIGHT_DP = 140;
    public static final int MAX_HEIGHT_DP = 600;

    private final SharedPreferences prefs;

    public WidgetPrefs(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    /** The widgets in board order; corrupt or empty entries are skipped.
     * Lazily migrates a legacy BoardStore board on first read. */
    public List<WidgetEntry> widgets() {
        ensureMigrated();
        return parse(prefs.getString(KEY_WIDGETS, ""));
    }

    /** Appends a widget to the end of the board (no-op when already present). */
    public void add(int id, int heightDp) {
        List<WidgetEntry> widgets = widgets();
        for (WidgetEntry widget : widgets) {
            if (widget.id() == id) return;
        }
        widgets.add(new WidgetEntry(id, clampHeight(heightDp), WidgetTaps.ON));
        save(widgets);
    }

    /** Removes a widget id; removing an absent id keeps the order intact. */
    public void remove(int id) {
        List<WidgetEntry> widgets = widgets();
        boolean changed = false;
        for (int i = widgets.size() - 1; i >= 0; i--) {
            if (widgets.get(i).id() == id) {
                widgets.remove(i);
                changed = true;
            }
        }
        if (changed) save(widgets);
    }

    /** Moves a widget from one board position to another; out-of-range
     * indices are no-ops. */
    public void move(int from, int to) {
        List<WidgetEntry> widgets = widgets();
        if (from < 0 || from >= widgets.size() || to < 0 || to >= widgets.size() || from == to) return;
        WidgetEntry moved = widgets.remove(from);
        widgets.add(to, moved);
        save(widgets);
    }

    /** Persists how touches on a widget are handled; unknown ids are no-ops. */
    public void setTaps(int id, WidgetTaps taps) {
        List<WidgetEntry> widgets = widgets();
        List<WidgetEntry> out = new ArrayList<>();
        boolean changed = false;
        for (WidgetEntry widget : widgets) {
            if (widget.id() == id && widget.taps() != taps) {
                out.add(widget.withTaps(taps));
                changed = true;
            } else {
                out.add(widget);
            }
        }
        if (changed) save(out);
    }

    /** Stored height in dp for the widget at a board position (test/BDD access). */
    public int heightAt(int position) {
        List<WidgetEntry> widgets = widgets();
        if (position < 0 || position >= widgets.size()) return -1;
        return widgets.get(position).heightDp();
    }

    /** The stored ids in board order (test/BDD access). */
    public List<Integer> ids() {
        List<Integer> out = new ArrayList<>();
        for (WidgetEntry widget : widgets()) out.add(widget.id());
        return out;
    }

    private void save(List<WidgetEntry> widgets) {
        StringBuilder sb = new StringBuilder();
        for (WidgetEntry widget : widgets) {
            if (sb.length() > 0) sb.append(';');
            sb.append(widget.id()).append(':').append(widget.heightDp()).append(':').append(widget.taps().code());
        }
        prefs.edit().putString(KEY_WIDGETS, sb.toString()).apply();
    }

    private int clampHeight(int heightDp) {
        return Math.max(MIN_HEIGHT_DP, Math.min(MAX_HEIGHT_DP, heightDp));
    }

    private void ensureMigrated() {
        if (prefs.contains(KEY_WIDGETS)) return;
        String legacyIds = prefs.getString(LEGACY_KEY_IDS, "");
        if (legacyIds.isEmpty()) return; // nothing to migrate: fresh board
        Map<String, String> sizes = legacySizes();
        StringBuilder sb = new StringBuilder();
        for (String part : legacyIds.split(",")) {
            try {
                int id = Integer.parseInt(part.trim());
                if (sb.length() > 0) sb.append(';');
                String size = sizes.get(String.valueOf(id));
                int height = size == null ? DEFAULT_HEIGHT_DP : parseHeight(size);
                sb.append(id).append(':').append(clampHeight(height)).append(':').append(WidgetTaps.ON.code());
            } catch (NumberFormatException ignored) {
                // tolerate garbage (hand-edited prefs, trailing separators)
            }
        }
        prefs.edit()
                .putString(KEY_WIDGETS, sb.toString())
                .remove(LEGACY_KEY_IDS)
                .remove(LEGACY_KEY_SIZES)
                .apply();
    }

    private Map<String, String> legacySizes() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String part : prefs.getString(LEGACY_KEY_SIZES, "").split(",")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            out.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
        }
        return out;
    }

    private static int parseHeight(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_HEIGHT_DP;
        }
    }

    /** Parses the Olauncher-format list; corrupt or empty entries are skipped. */
    static List<WidgetEntry> parse(String raw) {
        List<WidgetEntry> out = new ArrayList<>();
        if (raw == null) return out;
        for (String entry : raw.split(";")) {
            if (entry.trim().isEmpty()) continue;
            String[] parts = entry.split(":");
            Integer id = intOrNull(parts, 0);
            Integer height = intOrNull(parts, 1);
            if (id == null || height == null) continue;
            if (id < 0 || height <= 0) continue;
            WidgetTaps taps = WidgetTaps.ON;
            // Olauncher's richer shapes: "id:height:width:top:taps" (5 fields)
            // and the pre-2024 legacy "id:height:width:corner:top:fullscreen:taps" (7)
            int tapsIndex = parts.length == 7 ? 6 : parts.length >= 5 ? 4 : 2;
            if (parts.length > tapsIndex) {
                Integer code = intOrNull(parts, tapsIndex);
                if (code != null) taps = WidgetTaps.from(code);
            }
            out.add(new WidgetEntry(id, height, taps));
        }
        return out;
    }

    private static Integer intOrNull(String[] parts, int index) {
        if (index >= parts.length) return null;
        try {
            return Integer.parseInt(parts[index].trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

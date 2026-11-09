package me.pompel.elauncher;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persists the ordered list of appWidgetIds placed on the eBoard.
 * The ids themselves live in the AppWidgetHost service; this store only
 * remembers which ones belong to the board, in board order. */
public final class BoardStore {
    /** SharedPreferences file the board layout is kept in. */
    public static final String PREFS_FILE = "eboard_widgets";
    /** Key holding the comma-separated ordered appWidgetIds. */
    public static final String KEY_IDS = "widget_ids";
    /** Key holding the per-widget row height, in dp (id -> height). */
    public static final String KEY_SIZES = "widget_heights";
    /** Key holding the per-widget row width, in dp (id -> width). */
    public static final String KEY_WIDTHS = "widget_widths";
    /** Default row height in dp when nothing is stored. */
    public static final int DEFAULT_SIZE_DP = 200;

    private final SharedPreferences prefs;

    public BoardStore(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    /** Board widget ids in board order; corrupt or empty entries are skipped. */
    public List<Integer> ids() {
        List<Integer> out = new ArrayList<>();
        for (String part : raw().split(",")) {
            try {
                out.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // tolerate garbage (hand-edited prefs, trailing separators)
            }
        }
        return out;
    }

    /** Appends a widget id to the end of the board. */
    public void add(int id) {
        String raw = raw();
        save(raw.isEmpty() ? String.valueOf(id) : raw + "," + id);
    }

    /** Removes a widget id; removing an absent id keeps the order intact and
     * cleans the stored height entry (no stale sizes for future ids). */
    public void remove(int id) {
        List<Integer> rest = ids();
        rest.remove(Integer.valueOf(id));
        replaceAll(rest);
        Map<String, String> sizes = fields();
        if (sizes.remove(String.valueOf(id)) != null) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : sizes.entrySet()) {
                if (sb.length() > 0) sb.append(',');
                sb.append(e.getKey()).append('=').append(e.getValue());
            }
            prefs.edit().putString(KEY_SIZES, sb.toString()).apply();
        }
    }

    /** Replaces the whole board order (reordering). */
    public void replaceAll(List<Integer> order) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(order.get(i).intValue());
        }
        save(sb.toString());
    }

    /** Stored row height in dp for a widget id, DEFAULT_SIZE_DP when unset. */
    public int size(int id) {
        try {
            return Integer.parseInt(fields().getOrDefault(String.valueOf(id),
                    String.valueOf(DEFAULT_SIZE_DP)));
        } catch (NumberFormatException e) {
            return DEFAULT_SIZE_DP;
        }
    }

    /** Persists a row height in dp for a widget id. */
    public void setSize(int id, int dp) {
        Map<String, String> f = fields();
        f.put(String.valueOf(id), String.valueOf(dp));
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : f.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        prefs.edit().putString(KEY_SIZES, sb.toString()).apply();
    }

    /** True when the user has explicitly sized this widget's row. */
    public boolean hasSize(int id) {
        return fields().containsKey(String.valueOf(id));
    }

    /** True when the user has explicitly sized this widget's row width. */
    public boolean hasWidth(int id) {
        return widthFields().containsKey(String.valueOf(id));
    }

    /** Persists a row width in dp for a widget id. */
    public void setWidth(int id, int dp) {
        Map<String, String> f = widthFields();
        f.put(String.valueOf(id), String.valueOf(dp));
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : f.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        prefs.edit().putString(KEY_WIDTHS, sb.toString()).apply();
    }

    /** Stored row width in dp, or 0 when the user never resized the width. */
    public int width(int id) {
        try {
            return Integer.parseInt(widthFields().getOrDefault(String.valueOf(id), "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Map<String, String> widthFields() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String part : prefs.getString(KEY_WIDTHS, "").split(",")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            out.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
        }
        return out;
    }

    private Map<String, String> fields() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String part : prefs.getString(KEY_SIZES, "").split(",")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            out.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
        }
        return out;
    }

    private String raw() {
        return prefs.getString(KEY_IDS, "");
    }

    private void save(String value) {
        prefs.edit().putString(KEY_IDS, value).apply();
    }
}

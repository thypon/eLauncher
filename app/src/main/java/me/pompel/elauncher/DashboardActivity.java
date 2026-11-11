package me.pompel.elauncher;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.pompel.elauncher.widget.ResizeOverlay;
import me.pompel.elauncher.widget.WidgetEntry;
import me.pompel.elauncher.widget.WidgetHost;
import me.pompel.elauncher.widget.WidgetHostView;
import me.pompel.elauncher.widget.WidgetPrefs;
import me.pompel.elauncher.widget.WidgetTaps;

/**
 * eBoard — a single-column board of persistent home screen widgets, ported
 * from Olauncher. Registered as its own launcher entry point ("eBoard") and
 * opened by the launcher's default left-side gesture. Widgets picked from the
 * widget adder popover are hosted here for good: the AppWidgetHost binding is
 * kept by the system and the board order is persisted across restarts.
 *
 * Interaction model (Olauncher's): the board stacks widgets vertically, each
 * filling the board width. How a widget handles touches is per-widget
 * (WidgetTaps): on, double tap, or off — in the latter modes the touches go
 * to the board as if the widget weren't there. A long press anywhere on a
 * widget enters that widget's edit mode: an edit bar (remove, height, tap
 * mode, set up) appears above it with a dashed resize frame; keeping the
 * finger down after the long press drags the widget to reorder the stack.
 * Holding empty board space opens the widget adder popover; pressing empty
 * space leaves edit mode. The add button exists only on an empty board.
 */
public class DashboardActivity extends AppCompatActivity {
    /** Distinguishes eBoard-hosted widgets from other hosts' widget ids. */
    static final int HOST_ID = WidgetHost.HOST_ID;
    private static final int REQUEST_PICK = 1;
    private static final int REQUEST_BIND = 2;
    private static final int REQUEST_CONFIGURE = 3;
    /** Range of a widget row height in dp, used by the resize handle. */
    public static final int MIN_HEIGHT_DP = WidgetPrefs.MIN_HEIGHT_DP;
    public static final int MAX_HEIGHT_DP = WidgetPrefs.MAX_HEIGHT_DP;
    /** Hold duration that opens the adder on empty board space. */
    public static final long HOLD_MS = 700;
    /** Movement (px) that cancels a pending hold. */
    private static final float HOLD_CANCEL_SLOP_PX = 30;
    /** Tag of the edit bar row inside the stack (rows carry an Integer id). */
    private static final String EDIT_BAR_TAG = "eboard_edit_bar";
    /** Tag of the dashed resize frame inside the edited row. */
    private static final String RESIZE_OVERLAY_TAG = "eboard_resize_overlay";

    private WidgetHost host;
    private AppWidgetManager widgets;
    private ScrollView board;
    private LinearLayout boardStack;
    private TextView emptyHint;
    private Button addWidgetButton;
    private WidgetPrefs store;
    /** The one widget currently in edit mode, or -1. */
    private int editingWidgetId = -1;
    private AlertDialog adderDialog;
    /** Screen side the launcher opened this board from: -1 left, +1 right,
     * 0 when launched without the launcher (e.g. recents), in which case
     * horizontal flings stay inert. */
    private int entryDirection;
    private GestureDetector flingDetector;
    /** App widget id -> row view; rows survive rebuilds (AppWidgetHost views
     * must not be recreated on every bind). */
    private final Map<Integer, View> rows = new HashMap<>();
    private final Map<Integer, WidgetHostView> hostViews = new HashMap<>();
    // drag-reorder state
    private int dragStartIndex = -1;
    private int dragCurrentIndex = -1;
    /** Widget id waiting in the system configure activity, or -1. */
    private int pendingConfigureId = -1;

    /** Intent extra carrying the launcher-side entry direction (-1/+1/0). */
    public static final String EXTRA_ENTRY_DIRECTION = "me.pompel.elauncher.entry_direction";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // same dark-mode computation as the other views (MainActivity)
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean systemDark = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        boolean isDark = prefs.getBoolean("dark_mode_preference", systemDark);
        AppCompatDelegate_setDefaultNightMode(isDark);
        setTheme(isDark ? R.style.AppTheme_InvertedDark : R.style.AppTheme);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        entryDirection = getIntent().getIntExtra(EXTRA_ENTRY_DIRECTION, 0);
        flingDetector = new GestureDetector(this, new FlingListener());
        // the system back/forward edge gesture must not fight the disabled
        // entry fling: exclude the entry side's edge from the system gesture
        // zones (no-op below API 29 and for direct launches)
        findViewById(android.R.id.content).post(this::applySystemGestureExclusion);
        board = findViewById(R.id.board);
        boardStack = findViewById(R.id.board_stack);
        emptyHint = findViewById(R.id.empty_hint);
        addWidgetButton = findViewById(R.id.add_widget);
        widgets = AppWidgetManager.getInstance(this);
        host = new WidgetHost(this);
        store = new WidgetPrefs(getSharedPreferences(WidgetPrefs.PREFS_FILE, MODE_PRIVATE));
        addWidgetButton.setOnClickListener(v -> openAdder());
        // unified hold handling on EMPTY board space (the ScrollView itself is
        // the touch target when no widget is underneath): a hold opens the
        // adder, pressing empty space while editing leaves edit mode. Widget
        // long presses are detected by the widgets themselves (see createRow).
        final Handler handler = new Handler(Looper.getMainLooper());
        final float[] down = new float[2];
        final Runnable openAdder = this::openAdder;
        board.setOnTouchListener((v, e) -> {
            // return false: the ScrollView stays in charge of scrolling
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = e.getX();
                    down[1] = e.getY();
                    if (isEditing()) {
                        exitEditMode(); // pressing empty space leaves edit mode
                    } else {
                        handler.removeCallbacks(openAdder);
                        handler.postDelayed(openAdder, HOLD_MS);
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(e.getX() - down[0]) > HOLD_CANCEL_SLOP_PX
                            || Math.abs(e.getY() - down[1]) > HOLD_CANCEL_SLOP_PX) {
                        handler.removeCallbacks(openAdder);
                    }
                    break;
                default:
                    handler.removeCallbacks(openAdder);
                    break;
            }
            return false;
        });
    }

    /** Applies the same night-mode computation as MainActivity. */
    private void AppCompatDelegate_setDefaultNightMode(boolean dark) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(dark
                ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
    }

    @Override
    protected void onStart() {
        super.onStart();
        host.startListening();
        rebuildBoard();
    }

    @Override
    protected void onStop() {
        host.stopListening();
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        if (isEditing()) {
            exitEditMode();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        // observe-only: never consume, widget taps and scrolls keep working
        flingDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    /** Horizontal flings leave the board back to the launcher. The fling
     * motion that opened the board is disabled inside it; the opposite
     * fling finishes the board with the reverse directional animation. */
    private class FlingListener extends GestureDetector.SimpleOnGestureListener {
        @Override public boolean onDown(@NonNull MotionEvent e) { return true; }

        @Override public boolean onFling(@NonNull MotionEvent e1,
                @NonNull MotionEvent e2, float velocityX, float velocityY) {
            float xDiff = e2.getX() - e1.getX();
            float yDiff = e2.getY() - e1.getY();
            if (entryDirection == 0 || isEditing()
                    || Math.abs(xDiff) <= Math.abs(yDiff)
                    || Math.abs(xDiff) <= 100 || Math.abs(velocityX) <= 100) {
                return false;
            }
            // rightward fling = content moves right = left-side animation (-1)
            int flingDirection = xDiff > 0 ? -1 : 1;
            if (flingDirection != -entryDirection) {
                return true; // the entry motion: disabled inside the board
            }
            exitToLauncher(flingDirection);
            return true;
        }
    }

    private void exitToLauncher(int direction) {
        int[] anims = MainActivity.swipeLaunchAnimations(direction);
        finish();
        if (anims != null) {
            overridePendingTransition(anims[0], anims[1]);
        }
    }

    /** Excludes the entry side's edge strip from the system gesture zones:
     * the same side whose fling is disabled must not trigger the system
     * back/forward gesture either. entryDirection 0 (direct launch) and
     * API < 29 keep the system gestures untouched. */
    private void applySystemGestureExclusion() {
        if (entryDirection == 0 || Build.VERSION.SDK_INT < 29) return;
        View root = findViewById(android.R.id.content);
        int strip = Math.round(50 * getResources().getDisplayMetrics().density);
        android.graphics.Rect edge = new android.graphics.Rect();
        if (entryDirection == -1) edge.set(0, 0, strip, root.getHeight());
        else edge.set(root.getWidth() - strip, 0, root.getWidth(), root.getHeight());
        root.setSystemGestureExclusionRects(java.util.Collections.singletonList(edge));
    }

    /** A plain context for widget view creation: the activity context's
     * LayoutInflater carries the AppCompat view factory, and RemoteViews
     * reflection rejects the substituted classes it produces
     * (AppCompatImageView) with "Couldn't add widget". This wrapper serves
     * a CLONED inflater — cloneInContext does not carry the factories. */
    private android.content.Context plainWidgetContext() {
        return new android.content.ContextWrapper(this) {
            @Override
            public Object getSystemService(String name) {
                if (Context.LAYOUT_INFLATER_SERVICE.equals(name)) {
                    android.view.LayoutInflater base = (android.view.LayoutInflater)
                            getBaseContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    return base.cloneInContext(this);
                }
                return super.getSystemService(name);
            }
        };
    }

    /** Rebuilds the stack from the persisted widget list, dropping widgets
     * whose provider is no longer installed; row views are reused. */
    private void rebuildBoard() {
        List<WidgetEntry> entries = entriesWithProvider();
        boardStack.removeAllViews();
        rows.keySet().retainAll(idsOf(entries));
        hostViews.keySet().retainAll(idsOf(entries));
        for (int i = 0; i < entries.size(); i++) {
            WidgetEntry entry = entries.get(i);
            View row = ensureRow(entry);
            boardStack.addView(row);
            if (entry.id() == editingWidgetId) {
                boardStack.addView(editBar(entry), i); // bar above its widget
            }
        }
        updateUiState();
    }

    private List<Integer> idsOf(List<WidgetEntry> entries) {
        List<Integer> ids = new ArrayList<>();
        for (WidgetEntry entry : entries) ids.add(entry.id());
        return ids;
    }

    private List<WidgetEntry> entriesWithProvider() {
        List<WidgetEntry> out = new ArrayList<>();
        for (WidgetEntry entry : store.widgets()) {
            if (widgets.getAppWidgetInfo(entry.id()) != null) {
                out.add(entry);
            } else {
                host.deleteAppWidgetId(entry.id());
                store.remove(entry.id());
            }
        }
        return out;
    }

    private void updateUiState() {
        boolean empty = rowCount() == 0;
        emptyHint.setVisibility(empty ? View.VISIBLE : View.GONE);
        // the add button exists only on an empty board; afterwards a hold on
        // empty board space is the adder entry point
        addWidgetButton.setVisibility(empty && !isEditing() ? View.VISIBLE : View.GONE);
        if (empty) exitEditMode();
    }

    // ---- per-widget edit mode --------------------------------------------------------------

    private boolean isEditing() {
        return editingWidgetId != -1;
    }

    /** Switches exactly the given widget into edit mode (Olauncher style):
     * an edit bar appears above its row, plus a dashed resize frame once
     * "Height" is chosen. */
    void enterEditMode(int id) {
        if (editingWidgetId == id) return;
        exitEditMode();
        editingWidgetId = id;
        int position = positionOf(id);
        if (position >= 0) {
            boardStack.addView(editBar(entryAt(position)), position);
        }
        updateUiState();
    }

    void exitEditMode() {
        if (!isEditing()) return;
        editingWidgetId = -1;
        View bar = boardStack.findViewWithTag(EDIT_BAR_TAG);
        if (bar != null) boardStack.removeView(bar);
        dismissResizeOverlay();
        updateUiState();
    }

    public boolean isEditMode() {
        return isEditing();
    }

    /** No-arg BDD entry: puts the first row into edit mode. */
    void enterEditMode() {
        if (rowCount() > 0) enterEditMode(rowIdAt(0));
    }

    /** The bar shown above a widget in edit mode: remove, height (resize
     * frame), tap-mode cycle, and set up (only for widgets that need
     * configuring). */
    private LinearLayout editBar(WidgetEntry entry) {
        LinearLayout bar = new LinearLayout(this);
        bar.setTag(EDIT_BAR_TAG);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        int pad = (int) dp(8);
        bar.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        AppWidgetProviderInfo info = widgets.getAppWidgetInfo(entry.id());

        Button remove = new Button(this);
        remove.setTag(R.id.remove_handle);
        remove.setText(R.string.eboard_edit_remove);
        remove.setElevation(dp(6));
        remove.setOnClickListener(v -> removeWidget(entry.id()));
        bar.addView(remove, cell);

        Button height = new Button(this);
        height.setTag(R.id.resize_handle);
        height.setText(R.string.eboard_edit_height);
        height.setOnClickListener(v -> toggleResizeOverlay(entry.id()));
        bar.addView(height, cell);

        Button taps = new Button(this);
        taps.setTag(R.id.taps_button);
        taps.setText(tapsLabel(tapsOf(entry.id())));
        taps.setOnClickListener(v -> {
            // read the persisted mode: the entry captured at bar creation
            // would cycle from a stale state on every click
            WidgetTaps next = tapsOf(entry.id()).next();
            store.setTaps(entry.id(), next);
            WidgetHostView hv = hostViews.get(entry.id());
            if (hv != null) hv.setTaps(next);
            taps.setText(tapsLabel(next));
        });
        bar.addView(taps, cell);

        if (info != null && WidgetHostView.needsConfiguration(info)) {
            Button reconfigure = new Button(this);
            reconfigure.setText(R.string.eboard_edit_reconfigure);
            reconfigure.setOnClickListener(v -> startConfigure(entry.id(), info));
            bar.addView(reconfigure, cell);
        }
        return bar;
    }

    private String tapsLabel(WidgetTaps taps) {
        switch (taps) {
            case DOUBLE_TAP: return getString(R.string.eboard_taps_double);
            case OFF: return getString(R.string.eboard_taps_off);
            default: return getString(R.string.eboard_taps_on);
        }
    }

    /** The persisted tap mode of a widget id (ON when unknown). */
    private WidgetTaps tapsOf(int id) {
        for (WidgetEntry entry : store.widgets()) {
            if (entry.id() == id) return entry.taps();
        }
        return WidgetTaps.ON;
    }

    // ---- board state accessors (used by the BDD world) --------------------------------------

    /** Rows only: the edit bar carries a String tag, rows carry their id. */
    private List<View> rowChildren() {
        List<View> out = new ArrayList<>();
        for (int i = 0; i < boardStack.getChildCount(); i++) {
            View child = boardStack.getChildAt(i);
            if (child.getTag() instanceof Integer) out.add(child);
        }
        return out;
    }

    public int rowCount() {
        return rowChildren().size();
    }

    public View boardRowAt(int position) {
        List<View> children = rowChildren();
        if (position < 0 || position >= children.size()) return null;
        return children.get(position);
    }

    private int rowIdAt(int position) {
        return (Integer) boardRowAt(position).getTag();
    }

    private int positionOf(int id) {
        List<Integer> ids = store.ids();
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i) == id) return i;
        }
        return -1;
    }

    private WidgetEntry entryAt(int position) {
        return store.widgets().get(position);
    }

    /** Reorders the board and persists the order; out-of-range indices are
     * no-ops. */
    public void moveBoardRow(int from, int to) {
        store.move(from, to);
        rebuildBoard();
    }

    /** Applies a new row height in dp, persisting it and resizing the widget;
     * the height is clamped into MIN..MAX and invalid positions are no-ops. */
    public void applyRowHeight(int position, int heightDp) {
        if (position < 0 || position >= rowCount()) return;
        int id = rowIdAt(position);
        store.setHeight(id, heightDp);
        applyRowHeightPx(id, store.heightAt(position));
    }

    public int rowHeightDp(int position) {
        return store.heightAt(position);
    }

    /** BDD access to a row's widget view (tap-mode scenarios). */
    public WidgetHostView widgetHostViewAt(int position) {
        return hostViews.get(rowIdAt(position));
    }

    /** BDD helper: switches a row's tap mode directly. */
    public void setWidgetTaps(int position, WidgetTaps taps) {
        if (position < 0 || position >= rowCount()) return;
        int id = rowIdAt(position);
        store.setTaps(id, taps);
        WidgetHostView hv = hostViews.get(id);
        if (hv != null) hv.setTaps(taps);
    }

    /** Called when a hold lands on empty board space (no widget underneath):
     * opens the widget adder popover. */
    public void onEmptySpaceLongPressed(float x, float y) {
        if (isEditing()) return;
        if (rowCount() == 0) {
            openAdder();
            return;
        }
        // with widgets on the board: empty space = below the last row
        View last = boardRowAt(rowCount() - 1);
        if (last != null && y > last.getBottom() + board.getScrollY()) openAdder();
    }

    // ---- board rows ------------------------------------------------------------------------

    /** Builds a board row for a bound widget: the widget view (Olauncher's
     * touch dispatcher) in a fixed-height frame. A long press anywhere on the
     * widget enters its edit mode; keeping the finger down drags the row. */
    private View createRow(WidgetEntry entry) {
        AppWidgetProviderInfo info = widgets.getAppWidgetInfo(entry.id());
        WidgetHostView widget = (WidgetHostView) host.createView(plainWidgetContext(), entry.id(), info);
        // API 31+ wraps widgets in a system white rounded container; the board
        // wants the widget content itself, transparent
        widget.setBackgroundResource(0);
        widget.setTaps(entry.taps());
        widget.updateSize(boardWidthDp(), entry.heightDp());
        widget.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(entry.heightDp())));
        int id = entry.id();
        widget.setOnLongPress(() -> enterEditMode(id));
        widget.setOnDrag(dy -> dragRow(rowOfWidget(widget), dy));
        widget.setOnDragEnd(dy -> endDrag(rowOfWidget(widget), dy));
        // touches in tap modes other than "on" behave as board-space touches
        widget.setHomeGestures(flingDetector::onTouchEvent);
        widget.setOnHomeGestureCancel(() -> { /* the fling detector needs no cancel */ });

        FrameLayout row = new FrameLayout(this);
        row.setTag(id);
        row.addView(widget);
        return row;
    }

    private View rowOfWidget(WidgetHostView widget) {
        return widget.getParent() instanceof View ? (View) widget.getParent() : null;
    }

    private View ensureRow(WidgetEntry entry) {
        View row = rows.get(entry.id());
        if (row == null) {
            row = createRow(entry);
            rows.put(entry.id(), row);
        } else {
            // refresh from the persisted entry (taps mode may have changed)
            WidgetHostView hv = (WidgetHostView) ((ViewGroup) row).getChildAt(0);
            hv.setTaps(entry.taps());
            hv.getLayoutParams().height = (int) dp(entry.heightDp());
        }
        hostViews.put(entry.id(), (WidgetHostView) ((ViewGroup) row).getChildAt(0));
        return row;
    }

    private void removeWidget(int id) {
        host.deleteAppWidgetId(id);
        store.remove(id);
        if (editingWidgetId == id) editingWidgetId = -1;
        rebuildBoard();
    }

    private void applyRowHeightPx(int id, int heightDp) {
        View row = rows.get(id);
        if (row == null) return;
        WidgetHostView hv = (WidgetHostView) ((ViewGroup) row).getChildAt(0);
        row.getLayoutParams().height = (int) dp(heightDp);
        row.setLayoutParams(row.getLayoutParams());
        hv.getLayoutParams().height = (int) dp(heightDp);
        hv.updateSize(boardWidthDp(), heightDp);
    }

    // ---- drag-to-reorder (Olauncher's drag after long press) --------------------------------

    /** Moves the dragged row under the finger, swapping it past neighbor rows
     * once the dragged row's middle crosses theirs. */
    private void dragRow(View row, float dy) {
        if (row == null) return;
        int index = rowIndex(row);
        if (index < 0) return;
        if (dragStartIndex == -1) {
            dragStartIndex = index;
            dragCurrentIndex = index;
        }
        float middle = row.getTop() + row.getHeight() / 2f + dy;
        int spacing = (int) dp(8);
        if (dy > 0) {
            while (index + 1 < rowChildren().size()) {
                View next = rowChildren().get(index + 1);
                if (middle >= next.getTop() + next.getHeight() / 2f) {
                    boardStack.removeView(row);
                    boardStack.addView(row, stackIndexOfRowView(index + 1));
                    dy -= next.getHeight() + spacing;
                    middle -= next.getHeight() + spacing;
                    index++;
                } else break;
            }
        } else if (dy < 0) {
            while (index > 0) {
                View prev = rowChildren().get(index - 1);
                if (middle <= prev.getTop() + prev.getHeight() / 2f) {
                    boardStack.removeView(row);
                    boardStack.addView(row, stackIndexOfRowView(index - 1));
                    dy += prev.getHeight() + spacing;
                    middle += prev.getHeight() + spacing;
                    index--;
                } else break;
            }
        }
        dragCurrentIndex = index;
        row.setTranslationY(dy);
    }

    /** Stack index that places the row after `rowIndex` rows (skipping the
     * edit bar, which sits above the edited row). */
    private int stackIndexOfRowView(int rowIndex) {
        List<View> children = rowChildren();
        if (rowIndex >= children.size()) return boardStack.getChildCount();
        return boardStack.indexOfChild(children.get(rowIndex));
    }

    private int rowIndex(View row) {
        List<View> children = rowChildren();
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) == row) return i;
        }
        return -1;
    }

    /** Ends a drag: persists the settled order and lays the row back flat. */
    private void endDrag(View row, float dy) {
        if (row == null) return;
        row.setTranslationY(0);
        if (dragStartIndex != -1 && dragCurrentIndex != dragStartIndex) {
            store.move(dragStartIndex, dragCurrentIndex);
        }
        dragStartIndex = -1;
        dragCurrentIndex = -1;
        rebuildBoard();
    }

    // ---- resize frame (Olauncher's resize overlay, vertical only) ---------------------------

    private void toggleResizeOverlay(int id) {
        View row = rows.get(id);
        if (row == null) return;
        if (row.findViewWithTag(RESIZE_OVERLAY_TAG) != null) {
            dismissResizeOverlay();
            return;
        }
        View editedRow = rows.get(editingWidgetId);
        if (editedRow != row) return;
        ResizeOverlay overlay = new ResizeOverlay(this);
        overlay.setTag(RESIZE_OVERLAY_TAG);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TypedArray ta = obtainStyledAttributes(new int[]{androidx.appcompat.R.attr.colorPrimary});
        overlay.setColor(ta.getColor(0, 0xFF888888));
        ta.recycle();
        overlay.setOnResize(growHeight -> {
            int startDp = store.heightAt(positionOf(id));
            int h = Math.max(MIN_HEIGHT_DP, Math.min(MAX_HEIGHT_DP,
                    startDp + Math.round(growHeight / getResources().getDisplayMetrics().density)));
            applyRowHeightPx(id, h);
        });
        overlay.setOnResizeEnd(() -> store.setHeight(id, Math.round(
                rows.get(id).getHeight() / getResources().getDisplayMetrics().density)));
        overlay.setOnMove(dy -> dragRow(row, dy));
        overlay.setOnMoveEnd(dy -> endDrag(row, dy));
        ((ViewGroup) row).addView(overlay);
    }

    private void dismissResizeOverlay() {
        for (View row : new ArrayList<>(rows.values())) {
            View overlay = row.findViewWithTag(RESIZE_OVERLAY_TAG);
            if (overlay != null) ((ViewGroup) row).removeView(overlay);
        }
    }

    private int boardWidthDp() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return Math.round(dm.widthPixels / dm.density);
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }

    // ---- widget adder popover --------------------------------------------------------------

    /** Opens the half-screen bottom popover listing every provider's widget kind. */
    void openAdder() {
        // never reuse a stale dialog: a missed dismiss used to block every
        // later open (button and hold-on-empty-space) until a relaunch
        closeAdder();
        View content = getLayoutInflater().inflate(R.layout.widget_adder, null);
        LinearLayout list = content.findViewById(R.id.adder_list);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        for (AppWidgetProviderInfo info : widgets.getInstalledProviders()) {
            list.addView(adderRow(info, dm));
        }
        adderDialog = new AlertDialog.Builder(this).setView(content).create();
        adderDialog.show();
        // window tweaks must come after show(): pre-show setLayout is ignored
        Window w = adderDialog.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, dm.heightPixels / 2);
            w.setWindowAnimations(R.style.WidgetAdderAnimation);
        }
    }

    private LinearLayout adderRow(AppWidgetProviderInfo info, DisplayMetrics dm) {
        LinearLayout row = (LinearLayout) getLayoutInflater()
                .inflate(R.layout.widget_adder_item, null);
        TextView title = row.findViewById(R.id.kind_title);
        TextView sub = row.findViewById(R.id.kind_sub);
        title.setText(info.loadLabel(getPackageManager()));
        String app = appLabel(info.provider.getPackageName());
        String kind = description(info);
        sub.setText(kind.isEmpty() ? app : app + " · " + kind);
        fillPreview(row.findViewById(R.id.kind_preview), info);
        row.setOnClickListener(v -> pickKind(info));
        return row;
    }

    private String appLabel(String pkg) {
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    /** The widget kind shown in the adder: the provider description on API 31+,
     * empty otherwise (the app name alone is shown). */
    private String description(AppWidgetProviderInfo info) {
        if (Build.VERSION.SDK_INT < 31) return "";
        CharSequence d = info.loadDescription(this);
        return d == null ? "" : d.toString();
    }

    /** Renders the widget preview in the adder row: RemoteViews apply of the
     * provider's initial layout (resource-correct), else the provider-package
     * previewImage, else an outlined placeholder box. */
    private void fillPreview(FrameLayout box, AppWidgetProviderInfo info) {
        View preview = null;
        if (info.initialLayout != 0) {
            try {
                preview = new android.widget.RemoteViews(
                        info.provider.getPackageName(), info.initialLayout).apply(this, box);
            } catch (Exception ignored) {
                preview = null;
            }
        }
        if (preview == null && info.previewImage != 0) {
            try {
                Context pc = createPackageContext(info.provider.getPackageName(),
                        Context.CONTEXT_IGNORE_SECURITY);
                android.widget.ImageView iv = new android.widget.ImageView(this);
                iv.setImageDrawable(pc.getDrawable(info.previewImage));
                iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                preview = iv;
            } catch (Exception ignored) {
                preview = null;
            }
        }
        if (preview == null) {
            preview = new View(this);
            preview.setBackgroundResource(R.drawable.preview_placeholder);
        }
        preview.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        box.addView(preview);
    }

    /** Binds the reserved id to the picked provider directly when allowed,
     * otherwise hands over to the system bind confirmation (the widget kind is
     * already known: the user only confirms the binding permission). */
    private void pickKind(AppWidgetProviderInfo info) {
        int id = host.allocateAppWidgetId();
        if (widgets.bindAppWidgetIdIfAllowed(id, info.provider)) {
            finishAdd(id, info);
        } else {
            host.deleteAppWidgetId(id);
            int fallback = host.allocateAppWidgetId();
            Intent bind = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
            bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, fallback);
            bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
            startActivityForResult(bind, REQUEST_BIND);
            closeAdder(); // otherwise the popover scrim hides the system dialog
        }
    }

    /** A bound widget joins the board — unless it has a mandatory configure
     * activity, which runs first (Olauncher's flow). */
    private void finishAdd(int id, AppWidgetProviderInfo info) {
        if (info != null && WidgetHostView.needsConfiguration(info)) {
            pendingConfigureId = id;
            startConfigure(id, info);
            closeAdder();
            return;
        }
        store.add(id, info == null
                ? WidgetPrefs.DEFAULT_HEIGHT_DP
                : WidgetHostView.defaultHeightDp(this, info));
        closeAdder();
        rebuildBoard();
    }

    /** Launches a widget's configure activity for an already-bound id. */
    private void startConfigure(int id, AppWidgetProviderInfo info) {
        if (info.configure == null) return;
        Intent configure = new Intent();
        configure.setComponent(info.configure);
        configure.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        startActivityForResult(configure, REQUEST_CONFIGURE);
    }

    public void closeAdder() {
        if (adderDialog != null) {
            adderDialog.dismiss();
            adderDialog = null;
        }
    }

    public boolean adderOpen() {
        return adderDialog != null && adderDialog.isShowing();
    }

    // ---- system bind / pick / configure results ---------------------------------------------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        int id = data == null ? -1 : data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        if (requestCode == REQUEST_PICK && (id == -1 || resultCode != RESULT_OK
                || widgets.getAppWidgetInfo(id) == null)) {
            // cancelled or unbound pick: drop the reserved id, keep the board as-is
            if (id != -1) host.deleteAppWidgetId(id);
            return;
        }
        if (requestCode == REQUEST_PICK || requestCode == REQUEST_BIND) {
            if (resultCode != RESULT_OK || widgets.getAppWidgetInfo(id) == null) {
                if (id != -1) host.deleteAppWidgetId(id);
                return;
            }
            finishAdd(id, widgets.getAppWidgetInfo(id));
            return;
        }
        if (requestCode == REQUEST_CONFIGURE) {
            if (resultCode == RESULT_OK && pendingConfigureId != -1) {
                AppWidgetProviderInfo info = widgets.getAppWidgetInfo(pendingConfigureId);
                store.add(pendingConfigureId, info == null
                        ? WidgetPrefs.DEFAULT_HEIGHT_DP
                        : WidgetHostView.defaultHeightDp(this, info));
            } else if (pendingConfigureId != -1) {
                host.deleteAppWidgetId(pendingConfigureId);
            }
            pendingConfigureId = -1;
            rebuildBoard();
        }
    }
}

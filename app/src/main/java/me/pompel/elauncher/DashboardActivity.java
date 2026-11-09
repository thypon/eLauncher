package me.pompel.elauncher;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.app.ActivityOptions;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * eBoard — a macOS-Dashboard-style board of persistent home screen widgets.
 * Internal to the launcher (not exported, no launcher entry): opened by the
 * launcher's default left-side gesture, any gesture assigned to it in the
 * Settings picker, or in-app explicit intents only. Widgets picked from the
 * widget adder popover are hosted here for good: the AppWidgetHost binding is
 * kept by the system and the board order is persisted across restarts.
 *
 * Interaction model: the board lays widgets out in two columns; widget taps
 * reach the widgets themselves. Long press on empty board space opens the
 * widget adder popover (half the screen, bottom-anchored, listing every
 * provider's widget kind with a preview); a hold on a widget enters edit
 * mode, where each widget gets an (x) remove handle, a corner resize handle
 * and drag-to-reorder; pressing empty space leaves edit mode. The add button
 * exists only on an empty board.
 */
public class DashboardActivity extends AppCompatActivity {
    /** Distinguishes eBoard-hosted widgets from other hosts' widget ids. */
    static final int HOST_ID = 0x0E13;
    private static final int REQUEST_PICK = 1;
    private static final int REQUEST_BIND = 2;
    private static final int REQUEST_CONFIGURE = 3;
    /** Launcher3's REQUEST_RECONFIGURE_APPWIDGET: re-opening the
     * configuration of an already-placed widget. */
    private static final int REQUEST_RECONFIGURE = 13;
    private static final String STATE_PENDING_CONFIGURE_ID = "eboard.pendingConfigureId";

    /** The reserved app widget id whose configure screen is on screen
     * (Launcher3 keeps the pending add alive across the configure round
     * trip — and across the activity being recreated while it runs). */
    private int pendingConfigureId = AppWidgetManager.INVALID_APPWIDGET_ID;
    /** Range of a widget row height in dp, used by the resize handle. */
    public static final int MIN_HEIGHT_DP = 140;
    public static final int MAX_HEIGHT_DP = 600;
    public static final int MIN_WIDTH_DP = 100;
    /** Height of one grid cell: like Launcher3's non-continuous grid,
     * released resizes snap to whole cells (columns of the full board
     * width for width, this many dp for height). */
    public static final int CELL_HEIGHT_DP = 100;
    /** Fallback height when the provider advises nothing. */
    public static final int DEFAULT_HEIGHT_DP = 200;
    /** Hold duration that enters edit mode / opens the adder. */
    public static final long HOLD_MS = 700;
    /** Movement (px) that cancels a pending hold. */
    private static final float HOLD_CANCEL_SLOP_PX = 30;

    private AppWidgetHost host;
    private AppWidgetManager widgets;
    private RecyclerView board;
    private TextView emptyHint;
    private TextView editHint;
    private Button addWidgetButton;
    private BoardStore store;
    private BoardAdapter adapter;
    private boolean editMode;
    private AlertDialog adderDialog;
    /** Screen side the launcher opened this board from: -1 left, +1 right,
     * 0 when launched without the launcher (e.g. recents), in which case
     * horizontal flings stay inert. */
    private int entryDirection;
    private GestureDetector flingDetector;

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
        if (savedInstanceState != null) {
            pendingConfigureId = savedInstanceState.getInt(STATE_PENDING_CONFIGURE_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        flingDetector = new GestureDetector(this, new FlingListener());
        // the system back/forward edge gesture must not fight the disabled
        // entry fling: exclude the entry side's edge from the system gesture
        // zones (no-op below API 29 and for direct launches)
        findViewById(android.R.id.content).post(this::applySystemGestureExclusion);
        board = findViewById(R.id.board);
        emptyHint = findViewById(R.id.empty_hint);
        editHint = findViewById(R.id.edit_hint);
        addWidgetButton = findViewById(R.id.add_widget);
        // the application-context manager is shared with the app-scoped
        // widget host and is stable across configuration changes
        widgets = AppWidgetManager.getInstance(getApplicationContext());
        host = new AppWidgetHost(this, HOST_ID);
        store = new BoardStore(getSharedPreferences(BoardStore.PREFS_FILE, MODE_PRIVATE));
        adapter = new BoardAdapter();
        // two columns by default; a row whose widget is wider than a
        // column (an advised or resized wide widget) spans the full row
        GridLayoutManager grid = new GridLayoutManager(this, 2);
        grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                if (adapter == null || position < 0
                        || position >= adapter.ids.size()) return 1;
                int id = adapter.ids.get(position);
                AppWidgetProviderInfo info = widgets.getAppWidgetInfo(id);
                int wDp = store.hasWidth(id) ? store.width(id)
                        : advisedWidthDp(info);
                return wDp > columnWidthDp() ? 2 : 1;
            }
        });
        board.setLayoutManager(grid);
        board.setClickable(true); // so the empty-space hold logic receives touches
        board.setAdapter(adapter);
        board.addItemDecoration(new BoardSpacingDecoration((int) dp(8)));
        new ItemTouchHelper(new DragReorderCallback()).attachToRecyclerView(board);
        addWidgetButton.setOnClickListener(v -> openAdder());
        // unified hold handling on EMPTY board space (the RV itself is the
        // touch target when no widget is underneath): hold opens the adder,
        // pressing empty space while in edit mode leaves it. Taps on widgets
        // go through their own proxy overlay (see createRow).
        // An OnTouchListener on the board fires whenever the board view is
        // the touch target — a RecyclerView.OnItemTouchListener would NOT:
        // its onTouchEvent only runs after onInterceptTouchEvent returns
        // true, so the empty-space hold was dead there.
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable openAdder = this::openAdder;
        final float[] holdDown = new float[2];
        board.setOnTouchListener((rv, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    holdDown[0] = e.getX();
                    holdDown[1] = e.getY();
                    if (editMode) {
                        exitEditMode(); // pressing empty space leaves edit mode
                    } else {
                        handler.removeCallbacks(openAdder);
                        handler.postDelayed(openAdder, HOLD_MS);
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(e.getX() - holdDown[0]) > HOLD_CANCEL_SLOP_PX
                            || Math.abs(e.getY() - holdDown[1]) > HOLD_CANCEL_SLOP_PX) {
                        handler.removeCallbacks(openAdder);
                    }
                    break;
                default:
                    handler.removeCallbacks(openAdder);
                    break;
            }
            return false; // the RecyclerView keeps scrolling and tapping
        });
    }

    /** Applies the same night-mode computation as MainActivity. */
    private void AppCompatDelegate_setDefaultNightMode(boolean dark) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(dark
                ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_PENDING_CONFIGURE_ID, pendingConfigureId);
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
        if (editMode) {
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

    /** Horizontal flings leave the board back to the launcher. The fling
     * motion that opened the board is disabled inside it; the opposite
     * fling finishes the board with the reverse directional animation. */
    private class FlingListener extends GestureDetector.SimpleOnGestureListener {
        @Override public boolean onDown(@NonNull MotionEvent e) { return true; }

        @Override public boolean onFling(@NonNull MotionEvent e1,
                @NonNull MotionEvent e2, float velocityX, float velocityY) {
            float xDiff = e2.getX() - e1.getX();
            float yDiff = e2.getY() - e1.getY();
            if (entryDirection == 0 || editMode
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

    /** Rebuilds the board from the persisted widget list, dropping widgets
     * whose provider is no longer installed; rows are created lazily on bind. */
    private void rebuildBoard() {
        adapter.refresh(idsWithProvider());
        notifyDataSetChanged();
    }

    private java.util.List<Integer> idsWithProvider() {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (int id : store.ids()) {
            if (widgets.getAppWidgetInfo(id) != null) out.add(id);
            else {
                host.deleteAppWidgetId(id);
                store.remove(id);
            }
        }
        return out;
    }

    private void notifyDataSetChanged() {
        adapter.notifyDataSetChanged();
        updateUiState();
    }

    private void updateUiState() {
        boolean empty = adapter.getItemCount() == 0;
        emptyHint.setVisibility(empty ? View.VISIBLE : View.GONE);
        // the add button exists only on an empty board; afterwards long press
        // on empty board space is the adder entry point
        addWidgetButton.setVisibility(empty && !editMode ? View.VISIBLE : View.GONE);
        if (empty) exitEditMode();
    }

    // ---- edit mode -------------------------------------------------------------------------

    public void enterEditMode() {
        editMode = true;
        adapter.setEditMode(true);
        updateUiState(); // no notifyDataSetChanged: the touch that triggered the
                         // edit must not detach the row mid-gesture
        editHint.setVisibility(View.VISIBLE);
    }

    public void exitEditMode() {
        if (!editMode) return;
        editMode = false;
        adapter.setEditMode(false);
        updateUiState();
        editHint.setVisibility(View.GONE);
    }

    public boolean isEditMode() {
        return editMode;
    }

    // ---- board state accessors (used by the BDD world) --------------------------------------

    public int rowCount() {
        return adapter.getItemCount();
    }

    public View boardRowAt(int position) {
        return adapter.rowAt(position);
    }

    public int gridColumns() {
        return ((GridLayoutManager) board.getLayoutManager()).getSpanCount();
    }

    /** The board grid column width in dp under the current density
     * setting (observation entry point for the density scenarios). */
    public int boardColumnDp() {
        return columnWidthDp();
    }

    /** The span size the row's position occupies in the grid (1 = one
     * column, 2 = the full board row). */
    public int rowSpan(int position) {
        GridLayoutManager lm = (GridLayoutManager) board.getLayoutManager();
        return lm.getSpanSizeLookup().getSpanSize(position);
    }

    /** Re-creates the widget view of a placed row at its stored size:
     * updateAppWidgetSize alone does not force the provider's
     * RemoteViews to re-layout visually (used after a released resize
     * and after a re-run configuration). */
    private void recreateWidgetView(int id, AppWidgetProviderInfo info) {
        FrameLayout row = (FrameLayout) adapter.rows.get(id);
        if (row == null) return;
        int width = store.hasWidth(id) ? store.width(id) : advisedWidthDp(info);
        int height = store.hasSize(id) ? store.size(id) : advisedHeightDp(info);
        row.removeViewAt(0);
        AppWidgetHostView fresh = host.createView(plainWidgetContext(), id, info);
        fresh.updateAppWidgetSize(new Bundle(), width, height, width, height);
        fresh.setLayoutParams(new FrameLayout.LayoutParams(
                (int) dp(width), (int) dp(height)));
        row.addView(fresh, 0);
    }

    /** Reorders the board (drag-to-reorder lands here) and persists the order;
     * out-of-range indices are no-ops. */
    public void moveBoardRow(int from, int to) {
        int size = adapter.getItemCount();
        if (from < 0 || from >= size || to < 0 || to >= size || from == to) return;
        adapter.move(from, to);
    }

    /** Applies a new row height in dp, persisting it and resizing the widget;
     * the height is clamped into MIN..MAX and invalid positions are no-ops. */
    public void applyRowHeight(int position, int heightDp) {
        int clamped = Math.max(DashboardActivity.MIN_HEIGHT_DP,
                Math.min(DashboardActivity.MAX_HEIGHT_DP, heightDp));
        if (position < 0 || position >= adapter.getItemCount()) return;
        adapter.resize(position, clamped);
    }

    public int rowHeightDp(int position) {
        return adapter.heightDpAt(position);
    }

    /** Current rendered width of the widget view of a row, in dp. */
    public int rowWidthDp(int position) {
        ViewGroup row = (ViewGroup) adapter.rowAt(position);
        View widget = row.getChildAt(0);
        return Math.round(widget.getLayoutParams().width
                == ViewGroup.LayoutParams.MATCH_PARENT
                ? widget.getWidth() / boardDensity()
                : widget.getLayoutParams().width / boardDensity());
    }

    /** Advised row height for a widget: its provider's maximum advised
     * height when it declares one, else its declared default height,
     * clamped to the board limits. */
    private int advisedHeightDp(AppWidgetProviderInfo info) {
        if (info == null) return DEFAULT_HEIGHT_DP;
        int advised = info.maxResizeHeight > 0 ? info.maxResizeHeight
                : (info.minHeight > 0 ? info.minHeight : DEFAULT_HEIGHT_DP);
        return Math.max(MIN_HEIGHT_DP, Math.min(MAX_HEIGHT_DP, advised));
    }

    /** Advised row width for a widget: its provider's maximum advised
     * width when it declares one, up to the full board width — a wide
     * widget is not squeezed into a grid column — else the full grid
     * column it lives in, clamped to the board width. */
    private int advisedWidthDp(AppWidgetProviderInfo info) {
        if (info == null || info.maxResizeWidth <= 0) return columnWidthDp();
        return Math.max(MIN_WIDTH_DP, Math.min(boardWidthDp(), info.maxResizeWidth));
    }

    /** Board density: the system density scaled by the eBoard DPI setting
     * (a percent stored by the Settings screen; 100 = system density). All
     * board geometry — sizes, drag math, adder cards — uses this so the
     * setting zooms the whole board. */
    private float boardDensity() {
        int percent = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(this)
                .getInt("eboard_density_preference", 100);
        return getResources().getDisplayMetrics().density * (percent / 100f);
    }

    private int boardWidthDp() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return Math.round(dm.widthPixels / boardDensity());
    }

    /** Called when a hold lands on empty board space (no widget underneath):
     * opens the widget adder popover. */
    public void onEmptySpaceLongPressed(float x, float y) {
        if (editMode) return;
        if (adapter.getItemCount() == 0) {
            openAdder();
            return;
        }
        // with widgets on the board: empty space = below the last widget
        View under = board.findChildViewUnder(x, y);
        if (under == null) openAdder();
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
        // Launcher3 widget-picker structure: kinds grouped under their app,
        // each app section showing a grid of preview cards
        java.util.LinkedHashMap<String, java.util.List<AppWidgetProviderInfo>> byApp =
                new java.util.LinkedHashMap<>();
        for (AppWidgetProviderInfo info : widgets.getInstalledProviders()) {
            byApp.computeIfAbsent(info.provider.getPackageName(),
                    p -> new java.util.ArrayList<>()).add(info);
        }
        boolean first = true;
        for (java.util.Map.Entry<String, java.util.List<AppWidgetProviderInfo>> e
                : byApp.entrySet()) {
            ViewGroup section = (ViewGroup) adderSection(e.getKey(), e.getValue(), dm);
            list.addView(section);
            // Launcher3 picker behavior: every app header stays visible,
            // only one app's kinds are expanded at a time (first expanded)
            ViewGroup headerRow = (ViewGroup) section.getChildAt(0);
            headerRow.setOnClickListener(v -> {
                for (int i = 0; i < list.getChildCount(); i++) {
                    View s = list.getChildAt(i);
                    expandAdderSection(s, s == section
                            && s.findViewById(R.id.adder_grid).getVisibility()
                                    != View.VISIBLE);
                }
            });
            if (first) expandAdderSection(section, true);
            else expandAdderSection(section, false);
            first = false;
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

    /** Expands or collapses one app section (its kind grid) and updates the
     * header toggle glyph (Launcher3 expand state). */
    private void expandAdderSection(View section, boolean expand) {
        View grid = section.findViewById(R.id.adder_grid);
        grid.setVisibility(expand ? View.VISIBLE : View.GONE);
        TextView toggle = section.findViewById(R.id.adder_toggle);
        toggle.setText(expand ? "▾" : "▸");
        if (!expand) return;
        // fade the grid in like Launcher3's sheet entries
        grid.setAlpha(0f);
        grid.animate().alpha(1f).setDuration(120).start();
    }

    /** One app section of the adder: an app header (icon, name, kind count,
     * expand toggle) over a table of widget kind cards. Kinds whose grid span
     * covers the full column pair fill their own table row; narrower kinds
     * share a row (Launcher3 WidgetsListTableViewHolderBinder structure). */
    private View adderSection(String pkg, java.util.List<AppWidgetProviderInfo> kinds,
            DisplayMetrics dm) {
        LinearLayout section = (LinearLayout) getLayoutInflater()
                .inflate(R.layout.widget_adder_section, null);
        TextView appTitle = section.findViewById(R.id.adder_app_title);
        appTitle.setText(appLabel(pkg));
        TextView appCount = section.findViewById(R.id.adder_app_count);
        appCount.setText(getString(R.string.eboard_adder_count, kinds.size()));
        ImageView appIcon = section.findViewById(R.id.adder_app_icon);
        Drawable icon = appIcon(pkg);
        if (icon != null) appIcon.setImageDrawable(icon);
        else appIcon.setVisibility(View.GONE);
        TableLayout grid = section.findViewById(R.id.adder_grid);

        int columnPx = Math.round(columnWidthDp() * boardDensity());
        int cellPad = (int) dp(6);
        TableRow row = null;
        int spanBudget = 0;
        for (AppWidgetProviderInfo info : kinds) {
            int span = kindSpan(info, dm);
            if (span > spanBudget || row == null) {
                row = new TableRow(this);
                grid.addView(row, new TableLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                spanBudget = 2;
            }
            spanBudget -= span;
            View cell = getLayoutInflater().inflate(R.layout.widget_adder_cell, null);
            // Launcher3 WidgetCell: preview container weight = cell span,
            // so wider kinds get more room than their half-row neighbors
            cell.setLayoutParams(new TableRow.LayoutParams(
                    0, TableRow.LayoutParams.WRAP_CONTENT, span));
            TextView title = cell.findViewById(R.id.kind_title);
            title.setText(info.loadLabel(getPackageManager()));
            TextView sub = cell.findViewById(R.id.kind_sub);
            String kind = description(info);
            sub.setText(kind.isEmpty() ? appLabel(pkg) : appLabel(pkg) + " · " + kind);
            TextView dims = cell.findViewById(R.id.kind_dims);
            dims.setText(getString(R.string.eboard_adder_dims,
                    kindSpanX(info, dm), kindSpanY(info, dm)));
            View preview = cell.findViewById(R.id.kind_preview);
            // the preview fills the cell (MATCH_PARENT in the layout);
            // the cell itself is weighted by its span in the table row
            fillPreview((FrameLayout) preview, info);
            cell.setOnClickListener(v -> pickKind(info));
            row.addView(cell);
        }
        return section;
    }

    /** Grid columns the kind occupies in the adder (Launcher3 spanX,
     *  estimated from the declared minimum width: wider than a grid column
     *  = the full column pair). */
    private int kindSpan(AppWidgetProviderInfo info, DisplayMetrics dm) {
        return info.minWidth > columnWidthDp() ? 2 : 1;
    }

    private int kindSpanX(AppWidgetProviderInfo info, DisplayMetrics dm) {
        return kindSpan(info, dm);
    }

    private int kindSpanY(AppWidgetProviderInfo info, DisplayMetrics dm) {
        return Math.max(1, Math.round(info.minHeight / columnWidthDp()));
    }

    private Drawable appIcon(String pkg) {
        Drawable icon = null;
        try {
            icon = getPackageManager().getApplicationIcon(pkg);
        } catch (Exception ignored) {
        }
        if (icon == null) {
            // fall back to the widget receiver's icon, then to the launcher
            // icon (test fixtures register receivers without app records)
            for (AppWidgetProviderInfo i : widgets.getInstalledProviders()) {
                if (pkg.equals(i.provider.getPackageName())) {
                    try {
                        icon = i.loadIcon(this, 0);
                        break;
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return icon != null ? icon
                : androidx.core.content.res.ResourcesCompat
                        .getDrawable(getResources(), R.mipmap.ic_launcher, getTheme());
    }

    private String appLabel(String pkg) {
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            // fall back to the widget receiver's label (test fixtures)
            for (AppWidgetProviderInfo i : widgets.getInstalledProviders()) {
                if (pkg.equals(i.provider.getPackageName())) return i.loadLabel(getPackageManager());
            }
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
                        info.provider.getPackageName(), info.initialLayout)
                        .apply(plainWidgetContext(), box);
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
            // Launcher3 binds the widget with its advised size options so
            // the provider sizes its first RemoteViews to the landing spot;
            // options come from the bound info (the manager's authoritative
            // record), not the picker cell's captured copy
            widgets.updateAppWidgetOptions(id,
                    advisedOptions(widgets.getAppWidgetInfo(id)));
            beginAdd(id, info);
            closeAdder();
        } else {
            // Launcher3 keeps the allocated id through the system bind
            // confirmation: no second allocation behind the dialog
            Intent bind = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
            bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
            startActivityForResult(bind, REQUEST_BIND);
            closeAdder(); // otherwise the popover scrim hides the system dialog
        }
    }

    /** AppWidget options carrying the advised size in dp (Launcher3 binds
     * widgets with OPTION_APPWIDGET_MIN/MAX_* from the provider advice). */
    private Bundle advisedOptions(AppWidgetProviderInfo info) {
        int wDp = advisedWidthDp(info);
        int hDp = advisedHeightDp(info);
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, wDp);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, wDp);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, hDp);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, hDp);
        return options;
    }

    /** Launcher3-style add completion: a provider that declares a configure
     * activity must be configured before the row lands. The launch goes
     * through AppWidgetHost: on device the system appwidget service builds
     * the config PendingIntent (createAppWidgetConfigIntentSender), which is
     * also the only way non-exported configure activities are startable from
     * a launcher (a hand-built explicit intent throws SecurityException).
     * Cancelling or an unresolvable configure activity frees the id. */
    private void beginAdd(int id, AppWidgetProviderInfo info) {
        if (info != null && info.configure != null) {
            pendingConfigureId = id;
            try {
                // the system-sender PendingIntent starts as uid android; on
                // U+ it is BAL-hardened unless the options grant the
                // background-activity-start privilege — Launcher3 does the
                // same via ActivityContext.makeDefaultActivityOptions
                host.startAppWidgetConfigureActivityForResult(this, id, 0,
                        REQUEST_CONFIGURE, configureActivityOptions().toBundle());
            } catch (RuntimeException e) {
                // ActivityNotFoundException / SecurityException: no usable
                // configure activity, the reserved id must not leak
                pendingConfigureId = AppWidgetManager.INVALID_APPWIDGET_ID;
                host.deleteAppWidgetId(id);
            }
            return;
        }
        store.add(id);
        adapter.ids.add(id);
        notifyDataSetChanged();
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

    // ---- system bind / pick results ---------------------------------------------------------

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
        if (requestCode == REQUEST_BIND) {
            if (resultCode != RESULT_OK || widgets.getAppWidgetInfo(id) == null) {
                if (id != -1) host.deleteAppWidgetId(id);
                return;
            }
            // the system bind dialog did not carry our advised options
            widgets.updateAppWidgetOptions(id, advisedOptions(
                    widgets.getAppWidgetInfo(id)));
            beginAdd(id, widgets.getAppWidgetInfo(id));
        }
        if (requestCode == REQUEST_CONFIGURE) {
            // the result carries the id; a missing extra falls back to the
            // pending record (Launcher3's completeTwoStageWidgetDrop)
            if (id == -1) id = pendingConfigureId;
            pendingConfigureId = AppWidgetManager.INVALID_APPWIDGET_ID;
            AppWidgetProviderInfo info = id == -1 ? null : widgets.getAppWidgetInfo(id);
            if (resultCode == RESULT_OK && info != null) {
                store.add(id);
                adapter.ids.add(id);
                notifyDataSetChanged();
            } else if (id != -1) {
                // cancelled configuration: the reserved id is freed, the
                // board stays as it was
                host.deleteAppWidgetId(id);
            }
        } else if (requestCode == REQUEST_RECONFIGURE) {
            // re-running the configuration of a PLACED widget: a result
            // re-creates the view so the provider's fresh settings apply;
            // a cancel keeps the widget exactly as it was (no id delete —
            // Launcher3 leaves placed widgets alone on reconfigure cancel)
            if (id == -1) id = pendingConfigureId;
            pendingConfigureId = AppWidgetManager.INVALID_APPWIDGET_ID;
            AppWidgetProviderInfo info = id == -1 ? null : widgets.getAppWidgetInfo(id);
            if (resultCode == RESULT_OK && info != null) {
                recreateWidgetView(id, info);
            }
        }
    }

    // ---- board rows ------------------------------------------------------------------------

    /** Builds a board row for a bound widget: the widget view plus, in edit
     * mode, an (x) remove handle and a corner resize handle. Taps reach the
     * widget itself; a hold enters edit mode through the board-level touch
     * listener, and the row long click does the same (BDD path). */
    /** Options for configure-activity launches: on U+ the system-sender
     * PendingIntent is BAL-hardened, so the background-activity-start
     * privilege must be granted (Launcher3's allowBGLaunch). */
    private ActivityOptions configureActivityOptions() {
        ActivityOptions options = ActivityOptions.makeBasic();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        }
        return options;
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
                    // the application context's inflater carries no view
                    // factory: AppCompat installs its substituting factory
                    // only on the activity's inflater
                    android.view.LayoutInflater base = (android.view.LayoutInflater)
                            getApplicationContext().getSystemService(
                                    Context.LAYOUT_INFLATER_SERVICE);
                    return base.cloneInContext(this);
                }
                return super.getSystemService(name);
            }
        };
    }

    private View createRow(int id) {
        AppWidgetProviderInfo info = widgets.getAppWidgetInfo(id);
        // a resized size wins; otherwise the widget appears at its provider's
        // advised size: the biggest variant it renders, in the fewest pixels
        // (maxResize* when the provider advises a max, else its default)
        int heightDp = store.hasSize(id) ? store.size(id)
                : advisedHeightDp(info);
        int wDp = store.hasWidth(id) ? store.width(id)
                : advisedWidthDp(info);

        AppWidgetHostView widget = host.createView(plainWidgetContext(), id, info);
        // API 31+ wraps widgets in a system white rounded container; the board
        // wants the widget content itself, transparent
        widget.setBackgroundResource(0);
        widget.updateAppWidgetSize(new Bundle(), wDp, heightDp, wDp, heightDp);
        widget.setLayoutParams(new FrameLayout.LayoutParams(
                (int) dp(wDp), (int) dp(heightDp)));

        FrameLayout row = new FrameLayout(this);
        row.setTag(id);
        // the widget may be dragged wider than the row before the row
        // re-spans: let it paint across the neighbor cell while dragging
        row.setClipChildren(false);
        row.addView(widget);

        // (x) remove handle, iOS-manage-icons style, visible in edit mode:
        // ink badge with teal ring, floating with elevation over any widget
        TextView remove = new TextView(this);
        remove.setText("✕");
        remove.setTextColor(Color.WHITE);
        remove.setTextSize(16);
        remove.setGravity(Gravity.CENTER);
        remove.setBackgroundResource(R.drawable.remove_badge);
        remove.setTag(R.id.remove_handle);
        remove.setOnClickListener(v -> removeWidget(id));
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(
                (int) dp(30), (int) dp(30), Gravity.TOP | Gravity.END);
        rp.setMargins(0, (int) dp(14), (int) dp(14), 0);
        remove.setLayoutParams(rp);
        remove.setElevation(dp(6));
        remove.setVisibility(editMode ? View.VISIBLE : View.GONE);
        row.addView(remove);

        // corner resize handle (bottom-right), visible in edit mode
        TextView resize = new TextView(this);
        resize.setText("◢");
        resize.setTextColor(Color.WHITE);
        resize.setTextSize(12);
        resize.setGravity(Gravity.CENTER);
        resize.setBackgroundResource(R.drawable.remove_badge);
        resize.setTag(R.id.resize_handle);
        resize.setLayoutParams(new FrameLayout.LayoutParams(
                (int) dp(28), (int) dp(28), Gravity.BOTTOM | Gravity.END));
        resize.setVisibility(editMode ? View.VISIBLE : View.GONE);
        resize.setOnTouchListener(new ResizeTouchListener(id));
        row.addView(resize);

        // reconfigure gear (bottom-left), only for kinds that declare a
        // configure activity: it re-opens the configuration for the
        // already-placed widget (Launcher3's REQUEST_RECONFIGURE flow)
        boolean configurable = info != null && info.configure != null;
        if (configurable) {
            TextView gear = new TextView(this);
            gear.setText("⚙");
            gear.setTextColor(Color.WHITE);
            gear.setTextSize(14);
            gear.setGravity(Gravity.CENTER);
            gear.setBackgroundResource(R.drawable.remove_badge);
            gear.setTag(R.id.reconfigure_handle);
            gear.setOnClickListener(v -> {
                AppWidgetProviderInfo live = widgets.getAppWidgetInfo(id);
                if (live == null || live.configure == null) return;
                pendingConfigureId = id;
                try {
                    host.startAppWidgetConfigureActivityForResult(
                            DashboardActivity.this, id, 0,
                            REQUEST_RECONFIGURE, configureActivityOptions().toBundle());
                } catch (RuntimeException e) {
                    // unresolvable configure activity: keep the widget,
                    // just forget the pending record
                    pendingConfigureId = AppWidgetManager.INVALID_APPWIDGET_ID;
                }
            });
            gear.setLayoutParams(new FrameLayout.LayoutParams(
                    (int) dp(28), (int) dp(28), Gravity.BOTTOM | Gravity.START));
            FrameLayout.LayoutParams gp = (FrameLayout.LayoutParams) gear.getLayoutParams();
            gp.setMargins((int) dp(14), 0, 0, (int) dp(14));
            gear.setElevation(dp(6));
            gear.setVisibility(editMode ? View.VISIBLE : View.GONE);
            row.addView(gear);
        }

        // hold-to-edit proxy overlay: taps are forwarded to the widget itself
        // (clicks pass through), a 700ms hold enters edit mode (iOS style)
        // and the widget receives a cancel when that happens. In edit mode
        // the overlay steps aside so handles and drag-reorder are reachable.
        // The forwarded stream always targets the row's CURRENT widget view:
        // a released resize re-creates it, a stale reference would dead-drop
        // every later touch.
        View overlay = new View(this);
        overlay.setTag(R.id.hold_overlay);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Handler handler = new Handler(Looper.getMainLooper());
        final float[] down = new float[2];
        Runnable holdEnterEdit = () -> {
            long now = android.os.SystemClock.uptimeMillis();
            android.view.MotionEvent cancel = android.view.MotionEvent.obtain(
                    now, now, MotionEvent.ACTION_CANCEL, down[0], down[1], 0);
            row.getChildAt(0).dispatchTouchEvent(cancel);
            cancel.recycle();
            // a held scrollable widget gave up interception: give it back so
            // drag-reorder works in edit mode
            row.getParent().requestDisallowInterceptTouchEvent(false);
            enterEditMode();
        };
        overlay.setOnTouchListener((v, ev) -> {
            if (editMode) return true; // handles + drag work in edit mode
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = ev.getX();
                    down[1] = ev.getY();
                    handler.removeCallbacks(holdEnterEdit);
                    handler.postDelayed(holdEnterEdit, HOLD_MS);
                    // scrollable widget content (ScrollView/AbsListView inside
                    // the RemoteViews) must keep the stream: the board must
                    // not intercept scrolls meant for the widget
                    if (hasScrollableDescendant(row.getChildAt(0))) {
                        row.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    row.getChildAt(0).dispatchTouchEvent(ev);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(ev.getX() - down[0]) > HOLD_CANCEL_SLOP_PX
                            || Math.abs(ev.getY() - down[1]) > HOLD_CANCEL_SLOP_PX) {
                        handler.removeCallbacks(holdEnterEdit);
                    }
                    row.getChildAt(0).dispatchTouchEvent(ev);
                    return true;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(holdEnterEdit);
                    row.getChildAt(0).dispatchTouchEvent(ev);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(holdEnterEdit);
                    row.getChildAt(0).dispatchTouchEvent(ev);
                    return true;
                default:
                    return true;
            }
        });
        overlay.setVisibility(editMode ? View.GONE : View.VISIBLE);
        row.addView(overlay);

        row.setOnLongClickListener(v -> {
            enterEditMode();
            return true;
        });
        return row;
    }

    private void removeWidget(int id) {
        host.deleteAppWidgetId(id);
        store.remove(id);
        adapter.ids.remove(Integer.valueOf(id));
        adapter.rows.remove(id);
        notifyDataSetChanged();
    }

    /** True when the view tree contains a scrollable widget view
     * (ScrollView or AbsListView — ListView/GridView subclass it), i.e.
     * content the user scrolls INSIDE the widget. */
    private static boolean hasScrollableDescendant(View v) {
        if (v instanceof android.widget.ScrollView
                || v instanceof android.widget.AbsListView) {
            return true;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (hasScrollableDescendant(g.getChildAt(i))) return true;
            }
        }
        return false;
    }

    /** Dragging the corner resize handle resizes the row between
     * MIN_HEIGHT_DP and MAX_HEIGHT_DP, updating the widget host live; the new
     * height is persisted on release. The widget view is re-resolved on every
     * event: a released resize re-creates the view, so stale references would
     * resize a detached view. */
    private class ResizeTouchListener implements View.OnTouchListener {
        private final int id;
        private float startX, startY;
        private int startWidthDp, startHeightDp;

        ResizeTouchListener(int id) {
            this.id = id;
        }

        @Override
        public boolean onTouch(View v, MotionEvent ev) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            ViewGroup row = (ViewGroup) v.getParent();
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = ev.getRawX();
                    startY = ev.getRawY();
                    startWidthDp = Math.round(
                            row.getChildAt(0).getLayoutParams().width
                                    == ViewGroup.LayoutParams.MATCH_PARENT
                                    ? row.getChildAt(0).getWidth() / boardDensity()
                                    : row.getChildAt(0).getLayoutParams().width / boardDensity());
                    startHeightDp = Math.round(
                            row.getChildAt(0).getLayoutParams().height / boardDensity());
                    // the drag is vertical and crosses the scroll slop fast:
                    // without this the RecyclerView steals the stream to
                    // scroll and the resize dies mid-gesture
                    row.getParent().requestDisallowInterceptTouchEvent(true);
                    // the row re-spans only on release: until then the widget
                    // paints past the cell, so lift the cell above its
                    // neighbors or they cover the overflow
                    ((View) row.getParent()).setTranslationZ(
                            6f * getResources().getDisplayMetrics().density);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    AppWidgetHostView widget = (AppWidgetHostView) row.getChildAt(0);
                    // a widget may grow past its grid column: the row then
                    // spans the full board (the lookup re-spans on release)
                    int maxW = boardWidthDp();
                    int w = Math.max(MIN_WIDTH_DP, Math.min(maxW,
                            startWidthDp + Math.round((ev.getRawX() - startX) / boardDensity())));
                    int h = Math.max(MIN_HEIGHT_DP, Math.min(MAX_HEIGHT_DP,
                            startHeightDp + Math.round((ev.getRawY() - startY) / boardDensity())));
                    if (w != widget.getLayoutParams().width
                            || h != widget.getLayoutParams().height) {
                        widget.setLayoutParams(new FrameLayout.LayoutParams(
                                (int) dp(w), (int) dp(h)));
                        // the row frame follows the drag: it is measured with
                        // an exact height (the hold overlay relies on that)
                        row.getLayoutParams().height = (int) dp(h);
                        row.requestLayout();
                        widget.updateAppWidgetSize(new Bundle(), w, h, w, h);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    AppWidgetHostView widget = (AppWidgetHostView) row.getChildAt(0);
                    int height = snapHeightDp(Math.round(
                            widget.getLayoutParams().height / boardDensity()));
                    int width = snapWidthDp(Math.round(
                            widget.getLayoutParams().width / boardDensity()));
                    store.setSize(id, height);
                    store.setWidth(id, width);
                    // re-create the widget view: updateAppWidgetSize alone
                    // does not force the provider's RemoteViews to
                    // re-layout visually
                    AppWidgetProviderInfo info = widgets.getAppWidgetInfo(id);
                    if (info != null) recreateWidgetView(id, info);
                    // rebind the row so the span lookup re-evaluates the new
                    // width (a row resized past a column now spans the board)
                    ((View) row.getParent()).setTranslationZ(0f);
                    int position = adapter.ids.indexOf(id);
                    if (position >= 0) adapter.notifyItemChanged(position);
                    return true;
                }
                default:
                    return true;
            }
        }
    }

    private int columnWidthDp() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return Math.round(dm.widthPixels / 2f / boardDensity());
    }

    /** Launcher3-style non-continuous grid: a released resize snaps to
     * whole grid cells — width to whole columns (so a row never sits
     * half-way across a column boundary), height to whole cell heights. */
    private int snapWidthDp(int widthDp) {
        int cell = Math.max(MIN_WIDTH_DP, columnWidthDp());
        return Math.max(MIN_WIDTH_DP, Math.min(boardWidthDp(),
                Math.max(1, Math.round(widthDp / (float) cell)) * cell));
    }

    private int snapHeightDp(int heightDp) {
        return Math.max(MIN_HEIGHT_DP, Math.min(MAX_HEIGHT_DP,
                Math.max(1, Math.round(heightDp / (float) CELL_HEIGHT_DP))
                        * CELL_HEIGHT_DP));
    }

    private float dp(int value) {
        return value * boardDensity();
    }

    /** Even spacing between the grid cells (the compact look). */
    private class BoardSpacingDecoration extends RecyclerView.ItemDecoration {
        private final int spacing;

        BoardSpacingDecoration(int spacingPx) {
            this.spacing = spacingPx;
        }

        @Override
        public void getItemOffsets(android.graphics.Rect outRect, View view,
                                   RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            if (position >= 0) outRect.top = position < 2 ? 0 : spacing;
            outRect.left = position % 2 == 0 ? 0 : spacing / 2;
            outRect.right = position % 2 == 0 ? spacing / 2 : 0;
        }
    }

    // ---- board adapter ---------------------------------------------------------------------

    /** Adapter over the ordered widget rows. Row views are created once per
     * widget id and kept across rebinds (AppWidgetHost views must not be
     * recreated on every bind). Edit mode toggles the handles' visibility. */
    private class BoardAdapter extends RecyclerView.Adapter<BoardAdapter.Holder> {
        final java.util.List<Integer> ids = new java.util.ArrayList<>();
        final java.util.Map<Integer, View> rows = new java.util.HashMap<>();

        void refresh(java.util.List<Integer> newIds) {
            ids.clear();
            ids.addAll(newIds);
            rows.keySet().retainAll(ids);
        }

        void setEditMode(boolean on) {
            for (View row : rows.values()) {
                View remove = row.findViewWithTag(R.id.remove_handle);
                View resize = row.findViewWithTag(R.id.resize_handle);
                View gear = row.findViewWithTag(R.id.reconfigure_handle);
                if (remove != null) remove.setVisibility(on ? View.VISIBLE : View.GONE);
                if (resize != null) resize.setVisibility(on ? View.VISIBLE : View.GONE);
                if (gear != null) gear.setVisibility(on ? View.VISIBLE : View.GONE);
                // the hold overlay sits above the handles; it must step aside
                // in edit mode or it swallows every touch meant for them
                View overlay = row.findViewWithTag(R.id.hold_overlay);
                if (overlay != null) overlay.setVisibility(on ? View.GONE : View.VISIBLE);
            }
        }

        void move(int from, int to) {
            ids.add(to, ids.remove(from));
            store.replaceAll(ids);
            notifyItemMoved(from, to);
        }

        void resize(int position, int heightDp) {
            int id = ids.get(position);
            store.setSize(id, heightDp);
            DisplayMetrics dm = getResources().getDisplayMetrics();
            View row = rows.get(id);
            if (row != null) {
                row.getLayoutParams().height = (int) dp(heightDp);
                AppWidgetHostView widget = (AppWidgetHostView) ((ViewGroup) row).getChildAt(0);
                int width = widget.getLayoutParams().width == ViewGroup.LayoutParams.MATCH_PARENT
                        ? columnWidthDp()
                        : Math.round(widget.getLayoutParams().width / boardDensity());
                widget.setLayoutParams(new FrameLayout.LayoutParams(
                        (int) dp(width), (int) dp(heightDp)));
                widget.updateAppWidgetSize(new Bundle(), width, heightDp,
                        width, heightDp);
            }
        }

        int heightDpAt(int position) {
            int id = ids.get(position);
            // mirror createRow: a stored size wins, else the provider's
            // advised size — the row frame must match the widget view
            int h = store.hasSize(id) ? store.size(id)
                    : advisedHeightDp(widgets.getAppWidgetInfo(id));
            return h;
        }

        View rowAt(int position) {
            View row = rows.get(ids.get(position));
            if (row == null) {
                row = createRow(ids.get(position));
                rows.put(ids.get(position), row);
            }
            return row;
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(new FrameLayout(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            int id = ids.get(position);
            View row = rows.get(id);
            if (row == null) {
                row = createRow(id);
                rows.put(id, row);
            }
            ViewGroup old = (ViewGroup) row.getParent();
            if (old != null) old.removeView(row);
            holder.container.removeAllViews();
            // a resize drag can carry the widget past the column edge
            // before the row re-spans on release: never clip it
            holder.container.setClipChildren(false);
            holder.container.addView(row, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(heightDpAt(position))));
        }

        @Override
        public int getItemCount() {
            return ids.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final FrameLayout container;

            Holder(FrameLayout container) {
                super(container);
                this.container = container;
            }
        }
    }

    /** Long-press drag reorders the board in edit mode; moves land in
     * {@link #moveBoardRow} which persists the order. */
    private class DragReorderCallback extends ItemTouchHelper.Callback {
        @Override
        public boolean isLongPressDragEnabled() {
            return editMode;
        }

        @Override
        public boolean isItemViewSwipeEnabled() {
            return false;
        }

        @Override
        public int getMovementFlags(RecyclerView rv, RecyclerView.ViewHolder vh) {
            return ItemTouchHelper.Callback.makeMovementFlags(
                    ItemTouchHelper.UP | ItemTouchHelper.DOWN
                            | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0);
        }

        @Override
        public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder from,
                              RecyclerView.ViewHolder to) {
            moveBoardRow(from.getBindingAdapterPosition(), to.getBindingAdapterPosition());
            return true;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder vh, int direction) {
            // no swipe: removal goes through the (x) handle
        }
    }
}

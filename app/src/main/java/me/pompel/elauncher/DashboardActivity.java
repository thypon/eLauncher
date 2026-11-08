package me.pompel.elauncher;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * eBoard — a macOS-Dashboard-style board of persistent home screen widgets.
 * Registered as its own launcher entry point ("eBoard") and opened by the
 * launcher's default left-side gesture. Widgets picked from the widget adder
 * popover are hosted here for good: the AppWidgetHost binding is kept by the
 * system and the board order is persisted across restarts.
 *
 * Interaction model: long press on empty board space opens the widget adder
 * popover (half the screen, bottom-anchored, listing every provider's widget
 * kind); long press on a widget enters edit mode, where each widget gets an
 * (x) remove handle, a resize handle and drag-to-reorder. The add button
 * exists only on an empty board.
 */
public class DashboardActivity extends AppCompatActivity {
    /** Distinguishes eBoard-hosted widgets from other hosts' widget ids. */
    static final int HOST_ID = 0x0E13;
    private static final int REQUEST_PICK = 1;
    /** Range of a widget row height in dp, used by the resize handle. */
    static final int MIN_HEIGHT_DP = 140;
    static final int MAX_HEIGHT_DP = 600;
    /** Hold duration that enters edit mode while touching a widget. */
    static final long HOLD_TO_EDIT_MS = 700;

    private AppWidgetHost host;
    private AppWidgetManager widgets;
    private RecyclerView board;
    private TextView emptyHint;
    private Button addWidgetButton;
    private BoardStore store;
    private BoardAdapter adapter;
    private boolean editMode;
    private AlertDialog adderDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        board = findViewById(R.id.board);
        emptyHint = findViewById(R.id.empty_hint);
        addWidgetButton = findViewById(R.id.add_widget);
        widgets = AppWidgetManager.getInstance(this);
        host = new AppWidgetHost(this, HOST_ID);
        store = new BoardStore(getSharedPreferences(BoardStore.PREFS_FILE, MODE_PRIVATE));
        adapter = new BoardAdapter();
        board.setLayoutManager(new LinearLayoutManager(this));
        board.setAdapter(adapter);
        new ItemTouchHelper(new DragReorderCallback()).attachToRecyclerView(board);
        addWidgetButton.setOnClickListener(v -> openAdder());
        // empty board space: hold (700ms) opens the widget adder popover.
        // RecyclerView does not route the framework long click, and the
        // gesture detector proved flaky across touch streams, so the hold is
        // timed directly on the item-touch listener; movement cancels it.
        final View[] timerHost = {board};
        board.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            private final Handler handler = new Handler(Looper.getMainLooper());
            private float holdX, holdY;
            private final Runnable open = () -> onEmptySpaceLongPressed(holdX, holdY);

            @Override
            public boolean onInterceptTouchEvent(RecyclerView rv, android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        android.util.Log.d("eBoard", "boardTouch DOWN y=" + e.getY());
                        handler.removeCallbacks(open);
                        if (!editMode) {
                            holdX = e.getX();
                            holdY = e.getY();
                            handler.postDelayed(open, HOLD_TO_EDIT_MS);
                        }
                        break;
                    case android.view.MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getX() - holdX) > 30 || Math.abs(e.getY() - holdY) > 30) {
                            handler.removeCallbacks(open);
                        }
                        break;
                    default:
                        handler.removeCallbacks(open);
                        break;
                }
                return false;
            }
        });
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

    void enterEditMode() {
        editMode = true;
        adapter.setEditMode(true);
        updateUiState(); // no notifyDataSetChanged: the touch that triggered the
                         // edit must not detach the row mid-gesture
    }

    void exitEditMode() {
        if (!editMode) return;
        editMode = false;
        adapter.setEditMode(false);
        updateUiState();
    }

    boolean isEditMode() {
        return editMode;
    }

    // ---- board state accessors (used by the BDD world) --------------------------------------

    public int rowCount() {
        return adapter.getItemCount();
    }

    public View boardRowAt(int position) {
        return adapter.rowAt(position);
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
    void openAdder() {
        if (adderDialog != null) return;
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
        android.util.Log.d("eBoard", "adder shown, showing=" + adderDialog.isShowing());
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

    /** Binds the reserved id to the picked provider directly when allowed,
     * otherwise hands over to the system widget picker (which asks for the
     * binding permission and lets the user confirm the widget). */
    private void pickKind(AppWidgetProviderInfo info) {
        int id = host.allocateAppWidgetId();
        if (widgets.bindAppWidgetIdIfAllowed(id, info.provider)) {
            store.add(id);
            adapter.ids.add(id);
            closeAdder();
            notifyDataSetChanged();
        } else {
            host.deleteAppWidgetId(id);
            int fallback = host.allocateAppWidgetId();
            Intent pick = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
            pick.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, fallback);
            startActivityForResult(pick, REQUEST_PICK);
            closeAdder(); // otherwise the popover scrim hides the system picker
        }
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

    // ---- system picker result ---------------------------------------------------------------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK) return;
        int id = data == null ? -1 : data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        if (id == -1 || resultCode != RESULT_OK || widgets.getAppWidgetInfo(id) == null) {
            // cancelled or unbound pick: drop the reserved id, keep the board as-is
            if (id != -1) host.deleteAppWidgetId(id);
            return;
        }
        store.add(id);
        adapter.ids.add(id);
        notifyDataSetChanged();
    }

    // ---- board rows ------------------------------------------------------------------------

    /** Builds a board row for a bound widget: the widget view plus, in edit
     * mode, an (x) remove handle and a resize handle. A hold-to-remove
     * overlay sits on top for real touches, because clickable widget content
     * (e.g. the clock app shortcut) would swallow the gesture; the row long
     * click enters edit mode and the BDD path performs it directly. */
    private View createRow(int id) {
        AppWidgetProviderInfo info = widgets.getAppWidgetInfo(id);
        int heightDp = store.size(id);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int wDp = Math.round(dm.widthPixels / dm.density);

        AppWidgetHostView widget = host.createView(this, id, info);
        widget.updateAppWidgetSize(new Bundle(), wDp, heightDp, wDp, heightDp);
        widget.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(heightDp)));

        FrameLayout row = new FrameLayout(this);
        row.setTag(id);
        row.addView(widget);

        // (x) remove handle, iOS-manage-icons style, visible in edit mode:
        // ink badge with teal ring, readable over any widget color
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
        // floating: inset from the widget's squircle corner + elevation shadow
        rp.setMargins(0, (int) dp(14), (int) dp(14), 0);
        remove.setLayoutParams(rp);
        remove.setElevation(dp(6));
        remove.setVisibility(editMode ? View.VISIBLE : View.GONE);
        row.addView(remove);

        // resize handle along the bottom edge, visible in edit mode
        TextView resize = new TextView(this);
        resize.setBackgroundColor(Color.LTGRAY);
        resize.setTag(R.id.resize_handle);
        resize.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(20), Gravity.BOTTOM));
        resize.setVisibility(editMode ? View.VISIBLE : View.GONE);
        resize.setOnTouchListener(new ResizeTouchListener(widget, id, heightDp));
        row.addView(resize);

        // hold-to-edit overlay for real touches: a long hold on the widget
        // enters edit mode (like iOS), where the (x) and resize handles work.
        // Clickable widget content (e.g. the clock app shortcut) would
        // otherwise swallow the gesture; the row long click still enters edit
        // mode and the BDD path performs it directly. In edit mode the
        // overlay steps aside so handles and drag-reorder are reachable.
        View overlay = new View(this);
        overlay.setTag(R.id.hold_overlay);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable holdEnterEdit = this::enterEditMode;
        overlay.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (!editMode) handler.postDelayed(holdEnterEdit, HOLD_TO_EDIT_MS);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(holdEnterEdit);
                    return true;
                default:
                    return !editMode;
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

    /** Dragging the resize handle vertically resizes the row between
     * MIN_HEIGHT_DP and MAX_HEIGHT_DP; on release the new height is stored and
     * pushed to the widget host. */
    private class ResizeTouchListener implements View.OnTouchListener {
        private final AppWidgetHostView widget;
        private final int id;
        private float startY;
        private int startHeightDp;

        ResizeTouchListener(AppWidgetHostView widget, int id, int startHeightDp) {
            this.widget = widget;
            this.id = id;
            this.startHeightDp = startHeightDp;
        }

        @Override
        public boolean onTouch(View v, MotionEvent ev) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startY = ev.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int h = Math.max(MIN_HEIGHT_DP, Math.min(MAX_HEIGHT_DP,
                            startHeightDp + Math.round((ev.getRawY() - startY) / dm.density)));
                    widget.setLayoutParams(new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(h)));
                    widget.invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    int height = Math.round(widget.getLayoutParams().height / dm.density);
                    store.setSize(id, height);
                    widget.updateAppWidgetSize(new Bundle(),
                            Math.round(dm.widthPixels / dm.density), height,
                            Math.round(dm.widthPixels / dm.density), height);
                    return true;
                default:
                    return true;
            }
        }
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
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
                View overlay = row.findViewWithTag(R.id.hold_overlay);
                if (remove != null) remove.setVisibility(on ? View.VISIBLE : View.GONE);
                if (resize != null) resize.setVisibility(on ? View.VISIBLE : View.GONE);
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
                AppWidgetHostView widget = (AppWidgetHostView) ((ViewGroup) row).getChildAt(0);
                widget.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(heightDp)));
                widget.updateAppWidgetSize(new Bundle(),
                        Math.round(dm.widthPixels / dm.density), heightDp,
                        Math.round(dm.widthPixels / dm.density), heightDp);
            }
        }

        int heightDpAt(int position) {
            return store.size(ids.get(position));
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
            holder.container.addView(row, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
                    ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
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

package me.pompel.elauncher;

import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * Seeded fuzz over the new eBoard interactions: random operation sequences
 * (add, remove, reorder, resize, edit-mode toggles, restarts) with invariant
 * checks after every step, plus symbolic boundary cases (out-of-range
 * reorder/resize indices and heights). Anything found here must be either
 * guarded in the implementation or an acknowledged impossibility.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DashboardBoardFuzzTest {

    private ActivityScenario<DashboardActivity> scenario;
    private ComponentName provider;
    private final Random random = new Random(42);

    @Before
    public void setUp() {
        Context ctx = ApplicationProvider.getApplicationContext();
        provider = new ComponentName(ctx.getPackageName(), "me.fixtures.WidgetProvider");
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        info.provider = provider;
        info.label = "Widget";
        android.content.pm.ActivityInfo ai = new android.content.pm.ActivityInfo();
        ai.packageName = ctx.getPackageName();
        ai.nonLocalizedLabel = "Widget";
        try {
            java.lang.reflect.Field f = AppWidgetProviderInfo.class.getField("providerInfo");
            f.set(info, ai);
        } catch (Exception ignored) {
        }
        shadowOf(AppWidgetManager.getInstance(ctx)).addInstalledProvider(info);
        shadowOf(AppWidgetManager.getInstance(ctx)).setAllowedToBindAppWidgets(true);
    }

    private DashboardActivity activity() {
        DashboardActivity[] ref = new DashboardActivity[1];
        scenario.onActivity(a -> ref[0] = a);
        return ref[0];
    }

    private void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private void start() {
        Dialog stale = ShadowDialog.getLatestDialog();
        if (stale != null && stale.isShowing()) stale.dismiss();
        if (scenario != null) {
            scenario.close();
            idle();
        }
        scenario = ActivityScenario.launch(DashboardActivity.class);
        idle();
    }

    private List<Integer> storedIds() {
        return new BoardStore(ApplicationProvider.<Context>getApplicationContext()
                .getSharedPreferences(BoardStore.PREFS_FILE, Context.MODE_PRIVATE)).ids();
    }

    /** UI path: add button (empty board only) then first kind card in the
     * popover (the adder is sectioned: the first child of the list is an
     * app section header, the kinds live in its grid). */
    private void addWidget() {
        activity().findViewById(R.id.add_widget).performClick();
        Dialog dialog = ShadowDialog.getLatestDialog();
        if (dialog == null) throw new AssertionError("adder popover did not open");
        android.widget.TableLayout grid = (android.widget.TableLayout) dialog
                .getWindow().getDecorView().findViewById(R.id.adder_grid);
        if (grid == null || grid.getChildCount() == 0)
            throw new AssertionError("no kinds in popover");
        android.view.ViewGroup firstRow = (android.view.ViewGroup) grid.getChildAt(0);
        firstRow.getChildAt(0).performClick();
        idle();
    }

    private void removeAt(int position) {
        if (!activity().isEditMode()) activity().enterEditMode();
        View handle = activity().boardRowAt(position).findViewWithTag(R.id.remove_handle);
        if (handle == null) throw new AssertionError("no remove handle");
        handle.performClick();
        idle();
    }

    private boolean handleVisible(int position) {
        View handle = activity().boardRowAt(position).findViewWithTag(R.id.remove_handle);
        return handle != null && handle.getVisibility() == View.VISIBLE;
    }

    /** Invariants that must hold after every interaction. */
    private void invariants(int step) {
        DashboardActivity a = activity();
        int rows = a.rowCount();
        List<Integer> stored = storedIds();
        assertEquals("step " + step + ": row count must match stored ids", stored.size(), rows);
        assertEquals("step " + step + ": stored ids must be unique",
                new HashSet<>(stored).size(), stored.size());
        Set<Integer> bound = new HashSet<>();
        for (int id : AppWidgetManager.getInstance(ApplicationProvider.<Context>getApplicationContext())
                .getAppWidgetIds(provider)) bound.add(id);
        for (int id : stored) {
            assertTrue("step " + step + ": stored id " + id + " is not bound to the host",
                    bound.contains(id));
        }
        for (int pos = 0; pos < rows; pos++) {
            int h = a.rowHeightDp(pos);
            assertTrue("step " + step + ": height " + h + " out of range",
                    DashboardActivity.MIN_HEIGHT_DP <= h && h <= DashboardActivity.MAX_HEIGHT_DP);
        }
        boolean edit = a.isEditMode();
        if (rows == 0) {
            assertFalse("step " + step + ": edit mode must exit on an empty board", edit);
            assertTrue("step " + step + ": add button must be visible on an empty board",
                    a.findViewById(R.id.add_widget).getVisibility() == View.VISIBLE);
        } else {
            assertEquals("step " + step + ": handles visibility must match edit mode",
                    edit, handleVisible(0));
        }
    }

    @Test
    public void randomInteractionSequencesHoldInvariants() {
        start();
        for (int step = 0; step < 250; step++) {
            int op = random.nextInt(6);
            DashboardActivity a = activity();
            int rows = a.rowCount();
            boolean edit = a.isEditMode();
            switch (op) {
                case 0: // add (only reachable on an empty board, like the user)
                    if (rows == 0) addWidget();
                    break;
                case 1: // remove
                    if (rows > 0) removeAt(random.nextInt(rows));
                    break;
                case 2: // reorder (valid indices only, like a real drag)
                    if (rows > 1) {
                        int from = random.nextInt(rows), to = random.nextInt(rows);
                        a.moveBoardRow(from, to);
                        idle();
                    }
                    break;
                case 3: // resize (within range)
                    if (rows > 0) {
                        int dp = DashboardActivity.MIN_HEIGHT_DP + random.nextInt(
                                DashboardActivity.MAX_HEIGHT_DP - DashboardActivity.MIN_HEIGHT_DP + 1);
                        a.applyRowHeight(random.nextInt(rows), dp);
                        idle();
                    }
                    break;
                case 4: // edit mode toggle
                    if (edit) a.exitEditMode();
                    else if (rows > 0) a.enterEditMode();
                    break;
                case 5: // occasionally relaunch the board
                    if (random.nextInt(20) == 0) {
                        start();
                        break;
                    }
                    if (edit) a.exitEditMode();
                    break;
            }
            invariants(step);
        }
    }

    /** Symbolic boundaries: reorder indices out of range must be no-ops. */
    @Test
    public void moveOutOfBoundsIsNoOp() {
        start();
        addWidget();
        List<Integer> before = storedIds();
        activity().moveBoardRow(-1, 0);
        activity().moveBoardRow(0, -1);
        activity().moveBoardRow(0, 5);
        activity().moveBoardRow(5, 1);
        activity().moveBoardRow(0, 0);
        idle();
        assertEquals(before, storedIds());
        assertEquals(1, activity().rowCount());
        invariants(-1);
    }

    /** Symbolic boundaries: resize clamps into MIN..MAX even with extreme dp. */
    @Test
    public void resizeClampsToRange() {
        start();
        addWidget();
        activity().applyRowHeight(0, 10000);
        assertEquals(DashboardActivity.MAX_HEIGHT_DP, activity().rowHeightDp(0));
        activity().applyRowHeight(0, 10);
        assertEquals(DashboardActivity.MIN_HEIGHT_DP, activity().rowHeightDp(0));
        activity().applyRowHeight(-1, 300);
        activity().applyRowHeight(7, 300);
        idle();
        invariants(-2);
    }

    /** Removing from the board cleans the stored height (no stale entries). */
    @Test
    public void removeCleansStoredHeight() {
        start();
        addWidget();
        activity().applyRowHeight(0, 300);
        assertEquals(300, activity().rowHeightDp(0));
        removeAt(0);
        addWidget();
        assertEquals(BoardStore.DEFAULT_SIZE_DP, activity().rowHeightDp(0));
    }
}

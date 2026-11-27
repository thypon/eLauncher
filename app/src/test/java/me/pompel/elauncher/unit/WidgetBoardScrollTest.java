package me.pompel.elauncher.unit;

import android.view.View;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import me.pompel.elauncher.DashboardActivity;
import me.pompel.elauncher.bdd.steps.LauncherWorld;
import me.pompel.elauncher.widget.WidgetHostView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import android.view.ViewConfiguration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Diag: a widget with an internal vertical list must scroll itself; the
 * board ScrollView must not steal the drag. Temporary diagnostic test. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class WidgetBoardScrollTest {

    private LauncherWorld world;
    private DashboardActivity dash;
    private ScrollView board;

    @Before
    public void setUp() {
        world = new LauncherWorld();
        world.cleanScenarioState();
        world.installWidgetProvider("Digital", "A digital clock widget");
        world.startDashboard();
        world.seedTwoWidgets();
        dash = world.dashboard();
        board = dash.findViewById(me.pompel.elauncher.R.id.board);
    }

    private void layoutBoard() {
        int w = 320;
        int h = 470;
        board.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        board.layout(0, 0, w, h);
    }

    private View stack() {
        return board.getChildAt(0);
    }

    /** Records the touch actions the list sees. */
    static class LoggingList extends ListView {
        final java.util.List<String> actions = new java.util.ArrayList<>();
        LoggingList(android.content.Context c) { super(c); }
        @Override public boolean onTouchEvent(android.view.MotionEvent ev) {
            actions.add(android.view.MotionEvent.actionToString(ev.getActionMasked()));
            return super.onTouchEvent(ev);
        }
    }

    @Test
    public void listInsideWidgetScrollsInsteadOfTheBoard() {
        WidgetHostView hv = dash.widgetHostViewAt(0);
        final LoggingList[] holder = new LoggingList[1];
        LoggingList list = new LoggingList(world.appContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                world.appContext(), android.R.layout.simple_list_item_1) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, 40));
                return v;
            }
        };
        for (int i = 0; i < 100; i++) adapter.add("Item " + i);
        list.setAdapter(adapter);
        hv.addView(list, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        layoutBoard();

        float x = 60;
        float y = stack().getY() + dash.boardRowAt(0).getY() + 50;
        long time = android.os.SystemClock.uptimeMillis();
        board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                time, time, android.view.MotionEvent.ACTION_DOWN, x, y, 0));
        for (int i = 1; i <= 8; i++) {
            time += 16;
            board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    time, time, android.view.MotionEvent.ACTION_MOVE, x, y - 30 * i, 0));
        }
        time += 16;
        board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                time, time, android.view.MotionEvent.ACTION_UP, x, y - 240, 0));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        assertTrue("the widget's own list should scroll, got firstVisible="
                        + list.getFirstVisiblePosition(),
                list.getFirstVisiblePosition() > 0);
        assertEquals("the board must not scroll while the widget list scrolls",
                0, board.getScrollY());
    }

    @Test
    public void boardStillScrollsOverAStaticWidget() {
        dash.applyRowHeight(1, 400); // give the board room to scroll
        layoutBoard();
        float x = 60;
        float y = stack().getY() + dash.boardRowAt(1).getY() + 50;
        long time = android.os.SystemClock.uptimeMillis();
        board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                time, time, android.view.MotionEvent.ACTION_DOWN, x, y, 0));
        for (int i = 1; i <= 8; i++) {
            time += 16;
            board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    time, time, android.view.MotionEvent.ACTION_MOVE, x, y - 30 * i, 0));
        }
        time += 16;
        board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                time, time, android.view.MotionEvent.ACTION_UP, x, y - 240, 0));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        assertTrue("the board should scroll over a static widget, got "
                + board.getScrollY(), board.getScrollY() > 0);
    }

    @Test
    public void dragAfterLongPressMovesTheRowNotTheBoard() throws Exception {
        dash.applyRowHeight(1, 100); // stack incl. edit bars fits the viewport
        layoutBoard();
        float x = 60;
        float y = stack().getY() + dash.boardRowAt(0).getY() + 50;
        long time = android.os.SystemClock.uptimeMillis();
        board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                time, time, android.view.MotionEvent.ACTION_DOWN, x, y, 0));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(
                ViewConfiguration.getLongPressTimeout() + 100);
        // the long press has fired and put the board in edit mode
        assertTrue("long press should enter edit mode", dash.isEditMode());
        for (int i = 1; i <= 6; i++) {
            time += 16;
            board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    time, time, android.view.MotionEvent.ACTION_MOVE, x, y + 40 * i, 0));
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        }
        time += 16;
        board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                time, time, android.view.MotionEvent.ACTION_UP, x, y + 240, 0));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        assertEquals("the dragged widget must have moved below the first",
                "12,11", world.storedBoardIdsAsString());
        assertEquals("the board must not scroll while a widget is dragged",
                0, board.getScrollY());
    }

    @Test
    public void draggingTowardTheEdgeScrollsTheBoard() throws Exception {
        dash.applyRowHeight(1, 400); // content taller than the viewport
        layoutBoard();
        float x = 60;
        float y = stack().getY() + dash.boardRowAt(0).getY() + 50;
        long time = android.os.SystemClock.uptimeMillis();
        board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                time, time, android.view.MotionEvent.ACTION_DOWN, x, y, 0));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(
                ViewConfiguration.getLongPressTimeout() + 100);
        for (int i = 1; i <= 10; i++) {
            time += 16;
            board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    time, time, android.view.MotionEvent.ACTION_MOVE, x, y + 45 * i, 0));
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        }
        time += 16;
        board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                time, time, android.view.MotionEvent.ACTION_UP, x, y + 450, 0));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        assertTrue("dragging a row to the bottom edge should scroll the board",
                board.getScrollY() > 0);
    }

    @Test
    public void frameBodyDragScrollsTheBoardWithoutMovingWidgets() throws Exception {
        dash.applyRowHeight(1, 400); // content taller than the viewport
        dash.enterEditMode(0);
        layoutBoard();
        float x = 60;
        float y = stack().getY() + dash.boardRowAt(1).getY() + 50;
        long time = android.os.SystemClock.uptimeMillis();
        board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                time, time, android.view.MotionEvent.ACTION_DOWN, x, y, 0));
        for (int i = 1; i <= 8; i++) {
            time += 16;
            board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    time, time, android.view.MotionEvent.ACTION_MOVE, x, y - 30 * i, 0));
        }
        time += 16;
        board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                time, time, android.view.MotionEvent.ACTION_UP, x, y - 240, 0));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        assertTrue("a plain drag over a frame body should scroll the board, got "
                + board.getScrollY(), board.getScrollY() > 0);
        assertEquals("a plain drag must never reorder widgets",
                "11,12", world.storedBoardIdsAsString());
    }

    @Test
    public void frameLongPressThenDragMovesTheRow() throws Exception {
        dash.applyRowHeight(1, 100);
        dash.enterEditMode(0);
        layoutBoard();
        float x = 60;
        float y = stack().getY() + dash.boardRowAt(1).getY() + 50;
        long time = android.os.SystemClock.uptimeMillis();
        board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                time, time, android.view.MotionEvent.ACTION_DOWN, x, y, 0));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(
                ViewConfiguration.getLongPressTimeout() + 100);
        for (int i = 1; i <= 6; i++) {
            time += 16;
            board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    time, time, android.view.MotionEvent.ACTION_MOVE, x, y - 40 * i, 0));
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        }
        time += 16;
        board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                time, time, android.view.MotionEvent.ACTION_UP, x, y - 240, 0));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        assertEquals("a long-press drag on the frame must move the widget",
                "12,11", world.storedBoardIdsAsString());
        assertEquals("a widget drag must not scroll the board", 0, board.getScrollY());
        assertTrue("edit mode stays on after a drag", dash.isEditMode());
        assertTrue(world.framesOnAllRows());
    }

    @Test
    public void holdOnResizeHandleLeavesEditMode() throws Exception {
        dash.enterEditMode(0);
        layoutBoard();
        View row = dash.boardRowAt(0);
        float x = 60;
        float y = stack().getY() + row.getY() + row.getHeight() - 20; // bottom handle zone
        long time = android.os.SystemClock.uptimeMillis();
        board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                time, time, android.view.MotionEvent.ACTION_DOWN, x, y, 0));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(
                ViewConfiguration.getLongPressTimeout() + 100);
        time += ViewConfiguration.getLongPressTimeout() + 100;
        board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                time, time, android.view.MotionEvent.ACTION_UP, x, y, 0));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        assertTrue("holding the resize handle without dragging must leave edit mode",
                !dash.isEditMode());
        assertTrue("every frame must be gone after the hold", !world.framesOnAllRows());
        assertEquals("the widget's height must not change on a held handle",
                200, world.storedHeight(0));
    }

    @Test
    public void dragOnResizeHandleStillResizes() throws Exception {
        dash.enterEditMode(0);
        layoutBoard();
        View row = dash.boardRowAt(0);
        float x = 60;
        float y = stack().getY() + row.getY() + row.getHeight() - 20;
        long time = android.os.SystemClock.uptimeMillis();
        board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                time, time, android.view.MotionEvent.ACTION_DOWN, x, y, 0));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(
                ViewConfiguration.getLongPressTimeout() + 100);
        for (int i = 1; i <= 3; i++) {
            time += 16;
            board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    time, time, android.view.MotionEvent.ACTION_MOVE, x, y + 10 * i, 0));
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        }
        time += 16;
        board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                time, time, android.view.MotionEvent.ACTION_UP, x, y + 30, 0));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        assertEquals("a held handle that then drags must resize, not exit",
                230, world.storedHeight(0));
        assertTrue("resizing keeps edit mode on", dash.isEditMode());
    }

    @Test
    public void twoFingerDragScrollsTheBoardNotTheWidgetList() {
        dash.applyRowHeight(1, 400); // content taller than the viewport
        WidgetHostView hv = dash.widgetHostViewAt(0);
        final LoggingList[] holder = new LoggingList[1];
        LoggingList list = new LoggingList(world.appContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                world.appContext(), android.R.layout.simple_list_item_1) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, 40));
                return v;
            }
        };
        for (int i = 0; i < 100; i++) adapter.add("Item " + i);
        list.setAdapter(adapter);
        hv.addView(list, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        layoutBoard();

        float x = 60;
        float y = stack().getY() + dash.boardRowAt(0).getY() + 50;
        long time = android.os.SystemClock.uptimeMillis();
        board.dispatchTouchEvent(android.view.MotionEvent.obtain(
                time, time, android.view.MotionEvent.ACTION_DOWN, x, y, 0));
        // Second finger: must hand the gesture to the board
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[2];
        props[0] = new MotionEvent.PointerProperties();
        props[0].id = 0;
        props[1] = new MotionEvent.PointerProperties();
        props[1].id = 1;
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[2];
        coords[0] = new MotionEvent.PointerCoords();
        coords[0].x = x;
        coords[0].y = y;
        coords[1] = new MotionEvent.PointerCoords();
        coords[1].x = x + 30;
        coords[1].y = y + 10;
        time += 16;
        board.dispatchTouchEvent(MotionEvent.obtain(time, time,
                MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                2, props, coords, 0, 0, 1f, 1f, 0, 0,
                android.view.InputDevice.SOURCE_TOUCHSCREEN, 0));
        for (int i = 1; i <= 8; i++) {
            time += 16;
            coords[0].y = y - 30 * i;
            coords[1].y = y + 10 - 30 * i;
            board.dispatchTouchEvent(MotionEvent.obtain(time, time,
                    MotionEvent.ACTION_MOVE, 2, props, coords, 0, 0, 1f, 1f, 0, 0,
                    android.view.InputDevice.SOURCE_TOUCHSCREEN, 0));
        }
        time += 16;
        board.dispatchTouchEvent(MotionEvent.obtain(time, time,
                MotionEvent.ACTION_UP, 2, props, coords, 0, 0, 1f, 1f, 0, 0,
                android.view.InputDevice.SOURCE_TOUCHSCREEN, 0));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        assertTrue("two fingers must scroll the board, got " + board.getScrollY(),
                board.getScrollY() > 0);
        assertEquals("the widget's inner list must not scroll on two fingers",
                0, list.getFirstVisiblePosition());
        assertTrue("the inner list must have been cancelled by the board",
                list.actions.contains("ACTION_CANCEL"));
    }
}

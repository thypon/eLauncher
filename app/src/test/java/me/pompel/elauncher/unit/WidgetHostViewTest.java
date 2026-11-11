package me.pompel.elauncher.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.appwidget.AppWidgetProviderInfo;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.pompel.elauncher.widget.WidgetHostView;
import me.pompel.elauncher.widget.WidgetTaps;

/** Robolectric tests for the Olauncher-style touch dispatcher.
 * The view must live in an attached window: View.postDelayed routes
 * through the attach queue otherwise, so the long press would never run. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class WidgetHostViewTest {

    private WidgetHostView hostView;
    private Button child;
    private int childTaps;
    private final List<MotionEvent> homeEvents = new ArrayList<>();
    private int homeGestureCancels;
    private int longPresses;
    private final List<Float> drags = new ArrayList<>();
    private final List<Float> dragEnds = new ArrayList<>();

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        hostView = new WidgetHostView(activity);
        child = new Button(activity);
        child.setOnClickListener(v -> childTaps++);
        hostView.addView(child, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        activity.setContentView(hostView, new FrameLayout.LayoutParams(300, 200));
        hostView.measure(
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
        hostView.layout(0, 0, 300, 200);
        shadowOf(Looper.getMainLooper()).idle();
        hostView.setHomeGestures(homeEvents::add);
        hostView.setOnHomeGestureCancel(() -> homeGestureCancels++);
        hostView.setOnLongPress(() -> longPresses++);
        hostView.setOnDrag(drags::add);
        hostView.setOnDragEnd(dragEnds::add);
    }

    private MotionEvent touch(long time, int action, float x, float y) {
        return MotionEvent.obtain(time, time, action, x, y, 0);
    }

    private void dispatch(MotionEvent event) {
        hostView.dispatchTouchEvent(event);
        event.recycle();
    }

    /** A plain tap: DOWN then UP at the same point; the click fires from a
     * posted PerformClick, so the looper must run afterwards. */
    private void tap(long time, float x, float y) {
        dispatch(touch(time, MotionEvent.ACTION_DOWN, x, y));
        dispatch(touch(time + 50, MotionEvent.ACTION_UP, x, y));
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void defaultTapsModeIsOnAndReachesTheWidget() {
        assertEquals(WidgetTaps.ON, hostView.getTaps());
        tap(1000, 50, 50);
        assertEquals(1, childTaps);
        assertFalse("taps ON must not forward to the home screen", !homeEvents.isEmpty());
    }

    @Test
    public void tapsOffForwardsAllTouchesToHomeAndWidgetGetsNothing() {
        hostView.setTaps(WidgetTaps.OFF);
        tap(1000, 50, 50);
        assertEquals(0, childTaps);
        assertEquals("DOWN and UP must reach the home gestures", 2, homeEvents.size());
        // no long press: a plain tap stays a home gesture
        assertEquals(0, longPresses);
    }

    @Test
    public void doubleTapModeSwallowsSingleTapAndReplaysOnSecond() {
        hostView.setTaps(WidgetTaps.DOUBLE_TAP);
        // first tap: forwarded to the home screen, widget gets nothing
        tap(1000, 50, 50);
        assertEquals(0, childTaps);
        assertEquals(2, homeEvents.size());
        // second tap within the double-tap window: replayed to the widget as a tap
        tap(1100, 50, 50);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("the double tap must click the widget", 1, childTaps);
        // the second tap was locked away from the home screen
        assertEquals(2, homeEvents.size());
        assertEquals("the home screen must drop the handed-over gesture", 1, homeGestureCancels);
    }

    @Test
    public void doubleTapTooLateIsTwoHomeGestures() {
        hostView.setTaps(WidgetTaps.DOUBLE_TAP);
        tap(1000, 50, 50);
        // beyond the double-tap timeout: another forwarded gesture, no widget tap
        tap(1000 + ViewConfiguration.getDoubleTapTimeout() + 100, 50, 50);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, childTaps);
        assertEquals(4, homeEvents.size());
    }

    @Test
    public void longPressFiresEditAndCancelsHomeGesture() {
        hostView.setTaps(WidgetTaps.OFF);
        long timeout = ViewConfiguration.getLongPressTimeout();
        dispatch(touch(1000, MotionEvent.ACTION_DOWN, 50, 50));
        assertEquals("the home screen holds the gesture until the long press", 1, homeEvents.size());
        shadowOf(Looper.getMainLooper()).idleFor(timeout + 100, TimeUnit.MILLISECONDS);
        assertEquals("the long press must fire", 1, longPresses);
        assertEquals("a forwarded gesture must be cancelled when the widget takes over",
                1, homeGestureCancels);
        dispatch(touch(1000 + timeout + 150, MotionEvent.ACTION_UP, 50, 50));
        assertEquals("a long press is not a widget tap", 0, childTaps);
    }

    @Test
    public void longPressAlsoWorksInOnMode() {
        long timeout = ViewConfiguration.getLongPressTimeout();
        dispatch(touch(1000, MotionEvent.ACTION_DOWN, 50, 50));
        shadowOf(Looper.getMainLooper()).idleFor(timeout + 100, TimeUnit.MILLISECONDS);
        assertEquals(1, longPresses);
        assertEquals(0, homeGestureCancels);
    }

    @Test
    public void dragAfterLongPressReportsRawY() {
        long timeout = ViewConfiguration.getLongPressTimeout();
        dispatch(touch(1000, MotionEvent.ACTION_DOWN, 50, 50));
        shadowOf(Looper.getMainLooper()).idleFor(timeout + 100, TimeUnit.MILLISECONDS);
        dispatch(touch(1200, MotionEvent.ACTION_MOVE, 50, 150));
        dispatch(touch(1300, MotionEvent.ACTION_MOVE, 50, 260));
        dispatch(touch(1400, MotionEvent.ACTION_UP, 50, 260));
        // Platform semantics: the MOVE that starts the interception is handed to
        // the child as CANCEL, so the parent sees it only from the next MOVE on
        assertFalse("at least one drag must report the distance", drags.isEmpty());
        assertEquals(210f, drags.get(drags.size() - 1), 0.01f);
        assertEquals(1, dragEnds.size());
        assertEquals(210f, dragEnds.get(0), 0.01f);
        assertEquals("dragging must not tap the widget", 0, childTaps);
    }

    @Test
    public void movementBeyondSlopCancelsLongPress() {
        long timeout = ViewConfiguration.getLongPressTimeout();
        dispatch(touch(1000, MotionEvent.ACTION_DOWN, 50, 50));
        dispatch(touch(1050, MotionEvent.ACTION_MOVE, 50, 50 + 100));
        shadowOf(Looper.getMainLooper()).idleFor(timeout + 200, TimeUnit.MILLISECONDS);
        assertEquals("a moved touch must not become a long press", 0, longPresses);
        dispatch(touch(1300, MotionEvent.ACTION_UP, 50, 150));
        assertEquals(0, dragEnds.size());
    }

    @Test
    public void updateSizeIsSafeOnEveryApi() {
        hostView.updateSize(150, 100); // S path or deprecated fallback, no crash
        hostView.setAppWidget(7, new AppWidgetProviderInfo());
        assertEquals(0, hostView.getPaddingLeft());
        assertEquals(0, hostView.getPaddingBottom());
    }

    @Test
    public void needsConfigurationRequiresAConfigureIntent() {
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        assertFalse("no configure activity: nothing to configure",
                WidgetHostView.needsConfiguration(info));
    }
}

// Ported from Olauncher (https://github.com/tanujnotes/Olauncher), GPL-3.0.
// Modified for eLauncher: eInk (no crossfade — animations are useless and
// expensive on eInk), Java, single-column board.
package me.pompel.elauncher.widget;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.SizeF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.RemoteViews;

import java.util.Collections;

/** Widget view that reports a long press anywhere on the widget, even over
 * the widget's own buttons, so it can be edited. Keeping the finger down
 * after the long press drags the widget: onDrag gets the vertical distance
 * moved. */
public class WidgetHostView extends AppWidgetHostView {

    /** Called once a long press fires (the widget enters edit mode). */
    public interface OnLongPress {
        void onLongPress();
    }

    /** Called while dragging with the distance from the drag start, in px. */
    public interface OnDrag {
        void onDrag(float dy);
    }

    /** Called when a drag ends (finger up or cancelled). */
    public interface OnDragEnd {
        void onDragEnd(float dy);
    }

    /** The home screen's gesture handling (swipes, double tap). */
    public interface HomeGestureHandler {
        void onHomeGesture(MotionEvent event);
    }

    /** Tells the home screen to drop the gesture it was handed, because the
     * widget took it over. */
    public interface OnHomeGestureCancel {
        void onHomeGestureCancel();
    }

    private OnLongPress onLongPress;
    private OnDrag onDrag;
    private OnDragEnd onDragEnd;
    private HomeGestureHandler homeGestures;
    private OnHomeGestureCancel onHomeGestureCancel;

    /**
     * Unless {@link WidgetTaps#ON}, no touch reaches the widget's own views and
     * every gesture also goes to the home screen, as if the widget weren't
     * there; with {@link WidgetTaps#DOUBLE_TAP} a double tap is then passed on
     * to the widget as a tap. Long press still selects it for editing.
     */
    private WidgetTaps taps = WidgetTaps.ON;

    // Decided when a touch starts, so switching settings mid-gesture can't half-forward it
    private boolean forwardingHome = false;

    private final float touchSlop;
    private final float doubleTapSlop;
    private float downX;
    private float downY;
    private float downRawY;
    private boolean hasPerformedLongPress;
    private boolean movedBeyondSlop;

    // Double tap: when and where the last plain tap ended, and whether this touch is the second tap
    private long lastTapUpTime;
    private float lastTapX;
    private float lastTapY;
    private boolean isSecondTap;
    // Set while a double tap is replayed to the widget as a single tap
    private boolean deliveringTap;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            hasPerformedLongPress = true;
            if (onLongPress != null) {
                // The widget is being edited now, so the home screen mustn't also act on this touch
                if (forwardingHome) {
                    forwardingHome = false;
                    if (onHomeGestureCancel != null) onHomeGestureCancel.onHomeGestureCancel();
                }
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                onLongPress.onLongPress();
            }
        }
    };

    public WidgetHostView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        doubleTapSlop = ViewConfiguration.get(context).getScaledDoubleTapSlop();
    }

    public void setOnLongPress(OnLongPress listener) {
        this.onLongPress = listener;
    }

    public void setOnDrag(OnDrag listener) {
        this.onDrag = listener;
    }

    public void setOnDragEnd(OnDragEnd listener) {
        this.onDragEnd = listener;
    }

    public void setHomeGestures(HomeGestureHandler handler) {
        this.homeGestures = handler;
    }

    public void setOnHomeGestureCancel(OnHomeGestureCancel listener) {
        this.onHomeGestureCancel = listener;
    }

    public WidgetTaps getTaps() {
        return taps;
    }

    public void setTaps(WidgetTaps taps) {
        this.taps = taps == null ? WidgetTaps.ON : taps;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            isSecondTap = taps == WidgetTaps.DOUBLE_TAP
                    && ev.getEventTime() - lastTapUpTime <= ViewConfiguration.getDoubleTapTimeout()
                    && Math.abs(ev.getX() - lastTapX) <= doubleTapSlop
                    && Math.abs(ev.getY() - lastTapY) <= doubleTapSlop;
            forwardingHome = taps != WidgetTaps.ON && !isSecondTap;
            // The second tap belongs to the widget, so the home screen mustn't see a double tap (lock)
            if (isSecondTap && onHomeGestureCancel != null) onHomeGestureCancel.onHomeGestureCancel();
        }
        if (forwardingHome && homeGestures != null) homeGestures.onHomeGesture(ev);

        boolean wasPlainTap = !movedBeyondSlop && !hasPerformedLongPress;
        boolean handled = super.dispatchTouchEvent(ev);
        if (taps == WidgetTaps.DOUBLE_TAP) trackDoubleTap(ev, wasPlainTap);
        return handled;
    }

    private void trackDoubleTap(MotionEvent ev, boolean wasPlainTap) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_UP:
                if (!wasPlainTap) {
                    lastTapUpTime = 0L;
                } else if (isSecondTap) {
                    lastTapUpTime = 0L;
                    final float x = ev.getX();
                    final float y = ev.getY();
                    // After this touch has finished dispatching
                    post(new Runnable() {
                        @Override
                        public void run() {
                            tapWidget(x, y);
                        }
                    });
                } else {
                    lastTapUpTime = ev.getEventTime();
                    lastTapX = ev.getX();
                    lastTapY = ev.getY();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                lastTapUpTime = 0L;
                break;
            default:
                break;
        }
    }

    // Replays a tap at this point to the widget's own views, which do whatever a tap does there
    private void tapWidget(float x, float y) {
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(time, time, MotionEvent.ACTION_UP, x, y, 0);
        deliveringTap = true;
        try {
            super.dispatchTouchEvent(down);
            super.dispatchTouchEvent(up);
        } finally {
            deliveringTap = false;
            down.recycle();
            up.recycle();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (deliveringTap) return false;
        trackLongPress(ev);
        // Once the long press fired, take over the gesture so the widget doesn't also get a click
        return taps != WidgetTaps.ON || hasPerformedLongPress;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (deliveringTap) return true;
        // Reached when no child handles the touch, or after the long press took
        // over the gesture. Keep receiving events so long press works on empty
        // areas and the widget can be dragged.
        trackLongPress(ev);
        if (hasPerformedLongPress) {
            // Raw coordinates, since the view itself moves while being dragged
            float dy = ev.getRawY() - downRawY;
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    if (onDrag != null) onDrag.onDrag(dy);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    hasPerformedLongPress = false;
                    if (onDragEnd != null) onDragEnd.onDragEnd(dy);
                    break;
                default:
                    break;
            }
        }
        return true;
    }

    private void trackLongPress(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                hasPerformedLongPress = false;
                movedBeyondSlop = false;
                downX = ev.getX();
                downY = ev.getY();
                downRawY = ev.getRawY();
                removeCallbacks(longPressRunnable);
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(ev.getX() - downX) > touchSlop || Math.abs(ev.getY() - downY) > touchSlop) {
                    movedBeyondSlop = true;
                    removeCallbacks(longPressRunnable);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_DOWN:
                removeCallbacks(longPressRunnable);
                break;
            default:
                break;
        }
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        removeCallbacks(longPressRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(longPressRunnable);
        super.onDetachedFromWindow();
    }

    @Override
    public void setAppWidget(int appWidgetId, AppWidgetProviderInfo info) {
        super.setAppWidget(appWidgetId, info);
        // Android pads widgets made for older versions; fill the frame instead,
        // so every widget lines up in the board stack
        setPadding(0, 0, 0, 0);
    }

    /** Tells the widget how much space it has, so it can pick a fitting layout. */
    public void updateSize(int widthDp, int heightDp) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            updateAppWidgetSize(new Bundle(),
                    Collections.singletonList(new SizeF(widthDp, heightDp)));
        } else {
            updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp);
        }
    }

    /** Whether the widget has to be set up by hand before it can be shown. */
    public static boolean needsConfiguration(AppWidgetProviderInfo info) {
        boolean configurationOptional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && (info.widgetFeatures & AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0;
        return info.configure != null && !configurationOptional;
    }

    /** Height a newly added widget starts with, based on the size its provider asks for. */
    public static int defaultHeightDp(Context context, AppWidgetProviderInfo info) {
        float density = context.getResources().getDisplayMetrics().density;
        int minHeightDp = Math.round(info.minHeight / density);
        int maxHeightDp = context.getResources().getConfiguration().screenHeightDp / 2;
        int clamped = Math.max(WidgetPrefs.MIN_HEIGHT_DP,
                Math.min(minHeightDp, Math.max(WidgetPrefs.MIN_HEIGHT_DP, maxHeightDp)));
        return Math.min(clamped, WidgetPrefs.MAX_HEIGHT_DP);
    }
}

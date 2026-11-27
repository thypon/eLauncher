// Ported from Olauncher (https://github.com/tanujnotes/Olauncher), GPL-3.0.
// Modified for eLauncher: eInk (no crossfade), Java, single-column board
// (no width handle: every widget fills the board width).
package me.pompel.elauncher.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/** Drawn over a widget while it's being edited: a dashed outline with a
 * handle on the bottom edge. The widget underneath gets no touches meanwhile.
 * Dragging the handle resizes the widget; a long press followed by a drag
 * moves it (drag-reorder while editing); releasing a long press without
 * dragging leaves edit mode; dragging the body without a long press scrolls
 * the board instead, so edit mode never traps scrolling. */
public class ResizeOverlay extends View {

    /** Called while dragging the handle with the growth in px since the drag
     * started; negative shrinks. */
    public interface OnResize {
        void onResize(float growHeight);
    }

    /** Called when a resize drag ends. */
    public interface OnResizeEnd {
        void onResizeEnd();
    }

    /** Called while moving the widget with the vertical distance in px. */
    public interface OnMove {
        void onMove(float dy);
    }

    /** Called when a move drag ends. */
    public interface OnMoveEnd {
        void onMoveEnd(float dy);
    }

    /** Called when the user releases a long press without dragging: with a
     * frame on every widget, that is the "second long press" that leaves
     * edit mode. */
    public interface OnLongPress {
        void onLongPress();
    }

    /** Called with a scroll delta (px, positive = content moves down) while
     * the user drags over the widget body without a long press, so the board
     * can still be scrolled in edit mode. */
    public interface OnScroll {
        void onScroll(float dy);
    }

    private OnResize onResize;
    private OnResizeEnd onResizeEnd;
    private OnMove onMove;
    private OnMoveEnd onMoveEnd;
    private OnLongPress onLongPress;
    private OnScroll onScroll;

    private final float density;
    private final float handleTouchSize;
    private final float touchSlop;
    private final RectF rect = new RectF();

    private final Paint dimPaint = new Paint();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean draggingHeight;
    private boolean dragMode;
    private boolean scrollMode;
    private boolean longPressFired;
    private boolean movedSinceLongPress;
    private float startRawX;
    private float startRawY;
    private float lastRawY;
    private final Runnable longPressRunnable = new Runnable() {
        @Override public void run() {
            longPressFired = true;
            dragMode = true;
            movedSinceLongPress = false;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    };

    public ResizeOverlay(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        handleTouchSize = 44 * density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        dimPaint.setColor(0x40000000);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2 * density);
        borderPaint.setPathEffect(new DashPathEffect(new float[]{8 * density, 6 * density}, 0f));
        setColor(0xFFFFFFFF);
    }

    public void setOnResize(OnResize listener) {
        onResize = listener;
    }

    public void setOnResizeEnd(OnResizeEnd listener) {
        onResizeEnd = listener;
    }

    public void setOnMove(OnMove listener) {
        onMove = listener;
    }

    public void setOnMoveEnd(OnMoveEnd listener) {
        onMoveEnd = listener;
    }

    public void setOnLongPress(OnLongPress listener) {
        onLongPress = listener;
    }

    public void setOnScroll(OnScroll listener) {
        onScroll = listener;
    }

    /** Ink color of the outline and handle (theme primary). */
    public void setColor(int color) {
        borderPaint.setColor(color);
        handlePaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float inset = borderPaint.getStrokeWidth() / 2;
        float radius = 8 * density;
        rect.set(inset, inset, w - inset, h - inset);
        canvas.drawRoundRect(rect, radius, radius, dimPaint);
        canvas.drawRoundRect(rect, radius, radius, borderPaint);

        // Height handle, centered on the bottom edge
        float longSide = 20 * density;
        float thick = 3 * density;
        float edge = 6 * density;
        rect.set(w / 2 - longSide, h - edge - 2 * thick, w / 2 + longSide, h - edge);
        canvas.drawRoundRect(rect, thick, thick, handlePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                draggingHeight = event.getY() > getHeight() - handleTouchSize;
                // Raw coordinates, since the view itself changes size during the drag
                startRawX = event.getRawX();
                startRawY = event.getRawY();
                lastRawY = startRawY;
                dragMode = false;
                scrollMode = false;
                longPressFired = false;
                if (!draggingHeight) {
                    postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                }
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (draggingHeight) {
                    removeCallbacks(longPressRunnable);
                    if (onResize != null) onResize.onResize(event.getRawY() - startRawY);
                    return true;
                }
                if (dragMode) {
                    movedSinceLongPress = true;
                    if (onMove != null) onMove.onMove(event.getRawY() - startRawY);
                    return true;
                }
                if (scrollMode) {
                    // Finger up = see content further down
                    if (onScroll != null) onScroll.onScroll(lastRawY - event.getRawY());
                    lastRawY = event.getRawY();
                    return true;
                }
                if (Math.abs(event.getRawX() - startRawX) > touchSlop
                        || Math.abs(event.getRawY() - startRawY) > touchSlop) {
                    // A plain drag over the body scrolls the board, it never
                    // starts moving the widget before the long press fires
                    removeCallbacks(longPressRunnable);
                    scrollMode = true;
                    lastRawY = event.getRawY();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPressRunnable);
                if (draggingHeight) {
                    if (onResizeEnd != null) onResizeEnd.onResizeEnd();
                } else if (dragMode && movedSinceLongPress) {
                    if (onMoveEnd != null) onMoveEnd.onMoveEnd(event.getRawY() - startRawY);
                } else if (longPressFired && onLongPress != null) {
                    onLongPress.onLongPress();
                }
                draggingHeight = false;
                dragMode = false;
                scrollMode = false;
                longPressFired = false;
                movedSinceLongPress = false;
                break;
            default:
                break;
        }
        // Swallow everything so the widget underneath can't be tapped while editing
        return true;
    }
}

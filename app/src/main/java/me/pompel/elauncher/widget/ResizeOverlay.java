// Ported from Olauncher (https://github.com/tanujnotes/Olauncher), GPL-3.0.
// Modified for eLauncher: eInk (no crossfade), Java, single-column board
// (no width handle: every widget fills the board width).
package me.pompel.elauncher.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Drawn over a widget while it's being edited: a dashed outline with a
 * handle on the bottom edge. The widget underneath gets no touches meanwhile.
 * Dragging the handle reports how much the widget should grow since the drag
 * started; dragging anywhere else reports how far the widget was moved
 * vertically (drag-reorder while editing). */
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

    private OnResize onResize;
    private OnResizeEnd onResizeEnd;
    private OnMove onMove;
    private OnMoveEnd onMoveEnd;

    private final float density;
    private final float handleTouchSize;
    private final RectF rect = new RectF();

    private final Paint dimPaint = new Paint();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean draggingHeight;
    private boolean moving;
    private float startRawY;

    public ResizeOverlay(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        handleTouchSize = 44 * density;
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
                startRawY = event.getRawY();
                moving = !draggingHeight;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (moving) {
                    if (onMove != null) onMove.onMove(event.getRawY() - startRawY);
                    return true;
                }
                float growHeight = event.getRawY() - startRawY;
                if (onResize != null) onResize.onResize(growHeight);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (moving && onMoveEnd != null) onMoveEnd.onMoveEnd(event.getRawY() - startRawY);
                else if (!moving && onResizeEnd != null) onResizeEnd.onResizeEnd();
                draggingHeight = false;
                moving = false;
                break;
            default:
                break;
        }
        // Swallow everything so the widget underneath can't be tapped while editing
        return true;
    }
}

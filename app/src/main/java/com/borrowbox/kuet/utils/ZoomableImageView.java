package com.borrowbox.kuet.utils;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

public class ZoomableImageView extends AppCompatImageView {

    private final Matrix imageMatrix = new Matrix();
    private final float[] matrixValues = new float[9];

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private float minScale = 1.0f;
    private float maxScale = 5.0f;
    private float currentScale = 1.0f;

    private final PointF lastTouch = new PointF();
    private boolean isDragging = false;

    public ZoomableImageView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public ZoomableImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomableImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fitImageToCenter();
    }

    public void fitImageToCenter() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0) return;

        int dw = drawable.getIntrinsicWidth();
        int dh = drawable.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;

        float viewWidth = getWidth();
        float viewHeight = getHeight();

        float scale = Math.min(viewWidth / dw, viewHeight / dh);
        float dx = (viewWidth - dw * scale) * 0.5f;
        float dy = (viewHeight - dh * scale) * 0.5f;

        imageMatrix.reset();
        imageMatrix.postScale(scale, scale);
        imageMatrix.postTranslate(dx, dy);
        setImageMatrix(imageMatrix);

        currentScale = 1.0f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                lastTouch.set(event.getX(), event.getY());
                isDragging = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (isDragging && currentScale > 1.05f) {
                    float dx = event.getX() - lastTouch.x;
                    float dy = event.getY() - lastTouch.y;
                    imageMatrix.postTranslate(dx, dy);
                    checkBoundsAndApply();
                    lastTouch.set(event.getX(), event.getY());
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                break;
        }

        return true;
    }

    private void checkBoundsAndApply() {
        RectF rect = getMatrixRectF();
        if (rect == null) {
            setImageMatrix(imageMatrix);
            return;
        }

        float viewWidth = getWidth();
        float viewHeight = getHeight();
        float deltaX = 0, deltaY = 0;

        if (rect.width() >= viewWidth) {
            if (rect.left > 0) deltaX = -rect.left;
            if (rect.right < viewWidth) deltaX = viewWidth - rect.right;
        } else {
            deltaX = (viewWidth - rect.width()) * 0.5f - rect.left;
        }

        if (rect.height() >= viewHeight) {
            if (rect.top > 0) deltaY = -rect.top;
            if (rect.bottom < viewHeight) deltaY = viewHeight - rect.bottom;
        } else {
            deltaY = (viewHeight - rect.height()) * 0.5f - rect.top;
        }

        imageMatrix.postTranslate(deltaX, deltaY);
        setImageMatrix(imageMatrix);
    }

    private RectF getMatrixRectF() {
        Drawable d = getDrawable();
        if (d != null) {
            RectF rect = new RectF(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
            imageMatrix.mapRect(rect);
            return rect;
        }
        return null;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float factor = detector.getScaleFactor();
            float newScale = currentScale * factor;

            if (newScale < minScale) {
                factor = minScale / currentScale;
                currentScale = minScale;
            } else if (newScale > maxScale) {
                factor = maxScale / currentScale;
                currentScale = maxScale;
            } else {
                currentScale = newScale;
            }

            imageMatrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
            checkBoundsAndApply();
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (currentScale > 1.2f) {
                fitImageToCenter();
            } else {
                float targetScale = 2.5f;
                float factor = targetScale / currentScale;
                currentScale = targetScale;
                imageMatrix.postScale(factor, factor, e.getX(), e.getY());
                checkBoundsAndApply();
            }
            return true;
        }
    }
}

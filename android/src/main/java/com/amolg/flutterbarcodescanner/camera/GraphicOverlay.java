package com.amolg.flutterbarcodescanner.camera;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.amolg.flutterbarcodescanner.BarcodeCaptureActivity;
import com.amolg.flutterbarcodescanner.FlutterBarcodeScannerPlugin;
import com.amolg.flutterbarcodescanner.constants.AppConstants;
import com.amolg.flutterbarcodescanner.utils.AppUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

public class GraphicOverlay<T extends GraphicOverlay.Graphic> extends View {
    private final Object mLock = new Object();
    private float mWidthScaleFactor = 1.0f, mHeightScaleFactor = 1.0f;

    private int mFacing = CameraSource.CAMERA_FACING_BACK;
    private final Set<T> mGraphics = new HashSet<>();

    private float left, top, endY;
    private int rectWidth, rectHeight, frames, lineColor, lineWidth;
    private boolean revAnimation;

    public static abstract class Graphic {
        private final GraphicOverlay mOverlay;

        public Graphic(GraphicOverlay overlay) { mOverlay = overlay; }

        public abstract void draw(Canvas canvas);

        public float scaleX(float horizontal) { return horizontal * mOverlay.mWidthScaleFactor; }
        public float scaleY(float vertical) { return vertical * mOverlay.mHeightScaleFactor; }

        public float translateX(float x) {
            if (mOverlay.mFacing == CameraSource.CAMERA_FACING_FRONT) {
                return mOverlay.getWidth() - scaleX(x);
            } else {
                return scaleX(x);
            }
        }

        public float translateY(float y) { return scaleY(y); }

        public void postInvalidate() { mOverlay.postInvalidate(); }
    }

    public GraphicOverlay(android.content.Context context, AttributeSet attrs) {
        super(context, attrs);

        rectWidth = AppConstants.BARCODE_RECT_WIDTH;
        rectHeight = BarcodeCaptureActivity.SCAN_MODE == BarcodeCaptureActivity.SCAN_MODE_ENUM.QR.ordinal()
                ? AppConstants.BARCODE_RECT_HEIGHT
                : (int) (AppConstants.BARCODE_RECT_HEIGHT / 1.5);

        lineColor = Color.parseColor(FlutterBarcodeScannerPlugin.lineColor);
        lineWidth = AppConstants.BARCODE_LINE_WIDTH;
        frames = AppConstants.BARCODE_FRAMES;
    }

    public void clear() {
        synchronized (mLock) { mGraphics.clear(); }
        postInvalidate();
    }

    public void add(T graphic) {
        synchronized (mLock) { mGraphics.add(graphic); }
        postInvalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        left = (w - AppUtil.dpToPx(getContext(), rectWidth)) / 2f;
        top = (h - AppUtil.dpToPx(getContext(), rectHeight)) / 2f;
        endY = top;
        super.onSizeChanged(w, h, oldw, oldh);
    }

    public void remove(T graphic) {
        synchronized (mLock) { mGraphics.remove(graphic); }
        postInvalidate();
    }

    public List<T> getGraphics() {
        synchronized (mLock) { return new Vector<>(mGraphics); }
    }

    public float getWidthScaleFactor() { return mWidthScaleFactor; }
    public float getHeightScaleFactor() { return mHeightScaleFactor; }

    public void setCameraInfo(int previewWidth, int previewHeight, int facing) {
        synchronized (mLock) { mFacing = facing; }
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int cornerRadius = 0;
        Paint eraser = new Paint();
        eraser.setAntiAlias(true);
        eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        RectF rect = new RectF(left, top,
                AppUtil.dpToPx(getContext(), rectWidth) + left,
                AppUtil.dpToPx(getContext(), rectHeight) + top);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, eraser);

        Paint line = new Paint();
        line.setColor(lineColor);
        line.setStrokeWidth((float) lineWidth);

        if (endY >= top + AppUtil.dpToPx(getContext(), rectHeight) + frames) {
            revAnimation = true;
        } else if (endY == top + frames) {
            revAnimation = false;
        }

        if (revAnimation) {
            endY -= frames;
        } else {
            endY += frames;
        }
        canvas.drawLine(left, endY, left + AppUtil.dpToPx(getContext(), rectWidth), endY, line);
        invalidate();
    }
}

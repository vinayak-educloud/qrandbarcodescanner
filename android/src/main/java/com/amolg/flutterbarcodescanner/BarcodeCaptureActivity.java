package com.amolg.flutterbarcodescanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.amolg.flutterbarcodescanner.camera.CameraSource;
import com.amolg.flutterbarcodescanner.camera.CameraSourcePreview;
import com.amolg.flutterbarcodescanner.camera.GraphicOverlay;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;

public final class BarcodeCaptureActivity extends AppCompatActivity
        implements BarcodeGraphicTracker.BarcodeUpdateListener, View.OnClickListener {

    private static final int RC_HANDLE_GMS = 9001;
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    public static final String BarcodeObject = "Barcode";

    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview;
    private GraphicOverlay<BarcodeGraphic> mGraphicOverlay;

    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;

    private ImageView imgViewBarcodeCaptureUseFlash;
    private ImageView imgViewSwitchCamera;

    public static int SCAN_MODE = SCAN_MODE_ENUM.QR.ordinal();

    public enum SCAN_MODE_ENUM {
        QR,
        BARCODE,
        DEFAULT
    }

    enum USE_FLASH {
        ON,
        OFF
    }

    private int flashStatus = USE_FLASH.OFF.ordinal();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        try {
            setContentView(R.layout.barcode_capture);

            String buttonText;
            try {
                buttonText = getIntent().getStringExtra("cancelButtonText");
                if (buttonText == null) buttonText = "Cancel";
            } catch (Exception e) {
                buttonText = "Cancel";
                Log.e("BCActivity:onCreate()", "onCreate: " + e.getLocalizedMessage());
            }

            Button btnBarcodeCaptureCancel = findViewById(R.id.btnBarcodeCaptureCancel);
            btnBarcodeCaptureCancel.setText(buttonText);
            btnBarcodeCaptureCancel.setOnClickListener(this);

            imgViewBarcodeCaptureUseFlash = findViewById(R.id.imgViewBarcodeCaptureUseFlash);
            imgViewBarcodeCaptureUseFlash.setOnClickListener(this);
            imgViewBarcodeCaptureUseFlash.setVisibility(
                    FlutterBarcodeScannerPlugin.isShowFlashIcon ? View.VISIBLE : View.GONE);

            imgViewSwitchCamera = findViewById(R.id.imgViewSwitchCamera);
            imgViewSwitchCamera.setOnClickListener(this);

            mPreview = findViewById(R.id.preview);
            mGraphicOverlay = findViewById(R.id.graphicOverlay);

            boolean autoFocus = true;
            boolean useFlash = false;

            int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
            if (rc == PackageManager.PERMISSION_GRANTED) {
                createCameraSource(autoFocus, useFlash, CameraSource.CAMERA_FACING_BACK);
            } else {
                requestCameraPermission();
            }

            gestureDetector = new GestureDetector(this, new CaptureGestureListener());
            scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());

        } catch (Exception ignored) { }
    }

    private void requestCameraPermission() {
        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = view ->
                ActivityCompat.requestPermissions(thisActivity, permissions, RC_HANDLE_CAMERA_PERM);

        findViewById(R.id.topLayout).setOnClickListener(listener);
        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        boolean b = scaleGestureDetector.onTouchEvent(e);
        boolean c = gestureDetector.onTouchEvent(e);
        return b || c || super.onTouchEvent(e);
    }

    @SuppressLint("InlinedApi")
    private void createCameraSource(boolean autoFocus, boolean useFlash, int cameraFacing) {
        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(getApplicationContext()).build();
        BarcodeTrackerFactory barcodeFactory = new BarcodeTrackerFactory(mGraphicOverlay, this);
        barcodeDetector.setProcessor(new MultiProcessor.Builder<>(barcodeFactory).build());

        if (!barcodeDetector.isOperational()) {
            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;
            if (hasLowStorage) {
                Toast.makeText(this, R.string.low_storage_error, Toast.LENGTH_LONG).show();
            }
        }

        CameraSource.Builder builder = new CameraSource.Builder(getApplicationContext(), barcodeDetector)
                .setFacing(cameraFacing)
                .setRequestedPreviewSize(1600, 1024)
                .setRequestedFps(30.0f)
                .setFlashMode(useFlash ? Camera.Parameters.FLASH_MODE_TORCH : null);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            builder = builder.setFocusMode(
                    autoFocus ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : null);
        }

        if (mCameraSource != null) {
            mCameraSource.stop();
            mCameraSource.release();
        }
        mCameraSource = builder.build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraSource();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mPreview != null) {
            mPreview.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPreview != null) {
            mPreview.release();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            boolean autoFocus = true;
            boolean useFlash = false;
            createCameraSource(autoFocus, useFlash, CameraSource.CAMERA_FACING_BACK);
            return;
        }

        DialogInterface.OnClickListener listener = (dialog, id) -> finish();

        new AlertDialog.Builder(this)
                .setTitle("Allow permissions")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    private void startCameraSource() throws SecurityException {
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg = GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            if (dlg != null) dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                mCameraSource.release();
                mCameraSource = null;
            }
        }
        System.gc();
    }

    private boolean onTap(float rawX, float rawY) {
        int[] location = new int[2];
        mGraphicOverlay.getLocationOnScreen(location);
        float x = (rawX - location[0]) / mGraphicOverlay.getWidthScaleFactor();
        float y = (rawY - location[1]) / mGraphicOverlay.getHeightScaleFactor();

        Barcode best = null;
        float bestDistance = Float.MAX_VALUE;
        for (BarcodeGraphic graphic : mGraphicOverlay.getGraphics()) {
            Barcode barcode = graphic.getBarcode();
            if (barcode.getBoundingBox().contains((int) x, (int) y)) {
                best = barcode;
                break;
            }
            float dx = x - barcode.getBoundingBox().centerX();
            float dy = y - barcode.getBoundingBox().centerY();
            float distance = (dx * dx) + (dy * dy);
            if (distance < bestDistance) {
                best = barcode;
                bestDistance = distance;
            }
        }

        if (best != null) {
            Intent data = new Intent();
            data.putExtra(BarcodeObject, best);
            setResult(CommonStatusCodes.SUCCESS, data);
            finish();
            return true;
        }
        return false;
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.imgViewBarcodeCaptureUseFlash &&
                getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            try {
                if (flashStatus == USE_FLASH.OFF.ordinal()) {
                    flashStatus = USE_FLASH.ON.ordinal();
                    imgViewBarcodeCaptureUseFlash.setImageResource(R.drawable.ic_barcode_flash_on);
                    turnOnOffFlashLight(true);
                } else {
                    flashStatus = USE_FLASH.OFF.ordinal();
                    imgViewBarcodeCaptureUseFlash.setImageResource(R.drawable.ic_barcode_flash_off);
                    turnOnOffFlashLight(false);
                }
            } catch (Exception e) {
                Toast.makeText(this, "Unable to turn on flash", Toast.LENGTH_SHORT).show();
                Log.e("BarcodeCaptureActivity", "FlashOnFailure: " + e.getLocalizedMessage());
            }
        } else if (i == R.id.btnBarcodeCaptureCancel) {
            Barcode barcode = new Barcode();
            barcode.rawValue = "-1";
            barcode.displayValue = "-1";
            FlutterBarcodeScannerPlugin.onBarcodeScanReceiver(barcode);
            finish();
        } else if (i == R.id.imgViewSwitchCamera) {
            int currentFacing = mCameraSource.getCameraFacing();
            boolean autoFocus = mCameraSource.getFocusMode() != null;
            boolean useFlash = flashStatus == USE_FLASH.ON.ordinal();
            createCameraSource(autoFocus, useFlash, getInverseCameraFacing(currentFacing));
            startCameraSource();
        }
    }

    private int getInverseCameraFacing(int cameraFacing) {
        if (cameraFacing == CameraSource.CAMERA_FACING_FRONT) return CameraSource.CAMERA_FACING_BACK;
        if (cameraFacing == CameraSource.CAMERA_FACING_BACK) return CameraSource.CAMERA_FACING_FRONT;
        return CameraSource.CAMERA_FACING_BACK;
    }

    private void turnOnOffFlashLight(boolean isFlashToBeTurnOn) {
        try {
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
                String flashMode = isFlashToBeTurnOn
                        ? Camera.Parameters.FLASH_MODE_TORCH
                        : Camera.Parameters.FLASH_MODE_OFF;

                mCameraSource.setFlashMode(flashMode);
            } else {
                Toast.makeText(getBaseContext(), "Unable to access flashlight as flashlight not available", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(getBaseContext(), "Unable to access flashlight.", Toast.LENGTH_SHORT).show();
        }
    }

    private class CaptureGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            return onTap(e.getRawX(), e.getRawY()) || super.onSingleTapConfirmed(e);
        }
    }

    private class ScaleListener implements ScaleGestureDetector.OnScaleGestureListener {
        @Override public boolean onScale(ScaleGestureDetector detector) { return false; }
        @Override public boolean onScaleBegin(ScaleGestureDetector detector) { return true; }
        @Override public void onScaleEnd(ScaleGestureDetector detector) {
            mCameraSource.doZoom(detector.getScaleFactor());
        }
    }

    @Override
    public void onBarcodeDetected(Barcode barcode) {
        if (barcode != null) {
            if (FlutterBarcodeScannerPlugin.isContinuousScan) {
                FlutterBarcodeScannerPlugin.onBarcodeScanReceiver(barcode);
            } else {
                Intent data = new Intent();
                data.putExtra(BarcodeObject, barcode);
                setResult(CommonStatusCodes.SUCCESS, data);
                finish();
            }
        }
    }
}

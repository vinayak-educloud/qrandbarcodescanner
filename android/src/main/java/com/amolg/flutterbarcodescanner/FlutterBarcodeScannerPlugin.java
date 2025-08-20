package com.amolg.flutterbarcodescanner;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;

import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;

/**
 * FlutterBarcodeScannerPlugin (V2 embedding only)
 */
public class FlutterBarcodeScannerPlugin implements 
        MethodCallHandler, 
        FlutterPlugin, 
        ActivityAware, 
        ActivityPluginBinding.OnActivityResultListener, 
        StreamHandler {

    private static final String CHANNEL = "flutter_barcode_scanner";
    private static final String EVENT_CHANNEL = "flutter_barcode_scanner_receiver";
    private static final int RC_BARCODE_CAPTURE = 9001;

    private Activity activity;
    private Result pendingResult;
    private Map<String, Object> arguments;

    private MethodChannel methodChannel;
    private EventChannel eventChannel;
    private EventChannel.EventSink barcodeStream;
    private Application applicationContext;
    private ActivityPluginBinding activityBinding;

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        pendingResult = result;

        if (call.method.equals("scanBarcode")) {
            try {
                arguments = (Map<String, Object>) call.arguments;
                String lineColor = (String) arguments.get("lineColor");
                boolean isShowFlashIcon = (boolean) arguments.get("isShowFlashIcon");
                boolean isContinuousScan = (boolean) arguments.get("isContinuousScan");
                String cancelButtonText = (String) arguments.get("cancelButtonText");

                if (lineColor == null || lineColor.isEmpty()) {
                    lineColor = "#DC143C";
                }

                Intent intent = new Intent(activity, BarcodeCaptureActivity.class)
                        .putExtra("cancelButtonText", cancelButtonText)
                        .putExtra("lineColor", lineColor)
                        .putExtra("isShowFlashIcon", isShowFlashIcon)
                        .putExtra("isContinuousScan", isContinuousScan);

                if (isContinuousScan) {
                    activity.startActivity(intent);
                } else {
                    activity.startActivityForResult(intent, RC_BARCODE_CAPTURE);
                }
            } catch (Exception e) {
                Log.e("FlutterBarcodeScanner", "onMethodCall error: " + e.getMessage());
                pendingResult.success("-1");
            }
        } else {
            result.notImplemented();
        }
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_BARCODE_CAPTURE) {
            if (resultCode == CommonStatusCodes.SUCCESS && data != null) {
                try {
                    Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
                    if (barcode != null) {
                        pendingResult.success(barcode.rawValue);
                    } else {
                        pendingResult.success("-1");
                    }
                } catch (Exception e) {
                    pendingResult.success("-1");
                }
            } else {
                pendingResult.success("-1");
            }
            pendingResult = null;
            arguments = null;
            return true;
        }
        return false;
    }

    @Override
    public void onListen(Object args, EventChannel.EventSink events) {
        barcodeStream = events;
    }

    @Override
    public void onCancel(Object args) {
        barcodeStream = null;
    }

    // FlutterPlugin
    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        applicationContext = (Application) binding.getApplicationContext();
        BinaryMessenger messenger = binding.getBinaryMessenger();

        methodChannel = new MethodChannel(messenger, CHANNEL);
        methodChannel.setMethodCallHandler(this);

        eventChannel = new EventChannel(messenger, EVENT_CHANNEL);
        eventChannel.setStreamHandler(this);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        if (methodChannel != null) {
            methodChannel.setMethodCallHandler(null);
            methodChannel = null;
        }
        if (eventChannel != null) {
            eventChannel.setStreamHandler(null);
            eventChannel = null;
        }
        applicationContext = null;
    }

    // ActivityAware
    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        this.activity = binding.getActivity();
        this.activityBinding = binding;
        binding.addActivityResultListener(this);
    }

    @Override
    public void onDetachedFromActivity() {
        if (activityBinding != null) {
            activityBinding.removeActivityResultListener(this);
            activityBinding = null;
        }
        activity = null;
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }
}

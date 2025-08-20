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
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.PluginRegistry;

/**
 * FlutterBarcodeScannerPlugin (V2 embedding)
 */
public class FlutterBarcodeScannerPlugin implements
        MethodCallHandler,
        FlutterPlugin,
        ActivityAware,
        PluginRegistry.ActivityResultListener,
        EventChannel.StreamHandler {

    private static final String CHANNEL = "flutter_barcode_scanner";
    private static final String EVENT_CHANNEL = "flutter_barcode_scanner_receiver";
    private static final int RC_BARCODE_CAPTURE = 9001;

    // ====== Static state used by Activity/Overlay ======
    public static volatile boolean isShowFlashIcon = false;
    public static volatile boolean isContinuousScan = false;
    public static volatile String lineColor = "#DC143C";

    private static volatile EventChannel.EventSink sEventSink;

    /** Called by BarcodeCaptureActivity to emit results when in continuous mode (or cancel). */
    public static void onBarcodeScanReceiver(Barcode barcode) {
        EventChannel.EventSink sink = sEventSink;
        if (sink != null) {
            String value = (barcode != null && barcode.rawValue != null) ? barcode.rawValue : "-1";
            sink.success(value);
        }
    }
    // ===================================================

    private Activity activity;
    private Result pendingResult;
    private Map<String, Object> arguments;

    private MethodChannel methodChannel;
    private EventChannel eventChannel;
    private Application applicationContext;
    private ActivityPluginBinding activityBinding;

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
        binding.addActivityResultListener(this); // <-- correct interface type
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        if (activityBinding != null) {
            activityBinding.removeActivityResultListener(this); // <-- compiles now
            activityBinding = null;
        }
        activity = null;
    }

    // MethodCallHandler
    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        pendingResult = result;

        if ("scanBarcode".equals(call.method)) {
            try {
                // Read args
                // expected map keys: lineColor, isShowFlashIcon, isContinuousScan, cancelButtonText
                arguments = (Map<String, Object>) call.arguments;

                String lineColorArg = (String) arguments.get("lineColor");
                String cancelButtonText = (String) arguments.get("cancelButtonText");

                Boolean showFlash = safeBool(arguments.get("isShowFlashIcon"));
                Boolean continuous = safeBool(arguments.get("isContinuousScan"));

                // Update static state for Activity/Overlay
                lineColor = (lineColorArg == null || lineColorArg.isEmpty()) ? "#DC143C" : lineColorArg;
                isShowFlashIcon = showFlash != null && showFlash;
                isContinuousScan = continuous != null && continuous;

                if (activity == null) {
                    Log.e("FlutterBarcodeScanner", "Activity is null");
                    result.success("-1");
                    return;
                }

                Intent intent = new Intent(activity, BarcodeCaptureActivity.class)
                        .putExtra("cancelButtonText", cancelButtonText)
                        .putExtra("lineColor", lineColor)               // also in Intent (not required for overlay but ok)
                        .putExtra("isShowFlashIcon", isShowFlashIcon)   // activity also reads static
                        .putExtra("isContinuousScan", isContinuousScan);

                if (isContinuousScan) {
                    activity.startActivity(intent); // stream results via EventChannel
                } else {
                    activity.startActivityForResult(intent, RC_BARCODE_CAPTURE);
                }
            } catch (Exception e) {
                Log.e("FlutterBarcodeScanner", "onMethodCall error: " + e.getMessage(), e);
                safeFinishWith("-1");
            }
        } else {
            result.notImplemented();
        }
    }

    private static Boolean safeBool(Object o) {
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof String) return Boolean.parseBoolean((String) o);
        return null;
    }

    private void safeFinishWith(String value) {
        if (pendingResult != null) {
            pendingResult.success(value);
            pendingResult = null;
        }
        arguments = null;
    }

    // ActivityResultListener
    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != RC_BARCODE_CAPTURE) return false;

        if (resultCode == CommonStatusCodes.SUCCESS && data != null) {
            try {
                Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
                if (barcode != null) {
                    safeFinishWith(barcode.rawValue);
                } else {
                    safeFinishWith("-1");
                }
            } catch (Exception e) {
                safeFinishWith("-1");
            }
        } else {
            safeFinishWith("-1");
        }
        return true;
    }

    // EventChannel.StreamHandler
    @Override
    public void onListen(Object args, EventChannel.EventSink events) {
        sEventSink = events;
    }

    @Override
    public void onCancel(Object args) {
        sEventSink = null;
    }
}

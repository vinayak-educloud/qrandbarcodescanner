import 'dart:async';
import 'package:flutter/services.dart';

/// Scan mode which is either QR code or BARCODE
enum ScanMode { QR, BARCODE, DEFAULT }

/// Provides access to the barcode scanner plugin.
/// This acts as a bridge between Flutter and the native Android/iOS code.
class FlutterBarcodeScanner {
  static const MethodChannel _channel =
      MethodChannel('flutter_barcode_scanner');

  static const EventChannel _eventChannel =
      EventChannel('flutter_barcode_scanner_receiver');

  static Stream<String>? _onBarcodeReceiver;

  /// Scan with the camera until a barcode is identified, then return.
  ///
  /// [lineColor] - color of the scanning line in hex format (e.g. "#ff6666").
  /// [cancelButtonText] - text shown on cancel button.
  /// [isShowFlashIcon] - whether to show flash toggle.
  /// [scanMode] - QR, BARCODE, or DEFAULT.
  static Future<String> scanBarcode(
    String lineColor,
    String cancelButtonText,
    bool isShowFlashIcon,
    ScanMode scanMode,
  ) async {
    final params = <String, dynamic>{
      'lineColor': lineColor,
      'cancelButtonText': cancelButtonText.isEmpty ? 'Cancel' : cancelButtonText,
      'isShowFlashIcon': isShowFlashIcon,
      'isContinuousScan': false,
      'scanMode': scanMode.index,
    };

    final barcodeResult =
        await _channel.invokeMethod<String>('scanBarcode', params);

    return barcodeResult ?? '';
  }

  /// Returns a continuous stream of barcode scans until the user cancels.
  static Stream<String> getBarcodeStreamReceiver(
    String lineColor,
    String cancelButtonText,
    bool isShowFlashIcon,
    ScanMode scanMode,
  ) {
    final params = <String, dynamic>{
      'lineColor': lineColor,
      'cancelButtonText': cancelButtonText.isEmpty ? 'Cancel' : cancelButtonText,
      'isShowFlashIcon': isShowFlashIcon,
      'isContinuousScan': true,
      'scanMode': scanMode.index,
    };

    // Start scan in native
    _channel.invokeMethod('scanBarcode', params);

    // Stream from native
    _onBarcodeReceiver ??= _eventChannel
        .receiveBroadcastStream()
        .map((event) => event.toString());

    return _onBarcodeReceiver!;
  }
}

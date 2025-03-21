import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'onyxsdk_pen_platform_interface.dart';

/// Renders a native Android view which uses the Onyx SDK to draw on the screen.
class OnyxSdkPenArea extends StatefulWidget {
  const OnyxSdkPenArea({
    super.key,
    this.refreshDelay = const Duration(seconds: 1),
    this.strokeStyle = 0,
    this.strokeColor = 0,
    this.strokeWidth = 3.0,
    required this.child,
  });

  /// The delay after the pen stroke is finished before the screen is refreshed.
  ///
  /// Setting this too low will cause the screen to refresh while the user
  /// is still writing, which will make the screen get stuck in a half-drawn
  /// state.
  final Duration refreshDelay;
  final int strokeStyle;
  final int strokeColor;
  final double strokeWidth;

  final Widget child;

  @override
  State<OnyxSdkPenArea> createState() => _OnyxSdkPenAreaState();

  /// Optional method to initialize the onyxsdk_pen package.
  ///
  /// This method should be called in the main() method before runApp(),
  /// or before the first OnyxSdkPenArea widget is created.
  ///
  /// Returns true if the device is an Onyx device, false otherwise.
  static Future<bool> init() async {
    return await _OnyxSdkPenAreaState._findIsOnyxDevice();
  }
}

class _OnyxSdkPenAreaState extends State<OnyxSdkPenArea> with WidgetsBindingObserver {
  static bool? _isOnyxDevice = (kIsWeb || !Platform.isAndroid) ? false : null;
  static Future<bool> _findIsOnyxDevice() async {
    if (_isOnyxDevice != null) return _isOnyxDevice!;

    // use the platform interface to check if the device is an Onyx device
    return _isOnyxDevice = await OnyxsdkPenPlatform.instance.isOnyxDevice();
  }

  bool get isOnyxDevice {
    if (_isOnyxDevice != null) return _isOnyxDevice!;

    _findIsOnyxDevice().then((isOnyxDevice) {
      if (isOnyxDevice != _isOnyxDevice) {
        setState(() {
          _isOnyxDevice = isOnyxDevice;
        });
      }
    });

    // assume it's an Onyx device until the Future completes
    return _isOnyxDevice = true;
  }

  /// Parameters to pass to the platform side
  late final creationParams = <String, dynamic>{
    "refreshDelayMs": widget.refreshDelay.inMilliseconds,
    "strokeStyle": widget.strokeStyle,
    "strokeColor": widget.strokeColor,
    "strokeWidth": widget.strokeWidth,
  };
  late final channel = MethodChannel('onyxsdk_pen_area');

  /// This is used in the platform side to register the view.
  static const String viewType = 'onyxsdk_pen_area';

  @override
  void didUpdateWidget(OnyxSdkPenArea oldWidget) {
    super.didUpdateWidget(oldWidget);
    
    creationParams['refreshDelayMs'] = widget.refreshDelay.inMilliseconds;
    creationParams['strokeStyle'] = widget.strokeStyle;
    creationParams['strokeColor'] = widget.strokeColor;
    creationParams['strokeWidth'] = widget.strokeWidth;
    channel.invokeMethod('updateStroke', creationParams).catchError((e) {});
  }

  @override
  void initState() {
    WidgetsBinding.instance.addObserver(this);
    super.initState();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) async {
    switch (state) {
      case AppLifecycleState.resumed:
        channel.invokeMethod('setDraw', true).catchError((e) {});
        break;
      case AppLifecycleState.paused:
        channel.invokeMethod('setDraw', false).catchError((e) {});
        break;
      default:
    }
}

  @override
  Widget build(BuildContext context) {
    if (!isOnyxDevice) return widget.child;
    return Stack(
      fit: StackFit.expand,
      children: [
        Opacity(
          opacity: 0,
          child: AndroidView(
            viewType: viewType,
            creationParams: creationParams,
            creationParamsCodec: const StandardMessageCodec(),
            gestureRecognizers: <Factory<OneSequenceGestureRecognizer>>{
              Factory<OneSequenceGestureRecognizer>(
                () => EagerGestureRecognizer(),
              ),
            },
          ),
        ),
        widget.child,
      ],
    );
  }
}

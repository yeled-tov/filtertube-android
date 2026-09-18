import 'dart:io';

import 'package:flutter/services.dart';

/// קצב רענון גבוה במכשירים שתומכים.
///
/// אנדרואיד בלבד: ב-iOS המערכת מחליטה בעצמה ואין ממשק לבקש ממנה אחרת,
/// ולכן הקריאה שם היא no-op ולא כישלון.
class DisplayMode {
  DisplayMode._();

  static const MethodChannel _channel = MethodChannel('filtertube/display');

  static Future<void> applyHighRefreshRate() async {
    if (!Platform.isAndroid) return;
    try {
      await _channel.invokeMethod<void>('setHighRefreshRate');
    } catch (_) {
      // מכשיר שלא תומך פשוט נשאר בקצב שלו
    }
  }
}

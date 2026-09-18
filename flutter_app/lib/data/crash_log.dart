import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// שמירת הקריסה האחרונה, כדי להציג אותה בהפעלה הבאה.
///
/// ## למה לא פשוט לתת לאפליקציה ליפול
/// קריסה שאיש לא רואה היא באג שאיש לא מדווח עליו. הדוח נשמר במכשיר,
/// מוצג פעם אחת בהפעלה הבאה, והמשתמש יכול לשלוח אותו בלחיצה — בלי
/// SDK לניטור ובלי לשלוח שום דבר בלי שהוא ביקש. זה גם מה שמתאים
/// להצהרת הפרטיות: אין איסוף אוטומטי.
class CrashLog {
  CrashLog._();

  static const String _key = 'last_crash_report';
  static const int _maxLength = 4000;

  /// תופס כל שגיאה לא מטופלת — של Flutter ושל ה-Zone — ושומר אותה.
  static void install(void Function() runApp) {
    runZonedGuarded(
      () {
        final previous = FlutterError.onError;
        FlutterError.onError = (details) {
          previous?.call(details);
          unawaited(_store('${details.exception}\n${details.stack}'));
        };
        runApp();
      },
      (error, stack) => unawaited(_store('$error\n$stack')),
    );
  }

  static Future<void> _store(String report) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final full = '${DateTime.now().toIso8601String()}\n$report';
      // החיתוך נעשה על אורך המחרוזת עצמה: חישוב על אורך אחר היה זורק
      // בדיוק בנתיב שאמור לתפוס שגיאות.
      final trimmed =
          full.length > _maxLength ? full.substring(0, _maxLength) : full;
      await prefs.setString(_key, trimmed);
    } catch (_) {
      // אם גם השמירה נכשלה, אין מה לעשות — העיקר שלא ניפול כאן שוב
    }
  }

  static Future<String?> lastCrash() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final value = prefs.getString(_key);
      return (value == null || value.isEmpty) ? null : value;
    } catch (_) {
      return null;
    }
  }

  static Future<void> clear() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(_key);
    } catch (_) {
      // אין מה לעשות
    }
  }
}

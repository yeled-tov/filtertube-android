import 'dart:io';

import 'package:flutter_local_notifications/flutter_local_notifications.dart';

import '../models/video.dart';

/// התראות "סרטון חדש בערוץ שאתה עוקב אחריו".
///
/// ## למה מקומיות ולא Push
/// התראת Push דורשת רישום נפרד של אפליקציית Android ואפליקציית iOS
/// בקונסולת Firebase, וקובץ הגדרה לכל אחת — משהו שאי אפשר להכין מראש
/// בתוך המאגר. ההתראה כאן נבנית מהבדיקה שהאפליקציה ממילא עושה בכל פתיחה
/// מול הערוצים המאושרים, כלומר היא אמיתית ולא דורשת שרת.
///
/// מי שירצה בהמשך התראה גם כשהאפליקציה סגורה — זו הרחבה של אותו מסלול:
/// רישום האפליקציה ב-Firebase והוספת firebase_messaging.
class AppNotifications {
  AppNotifications._();

  static final FlutterLocalNotificationsPlugin _plugin =
      FlutterLocalNotificationsPlugin();
  static bool _ready = false;

  static const AndroidNotificationDetails _android = AndroidNotificationDetails(
    'new_videos',
    'סרטונים חדשים',
    channelDescription: 'התראה על סרטון חדש בערוץ מאושר שאתה עוקב אחריו',
    importance: Importance.defaultImportance,
    priority: Priority.defaultPriority,
  );

  static Future<void> init() async {
    if (_ready) return;
    try {
      await _plugin.initialize(
        const InitializationSettings(
          android: AndroidInitializationSettings('@mipmap/ic_launcher'),
          iOS: DarwinInitializationSettings(
            requestAlertPermission: false,
            requestBadgePermission: false,
            requestSoundPermission: false,
          ),
        ),
      );
      _ready = true;
    } catch (_) {
      // בלי התראות האפליקציה עובדת במלואה — התיבה באפליקציה עדיין מתמלאת
    }
  }

  /// מבקש הרשאה. נקרא רק כשהמשתמש מדליק את ההתראות בהגדרות, ולא בהפעלה
  /// הראשונה: בקשת הרשאה לפני שברור למה היא נדחית ברוב המקרים.
  static Future<bool> requestPermission() async {
    await init();
    try {
      if (Platform.isAndroid) {
        final android = _plugin.resolvePlatformSpecificImplementation<
            AndroidFlutterLocalNotificationsPlugin>();
        return await android?.requestNotificationsPermission() ?? false;
      }
      final ios = _plugin.resolvePlatformSpecificImplementation<
          IOSFlutterLocalNotificationsPlugin>();
      return await ios?.requestPermissions(alert: true, badge: true, sound: true) ??
          false;
    } catch (_) {
      return false;
    }
  }

  static Future<void> newVideos(List<Video> videos) async {
    if (videos.isEmpty) return;
    await init();
    if (!_ready) return;
    final title = videos.length == 1
        ? 'סרטון חדש ב${videos.first.channelName}'
        : '${videos.length} סרטונים חדשים בערוצים שלך';
    final body = videos.length == 1
        ? videos.first.title
        : videos.take(3).map((v) => v.title).join(' · ');
    try {
      await _plugin.show(
        1001,
        title,
        body,
        const NotificationDetails(
          android: _android,
          iOS: DarwinNotificationDetails(),
        ),
      );
    } catch (_) {
      // התראה שלא נשלחה לא אמורה להפיל את הרענון
    }
  }

  static Future<void> cancelAll() async {
    await init();
    if (!_ready) return;
    try {
      await _plugin.cancelAll();
    } catch (_) {
      // אין מה לעשות אם הביטול נכשל
    }
  }
}

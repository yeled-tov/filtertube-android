import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config.dart';
import '../models/video.dart';
import 'auth.dart';
import 'billing.dart';
import 'library_store.dart';
import 'settings_store.dart';

/// קריאות לשרת של FilterTube: בקשות ערוץ, דיווחים, ניהול וסנכרון ענן.
///
/// הכל עובר דרך אותן פונקציות שהאפליקציה הראשית משתמשת בהן, עם אותו אסימון
/// מאומת — כלומר בקשה שנשלחת מכאן מגיעה לאותו מקום בדיוק.
class Cloud {
  Cloud._();

  static final http.Client _client = http.Client();

  static Future<Map<String, dynamic>> _call(
    String path, {
    String method = 'POST',
    Map<String, dynamic>? body,
    Map<String, String>? query,
  }) async {
    final token = await appAuth.idToken();
    if (token == null) {
      return {'ok': false, 'message': 'יש להתחבר לחשבון FilterTube'};
    }
    try {
      var uri = Uri.parse('${AppConfig.functionsBase}/$path');
      if (query != null) uri = uri.replace(queryParameters: query);
      final headers = {
        'Authorization': 'Bearer $token',
        'Content-Type': 'application/json',
      };
      final resp = method == 'GET'
          ? await _client.get(uri, headers: headers).timeout(const Duration(seconds: 25))
          : await _client
              .post(uri, headers: headers, body: jsonEncode(body ?? const {}))
              .timeout(const Duration(seconds: 25));
      final decoded = jsonDecode(utf8.decode(resp.bodyBytes));
      if (decoded is Map<String, dynamic>) return decoded;
      return {'ok': false, 'message': 'תשובה לא צפויה מהשרת'};
    } catch (_) {
      return {'ok': false, 'message': 'אין חיבור לשרת. נסה שוב בעוד רגע'};
    }
  }

  // ── בקשות ערוץ ─────────────────────────────────────────────────────────

  static Future<Map<String, dynamic>> submitChannelRequest({
    required String name,
    required String url,
    required String category,
    required String gender,
    required String description,
  }) =>
      _call('submitChannelRequest', body: {
        'name': name,
        'url': url,
        'category': category,
        'gender': gender,
        'description': description,
      });

  static Future<Map<String, dynamic>> myChannelRequests() =>
      _call('listMyChannelRequests', method: 'GET');

  // ── דיווחים ────────────────────────────────────────────────────────────

  static Future<bool> submitBugReport(String title, [String details = '']) async {
    final res = await _call('submitBugReport',
        body: {'title': title, 'details': details});
    return res['ok'] == true;
  }

  // ── ניהול ──────────────────────────────────────────────────────────────

  static Future<Map<String, dynamic>> adminDashboard() =>
      _call('adminDashboard', method: 'GET');

  static Future<Map<String, dynamic>> listChannelRequests() =>
      _call('listChannelRequests', method: 'GET');

  static Future<Map<String, dynamic>> resolveChannelRequest(
          String id, String status,
          {String category = '', String gender = ''}) =>
      _call('resolveChannelRequest', body: {
        'id': id,
        'status': status,
        if (category.isNotEmpty) 'category': category,
        if (gender.isNotEmpty) 'gender': gender,
      });

  static Future<Map<String, dynamic>> upsertApprovedChannel({
    required String youtubeChannelId,
    required String name,
    required String category,
    required String gender,
  }) =>
      _call('upsertApprovedChannel', body: {
        'youtubeChannelId': youtubeChannelId,
        'name': name,
        'category': category,
        'gender': gender,
      });

  static Future<Map<String, dynamic>> removeApprovedChannel(String id) =>
      _call('removeApprovedChannel', body: {'youtubeChannelId': id});

  // ── סנכרון ענן (Firestore REST) ────────────────────────────────────────

  static Future<Map<String, dynamic>?> _firestore(
    String path, {
    String method = 'GET',
    Map<String, dynamic>? fields,
    List<String>? updateMask,
  }) async {
    final token = await appAuth.idToken();
    if (token == null) return null;
    try {
      var uri = Uri.parse('${AppConfig.firestoreBase}/$path');
      if (updateMask != null) {
        uri = uri.replace(queryParameters: {
          for (final f in updateMask) 'updateMask.fieldPaths': f,
        });
      }
      final headers = {
        'Authorization': 'Bearer $token',
        'Content-Type': 'application/json',
      };
      final resp = method == 'GET'
          ? await _client.get(uri, headers: headers).timeout(const Duration(seconds: 20))
          : await _client
              .patch(uri,
                  headers: headers, body: jsonEncode({'fields': fields ?? {}}))
              .timeout(const Duration(seconds: 20));
      if (resp.statusCode >= 400) return null;
      return jsonDecode(utf8.decode(resp.bodyBytes)) as Map<String, dynamic>;
    } catch (_) {
      return null;
    }
  }

  // Firestore REST מקודד כל ערך עם שם הטיפוס שלו. שתי העזרות האלה הן כל
  // ההבדל בין קוד קריא לבין מפות מקוננות בכל קריאה.
  static Map<String, dynamic> _str(String v) => {'stringValue': v};
  static Map<String, dynamic> _int(int v) => {'integerValue': '$v'};
  static Map<String, dynamic> _bool(bool v) => {'booleanValue': v};
  static Map<String, dynamic> _arr(List<String> v) => {
        'arrayValue': {'values': v.map(_str).toList()}
      };

  static String _readStr(Map<String, dynamic>? fields, String key) =>
      (fields?[key] as Map<String, dynamic>?)?['stringValue'] as String? ?? '';

  static int _readInt(Map<String, dynamic>? fields, String key) {
    final raw = (fields?[key] as Map<String, dynamic>?)?['integerValue'];
    if (raw is int) return raw;
    return int.tryParse(raw as String? ?? '') ?? 0;
  }

  static bool _readBool(Map<String, dynamic>? fields, String key) =>
      (fields?[key] as Map<String, dynamic>?)?['booleanValue'] as bool? ?? false;

  static List<String> _readArr(Map<String, dynamic>? fields, String key) {
    final values = ((fields?[key] as Map<String, dynamic>?)?['arrayValue']
        as Map<String, dynamic>?)?['values'] as List?;
    return (values ?? const [])
        .map((e) => (e as Map<String, dynamic>)['stringValue'] as String? ?? '')
        .where((e) => e.isNotEmpty)
        .toList();
  }

  /// מעלה את הפרופיל ואת מצב הספרייה. מה שנשמר תואם בדיוק לכללי האבטחה
  /// של Firestore — כל שדה נוסף היה גורם לכתיבה כולה להידחות.
  static Future<bool> upload({String appVersion = ''}) async {
    if (!appAuth.ready) return false;
    final uid = appAuth.uid;
    final now = DateTime.now().millisecondsSinceEpoch;

    final user = await _firestore('users/$uid',
        method: 'PATCH',
        fields: {'email': _str(appAuth.email), 'updatedAt': _int(now)},
        updateMask: ['email', 'updatedAt']);
    if (user == null) return false;

    // המגדר חייב להיות male/female לפי הכללים; ריק פשוט לא מסונכרן.
    final gender = appSettings.userGender;
    if (gender == 'male' || gender == 'female') {
      await _firestore('users/$uid/profile/main', method: 'PATCH', fields: {
        'name': _str(appSettings.userName),
        'email': _str(appAuth.email),
        'gender': _str(gender),
        'filterLevel': _int(appSettings.filterLevel),
        'onboardingDone': _bool(appSettings.onboardingDone),
        'updatedAtMillis': _int(now),
        if (appVersion.isNotEmpty) 'appVersion': _str(appVersion),
      }, updateMask: [
        'name',
        'email',
        'gender',
        'filterLevel',
        'onboardingDone',
        'updatedAtMillis',
        if (appVersion.isNotEmpty) 'appVersion',
      ]);
    }

    await _firestore('users/$uid/library/state', method: 'PATCH', fields: {
      'schemaVersion': _int(2),
      'searchHistory': _arr(appSettings.searchHistory.take(20).toList()),
      'localSubscriptions':
          _arr(appLibrary.localSubscriptions.take(500).toList()),
      // הכללים דורשים שהמפתח יהיה קיים. הגרסה הזו אינה מסנכרנת מנויים
      // מחשבון יוטיוב — אין לה חיבור כזה — ולכן הוא נשלח ריק במקום להישמט.
      'youtubeSubscriptions': _arr(const []),
      'updatedAtMillis': _int(now),
    }, updateMask: [
      'schemaVersion',
      'searchHistory',
      'localSubscriptions',
      'youtubeSubscriptions',
      'updatedAtMillis',
    ]);

    await _uploadItems('likes', appLibrary.likes.take(300).toList(), now);
    await _uploadItems(
        'localHistory', appLibrary.localHistory.take(300).toList(), now);

    for (final playlist in appLibrary.playlists.take(50)) {
      await _firestore(
        'users/$uid/library/playlists/entries/${Uri.encodeComponent(playlist.name)}',
        method: 'PATCH',
        fields: {
          'name': _str(playlist.name),
          'videos': {
            'arrayValue': {
              'values': playlist.videos
                  .take(250)
                  .map((v) => {'mapValue': {'fields': _videoFields(v)}})
                  .toList(),
            }
          },
          'updatedAtMillis': _int(now),
        },
        updateMask: ['name', 'videos', 'updatedAtMillis'],
      );
    }
    return true;
  }

  static Map<String, dynamic> _videoFields(Video v) => {
        'id': _str(v.id),
        'title': _str(v.title),
        'channelName': _str(v.channelName),
        'channelId': _str(v.channelId),
        'thumbnailUrl': _str(v.thumbnailUrl),
        'publishedAt': _int(v.publishedAt),
        'isShort': _bool(v.isShort),
      };

  /// כתיבה מרוכזת. מסמך-לכל-סרטון דרך PATCH היה מאות בקשות רשת לסנכרון
  /// אחד — כלומר סנכרון שלוקח דקות ונופל באמצע. `:commit` כותב עד 200
  /// מסמכים בבקשה אחת, וזה ההבדל בין סנכרון שעובד לסנכרון שרק מתחיל.
  static Future<void> _uploadItems(String kind, List<Video> videos, int now) async {
    if (videos.isEmpty) return;
    final uid = appAuth.uid;
    const base =
        'projects/${AppConfig.firebaseProjectId}/databases/(default)/documents';
    for (var i = 0; i < videos.length; i += 100) {
      final end = (i + 100) < videos.length ? i + 100 : videos.length;
      final writes = videos.sublist(i, end).map((v) => {
            'update': {
              'name': '$base/users/$uid/library/items/entries/${kind}_${v.id}',
              'fields': {
                'kind': _str(kind),
                'video': {'mapValue': {'fields': _videoFields(v)}},
                'updatedAtMillis': _int(now),
              },
            },
            'updateMask': {
              'fieldPaths': ['kind', 'video', 'updatedAtMillis']
            },
          }).toList();
      await _commit(writes);
    }
  }

  static Future<bool> _commit(List<Map<String, dynamic>> writes) async {
    final token = await appAuth.idToken();
    if (token == null) return false;
    try {
      final uri = Uri.parse(
          'https://firestore.googleapis.com/v1/projects/${AppConfig.firebaseProjectId}'
          '/databases/(default)/documents:commit');
      final resp = await _client
          .post(uri,
              headers: {
                'Authorization': 'Bearer $token',
                'Content-Type': 'application/json',
              },
              body: jsonEncode({'writes': writes}))
          .timeout(const Duration(seconds: 30));
      return resp.statusCode < 400;
    } catch (_) {
      return false;
    }
  }

  /// מוריד את הפרופיל ומצב הספרייה מהענן אל המכשיר.
  static Future<bool> download() async {
    if (!appAuth.ready) return false;
    final uid = appAuth.uid;

    final profile = await _firestore('users/$uid/profile/main');
    if (profile != null) {
      final fields = profile['fields'] as Map<String, dynamic>?;
      final name = _readStr(fields, 'name');
      final gender = _readStr(fields, 'gender');
      final level = _readInt(fields, 'filterLevel');
      if (name.isNotEmpty) await appSettings.setUserName(name);
      if (gender.isNotEmpty) await appSettings.setUserGender(gender);
      if (level >= 1 && level <= 3) await appSettings.setFilterLevel(level);
      if (_readBool(fields, 'onboardingDone')) {
        await appSettings.setOnboardingDone(true);
      }
    }

    final state = await _firestore('users/$uid/library/state');
    if (state != null) {
      final fields = state['fields'] as Map<String, dynamic>?;
      final history = _readArr(fields, 'searchHistory');
      if (history.isNotEmpty) await appSettings.replaceSearchHistory(history);
      final subs = _readArr(fields, 'localSubscriptions');
      if (subs.isNotEmpty) await appLibrary.replaceLocalSubscriptions(subs);
    }
    return profile != null || state != null;
  }

  /// האם הסנכרון פתוח למשתמש הזה. הסנכרון הוא מה ש-Premium מוכר, ולכן
  /// השער יושב כאן — בנקודה אחת שכל מסלולי הסנכרון עוברים דרכה — ולא
  /// בכפתור כזה או אחר שאפשר לשכוח.
  static bool get syncAllowed => appAuth.ready && appBilling.premiumActive;

  /// סנכרון דו-כיווני: קודם מושכים (כדי לא לדרוס מכשיר אחר), ואז דוחפים.
  static Future<bool> synchronize({String appVersion = ''}) async {
    if (!syncAllowed) return false;
    final down = await download();
    final up = await upload(appVersion: appVersion);
    return down || up;
  }
}

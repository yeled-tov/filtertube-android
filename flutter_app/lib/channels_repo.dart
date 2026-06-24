import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'models.dart';

/// טוען את הרשימה הלבנה מ-channels.json שב-repo (אותו מקור כמו אפליקציית האנדרואיד),
/// כך ששינויים בפאנל הניהול מתפרסמים גם לגרסה הזו. שומר במטמון מקומי לטעינה מהירה/לא מקוון.
class ChannelsRepo {
  static const String _rawUrl =
      'https://raw.githubusercontent.com/yeled-tov/filtertube-android/main/channels.json';
  static const String _prefsKey = 'channels_json_cache';

  /// רמת הסינון שבה מציגים ערוצי "דתי לייט".
  static const int datiLightLevel = 3;
  static const Set<String> _audioOnlyCategories = {'dati_light'};

  List<Channel> _channels = [];
  Set<String> _approvedIds = {};

  List<Channel> get channels => _channels;

  /// האם הערוץ מאושר ברמת הסינון הנוכחית.
  bool isApproved(String channelId) => _approvedIds.contains(channelId);

  /// הקטגוריה של ערוץ (general אם לא ידוע).
  String categoryOf(String channelId) => _channels
      .firstWhere((c) => c.id == channelId,
          orElse: () => const Channel(id: '', name: '', category: 'general'))
      .category;

  /// האם להציג כאודיו-בלבד: "דתי לייט" תמיד, ומוזיקה ברמה מחמירה (1).
  bool isAudioOnly(String channelId, int level) {
    final cat = categoryOf(channelId);
    if (cat == 'dati_light') return true;
    if (level == 1 && cat == 'music') return true;
    return false;
  }

  /// טוען מהמטמון מיידית, ואז מרענן מהרשת ברקע.
  Future<void> load({int level = 2}) async {
    final prefs = await SharedPreferences.getInstance();
    final cached = prefs.getString(_prefsKey);
    if (cached != null) {
      _parse(cached, level);
    }
    try {
      // חותמת-זמן עוקפת את מטמון ה-CDN של GitHub raw (~5 דק') — כך עדכון ערוצים
      // מפאנל הניהול מופיע כאן כמעט מיד, בדיוק כמו באפליקציה הרגילה (אותו URL).
      final resp = await http
          .get(Uri.parse('$_rawUrl?t=${DateTime.now().millisecondsSinceEpoch}'))
          .timeout(const Duration(seconds: 15));
      if (resp.statusCode == 200) {
        await prefs.setString(_prefsKey, resp.body);
        _parse(resp.body, level);
      }
    } catch (_) {
      // נשארים עם המטמון אם הרשת נכשלה
    }
  }

  void _parse(String body, int level) {
    final list = (jsonDecode(body) as List)
        .map((e) => Channel.fromJson(e as Map<String, dynamic>))
        .toList();
    final filtered = level == datiLightLevel
        ? list
        : list.where((c) => !_audioOnlyCategories.contains(c.category)).toList();
    _channels = filtered;
    _approvedIds = filtered.map((c) => c.id).toSet();
  }
}

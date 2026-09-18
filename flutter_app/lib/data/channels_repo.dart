import 'dart:convert';

import 'package:flutter/services.dart' show rootBundle;
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

import '../config.dart';
import '../models/channel.dart';
import 'auth.dart';

/// הרשימה הלבנה — אותו מקור בדיוק כמו באפליקציה הראשית, כך שערוץ שאושר
/// בפאנל הניהול מופיע בשתי הגרסאות.
///
/// סדר המקורות: נקודת הקצה המאומתת (אם יש חשבון) → אחסון Firebase →
/// GitHub raw → מטמון מקומי → הקובץ שמצורף לאפליקציה. תמיד יש משהו להציג,
/// גם בהתקנה ראשונה בלי רשת.
class ChannelsRepo {
  ChannelsRepo({http.Client? client}) : _client = client ?? http.Client();

  final http.Client _client;

  static const String _prefsKey = 'channels_json_cache';
  static const Duration _ttl = Duration(minutes: 30);

  List<Channel> _all = [];
  DateTime? _lastFetch;

  /// כל הערוצים המאושרים, ללא סינון רמה.
  List<Channel> get all => List.unmodifiable(_all);

  /// הערוצים שמותרים ברמת הסינון ובמגדר הנוכחיים.
  List<Channel> visible(int level, String gender) => _all.forLevel(level, gender);

  Set<String> _approvedIds = {};
  Map<String, String> _categoryById = {};
  Map<String, String> _nameById = {};

  bool isApproved(String channelId) => _approvedIds.contains(channelId);
  String categoryOf(String channelId) => _categoryById[channelId] ?? 'general';
  String? nameOf(String channelId) => _nameById[channelId];

  /// האם להציג כאודיו בלבד ברמה הנתונה.
  bool isAudioOnly(String channelId, int level, {bool audioOnlyMode = false}) =>
      isAudioOnlyContent(_categoryById[channelId], level, audioOnlyMode);

  bool get isEmpty => _all.isEmpty;

  /// טוען מהמטמון מיידית (כדי שהמסך לא יחכה), ואז מרענן מהרשת.
  Future<void> load({bool force = false}) async {
    final prefs = await SharedPreferences.getInstance();
    if (_all.isEmpty) {
      final cached = prefs.getString(_prefsKey);
      if (cached != null) _parse(cached);
      if (_all.isEmpty) {
        try {
          _parse(await rootBundle.loadString(AppConfig.channelsAsset));
        } catch (_) {
          // הקובץ המצורף חסר — נמשיך לרשת
        }
      }
    }

    final fresh = _lastFetch != null &&
        DateTime.now().difference(_lastFetch!) < _ttl &&
        _all.isNotEmpty;
    if (fresh && !force) return;

    final body = await _fetchAuthorized() ??
        await _fetchJson(AppConfig.channelsHostingUrl) ??
        await _fetchJson(AppConfig.channelsGithubUrl);
    if (body != null) {
      await prefs.setString(_prefsKey, body);
      _parse(body);
      _lastFetch = DateTime.now();
    }
  }

  /// נקודת הקצה המאומתת מחזירה בדיוק את מה שפאנל הניהול רואה — כולל ערוץ
  /// שאושר לפני דקה ועוד לא הגיע ל-channels.json שבמאגר.
  Future<String?> _fetchAuthorized() async {
    final token = await appAuth.idToken();
    if (token == null) return null;
    try {
      final resp = await _client.get(
        Uri.parse('${AppConfig.functionsBase}/listApprovedChannels'),
        headers: {'Authorization': 'Bearer $token'},
      ).timeout(const Duration(seconds: 12));
      if (resp.statusCode != 200) return null;
      final data = jsonDecode(utf8.decode(resp.bodyBytes)) as Map<String, dynamic>;
      if (data['ok'] != true) return null;
      return jsonEncode(data['channels']);
    } catch (_) {
      return null;
    }
  }

  Future<String?> _fetchJson(String url) async {
    try {
      // חותמת-זמן עוקפת את מטמון ה-CDN (~5 דק') — כך עדכון ערוצים מפאנל
      // הניהול מופיע כאן כמעט מיד.
      final uri = Uri.parse(
          '$url?t=${DateTime.now().millisecondsSinceEpoch}');
      final resp = await _client.get(uri).timeout(const Duration(seconds: 15));
      if (resp.statusCode != 200) return null;
      return utf8.decode(resp.bodyBytes);
    } catch (_) {
      return null;
    }
  }

  void _parse(String body) {
    try {
      final decoded = jsonDecode(body);
      final list = decoded is List
          ? decoded
          : ((decoded as Map<String, dynamic>)['channels'] as List? ?? const []);
      final channels = list
          .map((e) => Channel.fromJson(e as Map<String, dynamic>))
          .where((c) => c.youtubeChannelId.isNotEmpty)
          .toList();
      if (channels.isEmpty) return;
      _all = channels;
      _approvedIds = channels.map((c) => c.youtubeChannelId).toSet();
      _categoryById = {for (final c in channels) c.youtubeChannelId: c.category};
      _nameById = {for (final c in channels) c.youtubeChannelId: c.name};
    } catch (_) {
      // רשימה פגומה — נשארים עם מה שכבר יש
    }
  }
}

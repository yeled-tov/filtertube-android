import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config.dart';
import '../models/channel.dart';
import '../models/video.dart';

/// לקוח ל-YouTube Data API v3 הרשמי — חיפוש, מטא-דאטה, שידורים חיים
/// והשלמות חיפוש. **אין כאן כתובות ניגון**: הניגון עובר תמיד דרך נגן
/// ה-IFrame הרשמי, וזה מה שמאפשר להפיץ את האפליקציה בחנויות.
class YoutubeApi {
  YoutubeApi({http.Client? client, String? apiKey})
      : _client = client ?? http.Client(),
        _key = apiKey ?? AppConfig.youtubeApiKey;

  final http.Client _client;
  final String _key;

  static const String _base = 'https://www.googleapis.com/youtube/v3';

  Uri _uri(String path, Map<String, String> params) =>
      Uri.parse('$_base/$path').replace(queryParameters: {...params, 'key': _key});

  Future<Map<String, dynamic>?> _get(Uri uri) async {
    try {
      final resp = await _client.get(uri).timeout(const Duration(seconds: 15));
      if (resp.statusCode != 200) return null;
      return jsonDecode(resp.body) as Map<String, dynamic>;
    } catch (_) {
      return null;
    }
  }

  static String _thumb(Map<String, dynamic>? thumbs, String videoId) {
    if (thumbs != null) {
      for (final q in ['maxres', 'standard', 'high', 'medium', 'default']) {
        final url = (thumbs[q] as Map<String, dynamic>?)?['url'] as String?;
        if (url != null) return url;
      }
    }
    return Video.thumbFor(videoId);
  }

  static int _date(String? s) =>
      s == null ? 0 : (DateTime.tryParse(s)?.millisecondsSinceEpoch ?? 0);

  /// אורך ISO-8601 (PT1M30S) לשניות. שידור חי מחזיר "P0D" בלי חלק זמן —
  /// אין לו אורך, והפונקציה מחזירה 0 כדי שלא ייחשב בטעות לסרטון קצר.
  static int isoDurationSeconds(String? raw) {
    if (raw == null) return 0;
    final m = RegExp(r'^P(?:(\d+)D)?T(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?$')
        .firstMatch(raw);
    if (m == null) return 0;
    int g(int i) => int.tryParse(m.group(i) ?? '') ?? 0;
    final seconds = (double.tryParse(m.group(4) ?? '') ?? 0).round();
    return g(1) * 86400 + g(2) * 3600 + g(3) * 60 + seconds;
  }

  // ── מטא-דאטה + סינון הטמעה ─────────────────────────────────────────────

  /// משלים אורך, צפיות ויכולת הטמעה ל-[videos], ומסלק סרטונים שאי אפשר
  /// להטמיע — אלה שהיו נכשלים בנגן עם "הסרטון אינו זמין" (שגיאה 152).
  ///
  /// בקשה אחת לכל 50 מזהים = יחידת מכסה אחת. אם הבדיקה נכשלה — **לא**
  /// מסננים: עדיף להציג מאשר להחזיר מסך ריק בגלל תקלת רשת.
  Future<List<Video>> enrich(List<Video> videos, {int limit = 200}) async {
    if (videos.isEmpty) return videos;
    final scope = videos.take(limit).toList();
    final byId = <String, Video>{};
    final blocked = <String>{};

    for (var i = 0; i < scope.length; i += 50) {
      final end = (i + 50) < scope.length ? i + 50 : scope.length;
      final batch = scope.sublist(i, end);
      final data = await _get(_uri('videos', {
        'part': 'contentDetails,statistics,status,snippet',
        'id': batch.map((v) => v.id).join(','),
      }));
      if (data == null) continue;
      for (final raw in (data['items'] as List?) ?? const []) {
        final it = raw as Map<String, dynamic>;
        final id = it['id'] as String?;
        if (id == null) continue;
        final status = it['status'] as Map<String, dynamic>?;
        if (status?['embeddable'] == false) {
          blocked.add(id);
          continue;
        }
        final snippet = it['snippet'] as Map<String, dynamic>?;
        final details = it['contentDetails'] as Map<String, dynamic>?;
        final stats = it['statistics'] as Map<String, dynamic>?;
        final original = batch.firstWhere((v) => v.id == id,
            orElse: () => Video(
                id: id,
                title: '',
                channelName: '',
                channelId: '',
                thumbnailUrl: Video.thumbFor(id)));
        byId[id] = original.copyWith(
          title: (snippet?['title'] as String?)?.trim().isNotEmpty == true
              ? snippet!['title'] as String
              : original.title,
          channelName: (snippet?['channelTitle'] as String?) ?? original.channelName,
          channelId: (snippet?['channelId'] as String?) ?? original.channelId,
          thumbnailUrl:
              _thumb(snippet?['thumbnails'] as Map<String, dynamic>?, id),
          publishedAt: original.publishedAt > 0
              ? original.publishedAt
              : _date(snippet?['publishedAt'] as String?),
          durationSec: isoDurationSeconds(details?['duration'] as String?),
          viewCount: int.tryParse((stats?['viewCount'] as String?) ?? '') ??
              original.viewCount,
        );
      }
    }

    return videos
        .where((v) => !blocked.contains(v.id))
        .map((v) => byId[v.id] ?? v)
        .toList();
  }

  /// משאיר רק סרטונים קצרים שניתנים להטמעה — הפיד של השורטס.
  ///
  /// בניגוד ל-[enrich], כאן **נכשלים סגור**: בלי אורך אי אפשר לדעת שהסרטון
  /// קצר, וסרטון ארוך בפיד השורטס גרוע מפיד קצר יותר.
  Future<List<Video>> filterShorts(
    List<Video> videos, {
    Duration maxLength = const Duration(minutes: 3),
  }) async {
    if (videos.isEmpty) return videos;
    final keep = <String, Video>{};
    for (var i = 0; i < videos.length; i += 50) {
      final end = (i + 50) < videos.length ? i + 50 : videos.length;
      final batch = videos.sublist(i, end);
      final data = await _get(_uri('videos', {
        'part': 'status,contentDetails,statistics',
        'id': batch.map((v) => v.id).join(','),
      }));
      if (data == null) continue;
      for (final raw in (data['items'] as List?) ?? const []) {
        final it = raw as Map<String, dynamic>;
        final id = it['id'] as String?;
        if (id == null) continue;
        if ((it['status'] as Map<String, dynamic>?)?['embeddable'] != true) continue;
        final seconds = isoDurationSeconds(
            (it['contentDetails'] as Map<String, dynamic>?)?['duration'] as String?);
        if (seconds <= 0 || seconds > maxLength.inSeconds) continue;
        final original = batch.firstWhere((v) => v.id == id);
        keep[id] = original.copyWith(
          durationSec: seconds,
          isShort: true,
          viewCount: int.tryParse(((it['statistics'] as Map<String, dynamic>?)?[
                      'viewCount'] as String?) ??
                  '') ??
              original.viewCount,
        );
      }
    }
    return videos.where((v) => keep.containsKey(v.id)).map((v) => keep[v.id]!).toList();
  }

  // ── חיפוש ──────────────────────────────────────────────────────────────

  /// חיפוש גלובלי, מסונן לרשימה הלבנה.
  Future<List<Video>> search(
    String query,
    bool Function(String channelId) approved, {
    int max = 40,
  }) async {
    final data = await _get(_uri('search', {
      'part': 'snippet',
      'type': 'video',
      'maxResults': '$max',
      'q': query,
      'relevanceLanguage': 'he',
    }));
    return _videosFromSearch(data, approved);
  }

  /// חיפוש בתוך ערוץ אחד — מה שמאפשר "חפש בערוץ" בלי לצאת מהרשימה הלבנה.
  Future<List<Video>> searchInChannel(String channelId, String query,
      {int max = 25}) async {
    final data = await _get(_uri('search', {
      'part': 'snippet',
      'type': 'video',
      'channelId': channelId,
      'maxResults': '$max',
      'q': query,
      'order': 'relevance',
    }));
    return _videosFromSearch(data, (_) => true);
  }

  /// שידורים חיים פעילים בערוצים המאושרים.
  Future<List<Video>> liveNow(List<Channel> channels, {int perQuery = 25}) async {
    final approved = channels.map((c) => c.youtubeChannelId).toSet();
    final out = <Video>[];
    // חיפוש אחד לכל ערוץ היה יקר מדי במכסה; במקום זאת שואלים את יוטיוב על
    // שידורים חיים בעברית ומסננים לרשימה הלבנה, ואז משלימים מהערוצים
    // שהמשתמש עוקב אחריהם בפועל.
    final data = await _get(_uri('search', {
      'part': 'snippet',
      'type': 'video',
      'eventType': 'live',
      'maxResults': '$perQuery',
      'q': 'שידור חי',
      'relevanceLanguage': 'he',
    }));
    out.addAll(await _videosFromSearch(data, approved.contains));
    return out;
  }

  Future<List<Video>> channelLive(String channelId) async {
    final data = await _get(_uri('search', {
      'part': 'snippet',
      'type': 'video',
      'eventType': 'live',
      'channelId': channelId,
      'maxResults': '5',
    }));
    return _videosFromSearch(data, (_) => true);
  }

  Future<List<Video>> _videosFromSearch(
      Map<String, dynamic>? data, bool Function(String) approved) async {
    if (data == null) return const [];
    final out = <Video>[];
    for (final raw in (data['items'] as List?) ?? const []) {
      final it = raw as Map<String, dynamic>;
      final s = it['snippet'] as Map<String, dynamic>?;
      final vid = (it['id'] as Map<String, dynamic>?)?['videoId'] as String?;
      if (s == null || vid == null) continue;
      final chId = (s['channelId'] as String?) ?? '';
      if (!approved(chId)) continue; // סינון לרשימה הלבנה
      out.add(Video(
        id: vid,
        title: _unescape((s['title'] as String?) ?? ''),
        channelName: _unescape((s['channelTitle'] as String?) ?? ''),
        channelId: chId,
        thumbnailUrl: _thumb(s['thumbnails'] as Map<String, dynamic>?, vid),
        publishedAt: _date(s['publishedAt'] as String?),
      ));
    }
    return out;
  }

  /// יוטיוב מחזירה כותרות עם ישויות HTML (&amp;quot;). בלי הפענוח הזה הן
  /// מופיעות כך גם על המסך.
  static String _unescape(String input) => input
      .replaceAll('&amp;', '&')
      .replaceAll('&quot;', '"')
      .replaceAll('&#39;', "'")
      .replaceAll('&lt;', '<')
      .replaceAll('&gt;', '>');

  // ── ערוצים ─────────────────────────────────────────────────────────────

  /// סרטונים אחרונים מערוץ דרך פלייליסט ההעלאות (יחידת מכסה אחת).
  Future<List<Video>> channelUploads(Channel channel, {int max = 15}) async {
    final data = await _get(_uri('playlistItems', {
      'part': 'snippet',
      'maxResults': '$max',
      'playlistId': channel.uploadsPlaylistId,
    }));
    if (data == null) return const [];
    final out = <Video>[];
    for (final raw in (data['items'] as List?) ?? const []) {
      final s = (raw as Map<String, dynamic>)['snippet'] as Map<String, dynamic>?;
      if (s == null) continue;
      final vid =
          (s['resourceId'] as Map<String, dynamic>?)?['videoId'] as String?;
      if (vid == null) continue;
      out.add(Video(
        id: vid,
        title: _unescape((s['title'] as String?) ?? ''),
        channelName: (s['videoOwnerChannelTitle'] as String?) ?? channel.name,
        channelId:
            (s['videoOwnerChannelId'] as String?) ?? channel.youtubeChannelId,
        thumbnailUrl: _thumb(s['thumbnails'] as Map<String, dynamic>?, vid),
        publishedAt: _date(s['publishedAt'] as String?),
      ));
    }
    return out;
  }

  /// סמלי הערוצים — נטענים פעם אחת ונשמרים במטמון בזיכרון.
  final Map<String, String> _avatars = {};

  String? avatarOf(String channelId) => _avatars[channelId];

  Future<void> warmAvatars(List<String> channelIds) async {
    final missing = channelIds
        .where((id) => id.isNotEmpty && !_avatars.containsKey(id))
        .toSet()
        .toList();
    if (missing.isEmpty) return;
    for (var i = 0; i < missing.length && i < 150; i += 50) {
      final end = (i + 50) < missing.length ? i + 50 : missing.length;
      final data = await _get(_uri('channels', {
        'part': 'snippet',
        'id': missing.sublist(i, end).join(','),
      }));
      if (data == null) continue;
      for (final raw in (data['items'] as List?) ?? const []) {
        final it = raw as Map<String, dynamic>;
        final id = it['id'] as String?;
        final url = ((it['snippet'] as Map<String, dynamic>?)?['thumbnails']
                as Map<String, dynamic>?)?['default']?['url'] as String?;
        if (id != null && url != null) _avatars[id] = url;
      }
    }
  }

  /// מאתר ערוץ לפי שם — משמש בטופס "בקשת ערוץ" כדי למלא את הקישור מעצמו.
  Future<Channel?> findChannelByName(String name) async {
    final data = await _get(_uri('search', {
      'part': 'snippet',
      'type': 'channel',
      'maxResults': '1',
      'q': name,
    }));
    if (data == null) return null;
    final items = (data['items'] as List?) ?? const [];
    if (items.isEmpty) return null;
    final it = items.first as Map<String, dynamic>;
    final s = it['snippet'] as Map<String, dynamic>?;
    final id = (it['id'] as Map<String, dynamic>?)?['channelId'] as String?;
    if (id == null || s == null) return null;
    return Channel(
      youtubeChannelId: id,
      name: _unescape((s['title'] as String?) ?? name),
    );
  }

  /// מזהה הערוץ של סרטון — נדרש כשקישור חיצוני נפתח באפליקציה ויש לבדוק
  /// אם הערוץ מאושר לפני שמנגנים.
  Future<Video?> videoById(String videoId) async {
    final data = await _get(_uri('videos', {
      'part': 'snippet,contentDetails,statistics,status',
      'id': videoId,
    }));
    if (data == null) return null;
    final items = (data['items'] as List?) ?? const [];
    if (items.isEmpty) return null;
    final it = items.first as Map<String, dynamic>;
    final s = it['snippet'] as Map<String, dynamic>?;
    if (s == null) return null;
    return Video(
      id: videoId,
      title: _unescape((s['title'] as String?) ?? ''),
      channelName: _unescape((s['channelTitle'] as String?) ?? ''),
      channelId: (s['channelId'] as String?) ?? '',
      thumbnailUrl: _thumb(s['thumbnails'] as Map<String, dynamic>?, videoId),
      publishedAt: _date(s['publishedAt'] as String?),
      durationSec: isoDurationSeconds(
          (it['contentDetails'] as Map<String, dynamic>?)?['duration'] as String?),
      viewCount: int.tryParse(((it['statistics'] as Map<String, dynamic>?)?[
                  'viewCount'] as String?) ??
              '') ??
          0,
    );
  }
}

/// השלמות חיפוש — נקודת הקצה הציבורית של יוטיוב, בלי מפתח ובלי מכסה.
class YoutubeSuggest {
  static final http.Client _client = http.Client();

  static Future<List<String>> suggest(String query) async {
    final q = query.trim();
    if (q.isEmpty) return const [];
    final uri = Uri.parse('https://suggestqueries.google.com/complete/search')
        .replace(queryParameters: {
      'client': 'firefox',
      'ds': 'yt',
      'hl': 'he',
      'q': q,
    });
    try {
      final resp = await _client.get(uri).timeout(const Duration(seconds: 6));
      if (resp.statusCode != 200) return const [];
      final decoded = jsonDecode(utf8.decode(resp.bodyBytes)) as List;
      if (decoded.length < 2) return const [];
      return (decoded[1] as List).cast<String>().take(8).toList();
    } catch (_) {
      return const [];
    }
  }
}

import 'dart:convert';
import 'package:http/http.dart' as http;
import 'models.dart';

/// לקוח ל-YouTube Data API v3 הרשמי (חיפוש + מטא-דאטה בלבד — אין כתובות ניגון;
/// הניגון נעשה דרך נגן ה-IFrame). הכל מסונן לרשימה הלבנה לפני שמוצג.
///
/// אבטחה: לפני פרסום בחנות יש להגביל את המפתח ב-Google Cloud Console
/// (חתימת אפליקציה / חבילה), כי הוא גלוי בתוך ה-APK.
class YoutubeApi {
  YoutubeApi(this.apiKey);

  final String apiKey;
  static const String _base = 'https://www.googleapis.com/youtube/v3';

  String _thumb(Map<String, dynamic>? thumbs, String videoId) {
    if (thumbs != null) {
      for (final q in ['high', 'medium', 'default']) {
        final url = (thumbs[q] as Map<String, dynamic>?)?['url'] as String?;
        if (url != null) return url;
      }
    }
    return 'https://i.ytimg.com/vi/$videoId/hqdefault.jpg';
  }

  DateTime? _date(String? s) => s == null ? null : DateTime.tryParse(s);

  /// מסנן לרשימה שמכילה רק סרטונים שמותר להטמיע (status.embeddable) — דרך
  /// videos.list הרשמי (1 יחידת מכסה לכל 50 מזהים). כך המשתמש רואה רק סרטונים
  /// שיתנגנו בנגן ה-IFrame, בלי להיתקל ב"סרטון אינו זמין" (קוד 152).
  /// אם הבדיקה נכשלת — לא מסננים (עדיף להציג מאשר להחזיר ריק).
  Future<List<Video>> filterEmbeddable(List<Video> videos) async {
    if (videos.isEmpty) return videos;
    final embeddable = <String>{};
    for (var i = 0; i < videos.length; i += 50) {
      final end = (i + 50) < videos.length ? i + 50 : videos.length;
      final batch = videos.sublist(i, end);
      final ids = batch.map((v) => v.id).join(',');
      final uri = Uri.parse('$_base/videos').replace(queryParameters: {
        'part': 'status',
        'id': ids,
        'key': apiKey,
      });
      try {
        final resp = await http.get(uri).timeout(const Duration(seconds: 15));
        if (resp.statusCode != 200) {
          embeddable.addAll(batch.map((v) => v.id));
          continue;
        }
        final items = (jsonDecode(resp.body)['items'] as List?) ?? [];
        for (final it in items) {
          final id = it['id'] as String?;
          final emb = (it['status'] as Map<String, dynamic>?)?['embeddable'] as bool?;
          if (id != null && emb == true) embeddable.add(id);
        }
      } catch (_) {
        embeddable.addAll(batch.map((v) => v.id));
      }
    }
    return videos.where((v) => embeddable.contains(v.id)).toList();
  }

  /// סרטונים אחרונים מערוץ (דרך פלייליסט ההעלאות — זול במכסה).
  Future<List<Video>> channelUploads(Channel channel, {int max = 15}) async {
    final uri = Uri.parse('$_base/playlistItems').replace(queryParameters: {
      'part': 'snippet',
      'maxResults': '$max',
      'playlistId': channel.uploadsPlaylistId,
      'key': apiKey,
    });
    final resp = await http.get(uri).timeout(const Duration(seconds: 15));
    if (resp.statusCode != 200) return [];
    final items = (jsonDecode(resp.body)['items'] as List?) ?? [];
    final out = <Video>[];
    for (final it in items) {
      final s = it['snippet'] as Map<String, dynamic>?;
      if (s == null) continue;
      final vid = (s['resourceId'] as Map<String, dynamic>?)?['videoId'] as String?;
      if (vid == null) continue;
      out.add(Video(
        id: vid,
        title: (s['title'] as String?) ?? '',
        channelTitle: (s['videoOwnerChannelTitle'] as String?) ?? channel.name,
        channelId: (s['videoOwnerChannelId'] as String?) ?? channel.id,
        thumbnail: _thumb(s['thumbnails'] as Map<String, dynamic>?, vid),
        publishedAt: _date(s['publishedAt'] as String?),
      ));
    }
    return out;
  }

  /// חיפוש גלובלי, מסונן לרשימה הלבנה.
  Future<List<Video>> search(String query, bool Function(String channelId) approved,
      {int max = 30}) async {
    final uri = Uri.parse('$_base/search').replace(queryParameters: {
      'part': 'snippet',
      'type': 'video',
      'maxResults': '$max',
      'q': query,
      'key': apiKey,
    });
    final resp = await http.get(uri).timeout(const Duration(seconds: 15));
    if (resp.statusCode != 200) return [];
    final items = (jsonDecode(resp.body)['items'] as List?) ?? [];
    final out = <Video>[];
    for (final it in items) {
      final s = it['snippet'] as Map<String, dynamic>?;
      final vid = (it['id'] as Map<String, dynamic>?)?['videoId'] as String?;
      if (s == null || vid == null) continue;
      final chId = (s['channelId'] as String?) ?? '';
      if (!approved(chId)) continue; // סינון לרשימה הלבנה
      out.add(Video(
        id: vid,
        title: (s['title'] as String?) ?? '',
        channelTitle: (s['channelTitle'] as String?) ?? '',
        channelId: chId,
        thumbnail: _thumb(s['thumbnails'] as Map<String, dynamic>?, vid),
        publishedAt: _date(s['publishedAt'] as String?),
      ));
    }
    return out;
  }
}

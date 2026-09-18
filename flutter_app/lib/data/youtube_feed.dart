import 'dart:async';

import 'package:http/http.dart' as http;
import 'package:xml/xml.dart';

import '../models/channel.dart';
import '../models/video.dart';

/// הפיד של הערוצים המאושרים — דרך ה-RSS הציבורי של יוטיוב.
///
/// ## למה RSS ולא playlistItems
/// הרשימה הלבנה מונה מאות ערוצים, ורענון אחד דרך ה-Data API היה עולה יחידת
/// מכסה לכל ערוץ — כלומר מכסת היום כולה נשרפת תוך כמה עשרות רענונים, על פני
/// *כל* המשתמשים יחד. ה-RSS הוא מקור רשמי ופומבי של יוטיוב, ללא מכסה, וזה
/// בדיוק מה שהאפליקציה הראשית משתמשת בו — כך ששתי הגרסאות מציגות את אותו
/// פיד. ה-Data API נשמר לחיפוש ולהעשרת מטא-דאטה, שם הוא באמת נחוץ.
class YoutubeFeed {
  YoutubeFeed({http.Client? client}) : _client = client ?? http.Client();

  final http.Client _client;

  static const String _ua =
      'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) '
      'Chrome/130.0.0.0 Mobile Safari/537.36';

  /// כמה ערוצים נמשכים במקביל. יותר מזה והמכשיר פותח עשרות חיבורים בבת אחת,
  /// מה שדווקא מאט את הטעינה הראשונה.
  static const int _concurrency = 6;

  Future<List<Video>> channelFeed(Channel channel) async {
    if (!channel.youtubeChannelId.startsWith('UC')) return const [];
    final uri = Uri.parse(
        'https://www.youtube.com/feeds/videos.xml?channel_id=${channel.youtubeChannelId}');
    for (var attempt = 0; attempt < 2; attempt++) {
      try {
        final resp = await _client
            .get(uri, headers: const {'User-Agent': _ua})
            .timeout(const Duration(seconds: 15));
        if (resp.statusCode == 200) return _parse(resp.body, channel);
        if (resp.statusCode == 404) return const []; // ערוץ שנמחק
      } catch (_) {
        // ניסיון שני אחרי המתנה קצרה; אחריו מוותרים על הערוץ הזה בלבד
      }
      if (attempt == 0) {
        await Future<void>.delayed(const Duration(milliseconds: 500));
      }
    }
    return const [];
  }

  /// מושך את כל הערוצים ומחזיר פיד ממוין מהחדש לישן, בלי שורטס.
  Future<List<Video>> allChannelsFeed(List<Channel> channels) async {
    final targets =
        channels.where((c) => c.youtubeChannelId.startsWith('UC')).toList();
    if (targets.isEmpty) return const [];

    final out = <Video>[];
    for (var i = 0; i < targets.length; i += _concurrency) {
      final end =
          (i + _concurrency) < targets.length ? i + _concurrency : targets.length;
      final batch = await Future.wait(
        targets.sublist(i, end).map(channelFeed),
      );
      for (final list in batch) {
        out.addAll(list);
      }
    }
    out.sort((a, b) => b.publishedAt.compareTo(a.publishedAt));
    return out;
  }

  List<Video> _parse(String xml, Channel channel) {
    final out = <Video>[];
    try {
      final doc = XmlDocument.parse(xml);
      for (final entry in doc.findAllElements('entry')) {
        final id = entry.getElement('yt:videoId')?.innerText.trim() ??
            entry.getElement('videoId')?.innerText.trim() ??
            '';
        if (id.isEmpty) continue;
        final title = entry.getElement('title')?.innerText.trim() ?? '';
        final published = entry.getElement('published')?.innerText.trim() ?? '';
        final group = entry.getElement('media:group');
        final thumb = group
                ?.getElement('media:thumbnail')
                ?.getAttribute('url') ??
            Video.thumbFor(id);
        final views = int.tryParse(group
                    ?.getElement('media:community')
                    ?.getElement('media:statistics')
                    ?.getAttribute('views') ??
                '') ??
            0;
        final link = entry.getElement('link')?.getAttribute('href') ?? '';
        out.add(Video(
          id: id,
          title: title,
          channelName: entry.getElement('author')?.getElement('name')?.innerText.trim() ??
              channel.name,
          channelId: entry.getElement('yt:channelId')?.innerText.trim().isNotEmpty == true
              ? entry.getElement('yt:channelId')!.innerText.trim()
              : channel.youtubeChannelId,
          thumbnailUrl: thumb,
          publishedAt: DateTime.tryParse(published)?.millisecondsSinceEpoch ?? 0,
          isShort: link.contains('/shorts/'),
          viewCount: views,
        ));
      }
    } catch (_) {
      // XML פגום מערוץ אחד לא אמור להפיל את כל הפיד
      return const [];
    }
    return out;
  }
}

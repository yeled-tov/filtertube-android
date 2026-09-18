import 'dart:math';

import '../models/channel.dart';
import '../models/video.dart';
import 'youtube_api.dart';

class SearchOutcome {
  final List<Video> videos;
  final bool failed;
  const SearchOutcome(this.videos, {this.failed = false});
}

/// חיפוש בערוצים המאושרים בלבד.
///
/// ## למה שני מסלולים
/// חיפוש גלובלי אחד מחזיר תוצאות מכל יוטיוב, ואחרי סינון לרשימה הלבנה
/// לרוב נשארות מעט מדי. לכן כשמזוהה ערוץ מאושר שמתאים לשאילתה, שואלים
/// אותו ישירות — שם התוצאות תמיד מאושרות מראש. שני המסלולים רצים יחד,
/// והמסך מתעדכן מהתוצאה הראשונה שחוזרת.
class SearchEngine {
  SearchEngine(this.api);

  final YoutubeApi api;

  /// שמות ערוצים שמתאימים לשאילתה — ההשלמות ה"מקומיות" בשדה החיפוש.
  static List<String> channelSuggestions(List<Channel> channels, String query) {
    final q = query.trim().toLowerCase();
    if (q.isEmpty) return const [];
    return channels
        .where((c) => c.name.toLowerCase().contains(q))
        .map((c) => c.name)
        .take(4)
        .toList();
  }

  static List<Channel> matchingChannels(List<Channel> channels, String query) {
    final q = query.trim().toLowerCase();
    if (q.isEmpty) return const [];
    return channels.where((c) => c.name.toLowerCase().contains(q)).toList();
  }

  Future<SearchOutcome> search(
    String query,
    List<Channel> approved, {
    void Function(List<Video> partial)? onPartial,
  }) async {
    final trimmed = query.trim();
    if (trimmed.isEmpty) return const SearchOutcome([]);
    final approvedIds = approved.map((c) => c.youtubeChannelId).toSet();

    final results = <String, Video>{};
    var anySuccess = false;

    void merge(List<Video> list) {
      for (final v in list) {
        if (!approvedIds.contains(v.channelId)) continue;
        results.putIfAbsent(v.id, () => v);
      }
      onPartial?.call(results.values.toList());
    }

    // ערוצים ששמם תואם — עד שלושה, כדי לא לשרוף מכסה על שאילתה רחבה.
    final direct = matchingChannels(approved, trimmed).take(3).toList();
    final futures = <Future<List<Video>>>[
      api.search(trimmed, approvedIds.contains),
      ...direct.map((c) => api.channelUploads(c, max: 25)),
    ];

    for (final future in futures) {
      try {
        final list = await future;
        anySuccess = true;
        merge(list);
      } catch (_) {
        // מסלול אחד שנכשל לא אמור לבטל את השני
      }
    }

    if (!anySuccess) return const SearchOutcome([], failed: true);

    // דירוג פשוט: התאמת הכותרת קודמת, ואחריה טריות.
    final q = trimmed.toLowerCase();
    final list = results.values.toList()
      ..sort((a, b) {
        final sa = _relevance(a, q);
        final sb = _relevance(b, q);
        if (sa != sb) return sb.compareTo(sa);
        return b.publishedAt.compareTo(a.publishedAt);
      });
    return SearchOutcome(list);
  }

  static int _relevance(Video v, String q) {
    final title = v.title.toLowerCase();
    if (title == q) return 3;
    if (title.startsWith(q)) return 2;
    if (title.contains(q)) return 1;
    if (v.channelName.toLowerCase().contains(q)) return 1;
    return 0;
  }
}

/// ערבוב עם זרע קבוע לכל יום — כך "בחירה מהירה" לא קופצת בכל פתיחת מסך,
/// אבל כן מתחדשת מיום ליום.
List<T> shuffledStable<T>(List<T> items, [int? seed]) {
  final copy = List<T>.from(items);
  final day = DateTime.now();
  copy.shuffle(Random(seed ?? (day.year * 10000 + day.month * 100 + day.day)));
  return copy;
}

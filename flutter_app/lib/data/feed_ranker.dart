import 'dart:math';

import '../models/video.dart';

/// פרופיל הטעם של המשתמש. הכל מקומי: לייקים, היסטוריה, מנויים וחיפושים
/// כבר במכשיר, ולכן הדירוג עובד גם בלי רשת ולא עולה ולו בקשה אחת.
class TasteProfile {
  final Map<String, double> channelAffinity;
  final Map<String, double> categoryAffinity;
  final List<String> terms;

  const TasteProfile(this.channelAffinity, this.categoryAffinity, this.terms);

  bool get isEmpty => channelAffinity.isEmpty && terms.isEmpty;

  static const double _likeWeight = 3.0;
  static const double _subscribeWeight = 2.0;
  static const double _watchWeight = 1.0;
  static const double _watchHalfLifeDays = 21.0;

  static TasteProfile build({
    required List<Video> likes,
    required List<Video> history,
    required List<String> subscriptions,
    required List<String> searchTerms,
    required String? Function(String) categoryOf,
    int? now,
  }) {
    final at = now ?? DateTime.now().millisecondsSinceEpoch;
    final raw = <String, double>{};
    void add(String channelId, double weight) {
      if (channelId.isEmpty) return;
      raw[channelId] = (raw[channelId] ?? 0) + weight;
    }

    for (final v in likes) {
      add(v.channelId, _likeWeight);
    }
    for (final id in subscriptions) {
      add(id, _subscribeWeight);
    }
    for (final v in history) {
      final ageDays = max(0, at - v.watchedAt) / 86400000.0;
      add(v.channelId, _watchWeight * pow(0.5, ageDays / _watchHalfLifeDays));
    }

    final maxValue = raw.values.isEmpty ? 0.0 : raw.values.reduce(max);
    final channels = maxValue <= 0
        ? <String, double>{}
        : raw.map((k, v) => MapEntry(k, v / maxValue));

    final catRaw = <String, double>{};
    channels.forEach((id, score) {
      final cat = categoryOf(id);
      if (cat == null) return;
      catRaw[cat] = (catRaw[cat] ?? 0) + score;
    });
    final catMax = catRaw.values.isEmpty ? 0.0 : catRaw.values.reduce(max);
    final categories = catMax <= 0
        ? <String, double>{}
        : catRaw.map((k, v) => MapEntry(k, v / catMax));

    final terms = searchTerms
        .expand((t) => t.trim().toLowerCase().split(' '))
        .where((t) => t.length > 2)
        .toSet()
        .take(20)
        .toList();

    return TasteProfile(channels, categories, terms);
  }
}

/// דירוג הפיד — מקביל 1:1 ל-`FeedRanker` באפליקציה הראשית, כולל המשקלים
/// והפיזור בין ערוצים.
class FeedRanker {
  FeedRanker._();

  static const double _wAffinity = 0.45;
  static const double _wRecency = 0.33;
  static const double _wCategory = 0.12;
  static const double _wTerms = 0.10;
  static const double _recencyHalfLifeDays = 6.0;

  /// עונש על ערוץ שכבר הופיע — בלעדיו ערוץ פורה אחד משתלט על כל המסך.
  static const double _channelDecay = 0.45;

  /// סרטון שכבר נצפה יורד, אבל לא נעלם: לפעמים רוצים לחזור אליו.
  static const double _watchedFactor = 0.35;

  static List<Video> rank({
    required List<Video> videos,
    required TasteProfile profile,
    required String? Function(String) categoryOf,
    Set<String> watchedIds = const {},
    int? now,
  }) {
    if (videos.isEmpty) return videos;
    final at = now ?? DateTime.now().millisecondsSinceEpoch;
    final scored = videos
        .map((v) => _Scored(v, _score(v, profile, categoryOf, watchedIds, at)))
        .toList()
      ..sort((a, b) => b.score.compareTo(a.score));
    return _diversify(scored);
  }

  static double _score(Video video, TasteProfile profile,
      String? Function(String) categoryOf, Set<String> watchedIds, int now) {
    final ageDays = video.publishedAt <= 0
        ? 30.0
        : max(0, now - video.publishedAt) / 86400000.0;
    final recency = pow(0.5, ageDays / _recencyHalfLifeDays).toDouble();

    final affinity = profile.channelAffinity[video.channelId] ?? 0.0;
    final cat = categoryOf(video.channelId);
    final category = cat == null ? 0.0 : (profile.categoryAffinity[cat] ?? 0.0);

    final haystack = '${video.title} ${video.channelName}'.toLowerCase();
    final termHit = profile.terms.any(haystack.contains) ? 1.0 : 0.0;

    var total = _wAffinity * affinity +
        _wRecency * recency +
        _wCategory * category +
        _wTerms * termHit;

    if (watchedIds.contains(video.id)) total *= _watchedFactor;

    // רעש זעיר וקבוע לכל מזהה — מונע מסרטונים עם ציון זהה להופיע תמיד
    // באותו סדר, בלי להפוך את הפיד לאקראי.
    total += (video.id.hashCode.abs() % 1000) / 1000.0 * 0.02;
    return total;
  }

  static List<Video> _diversify(List<_Scored> scored) {
    final remaining = List<_Scored>.from(scored);
    final taken = <String, int>{};
    final out = <Video>[];

    while (remaining.isNotEmpty) {
      var bestIndex = 0;
      var bestValue = double.negativeInfinity;
      final window = min(remaining.length, 80);
      for (var i = 0; i < window; i++) {
        final entry = remaining[i];
        final penalty =
            pow(_channelDecay, taken[entry.video.channelId] ?? 0).toDouble();
        final value = entry.score * penalty;
        if (value > bestValue) {
          bestValue = value;
          bestIndex = i;
        }
      }
      final picked = remaining.removeAt(bestIndex);
      taken[picked.video.channelId] = (taken[picked.video.channelId] ?? 0) + 1;
      out.add(picked.video);
    }
    return out;
  }
}

class _Scored {
  final Video video;
  final double score;
  const _Scored(this.video, this.score);
}

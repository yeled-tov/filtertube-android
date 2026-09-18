import '../models/channel.dart';
import '../models/video.dart';
import 'feed_ranker.dart';
import 'search_engine.dart';

/// מיקס מוכן להשמעה — כותרת, תת-כותרת ורשימת שירים.
class Mix {
  final String id;
  final String title;
  final String subtitle;
  final List<Video> songs;
  final String? seedChannel;

  const Mix({
    required this.id,
    required this.title,
    required this.subtitle,
    required this.songs,
    this.seedChannel,
  });

  String? get artwork => songs.isEmpty ? null : songs.first.thumbnailUrl;
}

/// בונה את המיקסים של FilterMusic ממה שהמשתמש באמת שומע.
///
/// הכל מקומי ובלי אף בקשת רשת: המיקס נגזר מהפיד שכבר נטען, מהלייקים
/// ומההיסטוריה. מיקס שמתבסס על חיפוש היה גם איטי וגם יקר במכסה, והוא
/// ממילא לא היה מדויק יותר.
class MixBuilder {
  MixBuilder._();

  static const int _minSongs = 6;
  static const int _maxSongs = 40;
  static const int _maxArtistMixes = 4;
  static const int _coreArtists = 6;

  static List<Mix> build({
    required List<Video> pool,
    required List<Video> likes,
    required List<Video> history,
    required TasteProfile profile,
    required String? Function(String) categoryOf,
    required String? Function(String) channelName,
  }) {
    final all = [...pool, ...likes, ...history]
        .where((v) => v.id.isNotEmpty)
        .fold<Map<String, Video>>({}, (acc, v) {
          acc.putIfAbsent(v.id, () => v);
          return acc;
        })
        .values
        .where((v) => kMusicCategories.contains(categoryOf(v.channelId)))
        .toList();
    if (all.isEmpty) return const [];

    final mixes = <Mix>[];
    final heard = {...likes.map((v) => v.id), ...history.map((v) => v.id)};

    // ── המיקס היומי ────────────────────────────────────────────────────
    // הערוצים שהמשתמש הכי קשור אליהם, בערבוב יומי קבוע — אותו מיקס לאורך
    // היום, חדש למחרת.
    final core = (profile.channelAffinity.entries.toList()
          ..sort((a, b) => b.value.compareTo(a.value)))
        .take(_coreArtists)
        .map((e) => e.key)
        .toSet();
    if (core.isNotEmpty) {
      final daily = all.where((v) => core.contains(v.channelId)).toList();
      if (daily.length >= _minSongs) {
        mixes.add(Mix(
          id: 'daily',
          title: 'המיקס היומי שלך',
          subtitle: 'נבנה ממה שאתה שומע — מתעדכן מעצמו',
          songs: shuffledStable(daily).take(_maxSongs).toList(),
        ));
      }
    }

    // ── מיקס לכל אמן ───────────────────────────────────────────────────
    for (final channelId in core.take(_maxArtistMixes)) {
      final name = channelName(channelId);
      if (name == null || name.isEmpty) continue;
      final songs = all.where((v) => v.channelId == channelId).toList();
      if (songs.length < _minSongs) continue;
      mixes.add(Mix(
        id: 'artist_$channelId',
        title: 'מיקס $name',
        subtitle: 'הכי מושמע אצלך',
        songs: shuffledStable(songs).take(_maxSongs).toList(),
        seedChannel: channelId,
      ));
    }

    // ── מיקס לפי סגנון ─────────────────────────────────────────────────
    final topCategories = (profile.categoryAffinity.entries.toList()
          ..sort((a, b) => b.value.compareTo(a.value)))
        .take(2);
    for (final entry in topCategories) {
      final label = categoryLabels[entry.key];
      if (label == null) continue;
      final songs =
          all.where((v) => categoryOf(v.channelId) == entry.key).toList();
      if (songs.length < _minSongs) continue;
      mixes.add(Mix(
        id: 'cat_${entry.key}',
        title: 'מיקס $label',
        subtitle: 'הסגנון שאתה חוזר אליו',
        songs: shuffledStable(songs).take(_maxSongs).toList(),
      ));
    }

    // ── גילוי ──────────────────────────────────────────────────────────
    // דווקא מה שעוד לא שמעת — אחרת המיקסים הופכים למעגל סגור.
    final discovery = all.where((v) => !heard.contains(v.id)).toList();
    if (discovery.length >= _minSongs) {
      mixes.add(Mix(
        id: 'discovery',
        title: 'גילויים',
        subtitle: 'מהערוצים המאושרים — מה שעוד לא שמעת',
        songs: shuffledStable(discovery).take(_maxSongs).toList(),
      ));
    }

    return mixes;
  }
}

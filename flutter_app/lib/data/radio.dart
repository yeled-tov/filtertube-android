import 'dart:math';

import '../models/channel.dart';
import '../models/video.dart';
import 'channels_repo.dart';
import 'library_store.dart';
import 'settings_store.dart';
import 'youtube_feed.dart';

/// בניית תחנות רדיו מהטעם של המשתמש.
///
/// ## למה זה לא "סרטונים קשורים"
/// ה-API הרשמי סגר את relatedToVideoId, ובלעדיו אין דרך לשאול את יוטיוב
/// "מה דומה לזה". מה שכן יש הוא מה שהמשתמש עצמו סימן ושמע, ואת הרשימה
/// הלבנה — ומהשניים אפשר לבנות תחנה שבאמת מכירה אותו, בלי לצאת מהערוצים
/// המאושרים ובלי לשרוף מכסה על חיפושים.
class StationBuilder {
  StationBuilder({required this.channels, required this.feed});

  final ChannelsRepo channels;
  final YoutubeFeed feed;

  /// האם צריך לשאול את המשתמש על זמרים אהובים לפני שבונים תחנה.
  /// התקנה טרייה: אין לייקים, אין היסטוריה, ואין ממה להסיק טעם.
  bool needsArtistPicker() =>
      appLibrary.likes.isEmpty &&
      appLibrary.localHistory.isEmpty &&
      appLibrary.localSubscriptions.isEmpty;

  /// תחנה אישית: קודם מה שאהבת, אחר כך מה ששמעת, ורק אז חדש מהערוצים.
  Future<List<Video>> personalStation({
    bool musicOnly = false,
    int size = 30,
    int level = 2,
    String gender = '',
    List<Video> pool = const [],
  }) async {
    bool wanted(Video v) =>
        !musicOnly || kMusicCategories.contains(channels.categoryOf(v.channelId));

    final station = _dedup([
      ...appLibrary.likes.where(wanted),
      ...appLibrary.localHistory.where(wanted),
      ...pool.where(wanted),
    ]);
    if (station.length >= size) return _shuffled(station).take(size).toList();

    final visible = channels.visible(level, gender);
    bool style(Channel c) =>
        !musicOnly || kMusicCategories.contains(c.category);

    // סדר העדיפויות הוא סדר הוודאות: מה שהמשתמש בחר במפורש ("הזמרים
    // שלי"), אחר כך מה שהוא עוקב אחריו, ורק אז כל המאושרים. בלי השלב
    // הראשון הבחירה שנעשתה בחלון "איזה זמרים אתה אוהב?" לא הייתה משפיעה
    // על התחנה בכלל.
    final favorites = visible
        .where((c) => appSettings.favoriteArtists.contains(c.youtubeChannelId))
        .where(style)
        .toList();
    final followed = visible
        .where((c) => appLibrary.isSubscribed(c.youtubeChannelId))
        .where(style)
        .toList();
    final candidates = favorites.isNotEmpty
        ? favorites
        : (followed.isNotEmpty ? followed : visible.where(style).toList());
    if (candidates.isEmpty) return _shuffled(station).take(size).toList();

    for (final channel in _shuffled(candidates).take(8)) {
      station.addAll((await feed.channelFeed(channel)).where(wanted).take(6));
    }
    return _shuffled(_dedup(station)).take(size).toList();
  }

  /// תחנה מסביב לשיר מסוים — אותו סגנון, לא בהכרח אותו אמן.
  ///
  /// הסגנון נגזר מהקטגוריה של הערוץ, כי זה המידע היחיד שבאמת יש לנו.
  /// ובמכוון לא רק מאותו ערוץ, אחרת זו רשימת אמן ולא רדיו.
  Future<List<Video>> stationForSeed(
    Video seed, {
    int size = 30,
    int level = 2,
    String gender = '',
    List<Video> pool = const [],
  }) async {
    final category = channels.categoryOf(seed.channelId);
    final visible = channels.visible(level, gender);
    final sameStyle = visible
        .where((c) => c.category == category && c.youtubeChannelId != seed.channelId)
        .toList();
    final candidates = sameStyle.isEmpty ? visible : sameStyle;

    final out = <Video>[
      ...pool.where((v) => channels.categoryOf(v.channelId) == category),
    ];
    for (final channel in _shuffled(candidates).take(8)) {
      out.addAll((await feed.channelFeed(channel)).take(6));
    }
    out.removeWhere((v) => v.id == seed.id);
    return _shuffled(_dedup(out)).take(size).toList();
  }

  /// "בחירה מהירה" — מה שהכי סביר שירצו לשמוע עכשיו.
  ///
  /// בניגוד לתחנה, כאן **אין ערבוב** של מה שהמשתמש כבר סימן: הסדר הוא
  /// אהבתי ואז נשמע-לאחרונה, כדי שהאריחים לא יקפצו בכל פתיחה.
  List<Video> quickPicks(List<Video> pool, {int size = 27}) {
    bool music(Video v) =>
        kMusicCategories.contains(channels.categoryOf(v.channelId));
    final picks = _dedup([
      ...appLibrary.likes.where(music),
      ...appLibrary.localHistory.where(music),
    ]);
    if (picks.length >= size) return picks.take(size).toList();
    picks.addAll(pool.where(music));
    return _dedup(picks).take(size).toList();
  }

  List<Video> _dedup(List<Video> items) {
    final seen = <String>{};
    return items.where((v) => v.id.isNotEmpty && seen.add(v.id)).toList();
  }

  List<T> _shuffled<T>(List<T> items) {
    final copy = List<T>.from(items);
    copy.shuffle(Random());
    return copy;
  }
}

import 'dart:math';

import 'channels_repo.dart';
import 'library.dart';
import 'models.dart';
import 'youtube_api.dart';

/// קטגוריות שנחשבות מוזיקה — מה שנכנס לפילטר מיוזיק ולרדיו המוזיקלי.
const Set<String> kMusicCategories = {'music', 'dati_light', 'events'};

/// בניית תחנות רדיו מהטעם של המשתמש.
///
/// השם StationBuilder ולא RadioBuilder: פלאטר עצמה מייצאת RadioBuilder
/// (רכיב כפתור הרדיו) דרך material.dart, ושני שמות זהים בקובץ אחד הם
/// ambiguous_import.
///
/// ## למה זה לא "סרטונים קשורים"
/// ה-API הרשמי סגר את relatedToVideoId, ובלעדיו אין דרך לשאול את יוטיוב
/// "מה דומה לזה". מה שכן יש הוא מה שהמשתמש עצמו סימן ושמע, ואת הרשימה
/// הלבנה — ומהשניים אפשר לבנות תחנה שבאמת מכירה אותו, בלי לצאת מהערוצים
/// המאושרים ובלי לשרוף מכסה על חיפושים.
class StationBuilder {
  final YoutubeApi api;
  final ChannelsRepo channels;

  StationBuilder({required this.api, required this.channels});

  /// תחנה אישית: קודם מה שאהבת, אחר כך מה ששמעת, ורק אז חדש מהערוצים.
  ///
  /// [musicOnly] מגביל לערוצי מוזיקה — זה ההבדל בין "רדיו" ל"רדיו מוזיקלי".
  Future<List<Video>> personalStation({
    bool musicOnly = false,
    int size = 30,
  }) async {
    bool wanted(Video v) =>
        !musicOnly || kMusicCategories.contains(channels.categoryOf(v.channelId));

    final seed = <Video>[
      ...appLibrary.likes.where(wanted),
      ...appLibrary.history.where(wanted),
    ];

    final station = _dedup(seed);
    if (station.length >= size) return _shuffled(station).take(size).toList();

    // משלימים מהערוצים שהמשתמש עוקב אחריהם, ואם אין — מהמאושרים שיש.
    final followed = channels.channels
        .where((c) => appLibrary.isSubscribed(c.id))
        .where((c) => !musicOnly || kMusicCategories.contains(c.category))
        .toList();
    final pool = followed.isNotEmpty
        ? followed
        : channels.channels
            .where((c) => !musicOnly || kMusicCategories.contains(c.category))
            .toList();

    final picks = _shuffled(pool).take(8).toList();
    final fetched = await Future.wait(
      picks.map((c) => api.channelUploads(c, max: 6)),
    );
    for (final list in fetched) {
      station.addAll(list.where(wanted));
    }
    return _shuffled(_dedup(station)).take(size).toList();
  }

  /// "בחירה מהירה" — מה שהכי סביר שירצו לשמוע עכשיו.
  ///
  /// בניגוד לתחנה, כאן **אין ערבוב** של מה שהמשתמש כבר סימן: הסדר הוא
  /// אהבתי ואז נשמע-לאחרונה, כדי שהאריחים לא יקפצו ממקום למקום בכל פתיחה.
  /// רק ההשלמה מהערוצים מוגרלת, כי שם באמת אין העדפה.
  Future<List<Video>> quickPicks({int size = 27}) async {
    bool music(Video v) =>
        kMusicCategories.contains(channels.categoryOf(v.channelId));

    final picks = _dedup([
      ...appLibrary.likes.where(music),
      ...appLibrary.history.where(music),
    ]);
    if (picks.length >= size) return picks.take(size).toList();

    final pool = channels.channels
        .where((c) => kMusicCategories.contains(c.category))
        .toList();
    final followed = pool.where((c) => appLibrary.isSubscribed(c.id)).toList();
    final chosen = _shuffled(followed.isNotEmpty ? followed : pool).take(8);
    final fetched = await Future.wait(
      chosen.map((c) => api.channelUploads(c, max: 5)),
    );
    for (final list in fetched) {
      picks.addAll(list);
    }
    return _dedup(picks).take(size).toList();
  }

  /// תחנה מסביב לשיר מסוים — אותו סגנון, לא בהכרח אותו אמן.
  ///
  /// הסגנון נגזר מהקטגוריה של הערוץ, כי זה המידע היחיד שבאמת יש לנו: ה-API
  /// הרשמי לא מספק "דומה לזה". לכן התחנה נבנית מערוצים באותה קטגוריה —
  /// ובמכוון לא רק מאותו ערוץ, אחרת זה פלייליסט של אמן ולא רדיו.
  Future<List<Video>> stationForSeed(Video seed, {int size = 30}) async {
    final category = channels.categoryOf(seed.channelId);
    final sameStyle = channels.channels
        .where((c) => c.category == category && c.id != seed.channelId)
        .toList();
    final pool = sameStyle.isEmpty ? channels.channels : sameStyle;

    final picks = _shuffled(pool).take(8).toList();
    final fetched = await Future.wait(
      picks.map((c) => api.channelUploads(c, max: 6)),
    );
    final out = <Video>[];
    for (final list in fetched) {
      out.addAll(list);
    }
    out.removeWhere((v) => v.id == seed.id);
    return _shuffled(_dedup(out)).take(size).toList();
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

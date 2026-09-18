import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/channel.dart';
import '../models/video.dart';
import 'channels_repo.dart';
import 'feed_ranker.dart';
import 'library_store.dart';
import 'notifications.dart';
import 'playback.dart';
import 'radio.dart';
import 'search_engine.dart';
import 'settings_store.dart';
import 'youtube_api.dart';
import 'youtube_feed.dart';

enum FeedStatus { loading, ready, error }

/// המצב המשותף של האפליקציה: הערוצים המאושרים, הפיד המדורג, והשירותים
/// שכל המסכים משתמשים בהם. מופע אחד, כדי שמעבר בין לשוניות לא יטען הכל
/// מחדש ולא ישרוף מכסה.
class AppState extends ChangeNotifier {
  AppState();

  final YoutubeApi api = YoutubeApi();
  final YoutubeFeed feed = YoutubeFeed();
  final ChannelsRepo channels = ChannelsRepo();
  late final SearchEngine search = SearchEngine(api);
  late final StationBuilder stations =
      StationBuilder(channels: channels, feed: feed);

  static const String _feedCacheKey = 'feed_cache_v1';
  static const int _feedCacheMax = 400;

  FeedStatus status = FeedStatus.loading;
  String errorMessage = '';
  bool refreshing = false;

  List<Video> _feed = [];

  /// הפיד המדורג, בלי סרטונים שהמשתמש חסם לעצמו ובלי מה שכבר זוהה
  /// כ-Short.
  ///
  /// ההסתרה חלה רק על סרטון שאורכו **ידוע**: סרטון שטרם הועשר נשאר בפיד,
  /// כי הסתרה על סמך ניחוש הייתה מעלימה תוכן אמיתי. כך כל סרטון מופיע
  /// במקום אחד בלבד — בפיד או בלשונית ה-Shorts.
  List<Video> get videos => _feed
      .where((v) => !appLibrary.blockedIds.contains(v.id))
      .where((v) =>
          v.durationSec <= 0 || v.durationSec > AppState.shortMaxSeconds)
      .toList();

  /// הקטגוריות שבאמת יש להן סרטונים בפיד הנוכחי — צ'יפ שמוביל למסך ריק
  /// הוא באג, לא תצוגה.
  Set<String> feedCategories = {};

  List<Channel> visibleChannels = [];

  /// שורטס נשמרים בנפרד: הם לא מתערבבים בפיד הראשי.
  List<Video> shorts = [];
  bool shortsLoading = false;

  bool _enriching = false;

  Future<void> boot() async {
    await channels.load();
    playback.channels = channels;
    playback.radioContinuation = (seed) => stations.stationForSeed(
          seed,
          level: appSettings.filterLevel,
          gender: appSettings.userGender,
          pool: _feed,
        );
    _recomputeVisible();

    final cached = await _loadCache();
    if (cached.isNotEmpty) {
      _feed = _rank(cached);
      status = FeedStatus.ready;
      notifyListeners();
    }
    await refresh(silent: cached.isNotEmpty);
  }

  void _recomputeVisible() {
    visibleChannels =
        channels.visible(appSettings.filterLevel, appSettings.userGender);
  }

  /// נקרא אחרי שינוי רמת סינון או מגדר — הפיד כולו צריך להיבנות מחדש.
  Future<void> onFilterChanged() async {
    _recomputeVisible();
    _feed = [];
    shorts = [];
    status = FeedStatus.loading;
    notifyListeners();
    await refresh();
  }

  Future<void> refresh({bool silent = false}) async {
    if (refreshing) return;
    refreshing = true;
    if (!silent) status = FeedStatus.loading;
    notifyListeners();

    try {
      await channels.load();
      _recomputeVisible();
      if (visibleChannels.isEmpty) {
        if (_feed.isEmpty) {
          status = FeedStatus.error;
          errorMessage = 'רשימת הערוצים המאושרים ריקה — בדוק חיבור לאינטרנט';
        }
        return;
      }

      final fetched = await feed.allChannelsFeed(visibleChannels);
      if (fetched.isEmpty) {
        if (_feed.isEmpty) {
          status = FeedStatus.error;
          errorMessage = 'לא התקבלו סרטונים מהערוצים המאושרים';
        }
        return;
      }

      // סרטונים חדשים מערוצים שעוקבים אחריהם — לתיבה ולהתראה.
      await _collectNewVideos(fetched);

      _feed = _rank(fetched);
      status = FeedStatus.ready;
      errorMessage = '';
      notifyListeners();
      await _saveCache(_feed);

      // ההעשרה רצה אחרי ההצגה: קודם מה שנראה על המסך, אחר כך השאר.
      // כשהיא חסמה את ההצגה, מסך הבית חיכה למספר קריאות רשת רצופות לפני
      // שהראה משהו, וכל תקלה בדרך הופיעה כ"שגיאה בטעינה".
      unawaited(_enrichTop());
    } catch (e) {
      if (_feed.isEmpty) {
        status = FeedStatus.error;
        errorMessage = 'שגיאה בטעינת הפיד';
      }
    } finally {
      refreshing = false;
      notifyListeners();
    }
  }

  Future<void> _enrichTop() async {
    if (_enriching) return;
    _enriching = true;
    try {
      final enriched = await api.enrich(_feed, limit: 100);
      if (enriched.length == _feed.length) {
        _feed = enriched;
        notifyListeners();
        await _saveCache(_feed);
      }
      await api.warmAvatars(
          _feed.take(60).map((v) => v.channelId).toSet().toList());
      notifyListeners();
    } catch (_) {
      // ההעשרה היא שיפור, לא תנאי — כישלון שלה לא אמור להיראות למשתמש
    } finally {
      _enriching = false;
    }
  }

  List<Video> _rank(List<Video> source) {
    final clean = <String, Video>{};
    for (final v in source) {
      if (v.id.isEmpty) continue;
      clean.putIfAbsent(v.id, () => v);
    }
    final profile = TasteProfile.build(
      likes: [...appLibrary.likes, ...appLibrary.youtubeLikes],
      history: appLibrary.localHistory,
      subscriptions: appLibrary.localSubscriptions.toList(),
      searchTerms: appSettings.searchHistory,
      categoryOf: (id) => channels.categoryOf(id),
    );
    final ranked = FeedRanker.rank(
      videos: clean.values
          .where((v) => !appLibrary.blockedIds.contains(v.id))
          .toList(),
      profile: profile,
      categoryOf: (id) => channels.categoryOf(id),
      watchedIds: appLibrary.watchedIds,
    );
    feedCategories =
        ranked.map((v) => channels.categoryOf(v.channelId)).toSet();
    return ranked;
  }

  /// פרופיל הטעם הנוכחי — המיקסים והרדיו נבנים ממנו.
  TasteProfile tasteProfile() => TasteProfile.build(
        likes: [...appLibrary.likes, ...appLibrary.youtubeLikes],
        history: appLibrary.localHistory,
        subscriptions: appLibrary.localSubscriptions.toList(),
        searchTerms: appSettings.searchHistory,
        categoryOf: (id) => channels.categoryOf(id),
      );

  // ── שורטס ──────────────────────────────────────────────────────────────

  /// ## למה האורך הוא מה שמגדיר Short כאן
  /// האפליקציה הראשית מושכת את לשונית ה-Shorts של הערוץ דרך NewPipe. ל-API
  /// הרשמי אין לשונית כזו, ופיד ה-RSS מחזיר לכל סרטון קישור `watch?v=` —
  /// גם ל-Short. כלומר אין שום סימון להסתמך עליו, ומה שכן יש הוא האורך:
  /// יוטיוב עצמה מגדירה Short כסרטון של עד שלוש דקות. הבדיקה נעשית מול
  /// `videos.list` (בקשה אחת ל-50 סרטונים), והיא גם מאמתת שהסרטון ניתן
  /// להטמעה.
  ///
  /// המחיר: סרטון רגיל וקצר במיוחד ייכנס ללשונית ה-Shorts. אין דרך לדעת
  /// מ-API הרשמי אם הסרטון אנכי, ולכן זו ההפרדה הטובה ביותר שאפשר לעשות
  /// בלי לצאת ממנו.
  static const int shortMaxSeconds = 180;

  Future<void> loadShorts({bool force = false}) async {
    if (shortsLoading) return;
    if (shorts.isNotEmpty && !force) return;
    shortsLoading = true;
    notifyListeners();
    try {
      // כל הסרטונים הטריים הם מועמדים — לא רק אלה שסומנו, כי אין סימון.
      final candidates = (_feed.isNotEmpty
              ? _feed
              : await feed.allChannelsFeed(visibleChannels))
          .take(150)
          .toList();
      // נכשלים סגור: בלי אורך מאומת אי אפשר לדעת שהסרטון באמת קצר.
      final verified = await api.filterShorts(
        candidates,
        maxLength: const Duration(seconds: shortMaxSeconds),
      );
      shorts =
          verified.where((v) => !appLibrary.blockedIds.contains(v.id)).toList();
    } catch (_) {
      // נשארים עם מה שכבר יש
    } finally {
      shortsLoading = false;
      notifyListeners();
    }
  }

  // ── תיבת "סרטונים חדשים" ───────────────────────────────────────────────

  Future<void> _collectNewVideos(List<Video> fetched) async {
    final followed = appLibrary.localSubscriptions;
    if (followed.isEmpty) return;
    final cutoff =
        DateTime.now().subtract(const Duration(days: 3)).millisecondsSinceEpoch;
    final known = {
      ...appLibrary.newVideos.map((v) => v.id),
      ...appLibrary.watchedIds,
    };
    final fresh = fetched
        .where((v) =>
            followed.contains(v.channelId) &&
            v.publishedAt > cutoff &&
            !known.contains(v.id))
        .take(30)
        .toList();
    if (fresh.isEmpty) return;
    await appLibrary.addNewVideos(fresh);
    if (appSettings.newVideoNotifications) {
      await AppNotifications.newVideos(fresh);
    }
  }

  // ── מטמון הפיד ─────────────────────────────────────────────────────────

  Future<List<Video>> _loadCache() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final raw = prefs.getString(_feedCacheKey);
      if (raw == null || raw.isEmpty) return const [];
      return (jsonDecode(raw) as List)
          .map((e) => Video.fromJson(e as Map<String, dynamic>))
          .toList();
    } catch (_) {
      return const [];
    }
  }

  Future<void> _saveCache(List<Video> videos) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(
        _feedCacheKey,
        jsonEncode(
            videos.take(_feedCacheMax).map((v) => v.toJson()).toList()),
      );
    } catch (_) {
      // מטמון הוא נוחות; כישלון בכתיבה שלו לא אמור להישמע למשתמש
    }
  }
}

final appState = AppState();

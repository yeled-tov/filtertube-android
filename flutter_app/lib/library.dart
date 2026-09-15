import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'models.dart';

/// מופע גלובלי — הספרייה נגישה מהנגן, מהכרטיסים ומהמסכים בלי לחווט דרך הכל.
final appLibrary = LibraryStore();

/// הספרייה המקומית: אהבתי, היסטוריית צפייה ומעקב אחרי ערוצים.
///
/// ## למה הכול מקומי
/// באפליקציית החנות אין חשבון ואין שרת: כל מה שהמשתמש מסמן נשאר במכשיר.
/// זה גם מה שמאפשר לספרייה לעבוד מהרגע הראשון, בלי הרשמה ובלי רשת.
///
/// ## למה יש תקרות
/// רשימה שגדלה בלי גבול הופכת כל טעינה לאיטית יותר, ו-shared_preferences
/// כותב את כל המחרוזת בכל שינוי. התקרות שומרות על זמן פתיחה קבוע.
class LibraryStore extends ChangeNotifier {
  static const _kLikes = 'library_likes';
  static const _kHistory = 'library_history';
  static const _kSubscriptions = 'library_subscriptions';

  static const int _maxLikes = 500;
  static const int _maxHistory = 200;

  SharedPreferences? _prefs;

  List<Video> _likes = [];
  List<Video> _history = [];
  Set<String> _subscriptions = {};

  List<Video> get likes => List.unmodifiable(_likes);
  List<Video> get history => List.unmodifiable(_history);
  Set<String> get subscriptions => Set.unmodifiable(_subscriptions);

  Future<void> load() async {
    final prefs = await SharedPreferences.getInstance();
    _prefs = prefs;
    _likes = _readVideos(prefs, _kLikes);
    _history = _readVideos(prefs, _kHistory);
    _subscriptions = (prefs.getStringList(_kSubscriptions) ?? const []).toSet();
    notifyListeners();
  }

  List<Video> _readVideos(SharedPreferences prefs, String key) {
    final raw = prefs.getString(key);
    if (raw == null || raw.isEmpty) return [];
    try {
      return (jsonDecode(raw) as List)
          .map((e) => Video.fromJson(e as Map<String, dynamic>))
          .where((v) => v.id.isNotEmpty)
          .toList();
    } catch (_) {
      // רשומה פגומה לא אמורה למחוק את שאר הספרייה, אבל היא גם לא ניתנת
      // לתיקון — מתחילים ממנה מחדש במקום להפיל את המסך בכל פתיחה.
      return [];
    }
  }

  Future<void> _writeVideos(String key, List<Video> items) async {
    await _prefs?.setString(
      key,
      jsonEncode(items.map((v) => v.toJson()).toList()),
    );
  }

  // ── אהבתי ───────────────────────────────────────────────────────────────

  bool isLiked(String videoId) => _likes.any((v) => v.id == videoId);

  /// מחזיר את המצב *אחרי* הפעולה, כדי שהלב במסך יתעדכן בלי לקרוא שוב.
  Future<bool> toggleLike(Video video) async {
    final wasLiked = isLiked(video.id);
    _likes.removeWhere((v) => v.id == video.id);
    if (!wasLiked) {
      _likes.insert(0, video);
      if (_likes.length > _maxLikes) _likes = _likes.sublist(0, _maxLikes);
    }
    await _writeVideos(_kLikes, _likes);
    notifyListeners();
    return !wasLiked;
  }

  // ── היסטוריה ────────────────────────────────────────────────────────────

  /// רושם צפייה. סרטון שנצפה שוב עולה לראש הרשימה במקום להיכפל בה.
  Future<void> recordWatch(Video video) async {
    if (video.id.isEmpty) return;
    _history.removeWhere((v) => v.id == video.id);
    _history.insert(0, video);
    if (_history.length > _maxHistory) {
      _history = _history.sublist(0, _maxHistory);
    }
    await _writeVideos(_kHistory, _history);
    notifyListeners();
  }

  Future<void> clearHistory() async {
    _history = [];
    await _writeVideos(_kHistory, _history);
    notifyListeners();
  }

  // ── מעקב אחרי ערוצים ────────────────────────────────────────────────────

  bool isSubscribed(String channelId) => _subscriptions.contains(channelId);

  Future<bool> toggleSubscription(String channelId) async {
    if (channelId.isEmpty) return false;
    final nowSubscribed = !_subscriptions.contains(channelId);
    if (nowSubscribed) {
      _subscriptions.add(channelId);
    } else {
      _subscriptions.remove(channelId);
    }
    await _prefs?.setStringList(_kSubscriptions, _subscriptions.toList());
    notifyListeners();
    return nowSubscribed;
  }
}

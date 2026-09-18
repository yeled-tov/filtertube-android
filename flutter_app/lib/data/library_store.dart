import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../config.dart';
import '../models/video.dart';
import 'billing.dart';

final appLibrary = LibraryStore();

/// הספרייה המקומית: אהבתי, היסטוריה, אלבומים, מנויים, חסומים ותיבת
/// "סרטונים חדשים". מקבילה ל-`LibraryStore` באפליקציה הראשית, בלי ההורדות.
///
/// ## למה יש תקרות
/// רשימה שגדלה בלי גבול הופכת כל טעינה לאיטית יותר, ו-shared_preferences
/// כותב את כל המחרוזת בכל שינוי. התקרות שומרות על זמן פתיחה קבוע.
class LibraryStore extends ChangeNotifier {
  static const _kLikes = 'likes';
  static const _kPlaylists = 'playlists';
  static const _kBlocked = 'blocked_videos';
  static const _kLocalHistory = 'local_history';
  static const _kLocalSubs = 'local_subscriptions';
  static const _kNewVideos = 'new_videos_inbox';

  static const int _maxLikes = 500;
  static const int _maxHistory = 300;
  static const int _maxInbox = 120;

  SharedPreferences? _p;

  List<Video> _likes = [];
  List<Video> _localHistory = [];
  List<Video> _blocked = [];
  List<Video> _newVideos = [];
  List<Playlist> _playlists = [];
  Set<String> _localSubs = {};

  List<Video> get likes => List.unmodifiable(_likes);
  List<Video> get localHistory => List.unmodifiable(_localHistory);
  List<Video> get blockedVideos => List.unmodifiable(_blocked);
  List<Video> get newVideos => List.unmodifiable(_newVideos);
  List<Playlist> get playlists => List.unmodifiable(_playlists);
  Set<String> get localSubscriptions => Set.unmodifiable(_localSubs);

  /// מזהים בלבד — לסימונים על כרטיסים, בלי לסרוק רשימות בכל בנייה מחדש.
  Set<String> likedIds = {};
  Set<String> watchedIds = {};
  Set<String> blockedIds = {};

  Future<void> load() async {
    final prefs = await SharedPreferences.getInstance();
    _p = prefs;
    _likes = _readVideos(prefs, _kLikes);
    _localHistory = _readVideos(prefs, _kLocalHistory);
    _blocked = _readVideos(prefs, _kBlocked);
    _newVideos = _readVideos(prefs, _kNewVideos);
    _localSubs = (prefs.getStringList(_kLocalSubs) ?? const []).toSet();
    _playlists = _readList(prefs, _kPlaylists, Playlist.fromJson);
    _refreshBadges();
    notifyListeners();
  }

  void _refreshBadges() {
    likedIds = _likes.map((v) => v.id).toSet();
    watchedIds = _localHistory.map((v) => v.id).toSet();
    blockedIds = _blocked.map((v) => v.id).toSet();
  }

  List<T> _readList<T>(
      SharedPreferences prefs, String key, T Function(Map<String, dynamic>) from) {
    final raw = prefs.getString(key);
    if (raw == null || raw.isEmpty) return [];
    try {
      return (jsonDecode(raw) as List)
          .map((e) => from(e as Map<String, dynamic>))
          .toList();
    } catch (_) {
      // רשומה פגומה לא אמורה למחוק את שאר הספרייה, אבל היא גם לא ניתנת
      // לתיקון — מתחילים ממנה מחדש במקום להפיל את המסך בכל פתיחה.
      return [];
    }
  }

  List<Video> _readVideos(SharedPreferences prefs, String key) =>
      _readList(prefs, key, Video.fromJson).where((v) => v.id.isNotEmpty).toList();

  Future<void> _write(String key, List<Object> items) async {
    await _p?.setString(
      key,
      jsonEncode(items.map((v) {
        if (v is Video) return v.toJson();
        if (v is Playlist) return v.toJson();
        return <String, dynamic>{};
      }).toList()),
    );
  }

  // ── אהבתי ──────────────────────────────────────────────────────────────

  bool isLiked(String videoId) => likedIds.contains(videoId);

  /// מחזיר את המצב *אחרי* הפעולה, כדי שהלב במסך יתעדכן בלי לקרוא שוב.
  Future<bool> toggleLike(Video video) async {
    final wasLiked = _likes.any((v) => v.id == video.id);
    _likes.removeWhere((v) => v.id == video.id);
    if (!wasLiked) {
      _likes.insert(0, video);
      if (_likes.length > _maxLikes) _likes = _likes.sublist(0, _maxLikes);
    }
    await _write(_kLikes, _likes);
    _refreshBadges();
    notifyListeners();
    return !wasLiked;
  }

  // ── היסטוריה ───────────────────────────────────────────────────────────

  /// רושם צפייה. סרטון שנצפה שוב עולה לראש הרשימה במקום להיכפל בה.
  Future<void> recordWatch(Video video) async {
    if (video.id.isEmpty) return;
    _localHistory.removeWhere((v) => v.id == video.id);
    _localHistory.insert(
        0, video.copyWith(watchedAt: DateTime.now().millisecondsSinceEpoch));
    if (_localHistory.length > _maxHistory) {
      _localHistory = _localHistory.sublist(0, _maxHistory);
    }
    await _write(_kLocalHistory, _localHistory);
    _refreshBadges();
    notifyListeners();
  }

  Future<void> clearLocalHistory() async {
    _localHistory = [];
    await _write(_kLocalHistory, _localHistory);
    _refreshBadges();
    notifyListeners();
  }

  // ── חסימות אישיות ──────────────────────────────────────────────────────

  bool isBlocked(String videoId) => blockedIds.contains(videoId);

  Future<void> blockVideo(Video video) async {
    if (_blocked.any((v) => v.id == video.id)) return;
    _blocked.insert(0, video);
    await _write(_kBlocked, _blocked);
    _refreshBadges();
    notifyListeners();
  }

  Future<void> unblockVideo(String videoId) async {
    _blocked.removeWhere((v) => v.id == videoId);
    await _write(_kBlocked, _blocked);
    _refreshBadges();
    notifyListeners();
  }

  // ── אלבומים ────────────────────────────────────────────────────────────

  /// האם אפשר ליצור עוד אלבום ברמה הנוכחית (חינם/Premium).
  bool get canCreatePlaylist =>
      appBilling.premiumActive || _playlists.length < AppConfig.freePlaylistLimit;

  Future<void> createPlaylist(String name) async {
    final clean = name.trim();
    if (clean.isEmpty || _playlists.any((p) => p.name == clean)) return;
    if (!canCreatePlaylist) return;
    _playlists = [..._playlists, Playlist(name: clean, videos: const [])];
    await _write(_kPlaylists, _playlists);
    notifyListeners();
  }

  Future<void> deletePlaylist(String name) async {
    _playlists = _playlists.where((p) => p.name != name).toList();
    await _write(_kPlaylists, _playlists);
    notifyListeners();
  }

  Future<void> addToPlaylist(String name, Video video) async {
    final clean = name.trim();
    final index = _playlists.indexWhere((p) => p.name == clean);
    if (index == -1) {
      if (!canCreatePlaylist) return;
      _playlists = [
        ..._playlists,
        Playlist(name: clean, videos: [video])
      ];
    } else {
      final current = _playlists[index];
      if (current.videos.any((v) => v.id == video.id)) return;
      _playlists = [..._playlists];
      _playlists[index] =
          Playlist(name: current.name, videos: [...current.videos, video]);
    }
    await _write(_kPlaylists, _playlists);
    notifyListeners();
  }

  Future<void> removeFromPlaylist(String name, String videoId) async {
    final index = _playlists.indexWhere((p) => p.name == name);
    if (index == -1) return;
    final current = _playlists[index];
    _playlists = [..._playlists];
    _playlists[index] = Playlist(
      name: current.name,
      videos: current.videos.where((v) => v.id != videoId).toList(),
    );
    await _write(_kPlaylists, _playlists);
    notifyListeners();
  }

  Future<void> replacePlaylists(List<Playlist> list) async {
    _playlists = list;
    await _write(_kPlaylists, _playlists);
    notifyListeners();
  }

  // ── מנויים ─────────────────────────────────────────────────────────────

  bool isSubscribed(String channelId) => _localSubs.contains(channelId);

  Future<bool> toggleSubscription(String channelId) async {
    if (channelId.isEmpty) return false;
    final now = !_localSubs.contains(channelId);
    if (now) {
      _localSubs.add(channelId);
    } else {
      _localSubs.remove(channelId);
    }
    await _p?.setStringList(_kLocalSubs, _localSubs.toList());
    notifyListeners();
    return now;
  }

  Future<void> replaceLocalSubscriptions(Iterable<String> ids) async {
    _localSubs = ids.toSet();
    await _p?.setStringList(_kLocalSubs, _localSubs.toList());
    notifyListeners();
  }

  // ── תיבת "סרטונים חדשים" ───────────────────────────────────────────────

  Future<void> addNewVideos(List<Video> list) async {
    final known = _newVideos.map((v) => v.id).toSet();
    final fresh = list.where((v) => !known.contains(v.id)).toList();
    if (fresh.isEmpty) return;
    _newVideos = [...fresh, ..._newVideos].take(_maxInbox).toList();
    await _write(_kNewVideos, _newVideos);
    notifyListeners();
  }

  Future<void> clearNewVideos() async {
    _newVideos = [];
    await _write(_kNewVideos, _newVideos);
    notifyListeners();
  }

  /// מחיקת כל מה ששייך לחשבון — נקרא ביציאה מחשבון הענן.
  Future<void> clearAccountData() async {
    _likes = [];
    _localHistory = [];
    _blocked = [];
    _newVideos = [];
    _playlists = [];
    _localSubs = {};
    for (final key in [
      _kLikes,
      _kLocalHistory,
      _kBlocked,
      _kNewVideos,
      _kPlaylists,
      _kLocalSubs,
    ]) {
      await _p?.remove(key);
    }
    _refreshBadges();
    notifyListeners();
  }
}

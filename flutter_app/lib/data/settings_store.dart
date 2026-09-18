import 'dart:convert';
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart' show compute;
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../theme.dart';

/// מופע גלובלי — נגיש מכל מסך בלי לחווט דרך כל הווידג'טים.
final appSettings = SettingsStore();

/// כל ההגדרות המקומיות. מקביל ל-`SettingsStore` באפליקציה הראשית, בלי מה
/// ששייך להורדות ולניגון ברקע — שתיהן אינן קיימות בגרסת החנות.
class SettingsStore extends ChangeNotifier {
  static const _kShorts = 'shorts_enabled';
  static const _kLevel = 'filter_level';
  static const _kPwVerifier = 'filter_pw_verifier';
  static const _kPwSalt = 'filter_pw_salt';
  static const _kHighRefresh = 'high_refresh_rate';
  static const _kAccent = 'accent_color';
  static const _kAccent2 = 'accent2_color';
  static const _kPlayerStyle = 'player_style';
  static const _kFavoriteArtists = 'favorite_artists';
  static const _kArtistPickerSeen = 'artist_picker_seen';
  static const _kSpeed = 'playback_speed';
  static const _kAutoRadio = 'auto_radio_queue';
  static const _kNoDuplicates = 'prevent_queue_duplicates';
  static const _kMiniSwipe = 'mini_player_swipe';
  static const _kMiniRestart = 'mini_swipe_right_restarts';
  static const _kAudioOnly = 'audio_only_mode';
  static const _kThemeMode = 'theme_mode';
  static const _kMusicSwipeTrack = 'music_swipe_track';
  static const _kMusicSwipeDismiss = 'music_swipe_dismiss';
  static const _kVideoSwipeTrack = 'video_swipe_track';
  static const _kVideoSwipeDismiss = 'video_swipe_dismiss';
  static const _kNewVideoNotifications = 'new_video_notifications';
  static const _kOnboardingDone = 'onboarding_done';
  static const _kUserName = 'user_name';
  static const _kUserGender = 'user_gender';
  static const _kSeekShape = 'seek_bar_shape';
  static const _kSeekThickness = 'seek_bar_thickness';
  static const _kSeekGlow = 'seek_bar_glow';
  static const _kHistory = 'search_history';

  late SharedPreferences _p;
  bool _loaded = false;
  bool get loaded => _loaded;

  // ── סינון ──────────────────────────────────────────────────────────────

  bool shortsEnabled = true;

  /// 1 = מחמיר · 2 = רגיל · 3 = דתי לייט
  int filterLevel = 2;

  /// חל על כל רמות הסינון: הכל נשמע, שום דבר לא נראה.
  bool audioOnlyMode = false;

  /// "" = הכל · "male" · "female"
  String userGender = '';

  // ── תצוגה ──────────────────────────────────────────────────────────────

  /// 0 = מערכת · 1 = כהה · 2 = בהיר
  int themeMode = 0;
  bool highRefreshRate = true;
  int accentColor = 0xFFFF2D43;
  int accent2Color = 0xFFFF6A5C;

  // ── נגן ────────────────────────────────────────────────────────────────

  /// 1 = "מתנגן עכשיו" · 2 = בקרים על הווידאו
  int playerStyle = 1;

  /// אחוזים: 100 = מהירות רגילה
  int playbackSpeed = 100;

  bool autoRadioQueue = true;
  bool preventQueueDuplicates = true;

  int seekBarShape = 0; // 0 ישר · 1 גלי · 2 מזוגזג
  int seekBarThickness = 3;
  bool seekBarGlow = true;

  // ── מחוות ──────────────────────────────────────────────────────────────

  bool musicSwipeTrack = true;
  bool musicSwipeDismiss = true;
  bool videoSwipeTrack = true;
  bool videoSwipeDismiss = true;
  bool miniPlayerSwipe = true;
  bool miniSwipeRightRestarts = false;

  // ── שונות ──────────────────────────────────────────────────────────────

  bool newVideoNotifications = true;
  bool onboardingDone = false;
  String userName = '';
  Set<String> favoriteArtists = {};
  bool artistPickerSeen = false;

  String _pwVerifier = '';
  String _pwSalt = '';

  /// האם נקבע קוד הורים במכשיר הזה.
  bool get hasFilterPassword => _pwVerifier.isNotEmpty;

  Future<void> load() async {
    _p = await SharedPreferences.getInstance();
    shortsEnabled = _p.getBool(_kShorts) ?? true;
    filterLevel = _p.getInt(_kLevel) ?? 2;
    audioOnlyMode = _p.getBool(_kAudioOnly) ?? false;
    userGender = _p.getString(_kUserGender) ?? '';
    themeMode = _p.getInt(_kThemeMode) ?? 0;
    highRefreshRate = _p.getBool(_kHighRefresh) ?? true;
    accentColor = _p.getInt(_kAccent) ?? 0xFFFF2D43;
    accent2Color = _p.getInt(_kAccent2) ?? 0xFFFF6A5C;
    playerStyle = _p.getInt(_kPlayerStyle) ?? 1;
    playbackSpeed = _p.getInt(_kSpeed) ?? 100;
    autoRadioQueue = _p.getBool(_kAutoRadio) ?? true;
    preventQueueDuplicates = _p.getBool(_kNoDuplicates) ?? true;
    seekBarShape = _p.getInt(_kSeekShape) ?? 0;
    seekBarThickness = _p.getInt(_kSeekThickness) ?? 3;
    seekBarGlow = _p.getBool(_kSeekGlow) ?? true;
    musicSwipeTrack = _p.getBool(_kMusicSwipeTrack) ?? true;
    musicSwipeDismiss = _p.getBool(_kMusicSwipeDismiss) ?? true;
    videoSwipeTrack = _p.getBool(_kVideoSwipeTrack) ?? true;
    videoSwipeDismiss = _p.getBool(_kVideoSwipeDismiss) ?? true;
    miniPlayerSwipe = _p.getBool(_kMiniSwipe) ?? true;
    miniSwipeRightRestarts = _p.getBool(_kMiniRestart) ?? false;
    newVideoNotifications = _p.getBool(_kNewVideoNotifications) ?? true;
    onboardingDone = _p.getBool(_kOnboardingDone) ?? false;
    userName = _p.getString(_kUserName) ?? '';
    favoriteArtists = (_p.getStringList(_kFavoriteArtists) ?? const []).toSet();
    artistPickerSeen = _p.getBool(_kArtistPickerSeen) ?? false;
    _pwVerifier = _p.getString(_kPwVerifier) ?? '';
    _pwSalt = _p.getString(_kPwSalt) ?? '';
    _loaded = true;
    applyTheme(systemDark: ThemeState.instance.dark);
  }

  /// מיישם את הצבע ומצב הכהה על מצב הנושא החי.
  void applyTheme({required bool systemDark}) {
    ThemeState.instance.setPalette(Color(accentColor), Color(accent2Color));
    ThemeState.instance
        .setDark(themeMode == 1 ? true : (themeMode == 2 ? false : systemDark));
  }

  Future<void> _setBool(String key, bool v) async {
    await _p.setBool(key, v);
    notifyListeners();
  }

  Future<void> _setInt(String key, int v) async {
    await _p.setInt(key, v);
    notifyListeners();
  }

  Future<void> setShortsEnabled(bool v) async {
    shortsEnabled = v;
    await _setBool(_kShorts, v);
  }

  Future<void> setFilterLevel(int v) async {
    filterLevel = v;
    await _setInt(_kLevel, v);
  }

  Future<void> setAudioOnlyMode(bool v) async {
    audioOnlyMode = v;
    await _setBool(_kAudioOnly, v);
  }

  Future<void> setUserGender(String v) async {
    userGender = v;
    await _p.setString(_kUserGender, v);
    notifyListeners();
  }

  Future<void> setUserName(String v) async {
    userName = v;
    await _p.setString(_kUserName, v);
    notifyListeners();
  }

  Future<void> setThemeMode(int v, {required bool systemDark}) async {
    themeMode = v;
    await _p.setInt(_kThemeMode, v);
    ThemeState.instance
        .setDark(v == 1 ? true : (v == 2 ? false : systemDark));
    notifyListeners();
  }

  Future<void> setHighRefreshRate(bool v) async {
    highRefreshRate = v;
    await _setBool(_kHighRefresh, v);
  }

  Future<void> setAccent(int a, int b) async {
    accentColor = a;
    accent2Color = b;
    await _p.setInt(_kAccent, a);
    await _p.setInt(_kAccent2, b);
    ThemeState.instance.setPalette(Color(a), Color(b));
    notifyListeners();
  }

  Future<void> setPlayerStyle(int v) async {
    playerStyle = v;
    await _setInt(_kPlayerStyle, v);
  }

  Future<void> setPlaybackSpeed(int percent) async {
    playbackSpeed = percent;
    await _setInt(_kSpeed, percent);
  }

  Future<void> setAutoRadioQueue(bool v) async {
    autoRadioQueue = v;
    await _setBool(_kAutoRadio, v);
  }

  Future<void> setPreventQueueDuplicates(bool v) async {
    preventQueueDuplicates = v;
    await _setBool(_kNoDuplicates, v);
  }

  Future<void> setSeekBarShape(int v) async {
    seekBarShape = v;
    await _setInt(_kSeekShape, v);
  }

  Future<void> setSeekBarThickness(int v) async {
    seekBarThickness = v;
    await _setInt(_kSeekThickness, v);
  }

  Future<void> setSeekBarGlow(bool v) async {
    seekBarGlow = v;
    await _setBool(_kSeekGlow, v);
  }

  Future<void> setMusicSwipeTrack(bool v) async {
    musicSwipeTrack = v;
    await _setBool(_kMusicSwipeTrack, v);
  }

  Future<void> setMusicSwipeDismiss(bool v) async {
    musicSwipeDismiss = v;
    await _setBool(_kMusicSwipeDismiss, v);
  }

  Future<void> setVideoSwipeTrack(bool v) async {
    videoSwipeTrack = v;
    await _setBool(_kVideoSwipeTrack, v);
  }

  Future<void> setVideoSwipeDismiss(bool v) async {
    videoSwipeDismiss = v;
    await _setBool(_kVideoSwipeDismiss, v);
  }

  Future<void> setMiniPlayerSwipe(bool v) async {
    miniPlayerSwipe = v;
    await _setBool(_kMiniSwipe, v);
  }

  Future<void> setMiniSwipeRightRestarts(bool v) async {
    miniSwipeRightRestarts = v;
    await _setBool(_kMiniRestart, v);
  }

  Future<void> setNewVideoNotifications(bool v) async {
    newVideoNotifications = v;
    await _setBool(_kNewVideoNotifications, v);
  }

  Future<void> setOnboardingDone(bool v) async {
    onboardingDone = v;
    await _setBool(_kOnboardingDone, v);
  }

  Future<void> setFavoriteArtists(Set<String> v) async {
    favoriteArtists = v;
    await _p.setStringList(_kFavoriteArtists, v.toList());
    notifyListeners();
  }

  Future<void> setArtistPickerSeen(bool v) async {
    artistPickerSeen = v;
    await _setBool(_kArtistPickerSeen, v);
  }

  // ── קוד הורים ──────────────────────────────────────────────────────────
  //
  // הקוד עצמו לא נשמר בשום מקום: נשמר רק אימות מלוח (salt) שנגזר ב-PBKDF2-
  // HMAC-SHA256 עם 120,000 סיבובים. מי שיקרא את קובץ ההעדפות לא יוכל להסיק
  // ממנו את הקוד, וזו כל הנקודה — הקוד הזה הוא מה שמגן על רמת הסינון.
  //
  // הגזירה רצה ב-Isolate נפרד דרך `compute`. 120,000 סיבובי HMAC ב-Dart
  // טהור לוקחים כמה מאות מילישניות, ועל תהליכון הממשק זה היה נראה כמו
  // אפליקציה שנתקעה בכל פעם שמזינים את הקוד.

  Future<void> setFilterPassword(String password) async {
    final rnd = Random.secure();
    final salt = List<int>.generate(16, (_) => rnd.nextInt(256));
    final verifier = await compute(_derivePassword, (password, salt));
    _pwSalt = base64Encode(salt);
    _pwVerifier = base64Encode(verifier);
    await _p.setString(_kPwSalt, _pwSalt);
    await _p.setString(_kPwVerifier, _pwVerifier);
    notifyListeners();
  }

  Future<bool> checkFilterPassword(String input) async {
    if (_pwVerifier.isEmpty || _pwSalt.isEmpty) return false;
    final expected = base64Decode(_pwVerifier);
    final actual =
        await compute(_derivePassword, (input, base64Decode(_pwSalt).toList()));
    if (expected.length != actual.length) return false;
    // השוואה בזמן קבוע: יציאה מוקדמת בבית הראשון שנבדל מדליפה כמה תווים
    // נכונים, וזה בדיוק מה שהופך ניחוש לחיפוש.
    var diff = 0;
    for (var i = 0; i < expected.length; i++) {
      diff |= expected[i] ^ actual[i];
    }
    return diff == 0;
  }

  Future<void> clearFilterPassword() async {
    _pwVerifier = '';
    _pwSalt = '';
    await _p.remove(_kPwVerifier);
    await _p.remove(_kPwSalt);
    notifyListeners();
  }

  // ── היסטוריית חיפוש ────────────────────────────────────────────────────

  List<String> get searchHistory =>
      (_p.getString(_kHistory) ?? '').split('\n').where((s) => s.isNotEmpty).toList();

  Future<void> addSearchQuery(String query) async {
    final q = query.trim();
    if (q.isEmpty) return;
    final list = searchHistory..removeWhere((e) => e == q);
    list.insert(0, q);
    await _p.setString(_kHistory, list.take(20).join('\n'));
    notifyListeners();
  }

  Future<void> removeSearchQuery(String query) async {
    final list = searchHistory..removeWhere((e) => e == query);
    await _p.setString(_kHistory, list.join('\n'));
    notifyListeners();
  }

  Future<void> clearSearchHistory() async {
    await _p.remove(_kHistory);
    notifyListeners();
  }

  Future<void> replaceSearchHistory(List<String> queries) async {
    await _p.setString(_kHistory, queries.take(20).join('\n'));
    notifyListeners();
  }
}

/// PBKDF2-HMAC-SHA256 באורך בלוק אחד. פונקציה ברמת הקובץ כי `compute`
/// מריצה אותה ב-Isolate נפרד, ורק פונקציה כזו ניתנת להעברה לשם.
List<int> _derivePassword((String, List<int>) input) {
  const iterations = 120000;
  final (password, salt) = input;
  final key = utf8.encode(password);
  var block = Hmac(sha256, key).convert([...salt, 0, 0, 0, 1]).bytes;
  final result = List<int>.from(block);
  for (var i = 1; i < iterations; i++) {
    block = Hmac(sha256, key).convert(block).bytes;
    for (var j = 0; j < result.length; j++) {
      result[j] ^= block[j];
    }
  }
  return result;
}

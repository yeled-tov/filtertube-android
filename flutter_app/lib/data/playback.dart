import 'dart:async';

import 'package:flutter/material.dart';
import 'package:youtube_player_iframe/youtube_player_iframe.dart';

import '../models/channel.dart';
import '../models/video.dart';
import 'channels_repo.dart';
import 'library_store.dart';
import 'settings_store.dart';

/// מצב הנגן של כל האפליקציה — מופע אחד, ולכן המיני-נגן, מסך הנגן ונגן
/// המוזיקה מדברים כולם על אותו ניגון.
///
/// ## למה לא נגן לכל מסך
/// כל `YoutubePlayer` הוא WebView. שניים מהם פירושם שני עותקים של הנגן
/// ברקע, שניהם מנגנים, ושניהם צורכים זיכרון. מופע אחד שמשנה מקום וגודל
/// על המסך הוא מה שמאפשר למעבר בין מסך הנגן למיני-נגן להמשיך את הניגון
/// בלי קפיצה ובלי טעינה מחדש.
///
/// ## אין ניגון ברקע
/// בגרסת החנות אין ניגון כשהאפליקציה לא על המסך — לא כפיצ'ר חסר אלא
/// כהחלטה: תנאי השימוש של YouTube אוסרים על כך בנגן ההטמעה, וזה מה
/// שמאפשר לאפליקציה הזו להתפרסם בחנויות.
class PlaybackController extends ChangeNotifier {
  PlaybackController();

  YoutubePlayerController? _controller;
  YoutubePlayerController? get controller => _controller;

  StreamSubscription<YoutubePlayerValue>? _sub;
  Timer? _sleepTimer;

  Video? _current;
  Video? get current => _current;

  final List<Video> _queue = [];
  List<Video> get queue => List.unmodifiable(_queue);

  /// תור שמגיע מתחנה/רשימה ולא מהערוץ — ואז לא דורסים אותו בכל מעבר שיר.
  bool stationMode = false;

  /// FilterMusic פתוח — משנה איזה מסך נגן נפתח ומכריח אודיו.
  bool musicMode = false;

  /// הנגן פרוש על כל המסך (להבדיל מהמיני-נגן).
  bool expanded = false;

  bool _audioOnly = false;
  bool get audioOnly => _audioOnly;

  bool isPlaying = false;
  bool hasError = false;

  /// המעבר לפריט הבא כבר בדרך. בלי הדגל הזה אירוע "הסתיים" שמגיע פעמיים
  /// היה מדלג על שני פריטים במקום אחד.
  bool _advancing = false;
  Duration position = Duration.zero;
  Duration duration = Duration.zero;

  int sleepMinutesLeft = 0;

  ChannelsRepo? channels;

  /// נקרא כשהתור נגמר ויש להמשיך ברדיו — מוזרק מבחוץ כדי ש-PlaybackController
  /// לא יצטרך להכיר את בונה התחנות.
  Future<List<Video>> Function(Video seed)? radioContinuation;

  /// נקרא כשהנגן צריך להיפתח — הקליפה מחליטה איזה מסך להראות.
  VoidCallback? onOpenRequested;

  bool get isActive => _current != null;

  // ── אתחול ──────────────────────────────────────────────────────────────

  void attach(YoutubePlayerController controller) {
    _controller = controller;
    _sub?.cancel();
    _sub = controller.listen(_onValue);
  }

  YoutubePlayerParams buildParams() => const YoutubePlayerParams(
        showControls: false,
        showFullscreenButton: false,
        enableCaption: false,
        interfaceLanguage: 'he',
        // הנגן לא מציג "סרטונים קשורים" של יוטיוב: כל מה שמוצג באפליקציה
        // חייב לעבור דרך הרשימה הלבנה, והמסך שמופיע בסוף סרטון הוא הדרך
        // הקלה ביותר לעקוף אותה.
        strictRelatedVideos: true,
        showVideoAnnotations: false,
        playsInline: true,
        pointerEvents: PointerEvents.none,
      );

  void _onValue(YoutubePlayerValue value) {
    final playing = value.playerState == PlayerState.playing;
    final error = value.error != YoutubeError.none;
    final meta = value.metaData;
    var changed = false;
    if (playing != isPlaying) {
      isPlaying = playing;
      changed = true;
    }
    if (error != hasError) {
      hasError = error;
      changed = true;
    }
    if (meta.duration != duration) {
      duration = meta.duration;
      changed = true;
    }
    if (value.playerState == PlayerState.ended && !_advancing) {
      _advancing = true;
      _advance().whenComplete(() => _advancing = false);
    }
    if (changed) notifyListeners();
  }

  // ── ניגון ──────────────────────────────────────────────────────────────

  /// מתחיל ניגון. [queue] הוא תור מוכן (תחנה/רשימה); בלעדיו התור מתמלא
  /// מהערוץ של הסרטון.
  Future<void> play(
    Video video, {
    List<Video>? queue,
    bool music = false,
    bool open = true,
  }) async {
    musicMode = music;
    _current = video;
    stationMode = queue != null && queue.isNotEmpty;
    _queue
      ..clear()
      ..addAll((queue ?? const <Video>[]).where((v) => v.id != video.id));
    _applyAudioOnly(video);
    hasError = false;
    position = Duration.zero;
    if (open) expanded = true;
    notifyListeners();

    await _controller?.loadVideoById(videoId: video.id);
    await _applyPlaybackSettings();
    await appLibrary.recordWatch(video);
    if (open) onOpenRequested?.call();
  }

  /// כמה פריטים נכנסים לתור מרשימה. הפיד מונה מאות סרטונים, ותור באורך
  /// כזה גם מציף את מסך "הבא בתור" וגם לא משקף שום כוונה של המשתמש.
  static const int _maxQueue = 40;

  /// מנגן רשימה החל מ-[index], עם ההמשך שלה כתור — כמו בכל אפליקציית
  /// מוזיקה. הפריטים שלפניו לא נכנסים לתור; הם כבר "מאחור".
  Future<void> playFromList(List<Video> items, int index,
      {bool music = false}) async {
    if (items.isEmpty) return;
    final start = index.clamp(0, items.length - 1);
    await play(items[start],
        queue: items.sublist(start + 1).take(_maxQueue).toList(), music: music);
  }

  void _applyAudioOnly(Video video) {
    final category = channels?.categoryOf(video.channelId);
    _audioOnly = musicMode ||
        isAudioOnlyContent(
            category, appSettings.filterLevel, appSettings.audioOnlyMode);
  }

  Future<void> _applyPlaybackSettings() async {
    final rate = appSettings.playbackSpeed / 100.0;
    if (rate != 1.0) await _controller?.setPlaybackRate(rate);
  }

  Future<void> _advance() async {
    if (_queue.isNotEmpty) {
      final next = _queue.first;
      await play(next, queue: _queue.sublist(1), music: musicMode, open: false);
      return;
    }
    // כשהתור נגמר, ממשיכים באותו סגנון במקום לעצור.
    if (appSettings.autoRadioQueue && _current != null) {
      final builder = radioContinuation;
      if (builder == null) return;
      final station = await builder(_current!);
      if (station.isEmpty) return;
      await play(station.first,
          queue: station.sublist(1), music: musicMode, open: false);
    }
  }

  Future<void> next() async {
    if (_queue.isEmpty) return;
    final target = _queue.first;
    await play(target, queue: _queue.sublist(1), music: musicMode, open: false);
  }

  /// "הקודם" מתחיל את השיר מחדש אם עברו יותר מ-4 שניות — כמו בכל נגן.
  Future<void> previous() async {
    final pos = await _controller?.currentTime ?? 0;
    if (pos > 4) {
      await _controller?.seekTo(seconds: 0, allowSeekAhead: true);
      return;
    }
    final history = appLibrary.localHistory;
    final index = history.indexWhere((v) => v.id == _current?.id);
    if (index >= 0 && index + 1 < history.length) {
      await play(history[index + 1], music: musicMode, open: false);
    } else {
      await _controller?.seekTo(seconds: 0, allowSeekAhead: true);
    }
  }

  /// מוסיף לראש התור. מחזיר true אם נכנס מיד אחרי מה שמתנגן עכשיו.
  bool enqueueNext(Video video) {
    if (appSettings.preventQueueDuplicates &&
        _queue.any((v) => v.id == video.id)) {
      return false;
    }
    _queue.insert(0, video);
    notifyListeners();
    return true;
  }

  void enqueueLast(Video video) {
    if (appSettings.preventQueueDuplicates &&
        _queue.any((v) => v.id == video.id)) {
      return;
    }
    _queue.add(video);
    notifyListeners();
  }

  void removeFromQueue(String videoId) {
    _queue.removeWhere((v) => v.id == videoId);
    notifyListeners();
  }

  void shuffleQueue() {
    _queue.shuffle();
    notifyListeners();
  }

  Future<void> togglePlay() async {
    if (isPlaying) {
      await _controller?.pauseVideo();
    } else {
      await _controller?.playVideo();
    }
  }

  Future<void> pause() => _controller?.pauseVideo() ?? Future.value();

  Future<void> seekRelative(int seconds) async {
    final pos = await _controller?.currentTime ?? 0;
    await _controller?.seekTo(seconds: pos + seconds, allowSeekAhead: true);
  }

  Future<void> seekTo(double seconds) =>
      _controller?.seekTo(seconds: seconds, allowSeekAhead: true) ??
      Future.value();

  Future<void> setSpeed(int percent) async {
    await appSettings.setPlaybackSpeed(percent);
    await _controller?.setPlaybackRate(percent / 100.0);
    notifyListeners();
  }

  void toggleAudioOnly() {
    _audioOnly = !_audioOnly;
    notifyListeners();
  }

  void setExpanded(bool value) {
    if (expanded == value) return;
    expanded = value;
    notifyListeners();
  }

  /// עצירה מלאה וסגירת המיני-נגן.
  Future<void> stop() async {
    _sleepTimer?.cancel();
    sleepMinutesLeft = 0;
    await _controller?.stopVideo();
    _current = null;
    _queue.clear();
    expanded = false;
    isPlaying = false;
    notifyListeners();
  }

  // ── טיימר שינה ─────────────────────────────────────────────────────────

  void setSleepTimer(int minutes) {
    _sleepTimer?.cancel();
    sleepMinutesLeft = minutes;
    appSettings.setSleepTimerMinutes(minutes);
    if (minutes > 0) {
      _sleepTimer = Timer(Duration(minutes: minutes), () {
        _controller?.pauseVideo();
        sleepMinutesLeft = 0;
        notifyListeners();
      });
    }
    notifyListeners();
  }

  @override
  void dispose() {
    _sub?.cancel();
    _sleepTimer?.cancel();
    _controller?.close();
    super.dispose();
  }
}

final playback = PlaybackController();

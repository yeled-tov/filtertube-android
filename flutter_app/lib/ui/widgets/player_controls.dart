import 'dart:async';

import 'package:flutter/material.dart';
import 'package:youtube_player_iframe/youtube_player_iframe.dart';

import '../../data/library_store.dart';
import '../../data/playback.dart';
import '../../data/settings_store.dart';
import '../../theme.dart';
import 'seek_bar.dart';

/// פס ההתקדמות וכפתורי הניגון.
///
/// ## למה זה ווידג'ט נפרד
/// שני סגנונות הנגן מציגים בדיוק את אותם בקרים בשני מקומות שונים: מתחת
/// לווידאו, או מעליו. כשהקוד היה משוכפל, ההגדרה "עיצוב הנגן" לא באמת
/// שינתה כלום — היא רק הוסיפה מרווח. ווידג'ט אחד בשני מיקומים הוא מה
/// שהופך את ההגדרה למשהו שאפשר לראות.
///
/// הוא נרשם בעצמו לזרם המיקום, ולכן הוא עובד בשני המקומות בלי שההורה
/// יצטרך לנהל אותו.
class PlayerControls extends StatefulWidget {
  /// true = מרחף מעל הווידאו (סגנון "בקרים על הסרטון"), ואז הרקע כהה
  /// והכפתורים לבנים כדי להיקרא מעל כל תמונה.
  final bool onVideo;

  const PlayerControls({super.key, this.onVideo = false});

  @override
  State<PlayerControls> createState() => _PlayerControlsState();
}

class _PlayerControlsState extends State<PlayerControls> {
  Duration _position = Duration.zero;
  StreamSubscription<YoutubeVideoState>? _sub;

  @override
  void initState() {
    super.initState();
    final controller = playback.controller;
    if (controller == null) return;
    _sub = controller.videoStateStream.listen((state) {
      if (mounted) setState(() => _position = state.position);
    });
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }

  static String _fmt(Duration d) {
    final m = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final s = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return d.inHours > 0 ? '${d.inHours}:$m:$s' : '$m:$s';
  }

  Color get _fg => widget.onVideo ? Colors.white : AppTheme.text;
  Color get _dim => widget.onVideo ? Colors.white70 : AppTheme.subtext;

  @override
  Widget build(BuildContext context) {
    final video = playback.current;
    if (video == null) return const SizedBox.shrink();
    final total = playback.duration.inMilliseconds == 0
        ? 1
        : playback.duration.inMilliseconds;
    final value = (_position.inMilliseconds / total).clamp(0.0, 1.0);
    final liked = appLibrary.isLiked(video.id);

    final content = Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Row(
            children: [
              Text(_fmt(_position),
                  style: TextStyle(color: _dim, fontSize: 11)),
              Expanded(
                child: SeekBar(
                  value: value,
                  shape: appSettings.seekBarShape,
                  thickness: appSettings.seekBarThickness.toDouble(),
                  glow: appSettings.seekBarGlow,
                  onChanged: (v) => setState(() =>
                      _position = Duration(milliseconds: (v * total).round())),
                  onChangeEnd: (v) => playback.seekTo(v * total / 1000),
                ),
              ),
              Text(_fmt(playback.duration),
                  style: TextStyle(color: _dim, fontSize: 11)),
            ],
          ),
        ),
        Padding(
          padding: EdgeInsets.symmetric(
              horizontal: 6, vertical: widget.onVideo ? 0 : 2),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              IconButton(
                tooltip: '⏪ 10 ש׳',
                onPressed: () => playback.seekRelative(-10),
                icon: Icon(Icons.replay_10_rounded,
                    color: _fg, size: widget.onVideo ? 22 : 26),
              ),
              IconButton(
                tooltip: 'הקודם',
                onPressed: playback.previous,
                icon: Icon(Icons.skip_previous_rounded,
                    color: _fg, size: widget.onVideo ? 24 : 28),
              ),
              Container(
                decoration: BoxDecoration(
                    gradient: AppTheme.accentGradient, shape: BoxShape.circle),
                child: IconButton(
                  tooltip: playback.isPlaying ? 'השהה' : 'נגן',
                  onPressed: playback.togglePlay,
                  icon: Icon(
                      playback.isPlaying
                          ? Icons.pause_rounded
                          : Icons.play_arrow_rounded,
                      color: Colors.white,
                      size: widget.onVideo ? 26 : 32),
                ),
              ),
              IconButton(
                tooltip: 'הבא',
                onPressed: playback.queue.isEmpty ? null : playback.next,
                icon: Icon(Icons.skip_next_rounded,
                    color: playback.queue.isEmpty ? _dim : _fg,
                    size: widget.onVideo ? 24 : 28),
              ),
              IconButton(
                tooltip: '10 ש׳ ⏩',
                onPressed: () => playback.seekRelative(10),
                icon: Icon(Icons.forward_10_rounded,
                    color: _fg, size: widget.onVideo ? 22 : 26),
              ),
              IconButton(
                tooltip: 'אהבתי',
                onPressed: () async {
                  await appLibrary.toggleLike(video);
                  if (mounted) setState(() {});
                },
                icon: Icon(liked ? Icons.favorite : Icons.favorite_border,
                    color: liked ? AppTheme.accent : _fg,
                    size: widget.onVideo ? 21 : 24),
              ),
            ],
          ),
        ),
      ],
    );

    if (!widget.onVideo) return content;

    // מעל הווידאו: הצללה מלמטה, כדי שהבקרים ייקראו גם מעל תמונה בהירה.
    return DecoratedBox(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Colors.transparent, Colors.black87],
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.only(top: 22),
        child: content,
      ),
    );
  }
}

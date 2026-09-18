import 'dart:async';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';
import 'package:youtube_player_iframe/youtube_player_iframe.dart';

import '../data/app_state.dart';
import '../data/library_store.dart';
import '../data/playback.dart';
import '../data/settings_store.dart';
import '../models/video.dart';
import '../theme.dart';
import 'widgets/common.dart';
import 'widgets/seek_bar.dart';
import 'widgets/video_actions.dart';
import 'widgets/video_row.dart';

/// עטיפת האלבום שמכסה את הווידאו במצב אודיו בלבד.
///
/// הנגן ממשיך לרוץ מתחתיה — הוא חייב, אחרת אין קול. מה שמשתנה הוא רק מה
/// שהעין רואה, וזו בדיוק המשמעות של "אודיו בלבד" כאן.
class AudioOnlyCover extends StatelessWidget {
  const AudioOnlyCover({super.key});

  @override
  Widget build(BuildContext context) {
    final video = playback.current;
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [AppTheme.bg2, AppTheme.bg],
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
        ),
      ),
      alignment: Alignment.center,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final art = constraints.maxHeight * 0.62;
          return Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (video != null)
                ClipRRect(
                  borderRadius: BorderRadius.circular(art * 0.12),
                  child: CachedNetworkImage(
                    imageUrl: video.thumbnailUrl,
                    width: art,
                    height: art,
                    fit: BoxFit.cover,
                    errorWidget: (_, __, ___) => Container(
                      width: art,
                      height: art,
                      color: AppTheme.card,
                      child: Icon(Icons.music_note, color: AppTheme.accent),
                    ),
                  ),
                ),
              if (constraints.maxHeight > 90) ...[
                const SizedBox(height: 10),
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.headphones, color: AppTheme.accent, size: 15),
                    const SizedBox(width: 6),
                    Text('מצב אודיו',
                        style: TextStyle(
                            color: AppTheme.subtext2, fontSize: 11.5)),
                  ],
                ),
              ],
            ],
          );
        },
      ),
    );
  }
}

/// מסך הנגן המלא. הווידאו עצמו מרחף מעליו (ראה [AppShell]), ולכן כאן
/// שמור לו מקום בגובה [videoSlotHeight] בלבד.
class PlayerView extends StatefulWidget {
  final double videoSlotHeight;
  const PlayerView({super.key, required this.videoSlotHeight});

  @override
  State<PlayerView> createState() => _PlayerViewState();
}

class _PlayerViewState extends State<PlayerView> {
  Duration _position = Duration.zero;
  StreamSubscription<YoutubeVideoState>? _sub;

  @override
  void initState() {
    super.initState();
    _listen();
  }

  void _listen() {
    _sub?.cancel();
    final controller = playback.controller;
    if (controller == null) return;
    _sub = controller.videoStateStream.listen((state) {
      if (!mounted) return;
      setState(() => _position = state.position);
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

  @override
  Widget build(BuildContext context) {
    final video = playback.current;
    if (video == null) return const SizedBox.shrink();
    final safeTop = MediaQuery.of(context).padding.top;
    // סגנון 2 = בקרים על הווידאו; סגנון 1 = "מתנגן עכשיו" עם בקרים מתחת.
    final overlayControls = appSettings.playerStyle == 2;

    return Material(
      color: AppTheme.bg,
      child: Column(
        children: [
          SizedBox(height: safeTop),
          _topBar(video),
          // מקום שמור לווידאו. מחוות ההחלקה יושבות כאן ולא על ה-WebView:
          // ה-WebView בולע מחוות, ולכן מחווה שהוגדרה עליו פשוט לא עובדת.
          GestureDetector(
            behavior: HitTestBehavior.opaque,
            onVerticalDragEnd: appSettings.videoSwipeDismiss
                ? (d) {
                    if ((d.primaryVelocity ?? 0) > 260) {
                      playback.setExpanded(false);
                    }
                  }
                : null,
            onHorizontalDragEnd: appSettings.videoSwipeTrack
                ? (d) {
                    final v = d.primaryVelocity ?? 0;
                    if (v.abs() < 240) return;
                    v < 0 ? playback.next() : playback.previous();
                  }
                : null,
            // דאבל-טאפ לדילוג 10 שניות עובד תמיד, ואינו מושפע מהמתגים.
            onDoubleTapDown: (details) {
              final width = MediaQuery.of(context).size.width;
              playback.seekRelative(
                  details.localPosition.dx < width / 2 ? -10 : 10);
            },
            onDoubleTap: () {},
            child: SizedBox(
              height: widget.videoSlotHeight,
              width: double.infinity,
              child: playback.hasError ? _errorOverlay(video) : null,
            ),
          ),
          if (overlayControls) const SizedBox(height: 4),
          _progress(),
          _controls(video),
          Divider(height: 1, color: AppTheme.divider),
          Expanded(child: _details(video)),
        ],
      ),
    );
  }

  Widget _topBar(Video video) {
    return Row(
      children: [
        IconButton(
          tooltip: 'כווץ',
          onPressed: () => playback.setExpanded(false),
          icon: Icon(Icons.keyboard_arrow_down_rounded,
              color: AppTheme.text, size: 28),
        ),
        Expanded(
          child: Text(
            playback.musicMode ? 'מתנגן עכשיו' : 'FilterTube',
            style: TextStyle(
                color: AppTheme.subtext, fontSize: 12.5, letterSpacing: 0.4),
          ),
        ),
        IconButton(
          tooltip: 'פעולות',
          onPressed: () =>
              showVideoActions(context, video, musicMode: playback.musicMode),
          icon: Icon(Icons.more_vert, color: AppTheme.text),
        ),
      ],
    );
  }

  Widget _progress() {
    final total = playback.duration.inMilliseconds == 0
        ? 1
        : playback.duration.inMilliseconds;
    final value = (_position.inMilliseconds / total).clamp(0.0, 1.0);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12),
      child: Row(
        children: [
          Text(_fmt(_position),
              style: TextStyle(color: AppTheme.subtext, fontSize: 11)),
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
              style: TextStyle(color: AppTheme.subtext, fontSize: 11)),
        ],
      ),
    );
  }

  Widget _controls(Video video) {
    final liked = appLibrary.isLiked(video.id);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          IconButton(
            tooltip: '⏪ 10 ש׳',
            onPressed: () => playback.seekRelative(-10),
            icon: Icon(Icons.replay_10_rounded, color: AppTheme.text, size: 26),
          ),
          IconButton(
            tooltip: 'הקודם',
            onPressed: playback.previous,
            icon: Icon(Icons.skip_previous_rounded,
                color: AppTheme.text, size: 28),
          ),
          Container(
            decoration:
                BoxDecoration(gradient: AppTheme.accentGradient, shape: BoxShape.circle),
            child: IconButton(
              tooltip: playback.isPlaying ? 'השהה' : 'נגן',
              onPressed: playback.togglePlay,
              icon: Icon(
                  playback.isPlaying
                      ? Icons.pause_rounded
                      : Icons.play_arrow_rounded,
                  color: Colors.white,
                  size: 32),
            ),
          ),
          IconButton(
            tooltip: 'הבא',
            onPressed: playback.queue.isEmpty ? null : playback.next,
            icon: Icon(Icons.skip_next_rounded,
                color: playback.queue.isEmpty ? AppTheme.divider : AppTheme.text,
                size: 28),
          ),
          IconButton(
            tooltip: '10 ש׳ ⏩',
            onPressed: () => playback.seekRelative(10),
            icon: Icon(Icons.forward_10_rounded, color: AppTheme.text, size: 26),
          ),
          IconButton(
            tooltip: 'אהבתי',
            onPressed: () async {
              await appLibrary.toggleLike(video);
              if (mounted) setState(() {});
            },
            icon: Icon(liked ? Icons.favorite : Icons.favorite_border,
                color: liked ? AppTheme.accent : AppTheme.text, size: 24),
          ),
        ],
      ),
    );
  }

  Widget _details(Video video) {
    final upNext = playback.queue;
    return ListView(
      padding: const EdgeInsets.only(top: 10, bottom: 28),
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(video.title,
                  style: TextStyle(
                      color: AppTheme.text,
                      fontSize: 16,
                      height: 1.35,
                      fontWeight: FontWeight.bold)),
              const SizedBox(height: 4),
              Text(
                [
                  video.channelName,
                  if (video.formattedViewCount().isNotEmpty)
                    video.formattedViewCount(),
                  if (video.timeAgoHe() != 'תאריך לא זמין') video.timeAgoHe(),
                ].join(' · '),
                style: TextStyle(color: AppTheme.subtext, fontSize: 12),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        SizedBox(
          height: 78,
          child: ListView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            children: [
              _chip(Icons.radio, 'רדיו מהשיר', _startStation),
              _chip(
                playback.audioOnly ? Icons.videocam : Icons.headphones,
                playback.audioOnly ? 'הצג וידאו' : 'אודיו בלבד',
                _toggleAudio,
              ),
              _chip(Icons.speed, 'מהירות ${_speedLabel()}', _pickSpeed),
              _chip(
                  Icons.bedtime_outlined,
                  playback.sleepMinutesLeft > 0
                      ? 'טיימר ${playback.sleepMinutesLeft} דק׳'
                      : 'טיימר שינה',
                  _pickSleep),
              _chip(Icons.hd_outlined, 'איכות ${_qualityLabel()}', _pickQuality),
              _chip(Icons.playlist_add, 'הוסף לאלבום',
                  () => showPlaylistPicker(context, video)),
              _chip(Icons.share, 'שתף', () {
                SharePlus.instance.share(
                    ShareParams(uri: Uri.parse('https://youtu.be/${video.id}')));
              }),
              _chip(Icons.flag_outlined, 'דווח',
                  () => showReportDialog(context, video)),
            ],
          ),
        ),
        Divider(height: 20, color: AppTheme.divider),
        Padding(
          padding: const EdgeInsets.fromLTRB(14, 0, 14, 8),
          child: Row(
            children: [
              Text(playback.stationMode ? 'התחנה שלך' : 'הבא בתור',
                  style: TextStyle(
                      color: AppTheme.text,
                      fontWeight: FontWeight.bold,
                      fontSize: 14)),
              const Spacer(),
              if (upNext.length > 1)
                TextButton.icon(
                  onPressed: playback.shuffleQueue,
                  icon: Icon(Icons.shuffle, size: 16, color: AppTheme.accent),
                  label: Text('ערבוב',
                      style: TextStyle(color: AppTheme.accent, fontSize: 12)),
                ),
            ],
          ),
        ),
        if (upNext.isEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            child: Text('אין סרטונים בתור',
                style: TextStyle(color: AppTheme.subtext, fontSize: 12.5)),
          )
        else
          ...upNext.asMap().entries.map(
                (entry) => VideoListTile(
                  video: entry.value,
                  dense: true,
                  onTap: () => playback.play(entry.value,
                      queue: upNext.sublist(entry.key + 1),
                      music: playback.musicMode,
                      open: false),
                  trailing: IconButton(
                    icon: Icon(Icons.close, size: 16, color: AppTheme.subtext),
                    onPressed: () => playback.removeFromQueue(entry.value.id),
                  ),
                ),
              ),
      ],
    );
  }

  Widget _chip(IconData icon, String label, VoidCallback onTap) {
    return Padding(
      padding: const EdgeInsets.only(left: 8),
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          width: 84,
          padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 6),
          decoration: BoxDecoration(
            color: AppTheme.card,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: AppTheme.divider),
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, color: AppTheme.accent, size: 19),
              const SizedBox(height: 5),
              Text(label,
                  maxLines: 2,
                  textAlign: TextAlign.center,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(color: AppTheme.subtext2, fontSize: 10.5)),
            ],
          ),
        ),
      ),
    );
  }

  String _speedLabel() =>
      '${(appSettings.playbackSpeed / 100).toStringAsFixed(2).replaceFirst(RegExp(r'\.?0+$'), '')}x';

  String _qualityLabel() => appSettings.preferredQuality == 0
      ? 'אוטומטי'
      : '${appSettings.preferredQuality}p';

  void _toggleAudio() {
    playback.toggleAudioOnly();
    setState(() {});
  }

  Future<void> _startStation() async {
    final seed = playback.current;
    if (seed == null) return;
    showToast(context, 'בונה תחנה מ״${seed.title.characters.take(28)}״…');
    final station = await appState.stations.stationForSeed(
      seed,
      level: appSettings.filterLevel,
      gender: appSettings.userGender,
      pool: appState.videos,
    );
    if (!mounted) return;
    if (station.isEmpty) {
      showToast(context, 'לא מצאנו מספיק סרטונים באותו סגנון בערוצים המאושרים');
      return;
    }
    await playback.play(station.first,
        queue: station.sublist(1), music: playback.musicMode, open: false);
  }

  void _pickSpeed() => _pickOption<int>(
        'מהירות נגינה',
        const [50, 75, 100, 125, 150, 175, 200],
        (v) => '${(v / 100).toStringAsFixed(2).replaceFirst(RegExp(r'\.?0+$'), '')}x',
        appSettings.playbackSpeed,
        (v) async {
          await playback.setSpeed(v);
          if (mounted) setState(() {});
        },
      );

  void _pickSleep() => _pickOption<int>(
        'טיימר שינה',
        const [0, 15, 30, 45, 60, 90],
        (v) => v == 0 ? 'כבוי' : '$v דקות',
        playback.sleepMinutesLeft,
        (v) {
          playback.setSleepTimer(v);
          if (mounted) setState(() {});
        },
      );

  /// איכות הצפייה מועברת לנגן ההטמעה כהצעה. יוטיוב שומרת לעצמה את ההחלטה
  /// הסופית לפי רוחב הפס, ולכן זו העדפה ולא נעילה.
  void _pickQuality() => _pickOption<int>(
        'איכות צפייה',
        const [0, 1080, 720, 480, 360, 240, 144],
        (v) => v == 0 ? 'אוטומטי' : '${v}p',
        appSettings.preferredQuality,
        (v) async {
          await appSettings.setPreferredQuality(v);
          if (mounted) setState(() {});
        },
      );

  void _pickOption<T>(String title, List<T> options, String Function(T) label,
      T current, void Function(T) onPick) {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: AppTheme.surface,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(18))),
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.all(14),
              child: Text(title,
                  style: TextStyle(
                      color: AppTheme.text,
                      fontSize: 15,
                      fontWeight: FontWeight.bold)),
            ),
            ...options.map((o) => ListTile(
                  dense: true,
                  title: Text(label(o),
                      style: TextStyle(color: AppTheme.text, fontSize: 14)),
                  trailing: o == current
                      ? Icon(Icons.check, color: AppTheme.accent)
                      : null,
                  onTap: () {
                    Navigator.pop(sheetContext);
                    onPick(o);
                  },
                )),
            const SizedBox(height: 10),
          ],
        ),
      ),
    );
  }

  Widget _errorOverlay(Video video) {
    return Container(
      color: Colors.black,
      padding: const EdgeInsets.all(16),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.error_outline, color: Colors.white54, size: 36),
          const SizedBox(height: 8),
          const Text('הסרטון הזה אינו זמין לניגון כאן.',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.white, fontSize: 13)),
          const SizedBox(height: 12),
          if (playback.queue.isNotEmpty)
            FilledButton.icon(
              onPressed: playback.next,
              icon: const Icon(Icons.skip_next, size: 18),
              label: const Text('הבא בתור'),
              style: FilledButton.styleFrom(backgroundColor: AppTheme.accent),
            ),
        ],
      ),
    );
  }
}

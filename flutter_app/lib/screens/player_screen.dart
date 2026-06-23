import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:youtube_player_iframe/youtube_player_iframe.dart';
import '../models.dart';
import '../theme.dart';
import '../settings.dart';
import '../youtube_api.dart';
import '../channels_repo.dart';
import '../widgets/video_card.dart';

/// נגן וידאו מבוסס IFrame רשמי. ה-UI של יוטיוב מוסתר/נעול (showControls:false +
/// pointerEvents:none), והשלט שלנו יושב **מתחת** לווידאו כך שהוא תמיד אמין
/// (בלי קונפליקט גסטורות מול ה-WebView). מתחת — "הבא בתור" + ניגון רציף.
class PlayerScreen extends StatefulWidget {
  final Video video;
  final YoutubeApi api;
  final ChannelsRepo channels;

  const PlayerScreen({
    super.key,
    required this.video,
    required this.api,
    required this.channels,
  });

  @override
  State<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends State<PlayerScreen> {
  late YoutubePlayerController _controller;
  late Video _current;
  List<Video> _upNext = [];
  bool _advancing = false;
  bool _audioOnly = false;
  double _speed = 1.0;
  Timer? _sleepTimer;
  int _sleepMinutes = 0;
  StreamSubscription<YoutubePlayerValue>? _sub;

  @override
  void initState() {
    super.initState();
    _current = widget.video;
    _audioOnly =
        widget.channels.isAudioOnly(_current.channelId, appSettings.filterLevel);
    _controller = YoutubePlayerController(
      params: const YoutubePlayerParams(
        showControls: false,
        showFullscreenButton: false,
        enableCaption: false,
        interfaceLanguage: 'he',
        strictRelatedVideos: true,
        pointerEvents: PointerEvents.none,
      ),
    );
    _controller.loadVideoById(videoId: _current.id);
    _sub = _controller.listen((value) {
      if (value.playerState == PlayerState.ended &&
          !_advancing &&
          _upNext.isNotEmpty) {
        _advancing = true;
        _playVideo(_upNext.first);
      }
    });
    _loadUpNext();
  }

  Future<void> _loadUpNext() async {
    final ch = widget.channels.channels
        .where((c) => c.id == _current.channelId)
        .cast<Channel?>()
        .firstWhere((c) => c != null, orElse: () => null);
    if (ch == null) return;
    final vids = await widget.api.channelUploads(ch);
    if (!mounted) return;
    setState(() => _upNext = vids.where((v) => v.id != _current.id).toList());
  }

  void _playVideo(Video v) {
    setState(() {
      _current = v;
      _upNext = [];
      _advancing = false;
      _audioOnly = widget.channels.isAudioOnly(v.channelId, appSettings.filterLevel);
    });
    _controller.loadVideoById(videoId: v.id);
    _loadUpNext();
  }

  void _copyLink() {
    Clipboard.setData(ClipboardData(text: 'https://youtu.be/${_current.id}'));
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('הקישור הועתק'), duration: Duration(seconds: 1)),
    );
  }

  Widget _overflowMenu() {
    return PopupMenuButton<String>(
      icon: const Icon(Icons.more_vert, color: AppTheme.text),
      color: AppTheme.card,
      onSelected: (v) {
        switch (v) {
          case 'audio':
            setState(() => _audioOnly = !_audioOnly);
            break;
          case 'speed':
            _pickSpeed();
            break;
          case 'sleep':
            _pickSleep();
            break;
          case 'copy':
            _copyLink();
            break;
          case 'youtube':
            _openInYoutube();
            break;
        }
      },
      itemBuilder: (context) => [
        PopupMenuItem(
          value: 'audio',
          child: Row(children: [
            Icon(_audioOnly ? Icons.videocam : Icons.headphones,
                color: AppTheme.text, size: 20),
            const SizedBox(width: 10),
            Text(_audioOnly ? 'הצג וידאו' : 'אודיו בלבד',
                style: const TextStyle(color: AppTheme.text)),
          ]),
        ),
        PopupMenuItem(
          value: 'speed',
          child: Row(children: [
            const Icon(Icons.speed, color: AppTheme.text, size: 20),
            const SizedBox(width: 10),
            Text('מהירות (${_speed}x)',
                style: const TextStyle(color: AppTheme.text)),
          ]),
        ),
        PopupMenuItem(
          value: 'sleep',
          child: Row(children: [
            const Icon(Icons.bedtime, color: AppTheme.text, size: 20),
            const SizedBox(width: 10),
            Text(_sleepMinutes > 0 ? 'טיימר ($_sleepMinutes דק׳)' : 'טיימר שינה',
                style: const TextStyle(color: AppTheme.text)),
          ]),
        ),
        const PopupMenuItem(
          value: 'copy',
          child: Row(children: [
            Icon(Icons.link, color: AppTheme.text, size: 20),
            SizedBox(width: 10),
            Text('העתק קישור', style: TextStyle(color: AppTheme.text)),
          ]),
        ),
        const PopupMenuItem(
          value: 'youtube',
          child: Row(children: [
            Icon(Icons.open_in_new, color: AppTheme.text, size: 20),
            SizedBox(width: 10),
            Text('פתח ביוטיוב', style: TextStyle(color: AppTheme.text)),
          ]),
        ),
      ],
    );
  }

  Future<void> _seekRelative(int seconds) async {
    final pos = await _controller.currentTime;
    _controller.seekTo(seconds: pos + seconds, allowSeekAhead: true);
  }

  Future<void> _openInYoutube() async {
    final uri = Uri.parse('https://www.youtube.com/watch?v=${_current.id}');
    await launchUrl(uri, mode: LaunchMode.externalApplication);
  }

  String _fmt(Duration d) {
    final m = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final s = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    final h = d.inHours;
    return h > 0 ? '$h:$m:$s' : '$m:$s';
  }

  void _setSpeed(double rate) {
    setState(() => _speed = rate);
    _controller.setPlaybackRate(rate);
  }

  void _setSleep(int minutes) {
    _sleepTimer?.cancel();
    setState(() => _sleepMinutes = minutes);
    if (minutes > 0) {
      _sleepTimer = Timer(Duration(minutes: minutes), () {
        _controller.pauseVideo();
        if (mounted) setState(() => _sleepMinutes = 0);
      });
    }
  }

  void _pickSpeed() {
    _showSheet('מהירות ניגון', const [0.5, 0.75, 1.0, 1.25, 1.5, 2.0],
        (v) => '${v}x', _speed, (v) => _setSpeed(v as double));
  }

  void _pickSleep() {
    _showSheet('טיימר שינה', const [0, 15, 30, 45, 60],
        (v) => v == 0 ? 'כבוי' : '$v דקות', _sleepMinutes,
        (v) => _setSleep(v as int));
  }

  void _showSheet(String title, List<Object> options, String Function(Object) label,
      Object current, void Function(Object) onPick) {
    showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.surface,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(18))),
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.all(14),
              child: Text(title,
                  style: const TextStyle(
                      color: AppTheme.text,
                      fontSize: 15,
                      fontWeight: FontWeight.bold)),
            ),
            ...options.map((o) => ListTile(
                  title: Text(label(o),
                      style: const TextStyle(color: AppTheme.text)),
                  trailing: o == current
                      ? const Icon(Icons.check, color: AppTheme.accent)
                      : null,
                  onTap: () {
                    Navigator.pop(context);
                    onPick(o);
                  },
                )),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    _sub?.cancel();
    _sleepTimer?.cancel();
    _controller.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bg,
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _videoArea(),
            _controlBar(),
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 8, 2, 2),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Text(_current.title,
                        style: const TextStyle(
                            color: AppTheme.text,
                            fontSize: 16,
                            fontWeight: FontWeight.bold)),
                  ),
                  _overflowMenu(),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Text(_current.channelTitle,
                  style: const TextStyle(color: AppTheme.subtext, fontSize: 13)),
            ),
            const SizedBox(height: 6),
            const Divider(color: Color(0xFF272727), height: 1),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.only(top: 8),
                children: [
                  const Padding(
                    padding: EdgeInsets.fromLTRB(12, 4, 12, 8),
                    child: Text('הבא בתור',
                        style: TextStyle(
                            color: AppTheme.text,
                            fontWeight: FontWeight.bold,
                            fontSize: 14)),
                  ),
                  ..._upNext.map((v) =>
                      VideoListTile(video: v, onTap: () => _playVideo(v))),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _videoArea() {
    return GestureDetector(
      // החלקה למטה על הווידאו = מזעור/חזרה
      onVerticalDragEnd: (d) {
        if ((d.primaryVelocity ?? 0) > 250) Navigator.of(context).maybePop();
      },
      child: AspectRatio(
        aspectRatio: 16 / 9,
        child: Stack(
          fit: StackFit.expand,
          children: [
            // הנגן: מלא במצב וידאו; 1x1 במצב אודיו (ממשיך לנגן קול בלי להציג וידאו)
            Align(
              alignment: Alignment.topLeft,
              child: SizedBox(
                width: _audioOnly ? 1 : double.infinity,
                height: _audioOnly ? 1 : double.infinity,
                child: YoutubePlayer(controller: _controller, aspectRatio: 16 / 9),
              ),
            ),
            // מצב אודיו — אלבום-ארט במקום הווידאו
            if (_audioOnly) Positioned.fill(child: _albumArt()),
            // שכבת הסתרה עליונה — מכסה כותרת/לוגו/שיתוף של יוטיוב (רק במצב וידאו)
            if (!_audioOnly)
              const IgnorePointer(
                child: Align(
                  alignment: Alignment.topCenter,
                  child: SizedBox(
                    height: 42,
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          begin: Alignment.topCenter,
                          end: Alignment.bottomCenter,
                          colors: [Colors.black87, Colors.transparent],
                        ),
                      ),
                      child: SizedBox.expand(),
                    ),
                  ),
                ),
              ),
            Align(
              alignment: Alignment.topLeft,
              child: IconButton(
                icon: const Icon(Icons.keyboard_arrow_down,
                    color: Colors.white, size: 28),
                onPressed: () => Navigator.of(context).maybePop(),
              ),
            ),
            // שגיאת ניגון (סרטון נדיר שאסור להטמעה / לא זמין)
            YoutubeValueBuilder(
              controller: _controller,
              builder: (context, value) => value.error != YoutubeError.none
                  ? Positioned.fill(child: _errorOverlay(value.error))
                  : const SizedBox.shrink(),
            ),
          ],
        ),
      ),
    );
  }

  Widget _albumArt() {
    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          colors: [Color(0xFF1A1A1F), Color(0xFF0B0B0D)],
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
        ),
      ),
      alignment: Alignment.center,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 110,
            height: 110,
            clipBehavior: Clip.antiAlias,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(16),
              boxShadow: const [BoxShadow(color: Colors.black54, blurRadius: 18)],
            ),
            child: CachedNetworkImage(
              imageUrl: _current.thumbnail,
              fit: BoxFit.cover,
              errorWidget: (c, _, __) => Container(
                  color: AppTheme.card,
                  child: const Icon(Icons.music_note, color: AppTheme.accent)),
            ),
          ),
          const SizedBox(height: 12),
          const Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.headphones, color: AppTheme.accent, size: 16),
              SizedBox(width: 6),
              Text('מצב אודיו',
                  style: TextStyle(color: AppTheme.subtext2, fontSize: 12)),
            ],
          ),
        ],
      ),
    );
  }

  Widget _controlBar() {
    return Container(
      color: AppTheme.surface,
      padding: const EdgeInsets.fromLTRB(6, 2, 6, 6),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // פס התקדמות + זמנים
          StreamBuilder<YoutubeVideoState>(
            stream: _controller.videoStateStream,
            builder: (context, snapshot) {
              final pos = snapshot.data?.position ?? Duration.zero;
              final dur = _controller.metadata.duration;
              final total = dur.inMilliseconds == 0 ? 1 : dur.inMilliseconds;
              final value = (pos.inMilliseconds / total).clamp(0.0, 1.0);
              return Row(
                children: [
                  Text(_fmt(pos),
                      style:
                          const TextStyle(color: AppTheme.subtext, fontSize: 11)),
                  Expanded(
                    child: SliderTheme(
                      data: SliderTheme.of(context).copyWith(
                        trackHeight: 2.5,
                        thumbShape:
                            const RoundSliderThumbShape(enabledThumbRadius: 6),
                        overlayShape:
                            const RoundSliderOverlayShape(overlayRadius: 14),
                        activeTrackColor: AppTheme.accent,
                        inactiveTrackColor: Colors.white24,
                        thumbColor: AppTheme.accent,
                      ),
                      child: Slider(
                        value: value,
                        onChanged: (v) => _controller.seekTo(
                            seconds: v * total / 1000, allowSeekAhead: false),
                        onChangeEnd: (v) => _controller.seekTo(
                            seconds: v * total / 1000, allowSeekAhead: true),
                      ),
                    ),
                  ),
                  Text(_fmt(dur),
                      style:
                          const TextStyle(color: AppTheme.subtext, fontSize: 11)),
                ],
              );
            },
          ),
          // כפתורי שליטה
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              _btn(Icons.replay_10, () => _seekRelative(-10)),
              _btn(Icons.skip_previous,
                  () => _controller.seekTo(seconds: 0, allowSeekAhead: true)),
              YoutubeValueBuilder(
                controller: _controller,
                builder: (context, value) {
                  final playing = value.playerState == PlayerState.playing;
                  return Container(
                    decoration: const BoxDecoration(
                        color: AppTheme.accent, shape: BoxShape.circle),
                    child: IconButton(
                      icon: Icon(playing ? Icons.pause : Icons.play_arrow,
                          color: Colors.white, size: 30),
                      onPressed: () => playing
                          ? _controller.pauseVideo()
                          : _controller.playVideo(),
                    ),
                  );
                },
              ),
              _btn(Icons.skip_next,
                  _upNext.isNotEmpty ? () => _playVideo(_upNext.first) : null),
              _btn(Icons.forward_10, () => _seekRelative(10)),
              _btn(Icons.fullscreen, () => _controller.enterFullScreen()),
            ],
          ),
        ],
      ),
    );
  }

  Widget _btn(IconData icon, VoidCallback? onTap) {
    return IconButton(
      icon: Icon(icon,
          color: onTap == null ? AppTheme.subtext : AppTheme.text, size: 26),
      onPressed: onTap,
    );
  }

  Widget _errorOverlay(YoutubeError error) {
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
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              OutlinedButton.icon(
                onPressed: _openInYoutube,
                icon: const Icon(Icons.open_in_new, size: 18),
                label: const Text('פתח ביוטיוב'),
                style: OutlinedButton.styleFrom(foregroundColor: Colors.white),
              ),
              const SizedBox(width: 10),
              if (_upNext.isNotEmpty)
                FilledButton.icon(
                  onPressed: () => _playVideo(_upNext.first),
                  icon: const Icon(Icons.skip_next, size: 18),
                  label: const Text('הבא בתור'),
                  style:
                      FilledButton.styleFrom(backgroundColor: AppTheme.accent),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

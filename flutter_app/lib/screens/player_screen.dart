import 'dart:async';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:youtube_player_iframe/youtube_player_iframe.dart';
import '../models.dart';
import '../theme.dart';
import '../youtube_api.dart';
import '../channels_repo.dart';
import '../widgets/video_card.dart';

/// נגן וידאו מבוסס IFrame רשמי. ה-UI של יוטיוב מוסתר/נעול (showControls:false +
/// pointerEvents:none), והשלט שלנו **צף על הווידאו** (מופיע/נעלם בלחיצה).
/// מתחת — "הבא בתור" + ניגון רציף אוטומטי.
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
  bool _controlsVisible = true;
  bool _advancing = false;
  Timer? _hideTimer;
  StreamSubscription<YoutubePlayerValue>? _sub;

  @override
  void initState() {
    super.initState();
    _current = widget.video;
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
    _scheduleHide();
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

  void _scheduleHide() {
    _hideTimer?.cancel();
    _hideTimer = Timer(const Duration(seconds: 3), () {
      if (mounted) setState(() => _controlsVisible = false);
    });
  }

  void _toggleControls() {
    setState(() => _controlsVisible = !_controlsVisible);
    if (_controlsVisible) _scheduleHide();
  }

  void _playVideo(Video v) {
    setState(() {
      _current = v;
      _upNext = [];
      _advancing = false;
      _controlsVisible = true;
    });
    _controller.loadVideoById(videoId: v.id);
    _loadUpNext();
    _scheduleHide();
  }

  Future<void> _seekRelative(int seconds) async {
    final pos = await _controller.currentTime;
    _controller.seekTo(seconds: pos + seconds, allowSeekAhead: true);
    _scheduleHide();
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

  @override
  void dispose() {
    _hideTimer?.cancel();
    _sub?.cancel();
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
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 12, 12, 2),
              child: Text(_current.title,
                  style: const TextStyle(
                      color: AppTheme.text,
                      fontSize: 16,
                      fontWeight: FontWeight.bold)),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Text(_current.channelTitle,
                  style: const TextStyle(color: AppTheme.subtext, fontSize: 13)),
            ),
            const SizedBox(height: 8),
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
      onVerticalDragEnd: (d) {
        if ((d.primaryVelocity ?? 0) > 250) Navigator.of(context).maybePop();
      },
      child: AspectRatio(
        aspectRatio: 16 / 9,
        child: ColoredBox(
          color: Colors.black,
          child: Stack(
            fit: StackFit.expand,
            children: [
              YoutubePlayer(controller: _controller, aspectRatio: 16 / 9),
              // שגיאת ניגון נדירה (אסור להטמעה / לא זמין)
              YoutubeValueBuilder(
                controller: _controller,
                builder: (context, value) => value.error != YoutubeError.none
                    ? _errorOverlay(value.error)
                    : const SizedBox.shrink(),
              ),
              // שלט צף — לחיצה מציגה/מסתירה
              Positioned.fill(
                child: GestureDetector(
                  behavior: HitTestBehavior.opaque,
                  onTap: _toggleControls,
                  child: AnimatedOpacity(
                    opacity: _controlsVisible ? 1 : 0,
                    duration: const Duration(milliseconds: 200),
                    child: IgnorePointer(
                      ignoring: !_controlsVisible,
                      child: _floatingControls(),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _floatingControls() {
    return Container(
      color: Colors.black38,
      child: Column(
        children: [
          // שורה עליונה
          Row(
            children: [
              IconButton(
                icon: const Icon(Icons.keyboard_arrow_down,
                    color: Colors.white, size: 28),
                onPressed: () => Navigator.of(context).maybePop(),
              ),
              const Spacer(),
              IconButton(
                icon: const Icon(Icons.fullscreen, color: Colors.white),
                onPressed: () => _controller.enterFullScreen(),
              ),
            ],
          ),
          const Spacer(),
          // מרכז — ניגון ודילוג
          YoutubeValueBuilder(
            controller: _controller,
            builder: (context, value) {
              final playing = value.playerState == PlayerState.playing;
              return Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  _cBtn(Icons.skip_previous, 30,
                      () => _controller.seekTo(seconds: 0, allowSeekAhead: true)),
                  _cBtn(Icons.replay_10, 30, () => _seekRelative(-10)),
                  Container(
                    margin: const EdgeInsets.symmetric(horizontal: 8),
                    decoration: const BoxDecoration(
                        color: AppTheme.accent, shape: BoxShape.circle),
                    child: IconButton(
                      icon: Icon(playing ? Icons.pause : Icons.play_arrow,
                          color: Colors.white, size: 36),
                      onPressed: () {
                        playing
                            ? _controller.pauseVideo()
                            : _controller.playVideo();
                        _scheduleHide();
                      },
                    ),
                  ),
                  _cBtn(Icons.forward_10, 30, () => _seekRelative(10)),
                  _cBtn(Icons.skip_next, 30,
                      _upNext.isNotEmpty ? () => _playVideo(_upNext.first) : null),
                ],
              );
            },
          ),
          const Spacer(),
          // פס התקדמות תחתון
          _seekRow(),
        ],
      ),
    );
  }

  Widget _cBtn(IconData icon, double size, VoidCallback? onTap) {
    return IconButton(
      icon: Icon(icon,
          color: onTap == null ? Colors.white38 : Colors.white, size: size),
      onPressed: onTap,
    );
  }

  Widget _seekRow() {
    return StreamBuilder<YoutubeVideoState>(
      stream: _controller.videoStateStream,
      builder: (context, snapshot) {
        final pos = snapshot.data?.position ?? Duration.zero;
        final dur = _controller.metadata.duration;
        final total = dur.inMilliseconds == 0 ? 1 : dur.inMilliseconds;
        final value = (pos.inMilliseconds / total).clamp(0.0, 1.0);
        return Padding(
          padding: const EdgeInsets.symmetric(horizontal: 6),
          child: Row(
            children: [
              Text(_fmt(pos),
                  style: const TextStyle(color: Colors.white, fontSize: 11)),
              Expanded(
                child: SliderTheme(
                  data: SliderTheme.of(context).copyWith(
                    trackHeight: 2.5,
                    thumbShape:
                        const RoundSliderThumbShape(enabledThumbRadius: 6),
                    overlayShape:
                        const RoundSliderOverlayShape(overlayRadius: 14),
                    activeTrackColor: AppTheme.accent,
                    inactiveTrackColor: Colors.white30,
                    thumbColor: AppTheme.accent,
                  ),
                  child: Slider(
                    value: value,
                    onChanged: (v) => _controller.seekTo(
                        seconds: v * total / 1000, allowSeekAhead: false),
                    onChangeEnd: (v) {
                      _controller.seekTo(
                          seconds: v * total / 1000, allowSeekAhead: true);
                      _scheduleHide();
                    },
                  ),
                ),
              ),
              Text(_fmt(dur),
                  style: const TextStyle(color: Colors.white, fontSize: 11)),
            ],
          ),
        );
      },
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

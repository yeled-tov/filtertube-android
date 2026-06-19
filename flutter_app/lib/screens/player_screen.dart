import 'dart:async';
import 'package:flutter/material.dart';
import 'package:youtube_player_iframe/youtube_player_iframe.dart';
import '../models.dart';
import '../theme.dart';
import '../youtube_api.dart';
import '../channels_repo.dart';
import '../widgets/video_card.dart';

/// נגן וידאו מבוסס IFrame רשמי — השלט המקורי של יוטיוב מוסתר (showControls: false)
/// ומעליו שלט מותאם משלנו (פליי/פעוז, פס התקדמות, דילוג). מתחת — "הבא בתור"
/// מאותו ערוץ (מובטח ברשימה הלבנה).
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
  Timer? _hideTimer;

  @override
  void initState() {
    super.initState();
    _current = widget.video;
    _controller = YoutubePlayerController(
      params: const YoutubePlayerParams(
        showControls: false, // מסתירים את השלט המקורי של יוטיוב
        showFullscreenButton: false,
        enableCaption: false,
        strictRelatedVideos: true,
      ),
    );
    _controller.loadVideoById(videoId: _current.id);
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
    });
    _controller.loadVideoById(videoId: v.id);
    _loadUpNext();
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
            _playerWithControls(),
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 12, 12, 4),
              child: Text(
                _current.title,
                style: const TextStyle(
                    color: AppTheme.text, fontSize: 16, fontWeight: FontWeight.bold),
              ),
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
                  ..._upNext.map((v) => VideoListTile(
                        video: v,
                        onTap: () => _playVideo(v),
                      )),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _playerWithControls() {
    return AspectRatio(
      aspectRatio: 16 / 9,
      child: Stack(
        fit: StackFit.expand,
        children: [
          YoutubePlayer(controller: _controller, aspectRatio: 16 / 9),
          // שכבת מגע שמציגה/מסתירה את השלט
          GestureDetector(
            behavior: HitTestBehavior.translucent,
            onTap: _toggleControls,
          ),
          if (_controlsVisible) _controlsOverlay(),
        ],
      ),
    );
  }

  Widget _controlsOverlay() {
    return Positioned.fill(
      child: Container(
        color: Colors.black26,
        child: Column(
          children: [
            // שורה עליונה — חזרה
            Align(
              alignment: Alignment.topLeft,
              child: IconButton(
                icon: const Icon(Icons.arrow_back, color: Colors.white),
                onPressed: () => Navigator.of(context).maybePop(),
              ),
            ),
            const Spacer(),
            // פליי/פעוז + דילוגים
            YoutubeValueBuilder(
              controller: _controller,
              builder: (context, value) {
                final playing = value.playerState == PlayerState.playing;
                return Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    _ctrlIcon(Icons.replay_10, () => _seekRelative(-10)),
                    const SizedBox(width: 24),
                    _ctrlIcon(playing ? Icons.pause : Icons.play_arrow, () {
                      playing ? _controller.pauseVideo() : _controller.playVideo();
                      _scheduleHide();
                    }, size: 52),
                    const SizedBox(width: 24),
                    _ctrlIcon(Icons.forward_10, () => _seekRelative(10)),
                  ],
                );
              },
            ),
            const Spacer(),
            _progressBar(),
          ],
        ),
      ),
    );
  }

  Widget _ctrlIcon(IconData icon, VoidCallback onTap, {double size = 34}) {
    return IconButton(
      icon: Icon(icon, color: Colors.white, size: size),
      onPressed: onTap,
    );
  }

  Future<void> _seekRelative(int seconds) async {
    final pos = await _controller.currentTime;
    _controller.seekTo(seconds: pos + seconds, allowSeekAhead: true);
    _scheduleHide();
  }

  Widget _progressBar() {
    return StreamBuilder<YoutubeVideoState>(
      stream: _controller.videoStateStream,
      builder: (context, snapshot) {
        final pos = snapshot.data?.position ?? Duration.zero;
        final dur = _controller.metadata.duration;
        final total = dur.inMilliseconds == 0 ? 1 : dur.inMilliseconds;
        final value = (pos.inMilliseconds / total).clamp(0.0, 1.0);
        return Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          child: Row(
            children: [
              Text(_fmt(pos),
                  style: const TextStyle(color: Colors.white, fontSize: 11)),
              Expanded(
                child: SliderTheme(
                  data: SliderTheme.of(context).copyWith(
                    trackHeight: 2,
                    thumbShape:
                        const RoundSliderThumbShape(enabledThumbRadius: 6),
                    activeTrackColor: AppTheme.accent,
                    inactiveTrackColor: Colors.white24,
                    thumbColor: AppTheme.accent,
                  ),
                  child: Slider(
                    value: value,
                    onChanged: (v) {
                      _controller.seekTo(
                          seconds: (v * total / 1000), allowSeekAhead: false);
                    },
                    onChangeEnd: (v) {
                      _controller.seekTo(
                          seconds: (v * total / 1000), allowSeekAhead: true);
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
}

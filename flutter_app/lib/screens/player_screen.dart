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
    });
    _controller.loadVideoById(videoId: v.id);
    _loadUpNext();
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

  @override
  void dispose() {
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
            _controlBar(),
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 10, 12, 2),
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
            YoutubePlayer(controller: _controller, aspectRatio: 16 / 9),
            // שכבת הסתרה עליונה — מכסה כותרת/לוגו/שיתוף של יוטיוב
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

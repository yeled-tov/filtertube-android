import 'dart:async';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:youtube_player_iframe/youtube_player_iframe.dart';

import '../channels_repo.dart';
import '../library.dart';
import '../models.dart';
import '../settings.dart';
import '../theme.dart';
import '../youtube_api.dart';

/// שורטס — פיד אנכי של סרטונים קצרים מהערוצים המאושרים.
///
/// ## נגן אחד, לא נגן לכל עמוד
/// כל YoutubePlayer הוא WebView, ורשימה של WebViews תחנוק כל מכשיר. לכן
/// יש כאן נגן יחיד שממלא את המסך מאחורי ה-PageView, וה-PageView מחזיק רק
/// שכבות מידע שקופות. דפדוף = loadVideoById על אותו נגן.
class ShortsScreen extends StatefulWidget {
  final YoutubeApi api;
  final ChannelsRepo channels;

  /// האם הלשונית מוצגת כרגע.
  ///
  /// הלשוניות יושבות ב-IndexedStack, כלומר המסך הזה בנוי כל הזמן גם כשלא
  /// רואים אותו. בלי הדגל הזה הפיד היה נטען ומתנגן ברקע מרגע פתיחת
  /// האפליקציה — קול שמגיע ממסך שהמשתמש לא נמצא בו.
  final bool active;

  const ShortsScreen({
    super.key,
    required this.api,
    required this.channels,
    required this.active,
  });

  @override
  State<ShortsScreen> createState() => _ShortsScreenState();
}

class _ShortsScreenState extends State<ShortsScreen> {
  late final YoutubePlayerController _controller;
  final _pageController = PageController();

  List<Video> _items = [];
  bool _loading = true;
  int _index = 0;
  bool _playing = true;
  bool _loadedOnce = false;
  StreamSubscription<YoutubePlayerValue>? _sub;

  @override
  void initState() {
    super.initState();
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
    // שורט שנגמר חוזר על עצמו — זו ההתנהגות המוכרת מפיד קצר.
    _sub = _controller.listen((value) {
      if (value.playerState == PlayerState.ended) _controller.playVideo();
      final playing = value.playerState == PlayerState.playing;
      if (playing != _playing && mounted) setState(() => _playing = playing);
    });
    if (widget.active) _activate();
  }

  @override
  void didUpdateWidget(ShortsScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.active && !oldWidget.active) {
      _activate();
    } else if (!widget.active && oldWidget.active) {
      _controller.pauseVideo();
    }
  }

  /// טעינה עצלה בכניסה הראשונה, ומשם ואילך רק המשך ניגון.
  void _activate() {
    if (_loadedOnce) {
      _controller.playVideo();
      return;
    }
    _loadedOnce = true;
    _load();
  }

  @override
  void dispose() {
    _sub?.cancel();
    _pageController.dispose();
    _controller.close();
    super.dispose();
  }

  /// הפיד: העלאות אחרונות מהערוצים המאושרים, מסוננות לאורך קצר.
  ///
  /// ערוצי אודיו-בלבד (דתי לייט, ומוזיקה ברמה מחמירה) לא נכנסים לכאן כלל —
  /// פיד וידאו אנכי הוא בדיוק מה שהם לא אמורים להיות.
  Future<void> _load() async {
    final pool = widget.channels.channels
        .where((c) => !widget.channels.isAudioOnly(c.id, appSettings.filterLevel))
        .take(20)
        .toList();
    final fetched = await Future.wait(
      pool.map((c) => widget.api.channelUploads(c, max: 10)),
    );
    final all = <Video>[];
    for (final list in fetched) {
      all.addAll(list);
    }
    all.sort((a, b) => (b.publishedAt ?? DateTime(2000))
        .compareTo(a.publishedAt ?? DateTime(2000)));
    final shorts = await widget.api.filterShorts(all);
    if (!mounted) return;
    setState(() {
      _items = shorts;
      _loading = false;
    });
    if (shorts.isNotEmpty) _show(0);
  }

  void _togglePlay() {
    if (_playing) {
      _controller.pauseVideo();
    } else {
      _controller.playVideo();
    }
  }

  void _show(int i) {
    if (i < 0 || i >= _items.length) return;
    // דרך setState, אחרת השכבה של העמוד החדש עדיין חושבת שהיא לא הפעילה
    setState(() => _index = i);
    _controller.loadVideoById(videoId: _items[i].id);
    appLibrary.recordWatch(_items[i]);
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(
        backgroundColor: Colors.black,
        body: Center(child: CircularProgressIndicator(color: AppTheme.accent)),
      );
    }
    if (_items.isEmpty) {
      return Scaffold(
        backgroundColor: Colors.black,
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.bolt_rounded, color: AppTheme.subtext, size: 40),
                const SizedBox(height: 12),
                const Text('לא נמצאו סרטונים קצרים בערוצים המאושרים',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: AppTheme.subtext, fontSize: 14)),
                const SizedBox(height: 16),
                TextButton(
                  onPressed: () {
                    setState(() => _loading = true);
                    _load();
                  },
                  child: const Text('נסה שוב',
                      style: TextStyle(color: AppTheme.accent)),
                ),
              ],
            ),
          ),
        ),
      );
    }

    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        fit: StackFit.expand,
        children: [
          // הנגן ממלא את הגובה: תיבה של 16:9 ב-BoxFit.cover גדלה עד שהגובה
          // מתאים, וכך הסרטון האנכי שבתוכה תופס את המסך במקום לשבת כפס דק.
          FittedBox(
            fit: BoxFit.cover,
            child: SizedBox(
              width: 16,
              height: 9,
              child: YoutubePlayer(controller: _controller, aspectRatio: 16 / 9),
            ),
          ),
          PageView.builder(
            controller: _pageController,
            scrollDirection: Axis.vertical,
            itemCount: _items.length,
            onPageChanged: _show,
            itemBuilder: (context, i) => _Overlay(
              video: _items[i],
              // רק העמוד הפעיל מגיב ללחיצה — שכן שמציץ לא אמור לעצור ניגון
              active: i == _index,
              playing: _playing,
              onTogglePlay: _togglePlay,
            ),
          ),
        ],
      ),
    );
  }
}

/// שכבת המידע מעל הסרטון — כותרת, ערוץ, לב וכפתור השהיה.
class _Overlay extends StatefulWidget {
  final Video video;
  final bool active;
  final bool playing;
  final VoidCallback onTogglePlay;

  const _Overlay({
    required this.video,
    required this.active,
    required this.playing,
    required this.onTogglePlay,
  });

  @override
  State<_Overlay> createState() => _OverlayState();
}

class _OverlayState extends State<_Overlay> {
  void _togglePlay() {
    if (!widget.active) return;
    widget.onTogglePlay();
  }

  @override
  Widget build(BuildContext context) {
    final liked = appLibrary.isLiked(widget.video.id);
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: _togglePlay,
      child: Stack(
        fit: StackFit.expand,
        children: [
          // תמונת רקע מטושטשת ממלאת את הפינות שהנגן לא מכסה
          if (!widget.active)
            CachedNetworkImage(
              imageUrl: widget.video.thumbnail,
              fit: BoxFit.cover,
              errorWidget: (c, _, __) => const ColoredBox(color: Colors.black),
            ),
          const IgnorePointer(
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.bottomCenter,
                  end: Alignment.center,
                  colors: [Colors.black87, Colors.transparent],
                ),
              ),
              child: SizedBox.expand(),
            ),
          ),
          if (widget.active && !widget.playing)
            const Center(
              child: Icon(Icons.play_arrow_rounded,
                  color: Colors.white70, size: 68),
            ),
          Positioned(
            right: 12,
            bottom: 120,
            child: Column(
              children: [
                _action(
                  icon: liked ? Icons.favorite : Icons.favorite_border,
                  color: liked ? const Color(0xFFFF3B5C) : Colors.white,
                  onTap: () async {
                    await appLibrary.toggleLike(widget.video);
                    if (mounted) setState(() {});
                  },
                ),
              ],
            ),
          ),
          Positioned(
            right: 14,
            left: 14,
            bottom: 96,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(widget.video.channelTitle,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w700,
                        fontSize: 14)),
                const SizedBox(height: 4),
                Text(widget.video.title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                        color: Colors.white70, fontSize: 12.5, height: 1.35)),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _action({
    required IconData icon,
    required Color color,
    required VoidCallback onTap,
  }) {
    return IconButton(
      iconSize: 32,
      onPressed: onTap,
      icon: Icon(icon, color: color, shadows: const [
        Shadow(color: Colors.black54, blurRadius: 8),
      ]),
    );
  }
}

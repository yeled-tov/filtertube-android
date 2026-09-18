import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';

import '../data/library_store.dart';
import '../data/playback.dart';
import '../models/video.dart';
import '../theme.dart';
import 'widgets/video_actions.dart';

/// נגן ה-Shorts — מסך מלא אנכי עם החלקה למעלה לסרטון הבא.
///
/// ## איך זה עובד מעל נגן אחד
/// הסרטון עצמו הוא אותו WebView יחיד של האפליקציה, שנפרש כאן על כל המסך
/// ביחס 9:16. מה שנמצא ב-PageView הוא **רק שכבת הממשק** — כותרת, ערוץ,
/// לב ופעולות — והדפים שקופים. כך ההחלקה מרגישה כמו טיקטוק בלי לפתוח נגן
/// שני לכל סרטון, ובלי לטעון מחדש את הדף בכל החלקה.
class ShortsPlayerView extends StatefulWidget {
  const ShortsPlayerView({super.key});

  @override
  State<ShortsPlayerView> createState() => _ShortsPlayerViewState();
}

class _ShortsPlayerViewState extends State<ShortsPlayerView> {
  PageController? _controller;
  int _page = 0;

  @override
  void initState() {
    super.initState();
    _syncToCurrent();
  }

  @override
  void didUpdateWidget(covariant ShortsPlayerView oldWidget) {
    super.didUpdateWidget(oldWidget);
    _syncToCurrent();
  }

  /// הסרטון יכול להתחלף גם בלי החלקה (סיום סרטון, פתיחה מהגריד), ואז
  /// הדף צריך להדביק את הפער — אחרת הממשק מציג סרטון אחד והרמקול משמיע
  /// אחר.
  void _syncToCurrent() {
    final index =
        playback.shortsList.indexWhere((v) => v.id == playback.current?.id);
    if (index < 0) return;
    if (_controller == null) {
      _controller = PageController(initialPage: index);
      _page = index;
      return;
    }
    if (index != _page) {
      _page = index;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && _controller!.hasClients) _controller!.jumpToPage(index);
      });
    }
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final shorts = playback.shortsList;
    final controller = _controller;
    if (shorts.isEmpty || controller == null) return const SizedBox.shrink();

    return Material(
      color: Colors.black,
      child: Stack(
        children: [
          PageView.builder(
            controller: controller,
            scrollDirection: Axis.vertical,
            itemCount: shorts.length,
            onPageChanged: (index) {
              _page = index;
              playback.playShortAt(index);
            },
            itemBuilder: (context, index) => _overlay(shorts[index]),
          ),
          Positioned(
            top: MediaQuery.of(context).padding.top + 6,
            right: 8,
            child: IconButton(
              tooltip: 'סגור',
              onPressed: () => playback.setExpanded(false),
              icon: const Icon(Icons.keyboard_arrow_down_rounded,
                  color: Colors.white, size: 30),
            ),
          ),
          Positioned(
            top: MediaQuery.of(context).padding.top + 14,
            left: 0,
            right: 0,
            child: const Center(
              child: Text('Shorts',
                  style: TextStyle(
                      color: Colors.white,
                      fontSize: 15,
                      fontWeight: FontWeight.w800)),
            ),
          ),
        ],
      ),
    );
  }

  /// שכבת הממשק של סרטון אחד. שקופה בכוונה — הווידאו עובר מתחתיה.
  Widget _overlay(Video video) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: playback.togglePlay,
      child: Stack(
        children: [
          // סימון עצירה — בלי זה הקשה על המסך נראית כמו שום דבר.
          if (!playback.isPlaying)
            const Center(
              child: Icon(Icons.play_arrow_rounded,
                  color: Colors.white70, size: 64),
            ),
          Positioned(
            left: 12,
            right: 78,
            bottom: 34,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(video.title,
                    maxLines: 3,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                        color: Colors.white,
                        fontSize: 14,
                        height: 1.35,
                        fontWeight: FontWeight.w600,
                        shadows: [Shadow(color: Colors.black87, blurRadius: 6)])),
                const SizedBox(height: 6),
                Text(video.channelName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                        color: Colors.white70,
                        fontSize: 12.5,
                        shadows: [Shadow(color: Colors.black87, blurRadius: 6)])),
              ],
            ),
          ),
          Positioned(
            right: 10,
            bottom: 34,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                _action(
                  appLibrary.isLiked(video.id)
                      ? Icons.favorite
                      : Icons.favorite_border,
                  'אהבתי',
                  color: appLibrary.isLiked(video.id) ? AppTheme.accent : null,
                  onTap: () async {
                    await appLibrary.toggleLike(video);
                    if (mounted) setState(() {});
                  },
                ),
                _action(Icons.share_rounded, 'שתף', onTap: () {
                  SharePlus.instance.share(
                      ShareParams(uri: Uri.parse('https://youtu.be/${video.id}')));
                }),
                _action(Icons.more_horiz, 'עוד',
                    onTap: () => showVideoActions(context, video)),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _action(IconData icon, String label,
      {required VoidCallback onTap, Color? color}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 18),
      child: GestureDetector(
        onTap: onTap,
        child: Column(
          children: [
            Icon(icon, color: color ?? Colors.white, size: 27),
            const SizedBox(height: 4),
            Text(label,
                style: const TextStyle(color: Colors.white70, fontSize: 10.5)),
          ],
        ),
      ),
    );
  }
}

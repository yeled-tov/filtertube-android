import 'package:flutter/material.dart';
import 'package:youtube_player_iframe/youtube_player_iframe.dart';

import '../data/app_state.dart';
import '../data/library_store.dart';
import '../data/playback.dart';
import '../data/settings_store.dart';
import '../theme.dart';
import 'home_screen.dart';
import 'library_screen.dart';
import 'music/music_screen.dart';
import 'player_view.dart';
import 'search_screen.dart';
import 'settings_screen.dart';
import 'shorts_screen.dart';
import 'widgets/mini_player.dart';
import 'widgets/nav_bar.dart';

/// הקליפה של האפליקציה: הלשוניות, סרגל הניווט, המיני-נגן ומסך הנגן —
/// כולם באותו עץ ווידג'טים, סביב **נגן אחד**.
///
/// ## למה הכל כאן ולא בניווט רגיל
/// `YoutubePlayer` הוא WebView. אם כל מסך היה יוצר אחד משלו, מעבר בין
/// מסכים היה טוען את הסרטון מחדש והניגון היה נקטע. כאן הנגן נשאר במקומו
/// בעץ, ורק **המיקום והגודל שלו** מונפשים בין החלון הקטן שמעל סרגל
/// הניווט לבין הריבוע הגדול שבראש מסך הנגן.
class AppShell extends StatefulWidget {
  const AppShell({super.key});

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> with WidgetsBindingObserver {
  late final YoutubePlayerController _controller;
  int _index = 0;

  static const double _navHeight = 62;
  static const double _navMargin = 16;
  static const double _miniHeight = 58;
  static const double _miniThumbWidth = 92;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _controller = YoutubePlayerController(params: playback.buildParams());
    playback.attach(_controller);
    playback.onOpenRequested = () {
      if (mounted) setState(() {});
    };
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    playback.onOpenRequested = null;
    super.dispose();
  }

  /// אין ניגון ברקע בגרסת החנות: כשהאפליקציה יורדת מהמסך הניגון נעצר.
  ///
  /// `paused` ולא `inactive` — inactive נדלק גם על מגירת ההתראות או שיחה
  /// נכנסת, ולעצור שם היה נראה כמו תקלה.
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused) playback.pause();
  }

  List<Widget> get _tabs => [
        const HomeScreen(),
        if (appSettings.shortsEnabled) const ShortsScreen(),
        const SearchScreen(),
        const MusicScreen(),
        const LibraryScreen(),
        const SettingsScreen(),
      ];

  List<NavItem> get _items => [
        const NavItem('בית', Icons.home_rounded),
        if (appSettings.shortsEnabled) const NavItem('Shorts', Icons.bolt_rounded),
        const NavItem('חיפוש', Icons.search_rounded),
        const NavItem('מוזיקה', Icons.music_note_rounded),
        const NavItem('ספריה', Icons.video_library_rounded),
        const NavItem('הגדרות', Icons.settings_rounded),
      ];

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([appSettings, playback, appLibrary, appState]),
      builder: (context, _) {
        final items = _items;
        // כיבוי לשונית השורטס מקצר את הרשימה — בלי הקיצוץ כאן האינדקס
        // נשאר מחוץ לתחום ומסך הבית נעלם.
        if (_index >= items.length) _index = items.length - 1;

        final media = MediaQuery.of(context);
        final width = media.size.width;
        final safeTop = media.padding.top;
        final safeBottom = media.padding.bottom;
        final expanded = playback.expanded && playback.isActive;

        final playerWidth = expanded ? width : _miniThumbWidth;
        final playerHeight = playerWidth * 9 / 16;
        final playerLeft = expanded ? 0.0 : 10.0;
        final miniBottom = _navHeight + _navMargin + safeBottom + 4;
        final playerTop = expanded
            ? safeTop + 46
            : media.size.height - miniBottom - _miniHeight + (_miniHeight - playerHeight) / 2;

        return PopScope(
          canPop: !expanded,
          onPopInvokedWithResult: (didPop, _) {
            if (!didPop && expanded) playback.setExpanded(false);
          },
          child: Scaffold(
            backgroundColor: AppTheme.bg,
            body: Stack(
              children: [
                // ── תוכן הלשוניות ──────────────────────────────────────
                Positioned.fill(
                  child: IndexedStack(index: _index, children: _tabs),
                ),

                // ── מסך הנגן ───────────────────────────────────────────
                // נבנה רק כשהוא פתוח, אבל הנגן עצמו נשאר חי מתחתיו תמיד.
                if (playback.isActive)
                  AnimatedSlide(
                    duration: const Duration(milliseconds: 260),
                    curve: Curves.easeOutCubic,
                    offset: expanded ? Offset.zero : const Offset(0, 1),
                    child: IgnorePointer(
                      ignoring: !expanded,
                      child: AnimatedOpacity(
                        duration: const Duration(milliseconds: 200),
                        opacity: expanded ? 1 : 0,
                        child: PlayerView(videoSlotHeight: width * 9 / 16),
                      ),
                    ),
                  ),

                // ── המיני-נגן וסרגל הניווט ─────────────────────────────
                if (!expanded)
                  Positioned(
                    left: 0,
                    right: 0,
                    bottom: 0,
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (playback.isActive)
                          SizedBox(
                            height: _miniHeight,
                            child: MiniPlayer(
                              thumbnailWidth: _miniThumbWidth,
                              onOpen: () => playback.setExpanded(true),
                            ),
                          ),
                        NavBar(
                          items: items,
                          index: _index,
                          height: _navHeight,
                          margin: _navMargin,
                          onTap: (i) => setState(() => _index = i),
                        ),
                        SizedBox(height: safeBottom),
                      ],
                    ),
                  ),

                // ── הנגן עצמו ──────────────────────────────────────────
                // מופע יחיד שנע בין שני המיקומים. במצב אודיו הוא מוסתר
                // מאחורי עטיפת האלבום אבל ממשיך לנגן.
                if (playback.isActive)
                  AnimatedPositioned(
                    duration: const Duration(milliseconds: 260),
                    curve: Curves.easeOutCubic,
                    left: playerLeft,
                    top: playerTop,
                    width: playerWidth,
                    height: playerHeight,
                    child: ClipRRect(
                      borderRadius:
                          BorderRadius.circular(expanded ? 0 : 8),
                      child: YoutubePlayer(
                        controller: _controller,
                        aspectRatio: 16 / 9,
                        keepAlive: true,
                        enableFullScreenOnVerticalDrag: false,
                        backgroundColor: Colors.black,
                      ),
                    ),
                  ),

                // במצב אודיו בלבד הווידאו לא מוצג — אבל הוא חייב להישאר
                // מחובר כדי שהקול ימשיך. הכיסוי נמצא *מעל* הנגן ולכן
                // המשתמש רואה עטיפה ולא תמונה נעה.
                if (playback.isActive && playback.audioOnly)
                  AnimatedPositioned(
                    duration: const Duration(milliseconds: 260),
                    curve: Curves.easeOutCubic,
                    left: playerLeft,
                    top: playerTop,
                    width: playerWidth,
                    height: playerHeight,
                    child: const AudioOnlyCover(),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }
}

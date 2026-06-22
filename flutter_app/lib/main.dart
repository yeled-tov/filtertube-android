import 'dart:async';
import 'package:flutter/material.dart';
import 'package:app_links/app_links.dart';
import 'theme.dart';
import 'settings.dart';
import 'models.dart';
import 'youtube_api.dart';
import 'channels_repo.dart';
import 'screens/home_screen.dart';
import 'screens/search_screen.dart';
import 'screens/channels_screen.dart';
import 'screens/player_screen.dart';

/// מפתח ניווט גלובלי — לפתיחת קישורים חיצוניים מחוץ לעץ הווידג'טים.
final navigatorKey = GlobalKey<NavigatorState>();

/// המפתח הרשמי של YouTube Data API v3.
/// לפני פרסום בחנות יש להגביל אותו ב-Google Cloud Console (חבילה/חתימה).
const String kApiKey = 'AIzaSyDLAo5cUv4lt1Tsad50aMGFE0jl-mfRtOk';

void main() {
  runApp(const FilterTubeApp());
}

class FilterTubeApp extends StatelessWidget {
  const FilterTubeApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'FilterTube',
      navigatorKey: navigatorKey,
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      // האפליקציה בעברית — ברירת מחדל מימין לשמאל
      builder: (context, child) =>
          Directionality(textDirection: TextDirection.rtl, child: child!),
      home: const _Root(),
    );
  }
}

class _Root extends StatefulWidget {
  const _Root();

  @override
  State<_Root> createState() => _RootState();
}

class _RootState extends State<_Root> {
  final _api = YoutubeApi(kApiKey);
  final _channels = ChannelsRepo();
  final _settings = AppSettings();
  final _appLinks = AppLinks();
  StreamSubscription<Uri>? _linkSub;
  late Future<void> _ready;
  int _index = 0;
  int _feedKey = 0;

  @override
  void initState() {
    super.initState();
    _ready = _init();
    _setupDeepLinks();
  }

  Future<void> _init() async {
    await _settings.load();
    await _channels.load(level: _settings.filterLevel);
  }

  Future<void> _setupDeepLinks() async {
    final initial = await _appLinks.getInitialLink();
    if (initial != null) _handleLink(initial);
    _linkSub = _appLinks.uriLinkStream.listen(_handleLink);
  }

  /// פותח קישור יוטיוב חיצוני בנגן שלנו במקום באפליקציית יוטיוב.
  void _handleLink(Uri uri) {
    final id = _extractYouTubeId(uri.toString());
    if (id == null) return;
    navigatorKey.currentState?.push(MaterialPageRoute(
      builder: (_) => PlayerScreen(
        video: Video(
          id: id,
          title: '',
          channelTitle: '',
          channelId: '',
          thumbnail: 'https://i.ytimg.com/vi/$id/hqdefault.jpg',
        ),
        api: _api,
        channels: _channels,
      ),
    ));
  }

  String? _extractYouTubeId(String url) {
    for (final p in [
      r'[?&]v=([A-Za-z0-9_-]{11})',
      r'youtu\.be/([A-Za-z0-9_-]{11})',
      r'/shorts/([A-Za-z0-9_-]{11})',
      r'/live/([A-Za-z0-9_-]{11})',
      r'/embed/([A-Za-z0-9_-]{11})',
    ]) {
      final m = RegExp(p).firstMatch(url);
      if (m != null) return m.group(1);
    }
    return null;
  }

  @override
  void dispose() {
    _linkSub?.cancel();
    super.dispose();
  }

  Future<void> _onLevelChanged(int level) async {
    await _settings.setFilterLevel(level);
    await _channels.load(level: level);
    if (mounted) setState(() => _feedKey++);
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<void>(
      future: _ready,
      builder: (context, snap) {
        if (snap.connectionState == ConnectionState.waiting) {
          return const Scaffold(
            backgroundColor: AppTheme.bg,
            body: Center(child: CircularProgressIndicator(color: AppTheme.accent)),
          );
        }
        final screens = [
          HomeScreen(
              key: ValueKey(_feedKey),
              api: _api,
              channels: _channels,
              settings: _settings,
              onFilterLevelChanged: _onLevelChanged),
          ChannelsScreen(api: _api, channels: _channels),
          SearchScreen(api: _api, channels: _channels),
        ];
        return Scaffold(
          extendBody: true,
          body: IndexedStack(index: _index, children: screens),
          bottomNavigationBar: _FloatingNav(
            index: _index,
            onTap: (i) => setState(() => _index = i),
          ),
        );
      },
    );
  }
}

/// נאב בר צף יוקרתי — גלולה עם גרדיאנט לפריט הנבחר.
class _FloatingNav extends StatelessWidget {
  final int index;
  final ValueChanged<int> onTap;
  const _FloatingNav({required this.index, required this.onTap});

  static const List<(IconData, String)> _items = [
    (Icons.home_rounded, 'בית'),
    (Icons.subscriptions_rounded, 'ערוצים'),
    (Icons.search_rounded, 'חיפוש'),
  ];

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(26, 0, 26, 16),
      child: Container(
        height: 62,
        decoration: BoxDecoration(
          color: AppTheme.surface.withValues(alpha: 0.96),
          borderRadius: BorderRadius.circular(22),
          border: Border.all(color: AppTheme.stroke),
          boxShadow: [
            BoxShadow(
                color: Colors.black.withValues(alpha: 0.45),
                blurRadius: 22,
                offset: const Offset(0, 8)),
          ],
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: List.generate(_items.length, (i) {
            final selected = i == index;
            return GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: () => onTap(i),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 220),
                curve: Curves.easeOut,
                padding: EdgeInsets.symmetric(
                    horizontal: selected ? 16 : 12, vertical: 9),
                decoration: BoxDecoration(
                  gradient: selected ? AppTheme.accentGradient : null,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Row(
                  children: [
                    Icon(_items[i].$1,
                        color: selected ? Colors.white : AppTheme.subtext,
                        size: 22),
                    if (selected) ...[
                      const SizedBox(width: 7),
                      Text(_items[i].$2,
                          style: const TextStyle(
                              color: Colors.white,
                              fontWeight: FontWeight.w700,
                              fontSize: 13)),
                    ],
                  ],
                ),
              ),
            );
          }),
        ),
      ),
    );
  }
}

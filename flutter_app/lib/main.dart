import 'package:flutter/material.dart';
import 'theme.dart';
import 'settings.dart';
import 'youtube_api.dart';
import 'channels_repo.dart';
import 'screens/home_screen.dart';
import 'screens/search_screen.dart';
import 'screens/channels_screen.dart';

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
  late Future<void> _ready;
  int _index = 0;
  int _feedKey = 0;

  @override
  void initState() {
    super.initState();
    _ready = _init();
  }

  Future<void> _init() async {
    await _settings.load();
    await _channels.load(level: _settings.filterLevel);
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

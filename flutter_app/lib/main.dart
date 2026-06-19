import 'package:flutter/material.dart';
import 'theme.dart';
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
  late Future<void> _ready;
  int _index = 0;

  @override
  void initState() {
    super.initState();
    _ready = _channels.load();
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
          HomeScreen(api: _api, channels: _channels),
          ChannelsScreen(api: _api, channels: _channels),
          SearchScreen(api: _api, channels: _channels),
        ];
        return Scaffold(
          body: IndexedStack(index: _index, children: screens),
          bottomNavigationBar: NavigationBar(
            selectedIndex: _index,
            onDestinationSelected: (i) => setState(() => _index = i),
            backgroundColor: AppTheme.surface,
            indicatorColor: AppTheme.accent.withValues(alpha: 0.25),
            destinations: const [
              NavigationDestination(
                  icon: Icon(Icons.home_outlined),
                  selectedIcon: Icon(Icons.home),
                  label: 'בית'),
              NavigationDestination(
                  icon: Icon(Icons.subscriptions_outlined),
                  selectedIcon: Icon(Icons.subscriptions),
                  label: 'ערוצים'),
              NavigationDestination(
                  icon: Icon(Icons.search_outlined),
                  selectedIcon: Icon(Icons.search),
                  label: 'חיפוש'),
            ],
          ),
        );
      },
    );
  }
}

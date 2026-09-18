import 'package:flutter/material.dart';

import '../data/app_state.dart';
import '../data/library_store.dart';
import '../data/playback.dart';
import '../data/settings_store.dart';
import '../theme.dart';
import 'home_screen.dart';
import 'library_screen.dart';
import 'music/music_screen.dart';
import 'player_layer.dart';
import 'search_screen.dart';
import 'settings_screen.dart';
import 'shorts_screen.dart';
import 'widgets/nav_bar.dart';

/// הלשוניות וסרגל הניווט. הנגן עצמו אינו כאן אלא ב-[PlayerLayer], שמרחף
/// מעל כל הניווט — ראה ההסבר שם.
class AppShell extends StatefulWidget {
  const AppShell({super.key});

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  @override
  void initState() {
    super.initState();
    shellTab.addListener(_onTabRequested);
  }

  @override
  void dispose() {
    shellTab.removeListener(_onTabRequested);
    super.dispose();
  }

  void _onTabRequested() {
    if (mounted) setState(() {});
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
        final index = shellTab.value.clamp(0, items.length - 1);
        if (index != shellTab.value) shellTab.value = index;

        return Scaffold(
          backgroundColor: AppTheme.bg,
          extendBody: true,
          body: IndexedStack(index: index, children: _tabs),
          bottomNavigationBar: Padding(
            padding:
                EdgeInsets.only(bottom: MediaQuery.of(context).padding.bottom),
            child: NavBar(
              items: items,
              index: index,
              height: PlayerLayer.navHeight,
              margin: PlayerLayer.navMargin,
              onTap: (i) => shellTab.value = i,
            ),
          ),
        );
      },
    );
  }
}

/// מדד הלשונית של FilterMusic — משמש את מחליף המצבים במסך הבית.
int get musicTabIndex => appSettings.shortsEnabled ? 3 : 2;

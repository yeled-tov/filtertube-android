import 'dart:async';

import 'package:app_links/app_links.dart';
import 'package:flutter/material.dart';

import 'data/app_state.dart';
import 'data/auth.dart';
import 'data/crash_log.dart';
import 'data/display.dart';
import 'data/library_store.dart';
import 'data/notifications.dart';
import 'data/playback.dart';
import 'data/settings_store.dart';
import 'theme.dart';
import 'ui/channel_request_dialog.dart';
import 'ui/crash_dialog.dart';
import 'ui/onboarding_screen.dart';
import 'ui/player_layer.dart';
import 'ui/shell.dart';
import 'ui/widgets/common.dart';

final navigatorKey = GlobalKey<NavigatorState>();

void main() {
  // כל ההרצה עטופה בתופס שגיאות: קריסה שאיש לא רואה היא באג שאיש לא
  // מדווח עליו, והדוח נשמר במכשיר ומוצג בהפעלה הבאה.
  CrashLog.install(() async {
    WidgetsFlutterBinding.ensureInitialized();
    await appSettings.load();
    await appLibrary.load();
    await appAuth.load();
    await AppNotifications.init();
    runApp(const FilterTubeApp());
  });
}

class FilterTubeApp extends StatefulWidget {
  const FilterTubeApp({super.key});

  @override
  State<FilterTubeApp> createState() => _FilterTubeAppState();
}

class _FilterTubeAppState extends State<FilterTubeApp> {
  final AppLinks _appLinks = AppLinks();
  StreamSubscription<Uri>? _linkSub;
  bool _booted = false;
  bool _onboarded = appSettings.onboardingDone;

  @override
  void initState() {
    super.initState();
    _applyHighRefreshRate();
    _boot();
    _setupDeepLinks();
  }

  @override
  void dispose() {
    _linkSub?.cancel();
    super.dispose();
  }

  Future<void> _applyHighRefreshRate() async {
    if (!appSettings.highRefreshRate) return;
    await DisplayMode.applyHighRefreshRate();
  }

  Future<void> _boot() async {
    if (_onboarded) {
      await appState.boot();
    } else {
      // בהיכרות הראשונה המשתמש בוחר זמרים מתוך הרשימה הלבנה, ולכן היא
      // חייבת להיות טעונה עוד לפני שהמסך מוצג. הפיד עצמו נטען רק אחריה:
      // רמת הסינון והמגדר שנבחרים בה הם מה שקובע אילו ערוצים בכלל נמשכים.
      await appState.channels.load();
    }
    if (!mounted) return;
    setState(() => _booted = true);
    // אחרי שהמסך הראשון נבנה: לפני כן ל-navigatorKey עוד אין הקשר, והדוח
    // פשוט לא היה מוצג.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final context = navigatorKey.currentContext;
      if (context != null) showCrashDialogIfNeeded(context);
    });
  }

  Future<void> _setupDeepLinks() async {
    try {
      final initial = await _appLinks.getInitialLink();
      if (initial != null) _handleLink(initial);
      _linkSub = _appLinks.uriLinkStream.listen(_handleLink);
    } catch (_) {
      // בלי קישורים חיצוניים האפליקציה עובדת במלואה
    }
  }

  static String? extractVideoId(String url) {
    for (final pattern in [
      r'[?&]v=([A-Za-z0-9_-]{11})',
      r'youtu\.be/([A-Za-z0-9_-]{11})',
      r'/shorts/([A-Za-z0-9_-]{11})',
      r'/live/([A-Za-z0-9_-]{11})',
      r'/embed/([A-Za-z0-9_-]{11})',
    ]) {
      final match = RegExp(pattern).firstMatch(url);
      if (match != null) return match.group(1);
    }
    return null;
  }

  /// קישור יוטיוב חיצוני נפתח בנגן שלנו — **אחרי** שנבדק שהערוץ מאושר.
  ///
  /// בלי הבדיקה הזו קישור חיצוני היה מנגן כל סרטון ביוטיוב, כלומר חור
  /// שעוקף את כל מטרת האפליקציה.
  Future<void> _handleLink(Uri uri) async {
    final id = extractVideoId(uri.toString());
    if (id == null) return;
    final context = navigatorKey.currentContext;
    if (context == null) return;

    showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (_) => AlertDialog(
        title: const Text('בודק את הקישור…'),
        content: Text(
          'מוודאים שהערוץ נמצא ברשימת הערוצים המאושרים.',
          style: TextStyle(color: AppTheme.subtext2, fontSize: 13),
        ),
      ),
    );

    final video = await appState.api.videoById(id);
    if (!context.mounted) return;
    Navigator.of(context, rootNavigator: true).pop();

    if (video == null) {
      showToast(context, 'לא הצלחנו לפתוח את הסרטון הזה');
      return;
    }

    final approved = appState.channels
        .visible(appSettings.filterLevel, appSettings.userGender)
        .any((c) => c.youtubeChannelId == video.channelId);
    if (!approved) {
      if (!context.mounted) return;
      _showBlocked(context, video.channelName, video.channelId);
      return;
    }
    await playback.play(video);
  }

  void _showBlocked(BuildContext context, String channelName, String channelId) {
    showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('הערוץ אינו מאושר'),
        content: Text(
          'הערוץ "$channelName" לא נמצא ברשימת הערוצים המאושרים, ולכן '
          'הסרטון לא מנוגן.\n\n'
          'אם לדעתך הערוץ מתאים — אפשר לבקש להוסיף אותו. הפרטים כבר ימולאו '
          'מהקישור שפתחת; נשאר רק להסביר מה הערוץ מכיל.',
          style: TextStyle(
              color: AppTheme.subtext2, fontSize: 13.5, height: 1.45),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: Text('סגור', style: TextStyle(color: AppTheme.subtext2)),
          ),
          TextButton(
            onPressed: () {
              Navigator.pop(dialogContext);
              showChannelRequestDialog(
                context,
                prefillName: channelName,
                prefillUrl: channelId.isEmpty
                    ? ''
                    : 'https://www.youtube.com/channel/$channelId',
              );
            },
            child: Text('בקש להוסיף ערוץ',
                style: TextStyle(
                    color: AppTheme.accent, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: ThemeState.instance,
      builder: (context, _) {
        final systemDark =
            MediaQuery.platformBrightnessOf(context) == Brightness.dark;
        // מצב "מערכת" חייב לעקוב אחרי המכשיר גם כשהוא משתנה תוך כדי ריצה.
        if (appSettings.themeMode == 0 &&
            ThemeState.instance.dark != systemDark) {
          WidgetsBinding.instance.addPostFrameCallback(
              (_) => ThemeState.instance.setDark(systemDark));
        }
        return MaterialApp(
          title: 'FilterTube',
          navigatorKey: navigatorKey,
          debugShowCheckedModeBanner: false,
          theme: AppTheme.build(),
          // האפליקציה בעברית — ברירת מחדל מימין לשמאל. שכבת הנגן עוטפת
          // את הניווט כולו, ולכן היא נראית גם מעל מסך שנפתח מעל הלשוניות.
          builder: (context, child) => Directionality(
            textDirection: TextDirection.rtl,
            child: PlayerLayer(child: child ?? const SizedBox.shrink()),
          ),
          home: _home(),
        );
      },
    );
  }

  Widget _home() {
    if (!_booted) return _splash();
    if (!_onboarded) {
      return OnboardingScreen(onDone: () async {
        setState(() {
          _onboarded = true;
          _booted = false;
        });
        await appState.boot();
        if (mounted) setState(() => _booted = true);
      });
    }
    return const AppShell();
  }

  Widget _splash() {
    return Scaffold(
        backgroundColor: AppTheme.bg,
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 66,
                height: 66,
                decoration: BoxDecoration(
                  gradient: AppTheme.accentGradient,
                  borderRadius: BorderRadius.circular(20),
                ),
                child: const Icon(Icons.play_arrow_rounded,
                    color: Colors.white, size: 38),
              ),
              const SizedBox(height: 18),
              Text('FilterTube',
                  style: TextStyle(
                      color: AppTheme.text,
                      fontSize: 20,
                      fontWeight: FontWeight.w800)),
              const SizedBox(height: 14),
              SizedBox(
                width: 22,
                height: 22,
                child: CircularProgressIndicator(
                    strokeWidth: 2, color: AppTheme.accent),
              ),
            ],
          ),
        ),
    );
  }
}

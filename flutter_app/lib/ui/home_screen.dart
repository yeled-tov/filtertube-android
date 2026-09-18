import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/material.dart';

import '../data/app_state.dart';
import '../data/library_store.dart';
import '../data/playback.dart';
import '../data/settings_store.dart';
import '../models/channel.dart';
import '../models/video.dart';
import '../theme.dart';
import 'artist_picker_dialog.dart';
import 'live_screen.dart';
import 'player_layer.dart';
import 'shell.dart';
import 'new_videos_screen.dart';
import 'widgets/common.dart';
import 'widgets/video_row.dart';

/// מסך הבית — הפיד המדורג מכל הערוצים המאושרים, עם מחליף המצבים
/// FilterTube/FilterMusic, צ'יפים ליעדים ולסינון לפי קטגוריה, וכפתור
/// הרדיו האישי.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  String? _category;
  bool _radioStarting = false;

  /// null = עוד לא נבדק. false = אין חיבור, ואז מוצגת רצועת האופליין.
  ///
  /// המצב נבדק ברציפות ולא פעם אחת: המשתמש יוצא מטווח וחוזר, ורצועה
  /// שנתקעת על המסך אחרי שהרשת חזרה גרועה מלא להציג אותה בכלל.
  bool? _online;
  StreamSubscription<List<ConnectivityResult>>? _connectivity;

  @override
  void initState() {
    super.initState();
    _watchConnectivity();
  }

  @override
  void dispose() {
    _connectivity?.cancel();
    super.dispose();
  }

  Future<void> _watchConnectivity() async {
    try {
      void apply(List<ConnectivityResult> results) {
        final online = results.any((r) => r != ConnectivityResult.none);
        if (mounted && _online != online) setState(() => _online = online);
      }

      apply(await Connectivity().checkConnectivity());
      _connectivity = Connectivity().onConnectivityChanged.listen(apply);
    } catch (_) {
      // בלי בדיקת רשת פשוט לא מוצגת הרצועה
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([appState, appLibrary, appSettings]),
      builder: (context, _) {
        // אם הקטגוריה שנבחרה נעלמה מהפיד, חוזרים ל"הכל" במקום להשאיר את
        // המשתמש מול מסך ריק.
        if (_category != null && !appState.feedCategories.contains(_category)) {
          _category = null;
        }
        final all = appState.videos;
        final shown = _category == null
            ? all
            : all
                .where((v) => appState.channels.categoryOf(v.channelId) == _category)
                .toList();

        return Stack(
          children: [
            Column(
              children: [
                SizedBox(height: MediaQuery.of(context).padding.top + 14),
                _modeSwitch(),
                const SizedBox(height: 10),
                _chipsRow(),
                if (_online == false) _offlineBanner(),
                if (appState.refreshing && appState.status == FeedStatus.ready)
                  LinearProgressIndicator(
                      color: AppTheme.accent,
                      backgroundColor: AppTheme.divider,
                      minHeight: 2),
                Expanded(child: _body(shown)),
              ],
            ),
            _radioButton(),
          ],
        );
      },
    );
  }

  Widget _body(List<Video> videos) {
    switch (appState.status) {
      case FeedStatus.loading:
        return const CenteredLoading('טוען סרטונים...');
      case FeedStatus.error:
        return CenteredError(appState.errorMessage,
            onRetry: () => appState.refresh());
      case FeedStatus.ready:
        if (videos.isEmpty) {
          return const EmptyState(
            icon: Icons.video_library_outlined,
            title: 'אין סרטונים בקטגוריה זו',
          );
        }
        return RefreshIndicator(
          color: AppTheme.accent,
          backgroundColor: AppTheme.surface,
          onRefresh: () => appState.refresh(silent: true),
          child: ListView.builder(
            padding: EdgeInsets.only(top: 8, bottom: PlayerLayer.bottomInset(context)),
            itemCount: videos.length,
            itemBuilder: (context, i) => VideoRow(
              video: videos[i],
              onTap: () => playback.playFromList(videos, i),
            ),
          ),
        );
    }
  }

  /// מחליף המצבים ממלא שני תפקידים ולכן אין צורך בשורת כותרת נפרדת:
  /// החצי המסומן הוא שם האפליקציה, והחצי השני הוא הדרך למוזיקה.
  Widget _modeSwitch() {
    Widget half(String label, bool selected, VoidCallback onTap) => Expanded(
          child: GestureDetector(
            onTap: onTap,
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              padding: const EdgeInsets.symmetric(vertical: 9),
              decoration: BoxDecoration(
                gradient: selected ? AppTheme.accentGradient : null,
                borderRadius: BorderRadius.circular(50),
              ),
              alignment: Alignment.center,
              child: Text(label,
                  style: TextStyle(
                      color: selected ? Colors.white : AppTheme.subtext,
                      fontSize: 13,
                      fontWeight: FontWeight.w700)),
            ),
          ),
        );

    return Center(
      child: Container(
        width: 250,
        padding: const EdgeInsets.all(3),
        decoration: BoxDecoration(
          color: AppTheme.bg2,
          borderRadius: BorderRadius.circular(50),
          border: Border.all(color: AppTheme.divider),
        ),
        child: Row(
          children: [
            half('FilterTube', true, () {}),
            // מעבר ללשונית ולא מסך חדש מעליה: מסך שנפתח מעל הלשוניות
            // מכסה את סרגל הניווט ואת המיני-נגן, ואז אין ממנו דרך חזרה
            // חוץ מכפתור המערכת.
            half('FilterMusic', false, () => shellTab.value = musicTabIndex),
          ],
        ),
      ),
    );
  }

  Widget _chipsRow() {
    final newCount = appLibrary.newVideos.length;
    final categories = sortedCategories(
      appState.visibleChannels
          .map((c) => c.category)
          .where(appState.feedCategories.contains),
    );

    return SizedBox(
      height: 40,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12),
        children: [
          _destinationChip('שידורים חיים', Icons.live_tv_rounded,
              const Color(0xFFFF3B30), () {
            Navigator.push(context,
                MaterialPageRoute<void>(builder: (_) => const LiveScreen()));
          }),
          _destinationChip(
            newCount > 0 ? 'חדשים ($newCount)' : 'חדשים',
            Icons.notifications_rounded,
            newCount > 0 ? AppTheme.accent : AppTheme.subtext2,
            () {
              Navigator.push(
                  context,
                  MaterialPageRoute<void>(
                      builder: (_) => const NewVideosScreen()));
            },
          ),
          if (categories.isNotEmpty) ...[
            Container(
                width: 1,
                height: 22,
                margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 9),
                color: AppTheme.divider),
            Padding(
              padding: const EdgeInsets.only(left: 8),
              child: ChoiceChipBox(
                  label: 'הכל',
                  selected: _category == null,
                  onTap: () => setState(() => _category = null)),
            ),
            // הצ'יפים נבנים מהפיד ולא מרשימת הערוצים: צ'יפ שמוביל לכלום
            // הוא באג, לא תצוגה.
            ...categories.map(
              (cat) => Padding(
                padding: const EdgeInsets.only(left: 8),
                child: ChoiceChipBox(
                  label: categoryLabelHe(cat),
                  selected: _category == cat,
                  onTap: () => setState(() => _category = cat),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  /// במקום פיד שנכשל בשקט: אמירה ברורה מה קרה.
  Widget _offlineBanner() => Container(
        margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        decoration: BoxDecoration(
          color: AppTheme.card,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppTheme.divider),
        ),
        child: Row(
          children: [
            const Icon(Icons.cloud_off, color: AppTheme.tintAmber, size: 20),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('אין חיבור לאינטרנט',
                      style: TextStyle(
                          color: AppTheme.text,
                          fontSize: 13.5,
                          fontWeight: FontWeight.bold)),
                  Text('מוצג מה שנטען לאחרונה. הפיד יתעדכן כשהחיבור יחזור.',
                      style:
                          TextStyle(color: AppTheme.subtext, fontSize: 11.5)),
                ],
              ),
            ),
          ],
        ),
      );

  Widget _destinationChip(
      String label, IconData icon, Color tint, VoidCallback onTap) {
    return Padding(
      padding: const EdgeInsets.only(left: 8),
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 7),
          decoration: BoxDecoration(
            color: AppTheme.surface,
            borderRadius: BorderRadius.circular(50),
            border: Border.all(color: tint.withValues(alpha: 0.45)),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, color: tint, size: 15),
              const SizedBox(width: 6),
              Text(label,
                  style: TextStyle(color: AppTheme.text, fontSize: 12.5)),
            ],
          ),
        ),
      ),
    );
  }

  /// כפתור זכוכית קטן: נוכח בלי לכסות את הפיד. התחנה עצמה נבנית מהלייקים,
  /// מההיסטוריה ומהסגנון.
  Widget _radioButton() {
    return Positioned(
      right: 16,
      bottom: PlayerLayer.bottomInset(context) + (playback.isActive ? 10 : -40),
      child: GestureDetector(
        onTap: _radioStarting ? null : _startRadio,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          decoration: BoxDecoration(
            color: AppTheme.surface.withValues(alpha: 0.85),
            borderRadius: BorderRadius.circular(50),
            border: Border.all(color: Colors.white.withValues(alpha: 0.18)),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (_radioStarting)
                SizedBox(
                  width: 15,
                  height: 15,
                  child: CircularProgressIndicator(
                      strokeWidth: 1.6, color: AppTheme.accent),
                )
              else
                Icon(Icons.radio, color: AppTheme.accent, size: 16),
              const SizedBox(width: 6),
              Text(_radioStarting ? 'מכין…' : 'רדיו',
                  style: TextStyle(
                      color: AppTheme.text,
                      fontSize: 12.5,
                      fontWeight: FontWeight.w600)),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _startRadio() async {
    // התקנה טרייה: אין ממה להסיק טעם, ושאלה אחת עדיפה על "רדיו אישי"
    // שהוא בעצם הפיד הכללי בסדר אחר.
    if (appState.stations.needsArtistPicker() && !appSettings.artistPickerSeen) {
      final picked = await showArtistPicker(context);
      if (!picked) return;
    }
    if (!mounted) return;
    setState(() => _radioStarting = true);
    try {
      final station = await appState.stations.personalStation(
        level: appSettings.filterLevel,
        gender: appSettings.userGender,
        pool: appState.videos,
      );
      if (!mounted) return;
      if (station.isEmpty) {
        showToast(context,
            'עוד אין ממה לבנות תחנה — תשמע כמה סרטונים ותסמן לב, ואז זה יתחיל להכיר אותך');
        return;
      }
      await playback.play(station.first, queue: station.sublist(1));
    } finally {
      if (mounted) setState(() => _radioStarting = false);
    }
  }
}

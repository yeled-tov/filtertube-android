import 'package:flutter/material.dart';
import '../models.dart';
import '../theme.dart';
import '../youtube_api.dart';
import '../channels_repo.dart';
import '../widgets/video_card.dart';
import 'player_screen.dart';
import 'settings_screen.dart';

/// פיד הבית — סרטונים אחרונים מהערוצים המאושרים, ממוינים לפי תאריך.
/// כדי לחסוך במכסת ה-API מושכים מ-N הערוצים הראשונים בכל רענון.
class HomeScreen extends StatefulWidget {
  final YoutubeApi api;
  final ChannelsRepo channels;
  final Future<void> Function(int level) onFilterLevelChanged;

  const HomeScreen({
    super.key,
    required this.api,
    required this.channels,
    required this.onFilterLevelChanged,
  });

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  late Future<List<Video>> _future;

  @override
  void initState() {
    super.initState();
    _future = _loadFeed();
  }

  Future<List<Video>> _loadFeed() async {
    final chs = widget.channels.channels.take(24).toList();
    final results = await Future.wait(
      chs.map((c) => widget.api.channelUploads(c, max: 6)),
    );
    final all = <Video>[];
    for (final r in results) {
      all.addAll(r);
    }
    all.sort((a, b) => (b.publishedAt ?? DateTime(2000))
        .compareTo(a.publishedAt ?? DateTime(2000)));
    // מציגים רק סרטונים שמותר להטמיע (כדי שלא ייתקל ב"סרטון אינו זמין")
    return widget.api.filterEmbeddable(all);
  }

  Future<void> _refresh() async {
    final f = _loadFeed();
    setState(() => _future = f);
    await f;
  }

  void _open(Video v) {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => PlayerScreen(
        video: v,
        api: widget.api,
        channels: widget.channels,
      ),
    ));
  }

  void _openSettings() {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => SettingsScreen(
        onFilterLevelChanged: widget.onFilterLevelChanged,
      ),
    ));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        titleSpacing: 4,
        leadingWidth: 60,
        leading: Center(
          child: GestureDetector(
            onTap: _openSettings,
            child: Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(
                color: AppTheme.surface,
                shape: BoxShape.circle,
                border: Border.all(color: AppTheme.stroke),
              ),
              child: const Icon(Icons.person_outline,
                  color: AppTheme.text, size: 21),
            ),
          ),
        ),
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 30,
              height: 30,
              decoration: BoxDecoration(
                gradient: AppTheme.accentGradient,
                borderRadius: BorderRadius.circular(9),
              ),
              child: const Icon(Icons.play_arrow_rounded,
                  color: Colors.white, size: 22),
            ),
            const SizedBox(width: 9),
            const Text('FilterTube',
                style: TextStyle(
                    fontWeight: FontWeight.w800,
                    color: AppTheme.text,
                    fontSize: 19,
                    letterSpacing: -0.5)),
          ],
        ),
      ),
      body: RefreshIndicator(
        color: AppTheme.accent,
        onRefresh: _refresh,
        child: FutureBuilder<List<Video>>(
          future: _future,
          builder: (context, snap) {
            if (snap.connectionState == ConnectionState.waiting) {
              return const Center(
                  child: CircularProgressIndicator(color: AppTheme.accent));
            }
            final vids = snap.data ?? [];
            if (vids.isEmpty) {
              return ListView(children: const [
                SizedBox(height: 200),
                Center(
                    child: Text('אין סרטונים להצגה',
                        style: TextStyle(color: AppTheme.subtext))),
              ]);
            }
            return GridView.builder(
              padding: const EdgeInsets.fromLTRB(12, 6, 12, 90),
              gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                maxCrossAxisExtent: 380,
                childAspectRatio: 0.72,
                crossAxisSpacing: 12,
                mainAxisSpacing: 18,
              ),
              itemCount: vids.length,
              itemBuilder: (context, i) =>
                  VideoCard(video: vids[i], onTap: () => _open(vids[i])),
            );
          },
        ),
      ),
    );
  }
}

import 'package:flutter/material.dart';

import '../../data/app_state.dart';
import '../../data/library_store.dart';
import '../../data/mix_builder.dart';
import '../../data/playback.dart';
import '../../data/settings_store.dart';
import '../../models/channel.dart';
import '../../models/video.dart';
import '../../theme.dart';
import '../widgets/common.dart';
import '../widgets/video_row.dart';
import 'music_settings_screen.dart';
import '../player_layer.dart';

/// FilterMusic — אותו תוכן מאושר, מצב אחר: הכל נשמע כאודיו, והמסך בנוי
/// סביב שירים ומיקסים ולא סביב פיד.
class MusicScreen extends StatefulWidget {
  /// כשנפתח מתוך מחליף המצבים במסך הבית הוא מסך מלא עם חץ חזרה; כלשונית
  /// הוא חלק מהקליפה.
  final bool fullScreen;

  const MusicScreen({super.key, this.fullScreen = false});

  @override
  State<MusicScreen> createState() => _MusicScreenState();
}

class _MusicScreenState extends State<MusicScreen> {
  String _query = '';

  List<Video> get _musicPool => appState.videos
      .where((v) =>
          kMusicCategories.contains(appState.channels.categoryOf(v.channelId)))
      .toList();

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([appState, appLibrary, appSettings]),
      builder: (context, _) {
        final pool = _musicPool;
        final mixes = MixBuilder.build(
          pool: pool,
          likes: appLibrary.likes,
          history: appLibrary.localHistory,
          profile: appState.tasteProfile(),
          categoryOf: (id) => appState.channels.categoryOf(id),
          channelName: (id) => appState.channels.nameOf(id),
        );
        final quickPicks = appState.stations.quickPicks(pool);
        final liked = appLibrary.likes
            .where((v) => kMusicCategories
                .contains(appState.channels.categoryOf(v.channelId)))
            .toList();

        // שדה החיפוש נשאר קבוע מעל התוצאות. קודם הוא היה חלק מהרשימה
        // הנגללת, ולכן ברגע שהוקלדה אות אחת הוא נעלם יחד איתה — כלומר אי
        // אפשר היה לתקן את מה שהוקלד בלי לנקות ולהתחיל מחדש.
        final body = Column(
          children: [
            SizedBox(
                height: widget.fullScreen
                    ? 8
                    : MediaQuery.of(context).padding.top + 18),
            if (!widget.fullScreen) _header(context),
            _searchField(),
            Expanded(
              child: _query.isNotEmpty
                  ? _searchResults(pool)
                  : ListView(
                      padding: EdgeInsets.only(
                        bottom: PlayerLayer.bottomInset(context),
                      ),
                      children: [
                        if (quickPicks.isNotEmpty)
                          _quickPicks(quickPicks)
                        else
                          Padding(
                            padding: const EdgeInsets.all(20),
                            child: Text(
                              'עוד אין כאן מספיק מוזיקה להציג.\n'
                              'הפיד מתעדכן מהערוצים המאושרים — נסה שוב בעוד רגע.',
                              style: TextStyle(
                                  color: AppTheme.subtext,
                                  fontSize: 13,
                                  height: 1.6),
                            ),
                          ),
                        if (mixes.isNotEmpty) _mixes(mixes),
                        if (liked.isNotEmpty)
                          _shelf('השירים שאהבת', liked,
                              onPlayAll: () =>
                                  playback.playFromList(liked, 0, music: true)),
                        if (appLibrary.localHistory.isNotEmpty)
                          _shelf(
                            'הושמע לאחרונה',
                            appLibrary.localHistory
                                .where((v) => kMusicCategories.contains(
                                    appState.channels.categoryOf(v.channelId)))
                                .toList(),
                          ),
                      ],
                    ),
            ),
          ],
        );

        if (!widget.fullScreen) return body;
        return Scaffold(
          backgroundColor: AppTheme.bg,
          appBar: AppBar(
            backgroundColor: AppTheme.bg,
            title: const Text('FilterMusic'),
            actions: [
              IconButton(
                tooltip: 'הגדרות FilterMusic',
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute<void>(
                      builder: (_) => const MusicSettingsScreen()),
                ),
                icon: Icon(Icons.settings, color: AppTheme.text),
              ),
            ],
          ),
          body: body,
        );
      },
    );
  }

  Widget _header(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(20, 0, 12, 8),
        child: Row(
          children: [
            Text('מוזיקה', style: Theme.of(context).textTheme.displaySmall),
            const Spacer(),
            IconButton(
              tooltip: 'הגדרות FilterMusic',
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute<void>(
                    builder: (_) => const MusicSettingsScreen()),
              ),
              icon: Icon(Icons.settings, color: AppTheme.subtext),
            ),
          ],
        ),
      );

  Widget _searchField() => Padding(
        padding: const EdgeInsets.fromLTRB(16, 4, 16, 14),
        child: TextField(
          onChanged: (v) => setState(() => _query = v.trim()),
          style: TextStyle(color: AppTheme.text, fontSize: 14),
          decoration: InputDecoration(
            hintText: 'שיר או אמן',
            hintStyle: TextStyle(color: AppTheme.subtext, fontSize: 13),
            prefixIcon: Icon(Icons.search, color: AppTheme.subtext),
            filled: true,
            fillColor: AppTheme.bg2,
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(22),
              borderSide: BorderSide.none,
            ),
          ),
        ),
      );

  Widget _searchResults(List<Video> pool) {
    final results = pool
        .where((v) =>
            v.title.toLowerCase().contains(_query.toLowerCase()) ||
            v.channelName.toLowerCase().contains(_query.toLowerCase()))
        .toList();
    if (results.isEmpty) {
      return EmptyState(
        icon: Icons.search_off,
        title: 'לא נמצאו תוצאות ל״$_query״',
        body: 'החיפוש כאן מוגבל למוזיקה מהערוצים המאושרים.',
      );
    }
    return ListView.builder(
      padding: EdgeInsets.only(bottom: PlayerLayer.bottomInset(context)),
      itemCount: results.length,
      itemBuilder: (context, i) => VideoListTile(
        video: results[i],
        onTap: () => playback.playFromList(results, i, music: true),
      ),
    );
  }

  /// "בחירה מהירה" — שלוש שורות של שירים, בלי ערבוב של מה שכבר סומן,
  /// כדי שהאריחים לא יקפצו ממקום למקום בכל פתיחה.
  Widget _quickPicks(List<Video> picks) {
    final columns = <List<Video>>[];
    for (var i = 0; i < picks.length; i += 3) {
      columns.add(picks.sublist(i, (i + 3).clamp(0, picks.length)));
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _shelfTitle('בחירה מהירה',
            onPlayAll: () => playback.playFromList(picks, 0, music: true)),
        SizedBox(
          height: 230,
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            itemCount: columns.length,
            itemBuilder: (context, i) => SizedBox(
              width: MediaQuery.of(context).size.width * 0.82,
              child: Column(
                children: columns[i]
                    .map((v) => Expanded(
                          child: VideoListTile(
                            video: v,
                            dense: true,
                            onTap: () => playback.playFromList(
                                picks, picks.indexOf(v),
                                music: true),
                          ),
                        ))
                    .toList(),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _mixes(List<Mix> mixes) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _shelfTitle('מיקסים'),
          SizedBox(
            height: 196,
            child: ListView.builder(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 12),
              itemCount: mixes.length,
              itemBuilder: (context, i) {
                final mix = mixes[i];
                return Padding(
                  padding: const EdgeInsets.only(left: 12),
                  child: GestureDetector(
                    onTap: () =>
                        playback.playFromList(mix.songs, 0, music: true),
                    child: SizedBox(
                      width: 140,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Container(
                            width: 140,
                            height: 140,
                            clipBehavior: Clip.antiAlias,
                            decoration: BoxDecoration(
                              gradient: AppTheme.accentGradient,
                              borderRadius: BorderRadius.circular(14),
                            ),
                            child: Stack(
                              fit: StackFit.expand,
                              children: [
                                if (mix.artwork != null)
                                  Opacity(
                                    opacity: 0.85,
                                    child: Image.network(mix.artwork!,
                                        fit: BoxFit.cover,
                                        errorBuilder: (_, __, ___) =>
                                            const SizedBox.shrink()),
                                  ),
                                const DecoratedBox(
                                  decoration: BoxDecoration(
                                    gradient: LinearGradient(
                                      begin: Alignment.topCenter,
                                      end: Alignment.bottomCenter,
                                      colors: [
                                        Colors.transparent,
                                        Colors.black87
                                      ],
                                    ),
                                  ),
                                ),
                                const Align(
                                  alignment: Alignment.bottomLeft,
                                  child: Padding(
                                    padding: EdgeInsets.all(8),
                                    child: Icon(Icons.play_circle_fill,
                                        color: Colors.white, size: 26),
                                  ),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(height: 8),
                          Text(mix.title,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                  color: AppTheme.text,
                                  fontSize: 12.5,
                                  fontWeight: FontWeight.w700)),
                          Text(mix.subtitle,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                  color: AppTheme.subtext, fontSize: 11)),
                        ],
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      );

  Widget _shelf(String title, List<Video> songs, {VoidCallback? onPlayAll}) {
    if (songs.isEmpty) return const SizedBox.shrink();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _shelfTitle(title, onPlayAll: onPlayAll),
        SizedBox(
          height: 196,
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            itemCount: songs.length,
            itemBuilder: (context, i) => Padding(
              padding: const EdgeInsets.only(left: 12),
              child: SongTile(
                video: songs[i],
                onTap: () => playback.playFromList(songs, i, music: true),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _shelfTitle(String title, {VoidCallback? onPlayAll}) => Padding(
        padding: const EdgeInsets.fromLTRB(20, 14, 12, 10),
        child: Row(
          children: [
            Text(title,
                style: TextStyle(
                    color: AppTheme.text,
                    fontSize: 16,
                    fontWeight: FontWeight.w700)),
            const Spacer(),
            if (onPlayAll != null)
              TextButton.icon(
                onPressed: onPlayAll,
                icon: Icon(Icons.play_arrow, size: 17, color: AppTheme.accent),
                label: Text('נגן הכל',
                    style: TextStyle(color: AppTheme.accent, fontSize: 12.5)),
              ),
          ],
        ),
      );
}

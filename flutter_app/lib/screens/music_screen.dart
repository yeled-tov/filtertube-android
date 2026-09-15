import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../channels_repo.dart';
import '../library.dart';
import '../models.dart';
import '../radio.dart';
import '../theme.dart';
import '../widgets/video_card.dart';
import '../youtube_api.dart';
import 'player_screen.dart';

/// פילטר מיוזיק — מצב מוזיקה: אודיו בלבד, מהערוצים המאושרים בלבד.
///
/// ## למה זה מסך נפרד ולא פילטר בבית
/// האזנה מתנהגת אחרת מצפייה: העטיפה היא הניווט, התור חשוב יותר מהכותרת,
/// והניגון תמיד אודיו. מסך נפרד נותן את זה בלי לשבור את פיד הווידאו.
class MusicScreen extends StatefulWidget {
  final YoutubeApi api;
  final ChannelsRepo channels;

  const MusicScreen({super.key, required this.api, required this.channels});

  @override
  State<MusicScreen> createState() => _MusicScreenState();
}

class _MusicScreenState extends State<MusicScreen> {
  late final RadioBuilder _radio =
      RadioBuilder(api: widget.api, channels: widget.channels);

  late Future<List<Video>> _picks;
  bool _startingStation = false;

  @override
  void initState() {
    super.initState();
    _picks = _radio.quickPicks();
    appLibrary.addListener(_onLibraryChanged);
  }

  @override
  void dispose() {
    appLibrary.removeListener(_onLibraryChanged);
    super.dispose();
  }

  void _onLibraryChanged() {
    if (mounted) setState(() {});
  }

  /// ערוצי המוזיקה שמותרים ברמת הסינון הנוכחית — עוקבים קודם.
  List<Channel> get _artists {
    final all = widget.channels.channels
        .where((c) => kMusicCategories.contains(c.category))
        .toList();
    all.sort((a, b) {
      final fa = appLibrary.isSubscribed(a.id) ? 0 : 1;
      final fb = appLibrary.isSubscribed(b.id) ? 0 : 1;
      return fa != fb ? fa - fb : a.name.compareTo(b.name);
    });
    return all;
  }

  List<Video> get _likedSongs => appLibrary.likes
      .where((v) => kMusicCategories.contains(widget.channels.categoryOf(v.channelId)))
      .toList();

  Future<void> _refresh() async {
    final f = _radio.quickPicks();
    setState(() => _picks = f);
    await f;
  }

  void _play(Video v, List<Video> queue) {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => PlayerScreen(
        video: v,
        api: widget.api,
        channels: widget.channels,
        queue: queue,
        musicMode: true,
      ),
    ));
  }

  /// תחנה אישית — לחיצה אחת ומשהו מתנגן.
  Future<void> _startPersonalStation() async {
    if (_startingStation) return;
    setState(() => _startingStation = true);
    final station = await _radio.personalStation(musicOnly: true);
    if (!mounted) return;
    setState(() => _startingStation = false);
    if (station.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('אין עדיין מספיק מוזיקה לתחנה')),
      );
      return;
    }
    _play(station.first, station);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        titleSpacing: 16,
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
              child: const Icon(Icons.music_note_rounded,
                  color: Colors.white, size: 20),
            ),
            const SizedBox(width: 9),
            const Text('FilterMusic',
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
        child: ListView(
          padding: const EdgeInsets.only(bottom: 110),
          children: [
            _stationCard(),
            _sectionTitle('בחירה מהירה'),
            FutureBuilder<List<Video>>(
              future: _picks,
              builder: (context, snap) {
                if (snap.connectionState == ConnectionState.waiting) {
                  return const SizedBox(
                    height: 180,
                    child: Center(
                        child: CircularProgressIndicator(color: AppTheme.accent)),
                  );
                }
                final items = snap.data ?? const <Video>[];
                if (items.isEmpty) {
                  return const Padding(
                    padding: EdgeInsets.fromLTRB(16, 4, 16, 8),
                    child: Text('אין עדיין מוזיקה להציג',
                        style: TextStyle(color: AppTheme.subtext, fontSize: 13)),
                  );
                }
                return _SpeedDial(items: items, onPlay: (v) => _play(v, items));
              },
            ),
            if (_artists.isNotEmpty) ...[
              _sectionTitle('אמנים'),
              _artistsRow(),
            ],
            _likedSection(),
          ],
        ),
      ),
    );
  }

  Widget _sectionTitle(String t, {Widget? trailing}) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 18, 16, 10),
        child: Row(
          children: [
            Text(t,
                style: const TextStyle(
                    color: AppTheme.text,
                    fontWeight: FontWeight.w800,
                    fontSize: 16)),
            const Spacer(),
            if (trailing != null) trailing,
          ],
        ),
      );

  Widget _stationCard() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
      child: InkWell(
        onTap: _startPersonalStation,
        borderRadius: BorderRadius.circular(18),
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            gradient: AppTheme.accentGradient,
            borderRadius: BorderRadius.circular(18),
          ),
          child: Row(
            children: [
              const CircleAvatar(
                radius: 23,
                backgroundColor: Colors.white24,
                child: Icon(Icons.radio, color: Colors.white, size: 26),
              ),
              const SizedBox(width: 14),
              const Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('התחנה שלי',
                        style: TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.bold,
                            fontSize: 15)),
                    SizedBox(height: 2),
                    Text('רדיו רצוף לפי מה שאהבת ושמעת',
                        style: TextStyle(color: Colors.white70, fontSize: 12)),
                  ],
                ),
              ),
              if (_startingStation)
                const SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(
                      strokeWidth: 2.2, color: Colors.white),
                )
              else
                const Icon(Icons.play_circle_fill, color: Colors.white, size: 30),
            ],
          ),
        ),
      ),
    );
  }

  Widget _artistsRow() {
    final artists = _artists;
    return SizedBox(
      height: 112,
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12),
        itemCount: artists.length,
        itemBuilder: (context, i) {
          final c = artists[i];
          return GestureDetector(
            onTap: () => Navigator.of(context).push(MaterialPageRoute(
              builder: (_) => _ArtistScreen(
                channel: c,
                api: widget.api,
                channels: widget.channels,
              ),
            )),
            child: SizedBox(
              width: 82,
              child: Column(
                children: [
                  Padding(
                    padding: const EdgeInsets.all(4),
                    child: ChannelAvatar(name: c.name, size: 58),
                  ),
                  const SizedBox(height: 4),
                  Text(c.name,
                      maxLines: 2,
                      textAlign: TextAlign.center,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                          color: AppTheme.subtext2, fontSize: 11.5, height: 1.25)),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _likedSection() {
    final liked = _likedSongs;
    if (liked.isEmpty) return const SizedBox(height: 10);
    final shown = liked.take(5).toList();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _sectionTitle(
          'שירים שאהבתי',
          trailing: liked.length > shown.length
              ? TextButton(
                  onPressed: () => Navigator.of(context).push(MaterialPageRoute(
                    builder: (_) => _SongListScreen(
                      title: 'שירים שאהבתי',
                      songs: liked,
                      api: widget.api,
                      channels: widget.channels,
                    ),
                  )),
                  child: const Text('הצג הכול',
                      style: TextStyle(color: AppTheme.accent, fontSize: 13)),
                )
              : null,
        ),
        ...shown.map((v) => VideoListTile(video: v, onTap: () => _play(v, liked))),
      ],
    );
  }
}

/// "בחירה מהירה" — שלוש שורות של עטיפות, דפדוף אופקי ונקודות עמוד.
///
/// PageView ולא גלילה חופשית: כשמדפדפים אריחים שלמים העין לא מאבדת את
/// המקום, וזה גם מה שנותן את נקודות העמוד שמראות כמה עוד יש.
class _SpeedDial extends StatefulWidget {
  final List<Video> items;
  final ValueChanged<Video> onPlay;

  const _SpeedDial({required this.items, required this.onPlay});

  @override
  State<_SpeedDial> createState() => _SpeedDialState();
}

class _SpeedDialState extends State<_SpeedDial> {
  static const int _rows = 3;
  static const int _cols = 3;
  static const int _perPage = _rows * _cols;

  final _pageController = PageController();
  int _page = 0;

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final pages = (widget.items.length / _perPage).ceil();
    return LayoutBuilder(
      builder: (context, constraints) {
        const hPad = 16.0;
        const gap = 10.0;
        final tile =
            (constraints.maxWidth - hPad * 2 - gap * (_cols - 1)) / _cols;
        // עטיפה ריבועית + שתי שורות טקסט
        final rowHeight = tile + 40;
        final pageHeight = rowHeight * _rows + gap * (_rows - 1);
        return Column(
          children: [
            SizedBox(
              height: pageHeight,
              child: PageView.builder(
                controller: _pageController,
                itemCount: pages,
                onPageChanged: (i) => setState(() => _page = i),
                itemBuilder: (context, p) {
                  final start = p * _perPage;
                  final slice = widget.items
                      .skip(start)
                      .take(_perPage)
                      .toList();
                  return Padding(
                    padding: const EdgeInsets.symmetric(horizontal: hPad),
                    child: GridView.builder(
                      physics: const NeverScrollableScrollPhysics(),
                      padding: EdgeInsets.zero,
                      gridDelegate:
                          SliverGridDelegateWithFixedCrossAxisCount(
                        crossAxisCount: _cols,
                        crossAxisSpacing: gap,
                        mainAxisSpacing: gap,
                        mainAxisExtent: rowHeight,
                      ),
                      itemCount: slice.length,
                      itemBuilder: (context, i) => _CoverTile(
                        video: slice[i],
                        onTap: () => widget.onPlay(slice[i]),
                      ),
                    ),
                  );
                },
              ),
            ),
            if (pages > 1) ...[
              const SizedBox(height: 10),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: List.generate(pages, (i) {
                  final active = i == _page;
                  return AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    margin: const EdgeInsets.symmetric(horizontal: 3),
                    width: active ? 18 : 6,
                    height: 6,
                    decoration: BoxDecoration(
                      color: active ? AppTheme.accent : AppTheme.stroke,
                      borderRadius: BorderRadius.circular(3),
                    ),
                  );
                }),
              ),
            ],
          ],
        );
      },
    );
  }
}

/// אריח עטיפה — תמונה ריבועית עם שם השיר מתחתיה.
class _CoverTile extends StatelessWidget {
  final Video video;
  final VoidCallback onTap;

  const _CoverTile({required this.video, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Expanded(
            child: ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: CachedNetworkImage(
                imageUrl: video.thumbnail,
                fit: BoxFit.cover,
                placeholder: (c, _) => Container(color: AppTheme.card),
                errorWidget: (c, _, __) => Container(
                  color: AppTheme.card,
                  child: const Icon(Icons.music_note, color: AppTheme.subtext),
                ),
              ),
            ),
          ),
          const SizedBox(height: 6),
          Text(video.title,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                  color: AppTheme.text, fontSize: 11.5, height: 1.25)),
        ],
      ),
    );
  }
}

/// כל השירים של אמן אחד.
class _ArtistScreen extends StatefulWidget {
  final Channel channel;
  final YoutubeApi api;
  final ChannelsRepo channels;

  const _ArtistScreen({
    required this.channel,
    required this.api,
    required this.channels,
  });

  @override
  State<_ArtistScreen> createState() => _ArtistScreenState();
}

class _ArtistScreenState extends State<_ArtistScreen> {
  late final Future<List<Video>> _songs =
      widget.api.channelUploads(widget.channel, max: 40);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.channel.name,
            style: const TextStyle(
                fontWeight: FontWeight.w800, color: AppTheme.text, fontSize: 18)),
        actions: [
          IconButton(
            tooltip: appLibrary.isSubscribed(widget.channel.id)
                ? 'הפסק לעקוב'
                : 'עקוב',
            icon: Icon(
              appLibrary.isSubscribed(widget.channel.id)
                  ? Icons.check_circle
                  : Icons.add_circle_outline,
              color: appLibrary.isSubscribed(widget.channel.id)
                  ? AppTheme.accent
                  : AppTheme.text,
            ),
            onPressed: () async {
              await appLibrary.toggleSubscription(widget.channel.id);
              if (mounted) setState(() {});
            },
          ),
        ],
      ),
      body: FutureBuilder<List<Video>>(
        future: _songs,
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const Center(
                child: CircularProgressIndicator(color: AppTheme.accent));
          }
          final songs = snap.data ?? const <Video>[];
          if (songs.isEmpty) {
            return const Center(
                child: Text('אין שירים להצגה',
                    style: TextStyle(color: AppTheme.subtext)));
          }
          return _SongList(
              songs: songs, api: widget.api, channels: widget.channels);
        },
      ),
    );
  }
}

/// רשימת שירים עצמאית (למשל "שירים שאהבתי" המלא).
class _SongListScreen extends StatelessWidget {
  final String title;
  final List<Video> songs;
  final YoutubeApi api;
  final ChannelsRepo channels;

  const _SongListScreen({
    required this.title,
    required this.songs,
    required this.api,
    required this.channels,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('$title (${songs.length})',
            style: const TextStyle(
                fontWeight: FontWeight.w800, color: AppTheme.text, fontSize: 18)),
      ),
      body: _SongList(songs: songs, api: api, channels: channels),
    );
  }
}

/// רשימת שירים שמנגנת במצב מוזיקה — לחיצה על שיר פותחת תור מכל הרשימה.
class _SongList extends StatelessWidget {
  final List<Video> songs;
  final YoutubeApi api;
  final ChannelsRepo channels;

  const _SongList({
    required this.songs,
    required this.api,
    required this.channels,
  });

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      padding: const EdgeInsets.only(bottom: 110),
      itemCount: songs.length,
      itemBuilder: (context, i) => VideoListTile(
        video: songs[i],
        onTap: () => Navigator.of(context).push(MaterialPageRoute(
          builder: (_) => PlayerScreen(
            video: songs[i],
            api: api,
            channels: channels,
            queue: songs,
            musicMode: true,
          ),
        )),
      ),
    );
  }
}

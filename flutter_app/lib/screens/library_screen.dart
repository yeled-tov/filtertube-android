import 'package:flutter/material.dart';

import '../channels_repo.dart';
import '../library.dart';
import '../models.dart';
import '../theme.dart';
import '../widgets/video_card.dart';
import '../youtube_api.dart';
import 'player_screen.dart';

/// הספרייה — מה שסימנת, מה שראית, ואחרי מי אתה עוקב.
class LibraryScreen extends StatefulWidget {
  final YoutubeApi api;
  final ChannelsRepo channels;

  const LibraryScreen({super.key, required this.api, required this.channels});

  @override
  State<LibraryScreen> createState() => _LibraryScreenState();
}

class _LibraryScreenState extends State<LibraryScreen> {
  @override
  void initState() {
    super.initState();
    appLibrary.addListener(_onChanged);
  }

  @override
  void dispose() {
    appLibrary.removeListener(_onChanged);
    super.dispose();
  }

  void _onChanged() {
    if (mounted) setState(() {});
  }

  void _openCollection(String title, List<Video> videos, {bool history = false}) {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => _CollectionScreen(
        title: title,
        videos: videos,
        api: widget.api,
        channels: widget.channels,
        clearable: history,
      ),
    ));
  }

  @override
  Widget build(BuildContext context) {
    final followed = widget.channels.channels
        .where((c) => appLibrary.isSubscribed(c.id))
        .toList();

    return Scaffold(
      appBar: AppBar(
        title: const Text('הספרייה שלי',
            style: TextStyle(
                fontWeight: FontWeight.w800, color: AppTheme.text, fontSize: 19)),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 110),
        children: [
          Row(
            children: [
              _Tile(
                label: 'אהבתי',
                count: appLibrary.likes.length,
                icon: Icons.favorite_rounded,
                color: const Color(0xFFFF3B5C),
                onTap: () => _openCollection('אהבתי', appLibrary.likes),
              ),
              const SizedBox(width: 12),
              _Tile(
                label: 'היסטוריה',
                count: appLibrary.history.length,
                icon: Icons.history_rounded,
                color: const Color(0xFFFF9500),
                onTap: () => _openCollection('היסטוריה', appLibrary.history,
                    history: true),
              ),
            ],
          ),
          const SizedBox(height: 22),
          const Text('ערוצים שאני עוקב אחריהם',
              style: TextStyle(
                  color: AppTheme.text,
                  fontWeight: FontWeight.w700,
                  fontSize: 15)),
          const SizedBox(height: 10),
          if (followed.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 18),
              child: Text(
                'עוד לא עקבת אחרי אף ערוץ.\nבמסך הערוצים אפשר לסמן ערוצים שיופיעו כאן.',
                style: TextStyle(color: AppTheme.subtext, fontSize: 13, height: 1.6),
              ),
            )
          else
            ...followed.map((c) => _ChannelRow(channel: c)),
        ],
      ),
    );
  }
}

class _Tile extends StatelessWidget {
  final String label;
  final int count;
  final IconData icon;
  final Color color;
  final VoidCallback onTap;

  const _Tile({
    required this.label,
    required this.count,
    required this.icon,
    required this.color,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 18),
          decoration: BoxDecoration(
            color: AppTheme.surface,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppTheme.stroke),
          ),
          child: Column(
            children: [
              Icon(icon, color: color, size: 26),
              const SizedBox(height: 8),
              Text(label,
                  style: const TextStyle(
                      color: AppTheme.text,
                      fontWeight: FontWeight.w700,
                      fontSize: 14)),
              const SizedBox(height: 2),
              Text('$count',
                  style: const TextStyle(color: AppTheme.subtext, fontSize: 12)),
            ],
          ),
        ),
      ),
    );
  }
}

class _ChannelRow extends StatelessWidget {
  final Channel channel;
  const _ChannelRow({required this.channel});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 7),
      child: Row(
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              gradient: AppTheme.accentGradient,
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.person, color: Colors.white, size: 22),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(channel.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                        color: AppTheme.text,
                        fontWeight: FontWeight.w600,
                        fontSize: 14)),
                Text(categoryLabel(channel.category),
                    style:
                        const TextStyle(color: AppTheme.subtext, fontSize: 11.5)),
              ],
            ),
          ),
          IconButton(
            icon: const Icon(Icons.check_circle, color: AppTheme.accent),
            tooltip: 'הפסק לעקוב',
            onPressed: () => appLibrary.toggleSubscription(channel.id),
          ),
        ],
      ),
    );
  }
}

/// רשימת סרטונים של אוסף אחד — אהבתי או היסטוריה.
class _CollectionScreen extends StatefulWidget {
  final String title;
  final List<Video> videos;
  final YoutubeApi api;
  final ChannelsRepo channels;
  final bool clearable;

  const _CollectionScreen({
    required this.title,
    required this.videos,
    required this.api,
    required this.channels,
    required this.clearable,
  });

  @override
  State<_CollectionScreen> createState() => _CollectionScreenState();
}

class _CollectionScreenState extends State<_CollectionScreen> {
  late List<Video> _videos = widget.videos;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('${widget.title} (${_videos.length})',
            style: const TextStyle(
                fontWeight: FontWeight.w800, color: AppTheme.text, fontSize: 18)),
        actions: [
          if (widget.clearable && _videos.isNotEmpty)
            TextButton(
              onPressed: () async {
                await appLibrary.clearHistory();
                if (mounted) setState(() => _videos = []);
              },
              child: const Text('נקה', style: TextStyle(color: AppTheme.accent)),
            ),
        ],
      ),
      body: _videos.isEmpty
          ? const Center(
              child: Padding(
                padding: EdgeInsets.all(32),
                child: Text('האוסף ריק',
                    style: TextStyle(color: AppTheme.subtext, fontSize: 14)),
              ),
            )
          : ListView.builder(
              padding: const EdgeInsets.only(bottom: 110),
              itemCount: _videos.length,
              itemBuilder: (context, i) {
                final v = _videos[i];
                return VideoCard(
                  video: v,
                  onTap: () => Navigator.of(context).push(MaterialPageRoute(
                    builder: (_) => PlayerScreen(
                      video: v,
                      api: widget.api,
                      channels: widget.channels,
                    ),
                  )),
                );
              },
            ),
    );
  }
}

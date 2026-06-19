import 'package:flutter/material.dart';
import '../models.dart';
import '../theme.dart';
import '../youtube_api.dart';
import '../channels_repo.dart';
import '../widgets/video_card.dart';
import 'player_screen.dart';

const Map<String, String> kCategoryLabels = {
  'torah': 'תורה',
  'music': 'מוזיקה',
  'dati_light': 'דתי לייט',
  'kids': 'ילדים',
  'diy': 'עשה זאת בעצמך',
  'news': 'חדשות',
  'general': 'כללי',
};

/// רשימת הערוצים המאושרים — לחיצה פותחת את סרטוני הערוץ.
class ChannelsScreen extends StatelessWidget {
  final YoutubeApi api;
  final ChannelsRepo channels;

  const ChannelsScreen({super.key, required this.api, required this.channels});

  @override
  Widget build(BuildContext context) {
    final list = channels.channels;
    return Scaffold(
      appBar: AppBar(
        title: const Text('ערוצים',
            style: TextStyle(fontWeight: FontWeight.bold, color: AppTheme.text)),
      ),
      body: list.isEmpty
          ? const Center(
              child: Text('טוען ערוצים…',
                  style: TextStyle(color: AppTheme.subtext)))
          : ListView.separated(
              itemCount: list.length,
              separatorBuilder: (_, __) =>
                  const Divider(color: Color(0xFF222222), height: 1),
              itemBuilder: (context, i) {
                final c = list[i];
                return ListTile(
                  leading: CircleAvatar(
                    backgroundColor: AppTheme.accent.withValues(alpha: 0.85),
                    child: Text(
                      c.name.isNotEmpty ? c.name.characters.first : '?',
                      style: const TextStyle(color: Colors.white),
                    ),
                  ),
                  title: Text(c.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(color: AppTheme.text)),
                  subtitle: Text(kCategoryLabels[c.category] ?? c.category,
                      style: const TextStyle(color: AppTheme.subtext, fontSize: 12)),
                  trailing:
                      const Icon(Icons.chevron_left, color: AppTheme.subtext),
                  onTap: () => Navigator.of(context).push(MaterialPageRoute(
                    builder: (_) => ChannelVideosScreen(
                        channel: c, api: api, channels: channels),
                  )),
                );
              },
            ),
    );
  }
}

/// סרטוני ערוץ בודד.
class ChannelVideosScreen extends StatefulWidget {
  final Channel channel;
  final YoutubeApi api;
  final ChannelsRepo channels;

  const ChannelVideosScreen({
    super.key,
    required this.channel,
    required this.api,
    required this.channels,
  });

  @override
  State<ChannelVideosScreen> createState() => _ChannelVideosScreenState();
}

class _ChannelVideosScreenState extends State<ChannelVideosScreen> {
  late Future<List<Video>> _future;

  @override
  void initState() {
    super.initState();
    _future = widget.api.channelUploads(widget.channel, max: 30);
  }

  void _open(Video v) {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => PlayerScreen(
          video: v, api: widget.api, channels: widget.channels),
    ));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.channel.name,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(color: AppTheme.text)),
      ),
      body: FutureBuilder<List<Video>>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const Center(
                child: CircularProgressIndicator(color: AppTheme.accent));
          }
          final vids = snap.data ?? [];
          if (vids.isEmpty) {
            return const Center(
                child: Text('אין סרטונים', style: TextStyle(color: AppTheme.subtext)));
          }
          return GridView.builder(
            padding: const EdgeInsets.all(10),
            gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
              maxCrossAxisExtent: 360,
              childAspectRatio: 0.78,
              crossAxisSpacing: 10,
              mainAxisSpacing: 14,
            ),
            itemCount: vids.length,
            itemBuilder: (context, i) =>
                VideoCard(video: vids[i], onTap: () => _open(vids[i])),
          );
        },
      ),
    );
  }
}

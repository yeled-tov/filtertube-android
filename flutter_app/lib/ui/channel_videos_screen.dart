import 'package:flutter/material.dart';

import '../data/app_state.dart';
import '../data/library_store.dart';
import '../data/playback.dart';
import '../models/channel.dart';
import '../models/video.dart';
import '../theme.dart';
import 'widgets/common.dart';
import 'widgets/video_row.dart';

/// כל הסרטונים של ערוץ אחד — נטענים מה-RSS שלו, כלומר בלי לשרוף מכסה.
class ChannelVideosScreen extends StatefulWidget {
  final Channel channel;
  const ChannelVideosScreen({super.key, required this.channel});

  @override
  State<ChannelVideosScreen> createState() => _ChannelVideosScreenState();
}

class _ChannelVideosScreenState extends State<ChannelVideosScreen> {
  List<Video> _videos = [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final list = await appState.feed.channelFeed(widget.channel);
    if (!mounted) return;
    setState(() {
      _videos = list
          .where((v) => !appLibrary.blockedIds.contains(v.id))
          .toList();
      _loading = false;
    });
    final enriched = await appState.api.enrich(_videos, limit: 60);
    if (!mounted) return;
    setState(() => _videos = enriched);
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: appLibrary,
      builder: (context, _) {
        final following =
            appLibrary.isSubscribed(widget.channel.youtubeChannelId);
        return Scaffold(
          backgroundColor: AppTheme.bg,
          appBar: DetailTopBar(widget.channel.name.trim(), actions: [
            TextButton.icon(
              onPressed: () => appLibrary
                  .toggleSubscription(widget.channel.youtubeChannelId),
              icon: Icon(
                  following
                      ? Icons.notifications_active
                      : Icons.notifications_none,
                  size: 18,
                  color: following ? AppTheme.accent : AppTheme.subtext),
              label: Text(following ? 'עוקב ✓' : 'עקוב',
                  style: TextStyle(
                      color: following ? AppTheme.accent : AppTheme.subtext)),
            ),
          ]),
          body: _loading
              ? const CenteredLoading('טוען סרטונים...')
              : _videos.isEmpty
                  ? const EmptyState(
                      icon: Icons.videocam_off_outlined,
                      title: 'אין סרטונים להצגה',
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.only(bottom: 150),
                      itemCount: _videos.length,
                      itemBuilder: (context, i) => VideoRow(
                        video: _videos[i],
                        onTap: () => playback.playFromList(_videos, i),
                      ),
                    ),
        );
      },
    );
  }
}

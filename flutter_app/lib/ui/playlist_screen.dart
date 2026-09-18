import 'package:flutter/material.dart';

import '../data/library_store.dart';
import '../data/playback.dart';
import '../models/video.dart';
import '../theme.dart';
import 'widgets/common.dart';
import 'widgets/video_row.dart';

/// אלבום — נגן הכל, ערבוב, הסרת פריט ומחיקת האלבום.
class PlaylistScreen extends StatelessWidget {
  final String name;
  const PlaylistScreen({super.key, required this.name});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: appLibrary,
      builder: (context, _) {
        final matches =
            appLibrary.playlists.where((p) => p.name == name).toList();
        final videos = matches.isEmpty ? <Video>[] : matches.first.videos;

        return Scaffold(
          backgroundColor: AppTheme.bg,
          appBar: DetailTopBar(name, actions: [
            IconButton(
              tooltip: 'מחק אלבום',
              onPressed: () async {
                await appLibrary.deletePlaylist(name);
                if (context.mounted) Navigator.pop(context);
              },
              icon: Icon(Icons.delete_outline, color: AppTheme.subtext),
            ),
          ]),
          body: videos.isEmpty
              ? const EmptyState(
                  icon: Icons.playlist_play_rounded,
                  title: 'האלבום ריק',
                  body: 'אפשר להוסיף סרטונים מתפריט הפעולות של כל סרטון.',
                )
              : Column(
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(14, 6, 14, 10),
                      child: Row(
                        children: [
                          FilledButton.icon(
                            style: FilledButton.styleFrom(
                                backgroundColor: AppTheme.accent),
                            onPressed: () => playback.playFromList(videos, 0),
                            icon: const Icon(Icons.play_arrow, size: 18),
                            label: const Text('נגן הכל'),
                          ),
                          const SizedBox(width: 10),
                          OutlinedButton.icon(
                            onPressed: () {
                              final shuffled = [...videos]..shuffle();
                              playback.playFromList(shuffled, 0);
                            },
                            icon: const Icon(Icons.shuffle, size: 18),
                            label: const Text('ערבוב'),
                          ),
                        ],
                      ),
                    ),
                    Expanded(
                      child: ListView.builder(
                        padding: const EdgeInsets.only(bottom: 150),
                        itemCount: videos.length,
                        itemBuilder: (context, i) => VideoListTile(
                          video: videos[i],
                          onTap: () => playback.playFromList(videos, i),
                          trailing: IconButton(
                            icon: Icon(Icons.close,
                                size: 17, color: AppTheme.subtext),
                            onPressed: () => appLibrary.removeFromPlaylist(
                                name, videos[i].id),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
        );
      },
    );
  }
}

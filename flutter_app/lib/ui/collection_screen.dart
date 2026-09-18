import 'package:flutter/material.dart';

import '../data/playback.dart';
import '../models/video.dart';
import '../theme.dart';
import 'widgets/common.dart';
import 'widgets/video_row.dart';

/// מסך אוסף כללי — אהבתי, היסטוריה, חסומים. אותו מסך לכולם, כי ההבדל
/// ביניהם הוא רק הרשימה והכותרת.
class CollectionScreen extends StatelessWidget {
  final String title;
  final List<Video> videos;
  final Future<void> Function()? onClear;
  final bool readOnly;
  final String emptyBody;

  const CollectionScreen({
    super.key,
    required this.title,
    required this.videos,
    this.onClear,
    this.readOnly = false,
    this.emptyBody = '',
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bg,
      appBar: DetailTopBar(
        videos.isEmpty ? title : '$title (${videos.length})',
        actions: [
          if (onClear != null && videos.isNotEmpty)
            TextButton(
              onPressed: () async {
                await onClear!.call();
                if (context.mounted) Navigator.pop(context);
              },
              child: Text('נקה', style: TextStyle(color: AppTheme.accent)),
            ),
        ],
      ),
      body: videos.isEmpty
          ? EmptyState(
              icon: Icons.inbox_outlined,
              title: 'האוסף ריק',
              body: emptyBody,
            )
          : ListView.builder(
              padding: const EdgeInsets.only(bottom: 150),
              itemCount: videos.length,
              itemBuilder: (context, i) => readOnly
                  ? VideoListTile(video: videos[i], onTap: () {})
                  : VideoRow(
                      video: videos[i],
                      onTap: () => playback.playFromList(videos, i),
                    ),
            ),
    );
  }
}

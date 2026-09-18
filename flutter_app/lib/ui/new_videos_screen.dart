import 'package:flutter/material.dart';

import '../data/library_store.dart';
import '../data/playback.dart';
import '../theme.dart';
import 'widgets/common.dart';
import 'widgets/video_row.dart';

/// תיבת "סרטונים חדשים" — מה שעלה בערוצים שהמשתמש עוקב אחריהם.
class NewVideosScreen extends StatelessWidget {
  const NewVideosScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: appLibrary,
      builder: (context, _) {
        final videos = appLibrary.newVideos;
        return Scaffold(
          backgroundColor: AppTheme.bg,
          appBar: DetailTopBar(
            videos.isEmpty ? 'סרטונים חדשים' : 'סרטונים חדשים (${videos.length})',
            actions: [
              if (videos.isNotEmpty)
                TextButton(
                  onPressed: appLibrary.clearNewVideos,
                  child: Text('נקה',
                      style: TextStyle(color: AppTheme.accent)),
                ),
            ],
          ),
          body: videos.isEmpty
              ? const EmptyState(
                  icon: Icons.notifications_none_rounded,
                  title: 'אין סרטונים חדשים כרגע',
                  body: 'עקוב אחרי ערוצים מהספרייה — הסרטונים החדשים שלהם '
                      'יופיעו כאן ותקבל עליהם התראה.',
                )
              : ListView.builder(
                  padding: const EdgeInsets.only(bottom: 24),
                  itemCount: videos.length,
                  itemBuilder: (context, i) => VideoRow(
                    video: videos[i],
                    onTap: () => playback.playFromList(videos, i),
                  ),
                ),
        );
      },
    );
  }
}

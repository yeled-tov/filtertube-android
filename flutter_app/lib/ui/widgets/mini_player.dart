import 'package:flutter/material.dart';

import '../../data/playback.dart';
import '../../data/settings_store.dart';
import '../../theme.dart';

/// המיני-נגן: מה שמתנגן, בלי לתפוס את המסך.
///
/// המחוות כאן היו קיימות באפליקציה הראשית מהיום הראשון — החלקה שמאלה
/// לשיר הבא, ימינה לקודם, וגרירה למטה שעוצרת לגמרי. עכשיו יש להן מתג
/// בהגדרות, ולכן מי שהחליק בטעות ועצר את הניגון יכול לכבות אותן.
class MiniPlayer extends StatelessWidget {
  final double thumbnailWidth;
  final VoidCallback onOpen;

  const MiniPlayer(
      {super.key, required this.thumbnailWidth, required this.onOpen});

  @override
  Widget build(BuildContext context) {
    final video = playback.current;
    if (video == null) return const SizedBox.shrink();

    return GestureDetector(
      onTap: onOpen,
      onHorizontalDragEnd: appSettings.miniPlayerSwipe
          ? (details) {
              final v = details.primaryVelocity ?? 0;
              if (v.abs() < 220) return;
              // ב-RTL "שמאלה" על המסך הוא עדיין תנועה שלילית בציר X.
              if (v < 0) {
                playback.next();
              } else if (appSettings.miniSwipeRightRestarts) {
                playback.seekTo(0);
              } else {
                playback.previous();
              }
            }
          : null,
      onVerticalDragEnd: appSettings.miniPlayerSwipe
          ? (details) {
              if ((details.primaryVelocity ?? 0) > 260) playback.stop();
            }
          : null,
      child: Container(
        margin: const EdgeInsets.fromLTRB(12, 0, 12, 8),
        decoration: BoxDecoration(
          color: AppTheme.card,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppTheme.divider),
        ),
        child: Row(
          children: [
            // מקום שמור לנגן עצמו — הוא מרחף מעל הנקודה הזו בדיוק.
            SizedBox(width: thumbnailWidth + 14),
            Expanded(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(video.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                          color: AppTheme.text,
                          fontSize: 12.5,
                          fontWeight: FontWeight.w600)),
                  Text(video.channelName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style:
                          TextStyle(color: AppTheme.subtext, fontSize: 11)),
                ],
              ),
            ),
            IconButton(
              tooltip: playback.isPlaying ? 'השהה' : 'נגן',
              onPressed: playback.togglePlay,
              icon: Icon(
                  playback.isPlaying
                      ? Icons.pause_rounded
                      : Icons.play_arrow_rounded,
                  color: AppTheme.text),
            ),
            IconButton(
              tooltip: 'הבא בתור',
              onPressed: playback.queue.isEmpty ? null : playback.next,
              icon: Icon(Icons.skip_next_rounded,
                  color: playback.queue.isEmpty
                      ? AppTheme.divider
                      : AppTheme.text),
            ),
            IconButton(
              tooltip: 'עצור וסגור',
              onPressed: playback.stop,
              icon: Icon(Icons.close_rounded, color: AppTheme.subtext, size: 20),
            ),
          ],
        ),
      ),
    );
  }
}

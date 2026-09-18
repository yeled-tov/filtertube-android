import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';

import '../../data/auth.dart';
import '../../data/cloud.dart';
import '../../data/library_store.dart';
import '../../data/playback.dart';
import '../../models/video.dart';
import '../../theme.dart';
import 'common.dart';

/// תפריט הפעולות של סרטון — אותן פעולות כמו באפליקציה הראשית, בלי
/// "הורד" (אין הורדות בגרסת החנות).
Future<void> showVideoActions(BuildContext context, Video video,
    {bool musicMode = false}) {
  return showModalBottomSheet<void>(
    context: context,
    backgroundColor: AppTheme.surface,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
    ),
    builder: (sheetContext) =>
        _VideoActionsSheet(video: video, musicMode: musicMode),
  );
}

class _VideoActionsSheet extends StatelessWidget {
  final Video video;
  final bool musicMode;

  const _VideoActionsSheet({required this.video, required this.musicMode});

  @override
  Widget build(BuildContext context) {
    final liked = appLibrary.isLiked(video.id);
    return SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 4),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    musicMode ? 'פעולות לשיר' : 'פעולות לסרטון',
                    style: TextStyle(
                        color: AppTheme.text,
                        fontSize: 16,
                        fontWeight: FontWeight.bold),
                  ),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 10),
            child: Text(video.title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(color: AppTheme.subtext, fontSize: 12)),
          ),
          Divider(height: 1, color: AppTheme.divider),
          _action(context, Icons.queue_music_rounded, 'הבא בתור', () {
            final immediate = playback.enqueueNext(video);
            Navigator.pop(context);
            showToast(context,
                immediate ? 'נוסף לתור הבא' : 'כבר קיים בתור');
          }),
          _action(
            context,
            liked ? Icons.favorite : Icons.favorite_border,
            liked ? 'הסר מסרטונים שאהבתי' : 'הוסף לסרטונים שאהבתי',
            () async {
              await appLibrary.toggleLike(video);
              if (context.mounted) Navigator.pop(context);
            },
          ),
          _action(context, Icons.share_rounded, 'שתף סרטון', () {
            Navigator.pop(context);
            SharePlus.instance.share(
              ShareParams(uri: Uri.parse('https://youtu.be/${video.id}')),
            );
          }),
          _action(context, Icons.playlist_add_rounded, 'הוסף לאלבום', () {
            Navigator.pop(context);
            showPlaylistPicker(context, video);
          }),
          _action(context, Icons.flag_outlined, 'דווח על הסרטון', () {
            Navigator.pop(context);
            showReportDialog(context, video);
          }),
          // ── "הסר" הוא "אל תציג לי" ──────────────────────────────────
          // הסרה מהאוספים בלבד לא עשתה כלום: הפיד מגיע מהרשת והסרטון חזר
          // ברענון הבא. עכשיו הוא נכנס לרשימת החסומים האישית, והשחרור
          // יושב בהגדרות הסינון — מאחורי קוד ההורים.
          _action(context, Icons.block_rounded, 'אל תציג לי את זה יותר', () {
            Navigator.pop(context);
            _confirmBlock(context, video);
          }),
          const SizedBox(height: 8),
        ],
      ),
    );
  }

  Widget _action(
      BuildContext context, IconData icon, String label, VoidCallback onTap) {
    return ListTile(
      leading: Icon(icon, color: AppTheme.accent, size: 21),
      title: Text(label,
          style: TextStyle(color: AppTheme.text, fontSize: 14)),
      onTap: onTap,
      dense: true,
    );
  }
}

/// אישור לפני חסימה. חסימה קשה לבטל (היא דורשת את קוד ההורים), ולכן היא
/// לא אמורה לקרות בהקשה אחת ובלי שהמשתמש יודע מראש איך חוזרים ממנה.
void _confirmBlock(BuildContext context, Video video) {
  showDialog<void>(
    context: context,
    builder: (dialogContext) => AlertDialog(
      icon: Icon(Icons.block_rounded, color: AppTheme.accent),
      title: const Text('להסיר את הסרטון?'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(video.title,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(color: AppTheme.subtext2, fontSize: 13)),
          const SizedBox(height: 10),
          Text('הסרטון ייעלם ממסך הבית, מהחיפוש, מההיסטוריה ומהמיקסים.',
              style: TextStyle(
                  color: AppTheme.text, fontSize: 13.5, height: 1.45)),
          const SizedBox(height: 6),
          Text('שחרור בחזרה אפשרי רק בהגדרות הסינון, עם קוד ההורים.',
              style: TextStyle(
                  color: AppTheme.accent, fontSize: 12.5, height: 1.4)),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(dialogContext),
          child: Text('ביטול', style: TextStyle(color: AppTheme.subtext2)),
        ),
        TextButton(
          onPressed: () async {
            await appLibrary.blockVideo(video);
            if (dialogContext.mounted) Navigator.pop(dialogContext);
          },
          child: Text('הסר',
              style: TextStyle(
                  color: AppTheme.accent, fontWeight: FontWeight.bold)),
        ),
      ],
    ),
  );
}

/// בחירת אלבום (פלייליסט) — כולל יצירה של חדש באותו חלון.
void showPlaylistPicker(BuildContext context, Video video) {
  final controller = TextEditingController();
  showDialog<void>(
    context: context,
    builder: (dialogContext) => StatefulBuilder(
      builder: (dialogContext, setState) => AlertDialog(
        title: const Text('הוסף לאלבום'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: controller,
              decoration: const InputDecoration(labelText: 'אלבום חדש'),
            ),
            const SizedBox(height: 10),
            ...appLibrary.playlists.map(
              (playlist) => ListTile(
                dense: true,
                contentPadding: EdgeInsets.zero,
                leading: Icon(Icons.playlist_play_rounded,
                    color: AppTheme.subtext),
                title: Text(playlist.name,
                    style: TextStyle(color: AppTheme.text, fontSize: 14)),
                subtitle: Text('${playlist.videos.length} סרטונים',
                    style: TextStyle(color: AppTheme.subtext, fontSize: 11)),
                onTap: () async {
                  await appLibrary.addToPlaylist(playlist.name, video);
                  if (!dialogContext.mounted) return;
                  Navigator.pop(dialogContext);
                  showToast(dialogContext, 'נוסף לאלבום');
                },
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('ביטול'),
          ),
          TextButton(
            onPressed: () async {
              final name = controller.text.trim();
              if (name.isEmpty) return;
              await appLibrary.addToPlaylist(name, video);
              if (!dialogContext.mounted) return;
              Navigator.pop(dialogContext);
              showToast(dialogContext, 'נוסף לאלבום');
            },
            child: const Text('צור והוסף'),
          ),
        ],
      ),
    ),
  );
}

/// דיווח על סרטון — נשלח לאותו מקום שאליו האפליקציה הראשית שולחת.
void showReportDialog(BuildContext context, Video video) {
  final controller = TextEditingController();
  var sending = false;
  showDialog<void>(
    context: context,
    builder: (dialogContext) => StatefulBuilder(
      builder: (dialogContext, setState) => AlertDialog(
        title: const Text('דיווח על סרטון'),
        content: TextField(
          controller: controller,
          minLines: 3,
          maxLines: 5,
          decoration: const InputDecoration(labelText: 'מה הבעיה בסרטון?'),
        ),
        actions: [
          TextButton(
            onPressed: sending ? null : () => Navigator.pop(dialogContext),
            child: const Text('ביטול'),
          ),
          TextButton(
            onPressed: sending
                ? null
                : () async {
                    final reason = controller.text.trim();
                    if (reason.isEmpty) return;
                    setState(() => sending = true);
                    final ok = await Cloud.submitBugReport(
                      'דיווח על סרטון ${video.id}\nכותרת: ${video.title}',
                      reason,
                    );
                    if (!dialogContext.mounted) return;
                    setState(() => sending = false);
                    Navigator.pop(dialogContext);
                    // "שליחת הדיווח נכשלה" לא אמר למה. בלי חשבון מאומת
                    // השרת דוחה את הבקשה, וזה תנאי — לא תקלה.
                    showToast(
                      dialogContext,
                      ok
                          ? 'הדיווח נשלח'
                          : (appAuth.ready
                              ? 'שליחת הדיווח נכשלה — בדוק את החיבור'
                              : 'כדי לדווח צריך חשבון עם אימייל מאומת'),
                    );
                  },
            child: Text(sending ? 'שולח…' : 'שלח'),
          ),
        ],
      ),
    ),
  );
}

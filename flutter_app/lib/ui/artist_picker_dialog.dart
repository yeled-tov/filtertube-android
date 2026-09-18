import 'package:flutter/material.dart';

import '../data/app_state.dart';
import '../data/library_store.dart';
import '../data/settings_store.dart';
import '../models/channel.dart';
import '../theme.dart';
import 'widgets/common.dart';

/// "איזה זמרים אתה אוהב?" — נפתח כשמבקשים רדיו אישי ואין עדיין שום אות טעם.
///
/// ## למה שואלים במקום לנחש
/// התקנה טרייה: אין לייקים, אין היסטוריה, ואין ממה להסיק טעם. "רדיו אישי"
/// שנבנה מכלום הוא בעצם הפיד הכללי בסדר אחר, ושאלה אחת עדיפה על כך.
///
/// מחזיר `true` אם נבחרו זמרים והרדיו יכול להתחיל.
Future<bool> showArtistPicker(BuildContext context) async {
  final result = await showDialog<bool>(
    context: context,
    builder: (_) => const _ArtistPickerDialog(),
  );
  return result ?? false;
}

class _ArtistPickerDialog extends StatefulWidget {
  const _ArtistPickerDialog();

  @override
  State<_ArtistPickerDialog> createState() => _ArtistPickerDialogState();
}

class _ArtistPickerDialogState extends State<_ArtistPickerDialog> {
  static const int _minPicks = 3;

  final TextEditingController _search = TextEditingController();
  final Set<String> _picked = {};

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final query = _search.text.trim().toLowerCase();
    final channels = appState.visibleChannels
        .where((c) => kMusicCategories.contains(c.category))
        .where((c) => query.isEmpty || c.name.toLowerCase().contains(query))
        .take(60)
        .toList();

    final remaining = _minPicks - _picked.length;

    return AlertDialog(
      title: const Text('רדיו אישי'),
      content: SizedBox(
        width: 460,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'בחר לפחות $_minPicks, ולפי זה הרדיו יבנה את הסגנון שלך. '
              'ככל שתשמע יותר הוא יכיר אותך יותר טוב — אפשר לשנות בכל רגע.',
              style: TextStyle(
                  color: AppTheme.subtext, fontSize: 12.5, height: 1.5),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _search,
              onChanged: (_) => setState(() {}),
              decoration: const InputDecoration(labelText: 'חיפוש זמר אהוב'),
            ),
            const SizedBox(height: 12),
            Flexible(
              child: SingleChildScrollView(
                child: Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: channels
                      .map((c) => ChoiceChipBox(
                            label: c.name.trim(),
                            selected: _picked.contains(c.youtubeChannelId),
                            onTap: () => setState(() {
                              if (!_picked.remove(c.youtubeChannelId)) {
                                _picked.add(c.youtubeChannelId);
                              }
                            }),
                          ))
                      .toList(),
                ),
              ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context, false),
          child: Text('לא עכשיו', style: TextStyle(color: AppTheme.subtext)),
        ),
        FilledButton(
          style: FilledButton.styleFrom(backgroundColor: AppTheme.accent),
          onPressed: remaining > 0
              ? null
              : () async {
                  // הבחירה נשמרת גם כמנויים וגם כ"זמרים אהובים": המנויים
                  // מזינים את תיבת הסרטונים החדשים, והרשימה הנפרדת שורדת
                  // גם אם המשתמש יבטל מעקב אחרי ערוץ.
                  await appSettings.setFavoriteArtists(_picked);
                  await appSettings.setArtistPickerSeen(true);
                  for (final id in _picked) {
                    if (!appLibrary.isSubscribed(id)) {
                      await appLibrary.toggleSubscription(id);
                    }
                  }
                  if (context.mounted) Navigator.pop(context, true);
                },
          child: Text(remaining > 0
              ? 'בחר עוד $remaining'
              : 'הפעל רדיו (${_picked.length})'),
        ),
      ],
    );
  }
}

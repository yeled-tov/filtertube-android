import 'package:flutter/material.dart';

import '../data/app_state.dart';
import '../data/library_store.dart';
import '../data/settings_store.dart';
import '../models/channel.dart';
import '../theme.dart';
import 'channel_request_dialog.dart';
import 'channel_videos_screen.dart';
import 'widgets/common.dart';

/// כל הערוצים המאושרים — חיפוש, סינון לפי קטגוריה, מעקב, ובקשה להוסיף
/// ערוץ שחסר.
class ChannelsBrowseScreen extends StatefulWidget {
  const ChannelsBrowseScreen({super.key});

  @override
  State<ChannelsBrowseScreen> createState() => _ChannelsBrowseScreenState();
}

class _ChannelsBrowseScreenState extends State<ChannelsBrowseScreen> {
  String _query = '';
  String? _category;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([appState, appLibrary]),
      builder: (context, _) {
        final all = appState.visibleChannels;
        final categories = sortedCategories(all.map((c) => c.category));
        final shown = all.where((c) {
          if (_category != null && c.category != _category) return false;
          if (_query.isEmpty) return true;
          return c.name.toLowerCase().contains(_query.toLowerCase());
        }).toList()
          ..sort((a, b) => a.name.trim().compareTo(b.name.trim()));

        return Scaffold(
          backgroundColor: AppTheme.bg,
          appBar: DetailTopBar('ערוצים מאושרים (${all.length})', actions: [
            IconButton(
              tooltip: 'בקש ערוץ',
              onPressed: () => showChannelRequestDialog(context),
              icon: Icon(Icons.add_circle_outline, color: AppTheme.accent),
            ),
          ]),
          body: Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(14, 4, 14, 8),
                child: TextField(
                  onChanged: (v) => setState(() => _query = v.trim()),
                  style: TextStyle(color: AppTheme.text, fontSize: 14),
                  decoration: InputDecoration(
                    hintText: 'חפש ערוץ…',
                    hintStyle: TextStyle(color: AppTheme.subtext, fontSize: 13),
                    prefixIcon: Icon(Icons.search, color: AppTheme.subtext),
                    filled: true,
                    fillColor: AppTheme.bg2,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(22),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
              ),
              SizedBox(
                height: 40,
                child: ListView(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  children: [
                    Padding(
                      padding: const EdgeInsets.only(left: 8),
                      child: ChoiceChipBox(
                          label: 'הכל',
                          selected: _category == null,
                          onTap: () => setState(() => _category = null)),
                    ),
                    ...categories.map(
                      (cat) => Padding(
                        padding: const EdgeInsets.only(left: 8),
                        child: ChoiceChipBox(
                          label: categoryLabelHe(cat),
                          selected: _category == cat,
                          onTap: () => setState(() => _category = cat),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              Expanded(
                child: shown.isEmpty
                    ? EmptyState(
                        icon: Icons.tv_off,
                        title: 'לא נמצא ערוץ מתאים',
                        body: 'אם הערוץ שאתה מחפש אינו מאושר, אפשר לבקש '
                            'להוסיף אותו — נבדוק ונאשר אם מתאים.',
                        action: FilledButton.icon(
                          style: FilledButton.styleFrom(
                              backgroundColor: AppTheme.accent),
                          onPressed: () => showChannelRequestDialog(context,
                              prefillName: _query),
                          icon: const Icon(Icons.add, size: 18),
                          label: const Text('בקש להוסיף ערוץ'),
                        ),
                      )
                    : ListView.builder(
                        padding: const EdgeInsets.only(bottom: 150),
                        itemCount: shown.length,
                        itemBuilder: (context, i) => _row(shown[i]),
                      ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _row(Channel channel) {
    final following = appLibrary.isSubscribed(channel.youtubeChannelId);
    final audioOnly = isAudioOnlyContent(
        channel.category, appSettings.filterLevel, appSettings.audioOnlyMode);
    return ListTile(
      leading: ChannelAvatar(
        name: channel.name,
        imageUrl: appState.api.avatarOf(channel.youtubeChannelId),
      ),
      title: Text(channel.name.trim(),
          style: TextStyle(color: AppTheme.text, fontSize: 14.5)),
      subtitle: Row(
        children: [
          Text(categoryLabelHe(channel.category),
              style: TextStyle(color: AppTheme.subtext, fontSize: 11.5)),
          if (audioOnly) ...[
            const SizedBox(width: 6),
            Icon(Icons.headphones, size: 12, color: AppTheme.accent),
            const SizedBox(width: 3),
            Text('אודיו בלבד',
                style: TextStyle(color: AppTheme.accent, fontSize: 11)),
          ],
        ],
      ),
      trailing: TextButton(
        onPressed: () =>
            appLibrary.toggleSubscription(channel.youtubeChannelId),
        child: Text(following ? 'עוקב ✓' : 'עקוב',
            style: TextStyle(
                color: following ? AppTheme.accent : AppTheme.subtext2,
                fontSize: 12.5)),
      ),
      onTap: () => Navigator.push(
        context,
        MaterialPageRoute<void>(
            builder: (_) => ChannelVideosScreen(channel: channel)),
      ),
    );
  }
}

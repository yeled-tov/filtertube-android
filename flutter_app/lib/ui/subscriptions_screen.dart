import 'package:flutter/material.dart';

import '../data/app_state.dart';
import '../data/library_store.dart';
import '../theme.dart';
import 'channel_videos_screen.dart';
import 'widgets/common.dart';

/// הערוצים שהמשתמש עוקב אחריהם — הם מה שמזין את תיבת "סרטונים חדשים".
class SubscriptionsScreen extends StatelessWidget {
  const SubscriptionsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([appLibrary, appState]),
      builder: (context, _) {
        final channels = appState.visibleChannels
            .where((c) => appLibrary.isSubscribed(c.youtubeChannelId))
            .toList();

        return Scaffold(
          backgroundColor: AppTheme.bg,
          appBar: DetailTopBar('המנויים שלי (${channels.length})'),
          body: channels.isEmpty
              ? const EmptyState(
                  icon: Icons.subscriptions_outlined,
                  title: 'עוד לא עקבת אחרי ערוצים',
                  body: 'פתח "ערוצים מאושרים" בספרייה ולחץ "עקוב" — '
                      'הסרטונים החדשים שלהם יופיעו בתיבה ותקבל עליהם התראה.',
                )
              : ListView.builder(
                  padding: const EdgeInsets.only(bottom: 150),
                  itemCount: channels.length,
                  itemBuilder: (context, i) {
                    final channel = channels[i];
                    return ListTile(
                      leading: ChannelAvatar(
                        name: channel.name,
                        imageUrl:
                            appState.api.avatarOf(channel.youtubeChannelId),
                      ),
                      title: Text(channel.name,
                          style:
                              TextStyle(color: AppTheme.text, fontSize: 14.5)),
                      subtitle: Text('תקבל התראות על סרטונים חדשים',
                          style: TextStyle(
                              color: AppTheme.subtext, fontSize: 11.5)),
                      trailing: TextButton(
                        onPressed: () => appLibrary
                            .toggleSubscription(channel.youtubeChannelId),
                        child: Text('עוקב ✓',
                            style: TextStyle(color: AppTheme.accent)),
                      ),
                      onTap: () => Navigator.push(
                        context,
                        MaterialPageRoute<void>(
                          builder: (_) => ChannelVideosScreen(channel: channel),
                        ),
                      ),
                    );
                  },
                ),
        );
      },
    );
  }
}

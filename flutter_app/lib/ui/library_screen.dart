import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../data/app_state.dart';
import '../data/auth.dart';
import '../data/library_store.dart';
import '../models/video.dart';
import '../theme.dart';
import 'account_screen.dart';
import 'channels_browse_screen.dart';
import 'collection_screen.dart';
import 'my_requests_screen.dart';
import 'playlist_screen.dart';
import 'subscriptions_screen.dart';

/// הספרייה — אריחי הפסיפס של האפליקציה הראשית: אהבתי, מנויים, ערוצים
/// מאושרים, הבקשות שלי, היסטוריה, ואלבומים.
///
/// אין כאן "ההורדות שלי" ואין "מומלצים מיוטיוב": הראשון לא קיים בגרסת
/// החנות, והשני הגיע בגרסה הראשית ממקור שאינו ה-API הרשמי.
class LibraryScreen extends StatelessWidget {
  const LibraryScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([appLibrary, appAuth, appState]),
      builder: (context, _) {
        final likes = <Video>[
          ...appLibrary.likes,
          ...appLibrary.youtubeLikes,
        ].fold<Map<String, Video>>({}, (acc, v) {
          acc.putIfAbsent(v.id, () => v);
          return acc;
        }).values.toList();

        final tiles = <_TileSpec>[
          _TileSpec('סרטונים שאהבתי', likes.length, Icons.favorite,
              AppTheme.tintRed, likes.map((v) => v.thumbnailUrl).toList(), () {
            Navigator.push(
              context,
              MaterialPageRoute<void>(
                  builder: (_) => CollectionScreen(
                      title: 'סרטונים שאהבתי', videos: likes)),
            );
          }),
          _TileSpec(
              'היסטוריית צפייה',
              appLibrary.localHistory.length,
              Icons.history,
              AppTheme.tintOrange,
              appLibrary.localHistory.map((v) => v.thumbnailUrl).toList(), () {
            Navigator.push(
              context,
              MaterialPageRoute<void>(
                  builder: (_) => CollectionScreen(
                      title: 'היסטוריית צפייה',
                      videos: appLibrary.localHistory,
                      onClear: appLibrary.clearLocalHistory)),
            );
          }),
          _TileSpec(
              'מנויים',
              appLibrary.localSubscriptions.length,
              Icons.subscriptions,
              AppTheme.tintViolet,
              const [], () {
            Navigator.push(
              context,
              MaterialPageRoute<void>(
                  builder: (_) => const SubscriptionsScreen()),
            );
          }),
          _TileSpec(
              'ערוצים מאושרים',
              appState.visibleChannels.length,
              Icons.tv,
              AppTheme.accent,
              const [], () {
            Navigator.push(
              context,
              MaterialPageRoute<void>(
                  builder: (_) => const ChannelsBrowseScreen()),
            );
          }),
          _TileSpec('הבקשות שלי', -1, Icons.inbox, AppTheme.tintAmber, const [],
              () {
            Navigator.push(
              context,
              MaterialPageRoute<void>(builder: (_) => const MyRequestsScreen()),
            );
          }),
          _TileSpec('סרטונים שחסמתי', appLibrary.blockedVideos.length,
              Icons.block, AppTheme.tintTeal, const [], () {
            Navigator.push(
              context,
              MaterialPageRoute<void>(
                  builder: (_) => CollectionScreen(
                        title: 'סרטונים שחסמתי',
                        videos: appLibrary.blockedVideos,
                        readOnly: true,
                        emptyBody:
                            'בלחיצה ארוכה על סרטון אפשר לבחור "אל תציג לי את זה יותר".\n'
                            'השחרור נעשה בהגדרות ← סינון והגנה, עם קוד ההורים.',
                      )),
            );
          }),
        ];

        return ListView(
          padding: EdgeInsets.only(
              top: MediaQuery.of(context).padding.top + 22, bottom: 150),
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
              child:
                  Text('ספריה', style: Theme.of(context).textTheme.displaySmall),
            ),
            _accountCard(context),
            const SizedBox(height: 16),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Column(
                children: [
                  for (var i = 0; i < tiles.length; i += 2)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Row(
                        children: [
                          Expanded(child: _MosaicTile(spec: tiles[i])),
                          const SizedBox(width: 12),
                          Expanded(
                            child: i + 1 < tiles.length
                                ? _MosaicTile(spec: tiles[i + 1])
                                : const SizedBox.shrink(),
                          ),
                        ],
                      ),
                    ),
                ],
              ),
            ),
            _playlists(context),
          ],
        );
      },
    );
  }

  Widget _accountCard(BuildContext context) {
    final signedIn = appAuth.ready;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppTheme.card,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppTheme.divider),
        ),
        child: Row(
          children: [
            Icon(Icons.account_circle,
                color: signedIn ? AppTheme.tintGreen : AppTheme.accent, size: 30),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(signedIn ? appAuth.email : 'חשבון FilterTube',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                          color: AppTheme.text,
                          fontSize: 14,
                          fontWeight: FontWeight.w600)),
                  Text(
                    signedIn
                        ? 'הספרייה והפרופיל מסונכרנים לחשבון'
                        : 'התחברות שומרת את הספרייה ומאפשרת לבקש ערוצים',
                    style: TextStyle(color: AppTheme.subtext, fontSize: 11.5),
                  ),
                ],
              ),
            ),
            TextButton(
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute<void>(builder: (_) => const AccountScreen()),
              ),
              child: Text(signedIn ? 'נהל' : 'התחבר',
                  style: TextStyle(color: AppTheme.accent)),
            ),
          ],
        ),
      ),
    );
  }

  Widget _playlists(BuildContext context) {
    final playlists = appLibrary.playlists;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 14, 8, 4),
          child: Row(
            children: [
              const Icon(Icons.playlist_play_rounded,
                  color: AppTheme.tintRed, size: 20),
              const SizedBox(width: 8),
              Text('אלבומים',
                  style: Theme.of(context).textTheme.headlineSmall),
              const Spacer(),
              IconButton(
                tooltip: 'אלבום חדש',
                onPressed: () => _createPlaylist(context),
                icon: Icon(Icons.add, color: AppTheme.text),
              ),
            ],
          ),
        ),
        if (playlists.isEmpty)
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 8),
            child: Text('עדיין אין אלבומים — צור אחד עם +',
                style: TextStyle(color: AppTheme.subtext, fontSize: 12)),
          )
        else
          ...playlists.map(
            (playlist) => ListTile(
              leading: Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: AppTheme.bg2,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(Icons.playlist_play_rounded,
                    color: AppTheme.subtext),
              ),
              title: Text(playlist.name,
                  style: TextStyle(color: AppTheme.text, fontSize: 14.5)),
              subtitle: Text('${playlist.videos.length} סרטונים',
                  style: TextStyle(color: AppTheme.subtext, fontSize: 12)),
              onTap: () => Navigator.push(
                context,
                MaterialPageRoute<void>(
                    builder: (_) => PlaylistScreen(name: playlist.name)),
              ),
            ),
          ),
      ],
    );
  }

  void _createPlaylist(BuildContext context) {
    final controller = TextEditingController();
    showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('אלבום חדש'),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: const InputDecoration(labelText: 'שם האלבום'),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('בטל')),
          TextButton(
            onPressed: () async {
              await appLibrary.createPlaylist(controller.text);
              if (dialogContext.mounted) Navigator.pop(dialogContext);
            },
            child: const Text('צור'),
          ),
        ],
      ),
    );
  }
}

class _TileSpec {
  final String title;
  final int count;
  final IconData icon;
  final Color tint;
  final List<String> images;
  final VoidCallback onTap;

  const _TileSpec(
      this.title, this.count, this.icon, this.tint, this.images, this.onTap);
}

/// אריח פסיפס — עד ארבע תמונות מהאוסף כרקע, עם אייקון וכותרת מעליהן.
class _MosaicTile extends StatelessWidget {
  final _TileSpec spec;
  const _MosaicTile({required this.spec});

  @override
  Widget build(BuildContext context) {
    final images = spec.images.take(4).toList();
    return GestureDetector(
      onTap: spec.onTap,
      child: Container(
        height: 124,
        clipBehavior: Clip.antiAlias,
        decoration: BoxDecoration(
          color: AppTheme.card,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppTheme.divider),
        ),
        child: Stack(
          fit: StackFit.expand,
          children: [
            if (images.isNotEmpty)
              Opacity(
                opacity: 0.30,
                child: GridView.count(
                  crossAxisCount: 2,
                  physics: const NeverScrollableScrollPhysics(),
                  children: images
                      .map((url) => CachedNetworkImage(
                          imageUrl: url,
                          fit: BoxFit.cover,
                          errorWidget: (_, __, ___) =>
                              Container(color: AppTheme.bg2)))
                      .toList(),
                ),
              ),
            DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    AppTheme.card.withValues(alpha: 0.55),
                    AppTheme.card.withValues(alpha: 0.92),
                  ],
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Container(
                    width: 32,
                    height: 32,
                    decoration: BoxDecoration(
                      color: spec.tint.withValues(alpha: 0.18),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Icon(spec.icon, color: spec.tint, size: 18),
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(spec.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                              color: AppTheme.text,
                              fontSize: 13.5,
                              fontWeight: FontWeight.w700)),
                      if (spec.count >= 0)
                        Text('${spec.count} פריטים',
                            style: TextStyle(
                                color: AppTheme.subtext, fontSize: 11)),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

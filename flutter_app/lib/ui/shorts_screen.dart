import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../data/app_state.dart';
import '../data/playback.dart';
import '../models/video.dart';
import '../theme.dart';
import 'widgets/common.dart';
import 'widgets/video_actions.dart';

/// Shorts — סרטונים קצרים מהערוצים המאושרים בלבד.
///
/// הפיד נבנה מאותו RSS, ואורך הסרטונים מאומת מול ה-API לפני שהוא נכנס:
/// **נכשלים סגור** — בלי אורך מאומת הסרטון לא נכנס, כי סרטון ארוך בפיד
/// השורטס גרוע מפיד קצר יותר.
class ShortsScreen extends StatefulWidget {
  const ShortsScreen({super.key});

  @override
  State<ShortsScreen> createState() => _ShortsScreenState();
}

class _ShortsScreenState extends State<ShortsScreen> {
  @override
  void initState() {
    super.initState();
    // הטעינה מתחילה אחרי הפריים הראשון: היא מרעננת את appState, וקריאה
    // לכך מתוך build הייתה מזמינה בנייה מחדש בזמן בנייה.
    WidgetsBinding.instance.addPostFrameCallback((_) => appState.loadShorts());
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: appState,
      builder: (context, _) {
        if (appState.shortsLoading && appState.shorts.isEmpty) {
          return const CenteredLoading('טוען Shorts...');
        }
        if (appState.shorts.isEmpty) {
          return EmptyState(
            icon: Icons.bolt_rounded,
            title: 'לא נמצאו Shorts מהערוצים המאושרים',
            body: 'הפיד מתעדכן מהערוצים שאושרו — נסה שוב בעוד רגע.',
            action: FilledButton(
              style: FilledButton.styleFrom(backgroundColor: AppTheme.accent),
              onPressed: () => appState.loadShorts(force: true),
              child: const Text('רענן'),
            ),
          );
        }
        return Column(
          children: [
            SizedBox(height: MediaQuery.of(context).padding.top + 14),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 10),
              child: Row(
                children: [
                  Icon(Icons.bolt_rounded, color: AppTheme.accent, size: 22),
                  const SizedBox(width: 8),
                  Text('Shorts',
                      style: Theme.of(context).textTheme.displaySmall),
                  const Spacer(),
                  IconButton(
                    tooltip: 'רענן',
                    onPressed: () => appState.loadShorts(force: true),
                    icon: Icon(Icons.refresh, color: AppTheme.subtext),
                  ),
                ],
              ),
            ),
            Expanded(
              child: GridView.builder(
                padding: const EdgeInsets.fromLTRB(12, 0, 12, 150),
                gridDelegate:
                    const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 3,
                  mainAxisSpacing: 10,
                  crossAxisSpacing: 10,
                  childAspectRatio: 9 / 16,
                ),
                itemCount: appState.shorts.length,
                itemBuilder: (context, i) =>
                    _tile(appState.shorts, i),
              ),
            ),
          ],
        );
      },
    );
  }

  Widget _tile(List<Video> shorts, int index) {
    final video = shorts[index];
    return GestureDetector(
      onTap: () => playback.playShorts(shorts, index),
      onLongPress: () => showVideoActions(context, video),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(14),
        child: Stack(
          fit: StackFit.expand,
          children: [
            CachedNetworkImage(
              imageUrl: video.thumbnailUrl,
              fit: BoxFit.cover,
              placeholder: (_, __) => Container(color: AppTheme.card),
              errorWidget: (_, __, ___) => Container(color: AppTheme.card),
            ),
            const DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  stops: [0.45, 1],
                  colors: [Colors.transparent, Colors.black87],
                ),
              ),
            ),
            Positioned(
              left: 8,
              right: 8,
              bottom: 8,
              child: Text(video.title,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                      color: Colors.white,
                      fontSize: 11,
                      height: 1.3,
                      fontWeight: FontWeight.w600)),
            ),
          ],
        ),
      ),
    );
  }
}

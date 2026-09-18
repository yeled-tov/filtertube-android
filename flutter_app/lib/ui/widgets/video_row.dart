import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../../data/app_state.dart';
import '../../data/library_store.dart';
import '../../models/video.dart';
import '../../theme.dart';
import 'common.dart';
import 'video_actions.dart';

/// כרטיס הסרטון של מסך הבית — תמונה מלאה, סימוני "נצפה"/"אהבתי", ותפריט
/// פעולות שנפתח גם בלחיצה ארוכה וגם משלוש הנקודות.
///
/// שלוש הנקודות אינן מחליפות את הלחיצה הארוכה — הן מה שמסגיר שהיא קיימת.
class VideoRow extends StatelessWidget {
  final Video video;
  final VoidCallback onTap;

  const VideoRow({super.key, required this.video, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: appLibrary,
      builder: (context, _) {
        final liked = appLibrary.isLiked(video.id);
        final watched = appLibrary.watchedIds.contains(video.id);
        final duration = video.formattedDuration();

        return InkWell(
          onTap: onTap,
          onLongPress: () => showVideoActions(context, video),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(12, 0, 12, 18),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(16),
                  child: AspectRatio(
                    aspectRatio: 16 / 9,
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        CachedNetworkImage(
                          imageUrl: video.thumbnailUrl,
                          fit: BoxFit.cover,
                          placeholder: (_, __) => Container(color: AppTheme.card),
                          errorWidget: (_, __, ___) => Container(
                            color: AppTheme.card,
                            child: Icon(Icons.broken_image_outlined,
                                color: AppTheme.subtext),
                          ),
                        ),
                        // צל תחתון בלבד — הוא מה שמאפשר לקרוא את משך הסרטון
                        // גם מעל תמונה בהירה.
                        const DecoratedBox(
                          decoration: BoxDecoration(
                            gradient: LinearGradient(
                              begin: Alignment.topCenter,
                              end: Alignment.bottomCenter,
                              stops: [0.65, 1],
                              colors: [Colors.transparent, Colors.black45],
                            ),
                          ),
                        ),
                        if (duration.isNotEmpty)
                          Positioned(
                            bottom: 8,
                            left: 8,
                            child: Container(
                              padding: const EdgeInsets.symmetric(
                                  horizontal: 6, vertical: 2),
                              decoration: BoxDecoration(
                                color: Colors.black.withValues(alpha: 0.7),
                                borderRadius: BorderRadius.circular(6),
                              ),
                              child: Text(duration,
                                  style: const TextStyle(
                                      color: Colors.white, fontSize: 11)),
                            ),
                          ),
                        // סימון "נצפה" — פס מלא בתחתית התמונה, כמו ביוטיוב
                        if (watched)
                          Align(
                            alignment: Alignment.bottomCenter,
                            child: Container(height: 3, color: AppTheme.accent),
                          ),
                        if (liked)
                          Positioned(
                            top: 9,
                            right: 9,
                            child: Container(
                              padding: const EdgeInsets.all(5),
                              decoration: BoxDecoration(
                                color: Colors.black.withValues(alpha: 0.5),
                                shape: BoxShape.circle,
                              ),
                              child: Icon(Icons.favorite,
                                  color: AppTheme.accent, size: 12),
                            ),
                          ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    ChannelAvatar(
                      name: video.channelName,
                      imageUrl: appState.api.avatarOf(video.channelId),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(video.title,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                  color: AppTheme.text,
                                  fontSize: 14.5,
                                  height: 1.3,
                                  fontWeight: FontWeight.w600)),
                          const SizedBox(height: 3),
                          Text(_subtitle(video),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                  color: AppTheme.subtext, fontSize: 12)),
                          if (watched && video.watchedAgoHe().isNotEmpty)
                            Padding(
                              padding: const EdgeInsets.only(top: 2),
                              child: Text(video.watchedAgoHe(),
                                  style: TextStyle(
                                      color: AppTheme.accent, fontSize: 11)),
                            ),
                        ],
                      ),
                    ),
                    IconButton(
                      iconSize: 20,
                      visualDensity: VisualDensity.compact,
                      tooltip: 'פעולות לסרטון',
                      onPressed: () => showVideoActions(context, video),
                      icon: Icon(Icons.more_vert, color: AppTheme.subtext),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  static String _subtitle(Video video) {
    final parts = <String>[video.channelName];
    final views = video.formattedViewCount();
    if (views.isNotEmpty) parts.add(views);
    final time = video.timeAgoHe();
    // "תאריך לא זמין" רק מרעיש — עדיף להשמיט את החלק הזה
    if (time.isNotEmpty && time != 'תאריך לא זמין') parts.add(time);
    return parts.join(' · ');
  }
}

/// שורת סרטון אופקית — "הבא בתור", חיפוש, אוספים.
class VideoListTile extends StatelessWidget {
  final Video video;
  final VoidCallback onTap;
  final bool dense;
  final Widget? trailing;

  const VideoListTile({
    super.key,
    required this.video,
    required this.onTap,
    this.dense = false,
    this.trailing,
  });

  @override
  Widget build(BuildContext context) {
    final width = dense ? 104.0 : 132.0;
    final height = width * 9 / 16;
    final duration = video.formattedDuration();
    return InkWell(
      onTap: onTap,
      onLongPress: () => showVideoActions(context, video),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(10),
              child: Stack(
                children: [
                  CachedNetworkImage(
                    imageUrl: video.thumbnailUrl,
                    width: width,
                    height: height,
                    fit: BoxFit.cover,
                    placeholder: (_, __) => Container(
                        width: width, height: height, color: AppTheme.card),
                    errorWidget: (_, __, ___) => Container(
                        width: width, height: height, color: AppTheme.card),
                  ),
                  if (duration.isNotEmpty)
                    Positioned(
                      bottom: 4,
                      left: 4,
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 4, vertical: 1),
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: 0.72),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(duration,
                            style: const TextStyle(
                                color: Colors.white, fontSize: 10)),
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(video.title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                          color: AppTheme.text,
                          fontSize: 13,
                          height: 1.3,
                          fontWeight: FontWeight.w600)),
                  const SizedBox(height: 4),
                  Text(video.channelName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(color: AppTheme.subtext, fontSize: 11)),
                ],
              ),
            ),
            if (trailing != null) trailing!,
          ],
        ),
      ),
    );
  }
}

/// אריח שיר מרובע — הפריסה של FilterMusic.
class SongTile extends StatelessWidget {
  final Video video;
  final VoidCallback onTap;
  final double size;

  const SongTile(
      {super.key, required this.video, required this.onTap, this.size = 140});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      onLongPress: () => showVideoActions(context, video, musicMode: true),
      child: SizedBox(
        width: size,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(14),
              child: CachedNetworkImage(
                imageUrl: video.thumbnailUrl,
                width: size,
                height: size,
                fit: BoxFit.cover,
                placeholder: (_, __) =>
                    Container(width: size, height: size, color: AppTheme.card),
                errorWidget: (_, __, ___) => Container(
                  width: size,
                  height: size,
                  color: AppTheme.card,
                  child: Icon(Icons.music_note, color: AppTheme.accent),
                ),
              ),
            ),
            const SizedBox(height: 8),
            Text(video.title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                    color: AppTheme.text,
                    fontSize: 12.5,
                    height: 1.3,
                    fontWeight: FontWeight.w600)),
            const SizedBox(height: 2),
            Text(video.channelName,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(color: AppTheme.subtext, fontSize: 11)),
          ],
        ),
      ),
    );
  }
}

import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../models.dart';
import '../theme.dart';

/// כרטיס וידאו אנכי (לרשת/פיד).
class VideoCard extends StatelessWidget {
  final Video video;
  final VoidCallback onTap;

  const VideoCard({super.key, required this.video, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AspectRatio(
            aspectRatio: 16 / 9,
            child: ClipRRect(
              borderRadius: BorderRadius.circular(10),
              child: CachedNetworkImage(
                imageUrl: video.thumbnail,
                fit: BoxFit.cover,
                placeholder: (c, _) => Container(color: AppTheme.card),
                errorWidget: (c, _, __) =>
                    Container(color: AppTheme.card, child: const Icon(Icons.error)),
              ),
            ),
          ),
          const SizedBox(height: 6),
          Text(
            video.title,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
                color: AppTheme.text, fontSize: 13, fontWeight: FontWeight.w500),
          ),
          const SizedBox(height: 2),
          Text(
            video.channelTitle,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(color: AppTheme.subtext, fontSize: 11),
          ),
        ],
      ),
    );
  }
}

/// שורת וידאו אופקית (ל"הבא בתור").
class VideoListTile extends StatelessWidget {
  final Video video;
  final VoidCallback onTap;

  const VideoListTile({super.key, required this.video, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: CachedNetworkImage(
                imageUrl: video.thumbnail,
                width: 150,
                height: 84,
                fit: BoxFit.cover,
                placeholder: (c, _) => Container(
                    width: 150, height: 84, color: AppTheme.card),
                errorWidget: (c, _, __) => Container(
                    width: 150, height: 84, color: AppTheme.card),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(video.title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                          color: AppTheme.text,
                          fontSize: 13,
                          fontWeight: FontWeight.w500)),
                  const SizedBox(height: 4),
                  Text(video.channelTitle,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                          color: AppTheme.subtext, fontSize: 11)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

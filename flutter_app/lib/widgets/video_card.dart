import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../models.dart';
import '../theme.dart';

/// אווטאר ערוץ — עיגול עם גרדיאנט והאות הראשונה.
class ChannelAvatar extends StatelessWidget {
  final String name;
  final double size;
  const ChannelAvatar({super.key, required this.name, this.size = 32});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: const BoxDecoration(
        gradient: AppTheme.accentGradient,
        shape: BoxShape.circle,
      ),
      alignment: Alignment.center,
      child: Text(
        name.isNotEmpty ? name.characters.first : '?',
        style: TextStyle(
            color: Colors.white,
            fontSize: size * 0.45,
            fontWeight: FontWeight.bold),
      ),
    );
  }
}

/// כרטיס וידאו אנכי (לפיד/רשת) — תמונה מעוגלת, אווטאר ערוץ וטקסט נקי.
class VideoCard extends StatelessWidget {
  final Video video;
  final VoidCallback onTap;

  const VideoCard({super.key, required this.video, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(14),
            child: AspectRatio(
              aspectRatio: 16 / 9,
              child: CachedNetworkImage(
                imageUrl: video.thumbnail,
                fit: BoxFit.cover,
                placeholder: (c, _) => Container(color: AppTheme.card),
                errorWidget: (c, _, __) => Container(
                    color: AppTheme.card,
                    child: const Icon(Icons.broken_image_outlined,
                        color: AppTheme.subtext)),
              ),
            ),
          ),
          const SizedBox(height: 10),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ChannelAvatar(name: video.channelTitle, size: 32),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      video.title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                          color: AppTheme.text,
                          fontSize: 13.5,
                          height: 1.25,
                          fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      video.channelTitle,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                          color: AppTheme.subtext, fontSize: 11.5),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// שורת וידאו אופקית (ל"הבא בתור"/חיפוש) — קלפית עם תמונה מעוגלת.
class VideoListTile extends StatelessWidget {
  final Video video;
  final VoidCallback onTap;

  const VideoListTile({super.key, required this.video, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      child: Material(
        color: AppTheme.card,
        borderRadius: BorderRadius.circular(14),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(14),
          child: Padding(
            padding: const EdgeInsets.all(8),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(10),
                  child: CachedNetworkImage(
                    imageUrl: video.thumbnail,
                    width: 132,
                    height: 76,
                    fit: BoxFit.cover,
                    placeholder: (c, _) => Container(
                        width: 132, height: 76, color: AppTheme.surface),
                    errorWidget: (c, _, __) => Container(
                        width: 132, height: 76, color: AppTheme.surface),
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
                          style: const TextStyle(
                              color: AppTheme.text,
                              fontSize: 13,
                              height: 1.25,
                              fontWeight: FontWeight.w600)),
                      const SizedBox(height: 5),
                      Row(
                        children: [
                          ChannelAvatar(name: video.channelTitle, size: 18),
                          const SizedBox(width: 6),
                          Expanded(
                            child: Text(video.channelTitle,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                    color: AppTheme.subtext, fontSize: 11)),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

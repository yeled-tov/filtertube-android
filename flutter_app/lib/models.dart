// מודלים בסיסיים — וידאו וערוץ ברשימה הלבנה.

class Video {
  final String id;
  final String title;
  final String channelTitle;
  final String channelId;
  final String thumbnail;
  final DateTime? publishedAt;

  const Video({
    required this.id,
    required this.title,
    required this.channelTitle,
    required this.channelId,
    required this.thumbnail,
    this.publishedAt,
  });
}

class Channel {
  final String id; // youtube_channel_id
  final String name;
  final String category;

  const Channel({required this.id, required this.name, this.category = 'general'});

  factory Channel.fromJson(Map<String, dynamic> j) => Channel(
        id: j['youtube_channel_id'] as String,
        name: (j['name'] as String?) ?? '',
        category: (j['category'] as String?) ?? 'general',
      );

  /// מזהה פלייליסט ההעלאות של הערוץ: UC... → UU...
  String get uploadsPlaylistId =>
      id.startsWith('UC') ? 'UU${id.substring(2)}' : id;
}

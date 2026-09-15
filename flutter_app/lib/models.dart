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

  /// שמירה מקומית. התמונה לא נשמרת — היא נגזרת מהמזהה ותמיד זמינה, ואילו
  /// כתובת שנשמרה פעם אחת מתיישנת כשיוטיוב מחליפה דומיין תמונות.
  Map<String, dynamic> toJson() => {
        'id': id,
        'title': title,
        'channelTitle': channelTitle,
        'channelId': channelId,
        'publishedAt': publishedAt?.toIso8601String(),
      };

  factory Video.fromJson(Map<String, dynamic> j) => Video(
        id: (j['id'] as String?) ?? '',
        title: (j['title'] as String?) ?? '',
        channelTitle: (j['channelTitle'] as String?) ?? '',
        channelId: (j['channelId'] as String?) ?? '',
        thumbnail: 'https://i.ytimg.com/vi/${j['id']}/hqdefault.jpg',
        publishedAt: DateTime.tryParse((j['publishedAt'] as String?) ?? ''),
      );
}

/// שמות הקטגוריות בעברית — מקור אחד לכל מסך שמציג קטגוריה.
const Map<String, String> categoryLabels = {
  'torah': 'תורה',
  'torah_study': 'שיעורי תורה',
  'music': 'מוזיקה',
  'dati_light': 'דתי לייט',
  'kids': 'ילדים',
  'diy': 'עשה זאת בעצמך',
  'cooking': 'אפייה ובישול',
  'beauty': 'יופי',
  'fashion': 'אופנה',
  'home': 'בית',
  'education': 'חינוך',
  'events': 'אירועים והופעות',
  'news': 'חדשות',
  'general': 'כללי',
};

String categoryLabel(String id) => categoryLabels[id] ?? id;

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

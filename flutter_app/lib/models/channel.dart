/// ערוץ ברשימה הלבנה. מגיע מ-channels.json / מהשרת — מבנה זהה לאפליקציה
/// הראשית, כדי ששתי הגרסאות יקראו בדיוק את אותו מקור.
class Channel {
  final String youtubeChannelId;
  final String name;
  final String category;
  final String gender;

  const Channel({
    required this.youtubeChannelId,
    required this.name,
    this.category = 'general',
    this.gender = '',
  });

  factory Channel.fromJson(Map<String, dynamic> j) => Channel(
        youtubeChannelId:
            (j['youtube_channel_id'] as String?) ?? (j['youtubeChannelId'] as String?) ?? '',
        name: (j['name'] as String?) ?? '',
        category: (j['category'] as String?) ?? 'general',
        gender: (j['gender'] as String?) ?? '',
      );

  Map<String, dynamic> toJson() => {
        'youtube_channel_id': youtubeChannelId,
        'name': name,
        'category': category,
        'gender': gender,
      };

  /// מזהה פלייליסט ההעלאות של הערוץ: UC... → UU...
  String get uploadsPlaylistId => youtubeChannelId.startsWith('UC')
      ? 'UU${youtubeChannelId.substring(2)}'
      : youtubeChannelId;
}

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

String categoryLabelHe(String id) => categoryLabels[id] ?? id;

/// סדר קבוע לצ'יפים — לא לפי סדר הופעה אקראי ברשימת הערוצים.
List<String> sortedCategories(Iterable<String> categories) {
  final order = categoryLabels.keys.toList();
  final unique = categories.toSet().toList();
  unique.sort((a, b) {
    final ia = order.indexOf(a);
    final ib = order.indexOf(b);
    if (ia == -1 && ib == -1) return a.compareTo(b);
    if (ia == -1) return 1;
    if (ib == -1) return -1;
    return ia.compareTo(ib);
  });
  return unique;
}

/// קטגוריות "דתי לייט" — מוצגות רק ברמה 3, ותמיד מתנגנות כאודיו בלבד.
const Set<String> audioOnlyCategories = {'dati_light'};

/// רמת הסינון שבה מציגים את ערוצי "דתי לייט".
const int kDatiLightLevel = 3;

/// קטגוריות שנחשבות מוזיקה — מה שנכנס ל-FilterMusic ולרדיו המוזיקלי.
const Set<String> kMusicCategories = {'music', 'dati_light', 'events'};

/// האם התוכן הזה מוגבל לאודיו — **ללא קשר למי שמבקש ומה הוא ביקש**.
///
/// פונקציה אחת שכל המסלולים קוראים לה, כדי שאי אפשר יהיה להוסיף מסלול
/// שלישי ששוכח את הכלל.
bool isAudioOnlyContent(String? category, int level, bool audioOnlyMode) =>
    audioOnlyMode ||
    audioOnlyCategories.contains(category) ||
    (level == 1 && category == 'music');

extension ChannelFiltering on List<Channel> {
  /// סינון לפי רמת הסינון ומגדר יעד:
  ///  - רמה 3 (דתי לייט): כל הערוצים כולל "דתי לייט"
  ///  - אחרת: ללא ערוצי "דתי לייט"
  ///  - מגדר שנבחר משאיר ערוצים שמתאימים לו ואת אלה שפתוחים לכולם.
  List<Channel> forLevel(int level, [String userGender = '']) {
    final visible = level == kDatiLightLevel
        ? this
        : where((c) => !audioOnlyCategories.contains(c.category)).toList();
    final target = userGender.trim().toLowerCase();
    if (target.isEmpty) return visible;
    return visible.where((c) {
      final g = c.gender.trim().toLowerCase();
      return g.isEmpty || g == 'all' || g == target;
    }).toList();
  }
}

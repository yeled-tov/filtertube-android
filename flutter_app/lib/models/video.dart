/// סרטון YouTube מערוץ מאושר. מקביל ל-`data class Video` באפליקציה הראשית,
/// בלי השדות ששייכים להורדות (הן אינן קיימות בגרסת החנות).
class Video {
  final String id; // YouTube video ID (11 תווים)
  final String title;
  final String channelName;
  final String channelId;
  final String thumbnailUrl;

  /// מילישניות מאז epoch. 0 = לא ידוע.
  final int publishedAt;

  /// שורטס נשמרים ללשונית הייעודית ולא מתערבבים בפיד.
  final bool isShort;

  final int durationSec;
  final int viewCount;

  /// מתי *המשתמש* צפה (מילישניות). 0 = לא נצפה. שדה נפרד מ-[publishedAt]
  /// בכוונה, כדי שההיסטוריה לא תדרוס את תאריך ההעלאה האמיתי.
  final int watchedAt;

  const Video({
    required this.id,
    required this.title,
    required this.channelName,
    required this.channelId,
    required this.thumbnailUrl,
    this.publishedAt = 0,
    this.isShort = false,
    this.durationSec = 0,
    this.viewCount = 0,
    this.watchedAt = 0,
  });

  Video copyWith({
    String? title,
    String? channelName,
    String? channelId,
    String? thumbnailUrl,
    int? publishedAt,
    bool? isShort,
    int? durationSec,
    int? viewCount,
    int? watchedAt,
  }) =>
      Video(
        id: id,
        title: title ?? this.title,
        channelName: channelName ?? this.channelName,
        channelId: channelId ?? this.channelId,
        thumbnailUrl: thumbnailUrl ?? this.thumbnailUrl,
        publishedAt: publishedAt ?? this.publishedAt,
        isShort: isShort ?? this.isShort,
        durationSec: durationSec ?? this.durationSec,
        viewCount: viewCount ?? this.viewCount,
        watchedAt: watchedAt ?? this.watchedAt,
      );

  /// שמירה מקומית. התמונה לא נשמרת — היא נגזרת מהמזהה ותמיד זמינה, ואילו
  /// כתובת שנשמרה פעם אחת מתיישנת כשיוטיוב מחליפה דומיין תמונות.
  Map<String, dynamic> toJson() => {
        'id': id,
        'title': title,
        'channelName': channelName,
        'channelId': channelId,
        'publishedAt': publishedAt,
        'isShort': isShort,
        'durationSec': durationSec,
        'viewCount': viewCount,
        'watchedAt': watchedAt,
      };

  factory Video.fromJson(Map<String, dynamic> j) {
    final id = (j['id'] as String?) ?? '';
    return Video(
      id: id,
      title: (j['title'] as String?) ?? '',
      channelName: (j['channelName'] as String?) ?? (j['channelTitle'] as String?) ?? '',
      channelId: (j['channelId'] as String?) ?? '',
      thumbnailUrl: thumbFor(id),
      publishedAt: _int(j['publishedAt']),
      isShort: (j['isShort'] as bool?) ?? false,
      durationSec: _int(j['durationSec']),
      viewCount: _int(j['viewCount']),
      watchedAt: _int(j['watchedAt']),
    );
  }

  static int _int(Object? v) {
    if (v is int) return v;
    if (v is num) return v.toInt();
    if (v is String) return int.tryParse(v) ?? 0;
    return 0;
  }

  static String thumbFor(String videoId) =>
      'https://i.ytimg.com/vi/$videoId/hqdefault.jpg';

  /// "לפני 3 שעות", "לפני יומיים", או "תאריך לא זמין"
  String timeAgoHe() => _relativeHe(publishedAt);

  /// "נצפה לפני שעתיים" — ריק אם הסרטון מעולם לא נצפה.
  String watchedAgoHe() => watchedAt <= 0 ? '' : 'נצפה ${_relativeHe(watchedAt)}';

  static String _relativeHe(int timestamp) {
    if (timestamp <= 0) return 'תאריך לא זמין';
    final diff = DateTime.now().millisecondsSinceEpoch - timestamp;
    if (diff < -60000) return 'תאריך לא זמין'; // תאריך עתידי
    final mins = diff ~/ 60000;
    if (mins < 1) return 'עכשיו';
    if (mins == 1) return 'לפני דקה';
    if (mins < 60) return 'לפני $mins דק׳';
    final hrs = mins ~/ 60;
    if (hrs == 1) return 'לפני שעה';
    if (hrs == 2) return 'לפני שעתיים';
    if (hrs < 24) return 'לפני $hrs שעות';
    final days = hrs ~/ 24;
    if (days == 1) return 'אתמול';
    if (days == 2) return 'לפני יומיים';
    if (days < 7) return 'לפני $days ימים';
    final weeks = days ~/ 7;
    if (weeks == 1) return 'לפני שבוע';
    if (weeks == 2) return 'לפני שבועיים';
    if (weeks < 5) return 'לפני $weeks שבועות';
    final months = days ~/ 30;
    if (months <= 1) return 'לפני חודש';
    if (months == 2) return 'לפני חודשיים';
    if (months < 12) return 'לפני $months חודשים';
    final years = days ~/ 365;
    if (years <= 1) return 'לפני שנה';
    if (years == 2) return 'לפני שנתיים';
    return 'לפני $years שנים';
  }

  /// "4:32", "1:12:08", "0:45"
  String formattedDuration() {
    if (durationSec <= 0) return '';
    final h = durationSec ~/ 3600;
    final m = (durationSec % 3600) ~/ 60;
    final s = durationSec % 60;
    String two(int v) => v.toString().padLeft(2, '0');
    return h > 0 ? '$h:${two(m)}:${two(s)}' : '$m:${two(s)}';
  }

  /// "950 צפיות", "1.2K צפיות", "1.25M צפיות"
  String formattedViewCount() {
    if (viewCount <= 0) return '';
    if (viewCount < 1000) return '$viewCount צפיות';
    String trim(String v) =>
        v.contains('.') ? v.replaceFirst(RegExp(r'\.?0+$'), '') : v;
    if (viewCount < 1000000) {
      return '${trim((viewCount / 1000).toStringAsFixed(1))}K צפיות';
    }
    if (viewCount < 1000000000) {
      return '${trim((viewCount / 1000000).toStringAsFixed(2))}M צפיות';
    }
    return '${trim((viewCount / 1000000000).toStringAsFixed(2))}B צפיות';
  }
}

/// ערוץ שהמשתמש עוקב אחריו (מנוי מקומי או מיוטיוב).
class SubChannel {
  final String channelId;
  final String title;
  final String thumbnailUrl;

  const SubChannel({
    required this.channelId,
    required this.title,
    this.thumbnailUrl = '',
  });

  Map<String, dynamic> toJson() =>
      {'channelId': channelId, 'title': title, 'thumbnailUrl': thumbnailUrl};

  factory SubChannel.fromJson(Map<String, dynamic> j) => SubChannel(
        channelId: (j['channelId'] as String?) ?? '',
        title: (j['title'] as String?) ?? '',
        thumbnailUrl: (j['thumbnailUrl'] as String?) ?? '',
      );
}

/// אלבום/פלייליסט מקומי.
class Playlist {
  final String name;
  final List<Video> videos;

  const Playlist({required this.name, required this.videos});

  Map<String, dynamic> toJson() =>
      {'name': name, 'videos': videos.map((v) => v.toJson()).toList()};

  factory Playlist.fromJson(Map<String, dynamic> j) => Playlist(
        name: (j['name'] as String?) ?? '',
        videos: ((j['videos'] as List?) ?? const [])
            .map((e) => Video.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

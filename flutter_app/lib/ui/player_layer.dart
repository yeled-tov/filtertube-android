import 'package:flutter/material.dart';
import 'package:youtube_player_iframe/youtube_player_iframe.dart';

import '../data/playback.dart';
import 'player_view.dart';
import 'shorts_player_view.dart';
import 'widgets/mini_player.dart';

/// שכבת הנגן — יושבת **מעל כל הניווט** של האפליקציה.
///
/// ## למה מעל הניווט ולא בתוך מסך
/// המשתמש פותח סרטון גם ממסך שנפתח מעל הלשוניות: שידורים חיים, אוסף
/// בספרייה, ערוץ. אם הנגן היה חלק מהמסך הראשי, הוא היה נקבר מתחת למסך
/// שנפתח — הניגון היה מתחיל, והמשתמש לא היה רואה כלום. כאן הנגן מרחף מעל
/// הכל, ולכן הוא נראה מאיפה שלא יפתחו אותו.
///
/// ## למה WebView אחד
/// כל `YoutubePlayer` הוא WebView. שניים מהם פירושם שני עותקים של הנגן,
/// שניהם מנגנים. כאן יש אחד, והמעבר בין המיני-נגן למסך המלא הוא הנפשה של
/// המיקום והגודל שלו — ולכן הניגון ממשיך בלי לטעון מחדש.
class PlayerLayer extends StatefulWidget {
  /// המסכים של האפליקציה. הם מצוירים מתחת לשכבה הזו.
  final Widget child;

  const PlayerLayer({super.key, required this.child});

  /// גובה סרגל הניווט הצף ושוליו — המיני-נגן יושב בדיוק מעליו.
  static const double navHeight = 62;
  static const double navMargin = 16;
  static const double miniHeight = 58;
  static const double miniThumbWidth = 92;

  /// כמה מקום לפנות בתחתית מסך שגולל, כדי שהפריט האחרון לא ייחתך.
  static double bottomInset(BuildContext context) =>
      navHeight +
      navMargin +
      miniHeight +
      MediaQuery.of(context).padding.bottom +
      12;

  @override
  State<PlayerLayer> createState() => _PlayerLayerState();
}

class _PlayerLayerState extends State<PlayerLayer> with WidgetsBindingObserver {
  late final YoutubePlayerController _controller;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _controller = YoutubePlayerController(params: playback.buildParams());
    playback.attach(_controller);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  /// אין ניגון ברקע בגרסת החנות: כשהאפליקציה יורדת מהמסך הניגון נעצר.
  ///
  /// `paused` ולא `inactive` — inactive נדלק גם על מגירת ההתראות או שיחה
  /// נכנסת, ולעצור שם היה נראה כמו תקלה.
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused) playback.pause();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: playback,
      builder: (context, _) {
        final media = MediaQuery.of(context);
        final width = media.size.width;
        final safeTop = media.padding.top;
        final safeBottom = media.padding.bottom;
        final expanded = playback.expanded && playback.isActive;

        // Shorts נפרשים על כל המסך ביחס 9:16; כל השאר נשאר 16:9.
        final shorts = expanded && playback.shortsMode;
        final playerWidth = expanded ? width : PlayerLayer.miniThumbWidth;
        final playerHeight =
            shorts ? media.size.height : playerWidth * 9 / 16;
        // הממשק כולו מימין לשמאל, ולכן החלון הקטן יושב בצד ימין של סרגל
        // המיני-נגן. מחושב כ-left ולא כ-right כי AnimatedPositioned מנפיש
        // מאפיין אחד בין שני המצבים, ובמצב הפרוש הנגן ממלא את כל הרוחב.
        final playerLeft = expanded
            ? 0.0
            : width -
                PlayerLayer.navMargin -
                6 -
                PlayerLayer.miniThumbWidth;
        final miniBottom =
            PlayerLayer.navHeight + PlayerLayer.navMargin + safeBottom + 4;
        final playerTop = shorts
            ? 0.0
            : expanded
                ? safeTop + 48
                : media.size.height -
                    miniBottom -
                    PlayerLayer.miniHeight +
                    (PlayerLayer.miniHeight - playerHeight) / 2;

        // BackButtonListener ולא PopScope: השכבה הזו יושבת *מעל* ה-Navigator,
        // ו-PopScope שם היה מתחרה על אותה לחיצה עם המסכים שמתחתיו.
        // BackButtonListener מחזיר "טיפלתי" ורק אז עוצר את השרשרת, ולכן
        // לחיצת חזרה כשהנגן מכווץ ממשיכה כרגיל אל המסך שמתחת.
        return BackButtonListener(
          onBackButtonPressed: () async {
            if (!playback.expanded || !playback.isActive) return false;
            playback.setExpanded(false);
            return true;
          },
          child: Stack(
            children: [
              Positioned.fill(child: widget.child),

              // ── מסך הנגן המלא ────────────────────────────────────────
              if (playback.isActive)
                AnimatedSlide(
                  duration: const Duration(milliseconds: 260),
                  curve: Curves.easeOutCubic,
                  offset: expanded ? Offset.zero : const Offset(0, 1),
                  child: IgnorePointer(
                    ignoring: !expanded,
                    child: AnimatedOpacity(
                      duration: const Duration(milliseconds: 200),
                      opacity: expanded ? 1 : 0,
                      child: playback.shortsMode
                          ? const ShortsPlayerView()
                          : PlayerView(videoSlotHeight: width * 9 / 16),
                    ),
                  ),
                ),

              // ── המיני-נגן ────────────────────────────────────────────
              if (playback.isActive && !expanded)
                Positioned(
                  left: 0,
                  right: 0,
                  bottom: miniBottom,
                  height: PlayerLayer.miniHeight,
                  child: MiniPlayer(
                    thumbnailWidth: PlayerLayer.miniThumbWidth,
                    onOpen: () => playback.setExpanded(true),
                  ),
                ),

              // ── הנגן עצמו ────────────────────────────────────────────
              // נשאר בעץ גם כשלא מנוגן דבר, רק שקוף. הסיבה אינה עיצובית:
              // פירוק והרכבה של ה-WebView טוענים מחדש את דף הנגן, ופקודת
              // ניגון שנשלחת בדיוק אז נופלת על דף שעדיין מתחלף — כלומר
              // הסרטון הראשון אחרי "עצור" פשוט לא היה מתחיל.
              AnimatedPositioned(
                duration: const Duration(milliseconds: 260),
                curve: Curves.easeOutCubic,
                left: playerLeft,
                top: playerTop,
                width: playerWidth,
                height: playerHeight,
                // ── הנגן אינו מקבל מגע, אף פעם ──────────────────────
                // כל הבקרים הם שלנו (showControls: false), ולכן אין למשתמש
                // שום סיבה לגעת ב-WebView עצמו. בלי ה-IgnorePointer הזה
                // הוא בולע כל מחווה שעוברת מעליו: ההחלקה להחלפת סרטון,
                // ההחלקה למטה לכיווץ, הדאבל-טאפ לדילוג, וההחלקה האנכית
                // של ה-Shorts — כולן היו מוגדרות ופשוט לא עובדות.
                child: IgnorePointer(
                  child: AnimatedOpacity(
                    duration: const Duration(milliseconds: 180),
                    opacity: playback.isActive ? 1 : 0,
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(expanded ? 0 : 8),
                      child: YoutubePlayer(
                        controller: _controller,
                        aspectRatio: shorts ? 9 / 16 : 16 / 9,
                        keepAlive: true,
                        enableFullScreenOnVerticalDrag: false,
                        backgroundColor: Colors.black,
                      ),
                    ),
                  ),
                ),
              ),

              // במצב אודיו בלבד הווידאו לא מוצג — אבל הוא חייב להישאר
              // מחובר כדי שהקול ימשיך. הכיסוי נמצא *מעל* הנגן, ולכן
              // המשתמש רואה עטיפה ולא תמונה נעה. גם הוא מוחרג ממגע: יש לו
              // רקע, ובלי ההחרגה הוא היה חוסם את המחוות בדיוק כמו שהנגן
              // עצמו חסם אותן.
              if (playback.isActive && playback.audioOnly)
                AnimatedPositioned(
                  duration: const Duration(milliseconds: 260),
                  curve: Curves.easeOutCubic,
                  left: playerLeft,
                  top: playerTop,
                  width: playerWidth,
                  height: playerHeight,
                  child: const IgnorePointer(child: AudioOnlyCover()),
                ),
            ],
          ),
        );
      },
    );
  }
}

/// באיזו לשונית הקליפה נמצאת. גלובלי כדי שמחליף המצבים במסך הבית יוכל
/// לעבור ל-FilterMusic בלי לפתוח מסך חדש מעל — מסך כזה היה מכסה את
/// המיני-נגן ואת סרגל הניווט.
final ValueNotifier<int> shellTab = ValueNotifier<int>(0);

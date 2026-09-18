import 'package:flutter/material.dart';

/// ערכת צבע — שם, צבע ראשי וצבע משני לגרדיאנט. זהה 1:1 לאפליקציה הראשית.
class Palette {
  final String name;
  final Color accent;
  final Color accent2;
  const Palette(this.name, this.accent, this.accent2);
}

const List<Palette> kPalettes = [
  Palette('אמבר', Color(0xFFFF2D43), Color(0xFFFF6A5C)),
  Palette('מלכותי', Color(0xFF7B5CFF), Color(0xFFB98CFF)),
  Palette('אקווה', Color(0xFF10C8A2), Color(0xFF5BE3C0)),
];

/// מצב הנושא החי. מקביל ל-`object ThemeState` באפליקציה הראשית: כל המסכים
/// קוראים ממנו ישירות, ושינוי צבע או מצב כהה מרענן את האפליקציה כולה.
class ThemeState extends ChangeNotifier {
  ThemeState._();
  static final ThemeState instance = ThemeState._();

  Color _accent = kPalettes.first.accent;
  Color _accent2 = kPalettes.first.accent2;
  bool _dark = true;

  Color get accent => _accent;
  Color get accent2 => _accent2;
  bool get dark => _dark;

  void setPalette(Color a, Color b) {
    if (_accent == a && _accent2 == b) return;
    _accent = a;
    _accent2 = b;
    notifyListeners();
  }

  void setDark(bool value) {
    if (_dark == value) return;
    _dark = value;
    notifyListeners();
  }
}

/// סולם המשטחים והצבעים — מועתק מהאפליקציה הראשית כדי ששתי הגרסאות
/// ייראו כאותו מוצר.
///
/// ## למה הסדר הזה חשוב
/// במצב כהה, משטח שמורם מעל אחר הוא **בהיר** יותר — כך העין קוראת עומק.
/// הסולם מונוטוני: bg < bg2 < card < surface, וההפרשים קטנים בכוונה.
class AppTheme {
  AppTheme._();

  static ThemeState get _s => ThemeState.instance;

  static Color get accent => _s.accent;
  static Color get accent2 => _s.accent2;
  static bool get dark => _s.dark;

  static Color get bg => dark ? const Color(0xFF0A0A0D) : const Color(0xFFF7F7F9);
  static Color get bg2 => dark ? const Color(0xFF121216) : const Color(0xFFEFEFF3);
  static Color get card => dark ? const Color(0xFF17171C) : const Color(0xFFFFFFFF);
  static Color get surface => dark ? const Color(0xFF1D1D23) : const Color(0xFFFFFFFF);
  static Color get divider => dark ? const Color(0xFF2B2B33) : const Color(0xFFE4E4EA);
  static Color get text => dark ? const Color(0xFFF5F5F7) : const Color(0xFF101014);
  static Color get subtext => dark ? const Color(0xFF8E8E99) : const Color(0xFF6E6E78);
  static Color get subtext2 => dark ? const Color(0xFFC8C8D2) : const Color(0xFF44444C);

  /// שם ישן שנשאר בשימוש בכמה מסכים — אותו ערך כמו [divider].
  static Color get stroke => divider;

  /// גוון ההדגשה ברקע — שקיפות ולא צבע קבוע, כדי שיעבוד מעל כל משטח בסולם.
  static Color get accentSoft => accent.withValues(alpha: dark ? 0.16 : 0.10);
  static Color get onAccent => Colors.white;

  static LinearGradient get accentGradient => LinearGradient(
        colors: [accent, accent2],
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
      );

  static List<Color> get accentColors => [accent, accent2];

  // ── צבעי אייקונים בקבוצות ההגדרות (מקביל ל-Tint שבאפליקציה הראשית) ────
  static const Color tintRed = Color(0xFFFF3B30);
  static const Color tintOrange = Color(0xFFFF9500);
  static const Color tintAmber = Color(0xFFFFAA00);
  static const Color tintGreen = Color(0xFF22C55E);
  static const Color tintTeal = Color(0xFF14B8A6);
  static const Color tintBlue = Color(0xFF3B82F6);
  static const Color tintViolet = Color(0xFF7B5CFF);
  static const Color tintPink = Color(0xFFEC4899);
  static const Color tintGold = Color(0xFFD4AF37);

  /// הטיפוגרפיה: Rubik נבחר כי הוא נבנה לעברית ולטינית יחד, כך ש-"FilterTube"
  /// ו"הספרייה שלי" נראים כמו אותה אפליקציה. רווח-השורה נדיב מהרגיל כי
  /// לעברית אין אותיות עולות ויורדות, ושורה עברית נראית דחוסה באותו גובה
  /// שנכון באנגלית.
  static TextTheme _typography(Color body) {
    TextStyle s(double size, FontWeight w, double height) => TextStyle(
          fontFamily: 'Rubik',
          fontSize: size,
          fontWeight: w,
          height: height,
          color: body,
        );
    return TextTheme(
      displaySmall: s(28, FontWeight.w800, 1.25),
      headlineSmall: s(19, FontWeight.w700, 1.3),
      titleLarge: s(17, FontWeight.w700, 1.3),
      titleMedium: s(14.5, FontWeight.w600, 1.35),
      bodyLarge: s(14.5, FontWeight.w400, 1.45),
      bodyMedium: s(13.5, FontWeight.w400, 1.45),
      bodySmall: s(12, FontWeight.w400, 1.4),
      labelLarge: s(14, FontWeight.w600, 1.3),
      labelMedium: s(12.5, FontWeight.w600, 1.3),
      labelSmall: s(11, FontWeight.w500, 1.3),
    );
  }

  static ThemeData build() {
    final base = dark ? ThemeData.dark(useMaterial3: true) : ThemeData.light(useMaterial3: true);
    return base.copyWith(
      scaffoldBackgroundColor: bg,
      canvasColor: bg,
      colorScheme: base.colorScheme.copyWith(
        primary: accent,
        secondary: accent2,
        surface: surface,
        onSurface: text,
        onPrimary: onAccent,
        outline: divider,
        outlineVariant: divider,
        surfaceContainerHighest: bg2,
      ),
      textTheme: _typography(text),
      dividerColor: divider,
      dialogTheme: DialogThemeData(
        backgroundColor: surface,
        titleTextStyle: TextStyle(
            fontFamily: 'Rubik', fontSize: 17, fontWeight: FontWeight.w700, color: text),
        contentTextStyle:
            TextStyle(fontFamily: 'Rubik', fontSize: 13.5, height: 1.45, color: text),
      ),
      bottomSheetTheme: BottomSheetThemeData(backgroundColor: surface),
      appBarTheme: AppBarTheme(
        backgroundColor: bg,
        foregroundColor: text,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        titleTextStyle: TextStyle(
            fontFamily: 'Rubik', fontSize: 18, fontWeight: FontWeight.w700, color: text),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: surface,
        contentTextStyle: TextStyle(fontFamily: 'Rubik', color: text, fontSize: 13),
        behavior: SnackBarBehavior.floating,
      ),
      splashColor: accent.withValues(alpha: 0.10),
      highlightColor: accent.withValues(alpha: 0.06),
      switchTheme: SwitchThemeData(
        thumbColor: WidgetStateProperty.resolveWith(
            (s) => s.contains(WidgetState.selected) ? Colors.white : subtext),
        trackColor: WidgetStateProperty.resolveWith(
            (s) => s.contains(WidgetState.selected) ? accent : divider),
        trackOutlineColor: WidgetStateProperty.all(Colors.transparent),
      ),
      radioTheme: RadioThemeData(
        fillColor: WidgetStateProperty.resolveWith(
            (s) => s.contains(WidgetState.selected) ? accent : subtext),
      ),
      progressIndicatorTheme: ProgressIndicatorThemeData(color: accent),
    );
  }
}

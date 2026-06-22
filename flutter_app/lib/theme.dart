import 'package:flutter/material.dart';

/// שפת עיצוב יוקרתית — כהה עמוק, אדום חי, פינות מעוגלות וטיפוגרפיה נקייה.
/// משותפת לשתי האפליקציות (אותה זהות חזותית).
class AppTheme {
  // צבע ראשי + גרדיאנט
  static const Color accent = Color(0xFFFF2D43);
  static const Color accent2 = Color(0xFFFF6A5C);
  static const LinearGradient accentGradient = LinearGradient(
    colors: [accent, accent2],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  // רקעים — כהה עמוק עם נגיעה קרירה
  static const Color bg = Color(0xFF0B0B0D);
  static const Color bg2 = Color(0xFF111114);
  static const Color surface = Color(0xFF18181B);
  static const Color card = Color(0xFF1E1E22);
  static const Color stroke = Color(0xFF2A2A30);

  // טקסט
  static const Color text = Color(0xFFF5F5F7);
  static const Color subtext = Color(0xFF8E8E96);
  static const Color subtext2 = Color(0xFFC6C6CE);

  static ThemeData dark() {
    final base = ThemeData.dark(useMaterial3: true);
    return base.copyWith(
      scaffoldBackgroundColor: bg,
      colorScheme: base.colorScheme.copyWith(
        primary: accent,
        secondary: accent2,
        surface: surface,
        onSurface: text,
      ),
      textTheme: base.textTheme.apply(bodyColor: text, displayColor: text),
      appBarTheme: const AppBarTheme(
        backgroundColor: bg,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
      ),
      splashColor: accent.withValues(alpha: 0.10),
      highlightColor: accent.withValues(alpha: 0.06),
    );
  }
}

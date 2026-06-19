import 'package:flutter/material.dart';

/// ערכת נושא — אדום ראשי, רקע כהה (תואם לאפליקציית האנדרואיד הקיימת).
class AppTheme {
  static const Color accent = Color(0xFFFF0000);
  static const Color bg = Color(0xFF0F0F0F);
  static const Color surface = Color(0xFF1A1A1A);
  static const Color card = Color(0xFF1F1F1F);
  static const Color text = Colors.white;
  static const Color subtext = Color(0xFF999999);

  static ThemeData dark() {
    final base = ThemeData.dark(useMaterial3: true);
    return base.copyWith(
      scaffoldBackgroundColor: bg,
      colorScheme: base.colorScheme.copyWith(
        primary: accent,
        secondary: accent,
        surface: surface,
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: bg,
        elevation: 0,
        centerTitle: false,
      ),
    );
  }
}

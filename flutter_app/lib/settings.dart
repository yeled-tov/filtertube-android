import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// מופע גלובלי — נגיש מכל מקום (נגן, מסכים) בלי לחווט דרך כל הווידג'טים.
final appSettings = AppSettings();

/// הגדרות מקומיות (shared_preferences) — נטענות פעם אחת בהפעלה ומוחזקות בזיכרון.
class AppSettings extends ChangeNotifier {
  static const _kLevel = 'filter_level';
  static const _kShorts = 'shorts_enabled';

  late SharedPreferences _p;

  /// רמת סינון: 1 = מחמיר · 2 = רגיל · 3 = דתי לייט (מציג את כל הקטגוריות).
  int filterLevel = 2;

  /// הצגת שורטס.
  bool shortsEnabled = true;

  Future<void> load() async {
    _p = await SharedPreferences.getInstance();
    filterLevel = _p.getInt(_kLevel) ?? 2;
    shortsEnabled = _p.getBool(_kShorts) ?? true;
  }

  Future<void> setFilterLevel(int v) async {
    filterLevel = v;
    await _p.setInt(_kLevel, v);
    notifyListeners();
  }

  Future<void> setShortsEnabled(bool v) async {
    shortsEnabled = v;
    await _p.setBool(_kShorts, v);
    notifyListeners();
  }
}

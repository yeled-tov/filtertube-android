import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// מופע גלובלי — נגיש מכל מקום (נגן, מסכים) בלי לחווט דרך כל הווידג'טים.
final appSettings = AppSettings();

/// הגדרות מקומיות (shared_preferences) — נטענות פעם אחת בהפעלה ומוחזקות בזיכרון.
class AppSettings extends ChangeNotifier {
  static const _kLevel = 'filter_level';
  static const _kShorts = 'shorts_enabled';
  static const _kLockCode = 'parental_lock_code';

  late SharedPreferences _p;

  /// רמת סינון: 1 = מחמיר · 2 = רגיל · 3 = דתי לייט (מציג את כל הקטגוריות).
  int filterLevel = 2;

  /// הצגת שורטס.
  bool shortsEnabled = true;

  String _lockCode = '';

  /// האם רמת הסינון נעולה בקוד.
  ///
  /// ## למה זה קיים
  /// רמת סינון שכל אחד יכול לשנות בשתי לחיצות אינה סינון — היא העדפה.
  /// הנעילה היא מה שהופך אותה להחלטה של מי שהתקין את האפליקציה, ולכן היא
  /// חלה על שינוי הרמה ועל ביטול הנעילה עצמה.
  bool get isLocked => _lockCode.isNotEmpty;

  Future<void> load() async {
    _p = await SharedPreferences.getInstance();
    filterLevel = _p.getInt(_kLevel) ?? 2;
    shortsEnabled = _p.getBool(_kShorts) ?? true;
    _lockCode = _p.getString(_kLockCode) ?? '';
  }

  bool codeMatches(String code) => _lockCode == code.trim();

  /// קובע קוד נעילה. קוד ריק מבטל את הנעילה.
  Future<void> setLockCode(String code) async {
    _lockCode = code.trim();
    if (_lockCode.isEmpty) {
      await _p.remove(_kLockCode);
    } else {
      await _p.setString(_kLockCode, _lockCode);
    }
    notifyListeners();
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

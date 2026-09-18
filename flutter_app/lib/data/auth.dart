import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

import '../config.dart';

final appAuth = AuthStore();

class AuthResult {
  final bool ok;
  final String message;
  const AuthResult(this.ok, [this.message = '']);
}

/// חשבון FilterTube — מול Firebase Authentication דרך ה-REST הרשמי.
///
/// ## למה REST ולא ה-SDK המקורי
/// ה-SDK דורש רישום נפרד של אפליקציית Android ואפליקציית iOS בקונסולה,
/// וקובץ הגדרה לכל אחת. REST עובד מול אותו פרויקט עם מפתח Web יחיד, זהה
/// בשתי המערכות — כלומר אותה בנייה עובדת באנדרואיד ובאייפון בלי קבצי
/// הגדרה בכלל, וזו בדיוק הדרישה של גרסת החנות.
///
/// ## למה אימות מייל הוא תנאי
/// הפונקציות בשרת (בקשת ערוץ, דיווח, ניהול) דוחות אסימון שהמייל שלו לא
/// אומת. בלי התנאי כאן המשתמש היה נתקל בשגיאה סתומה במקום בהסבר.
class AuthStore extends ChangeNotifier {
  static const _kRefresh = 'auth_refresh_token';
  static const _kUid = 'auth_uid';
  static const _kEmail = 'auth_email';
  static const _kVerified = 'auth_email_verified';

  final http.Client _client = http.Client();

  String? _idToken;
  DateTime? _expiry;
  String _refreshToken = '';

  String uid = '';
  String email = '';
  bool emailVerified = false;

  bool get signedIn => uid.isNotEmpty && _refreshToken.isNotEmpty;
  bool get ready => signedIn && emailVerified;
  bool get isAdmin =>
      ready && email.toLowerCase() == AppConfig.adminEmail.toLowerCase();

  /// האם בכלל אפשר להשתמש בחשבון בבנייה הזו.
  bool get configured => AppConfig.firebaseWebApiKey.isNotEmpty;

  Future<void> load() async {
    final p = await SharedPreferences.getInstance();
    _refreshToken = p.getString(_kRefresh) ?? '';
    uid = p.getString(_kUid) ?? '';
    email = p.getString(_kEmail) ?? '';
    emailVerified = p.getBool(_kVerified) ?? false;
    notifyListeners();
  }

  Future<void> _persist() async {
    final p = await SharedPreferences.getInstance();
    await p.setString(_kRefresh, _refreshToken);
    await p.setString(_kUid, uid);
    await p.setString(_kEmail, email);
    await p.setBool(_kVerified, emailVerified);
  }

  Uri _identity(String method) =>
      Uri.parse('https://identitytoolkit.googleapis.com/v1/accounts:$method'
          '?key=${AppConfig.firebaseWebApiKey}');

  Future<Map<String, dynamic>?> _post(Uri uri, Map<String, dynamic> body) async {
    try {
      final resp = await _client
          .post(uri,
              headers: const {'Content-Type': 'application/json'},
              body: jsonEncode(body))
          .timeout(const Duration(seconds: 20));
      final decoded =
          jsonDecode(utf8.decode(resp.bodyBytes)) as Map<String, dynamic>;
      if (resp.statusCode != 200) {
        return {'__error': (decoded['error']?['message'] ?? 'UNKNOWN') as String};
      }
      return decoded;
    } catch (_) {
      return {'__error': 'NETWORK'};
    }
  }

  /// הודעות שגיאה בעברית — קוד שגיאה גולמי של גוגל לא אומר למשתמש כלום.
  static String _translate(String code) {
    if (code.startsWith('EMAIL_EXISTS')) return 'כתובת המייל כבר רשומה';
    if (code.startsWith('EMAIL_NOT_FOUND')) return 'לא נמצא חשבון עם המייל הזה';
    if (code.startsWith('INVALID_PASSWORD') ||
        code.startsWith('INVALID_LOGIN_CREDENTIALS')) {
      return 'המייל או הסיסמה שגויים';
    }
    if (code.startsWith('WEAK_PASSWORD')) return 'סיסמה חלשה מדי (לפחות 6 תווים)';
    if (code.startsWith('INVALID_EMAIL')) return 'כתובת מייל לא תקינה';
    if (code.startsWith('TOO_MANY_ATTEMPTS')) {
      return 'יותר מדי ניסיונות. נסה שוב בעוד כמה דקות';
    }
    if (code.startsWith('USER_DISABLED')) return 'החשבון הושבת';
    if (code == 'NETWORK') return 'אין חיבור לאינטרנט';
    return 'אירעה שגיאה. נסה שוב';
  }

  Future<AuthResult> signUp(String mail, String password) async {
    if (!configured) return const AuthResult(false, 'החשבון לא הוגדר בבנייה זו');
    final data = await _post(_identity('signUp'), {
      'email': mail.trim(),
      'password': password,
      'returnSecureToken': true,
    });
    final error = data?['__error'] as String?;
    if (error != null) return AuthResult(false, _translate(error));
    await _store(data!);
    await sendVerificationEmail();
    return const AuthResult(true, 'נשלח מייל אימות — יש לאשר ואז להתחבר');
  }

  Future<AuthResult> signIn(String mail, String password) async {
    if (!configured) return const AuthResult(false, 'החשבון לא הוגדר בבנייה זו');
    final data = await _post(_identity('signInWithPassword'), {
      'email': mail.trim(),
      'password': password,
      'returnSecureToken': true,
    });
    final error = data?['__error'] as String?;
    if (error != null) return AuthResult(false, _translate(error));
    await _store(data!);
    await refreshProfile();
    if (!emailVerified) {
      return const AuthResult(false, 'יש לאמת את כתובת המייל. שלחנו לך קישור.');
    }
    return const AuthResult(true);
  }

  Future<void> _store(Map<String, dynamic> data) async {
    _idToken = data['idToken'] as String?;
    _refreshToken = (data['refreshToken'] as String?) ?? _refreshToken;
    uid = (data['localId'] as String?) ?? uid;
    email = (data['email'] as String?) ?? email;
    final expires = int.tryParse((data['expiresIn'] as String?) ?? '3600') ?? 3600;
    _expiry = DateTime.now().add(Duration(seconds: expires - 60));
    await _persist();
    notifyListeners();
  }

  Future<AuthResult> sendVerificationEmail() async {
    final token = await idToken();
    if (token == null) return const AuthResult(false, 'יש להתחבר קודם');
    final data = await _post(_identity('sendOobCode'), {
      'requestType': 'VERIFY_EMAIL',
      'idToken': token,
    });
    final error = data?['__error'] as String?;
    if (error != null) return AuthResult(false, _translate(error));
    return const AuthResult(true, 'נשלח מייל אימות');
  }

  Future<AuthResult> sendPasswordReset(String mail) async {
    final data = await _post(_identity('sendOobCode'), {
      'requestType': 'PASSWORD_RESET',
      'email': mail.trim(),
    });
    final error = data?['__error'] as String?;
    if (error != null) return AuthResult(false, _translate(error));
    return const AuthResult(true, 'נשלח מייל לאיפוס סיסמה');
  }

  Future<AuthResult> changePassword(String current, String next) async {
    if (email.isEmpty) return const AuthResult(false, 'יש להתחבר קודם');
    final check = await signIn(email, current);
    if (!check.ok && !emailVerified) return check;
    final token = await idToken();
    if (token == null) return const AuthResult(false, 'יש להתחבר מחדש');
    final data = await _post(_identity('update'), {
      'idToken': token,
      'password': next,
      'returnSecureToken': true,
    });
    final error = data?['__error'] as String?;
    if (error != null) return AuthResult(false, _translate(error));
    await _store(data!);
    return const AuthResult(true, 'הסיסמה עודכנה');
  }

  /// בודק מול השרת אם המייל אומת — המשתמש לוחץ על הקישור מחוץ לאפליקציה.
  Future<void> refreshProfile() async {
    final token = await idToken();
    if (token == null) return;
    final data = await _post(_identity('lookup'), {'idToken': token});
    final users = data?['users'] as List?;
    if (users == null || users.isEmpty) return;
    final u = users.first as Map<String, dynamic>;
    emailVerified = (u['emailVerified'] as bool?) ?? false;
    email = (u['email'] as String?) ?? email;
    uid = (u['localId'] as String?) ?? uid;
    await _persist();
    notifyListeners();
  }

  /// אסימון תקף לקריאות לשרת. מרענן מעצמו כשפג.
  Future<String?> idToken() async {
    if (!configured || _refreshToken.isEmpty) return null;
    if (_idToken != null && _expiry != null && DateTime.now().isBefore(_expiry!)) {
      return _idToken;
    }
    try {
      final resp = await _client
          .post(
            Uri.parse(
                'https://securetoken.googleapis.com/v1/token?key=${AppConfig.firebaseWebApiKey}'),
            body: {'grant_type': 'refresh_token', 'refresh_token': _refreshToken},
          )
          .timeout(const Duration(seconds: 20));
      if (resp.statusCode != 200) return null;
      final data = jsonDecode(resp.body) as Map<String, dynamic>;
      _idToken = data['id_token'] as String?;
      _refreshToken = (data['refresh_token'] as String?) ?? _refreshToken;
      uid = (data['user_id'] as String?) ?? uid;
      final expires = int.tryParse((data['expires_in'] as String?) ?? '3600') ?? 3600;
      _expiry = DateTime.now().add(Duration(seconds: expires - 60));
      await _persist();
      return _idToken;
    } catch (_) {
      return null;
    }
  }

  Future<void> signOut() async {
    _idToken = null;
    _expiry = null;
    _refreshToken = '';
    uid = '';
    email = '';
    emailVerified = false;
    await _persist();
    notifyListeners();
  }
}

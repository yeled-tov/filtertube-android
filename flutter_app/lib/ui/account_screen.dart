import 'package:flutter/material.dart';
import 'package:package_info_plus/package_info_plus.dart';

import '../config.dart';
import '../data/auth.dart';
import '../data/cloud.dart';
import '../data/library_store.dart';
import '../data/settings_store.dart';
import '../theme.dart';
import 'widgets/common.dart';

/// חשבון FilterTube — הרשמה, כניסה, אימות מייל וסנכרון ענן.
///
/// ## למה החשבון אינו חובה
/// האפליקציה עובדת במלואה בלי חשבון: הסינון, הספרייה וההעדפות כולם
/// מקומיים. החשבון נדרש רק למה שבאמת מצריך שרת — סנכרון בין מכשירים,
/// בקשות ערוץ ודיווחים. חסימת האפליקציה מאחורי הרשמה היא גם חסם למשתמש
/// וגם עילה לדחייה בבדיקת App Store, ואין לה כאן תמורה.
class AccountScreen extends StatefulWidget {
  const AccountScreen({super.key});

  @override
  State<AccountScreen> createState() => _AccountScreenState();
}

class _AccountScreenState extends State<AccountScreen> {
  final _email = TextEditingController();
  final _password = TextEditingController();

  bool _registering = false;
  bool _busy = false;
  String _status = '';
  bool _statusOk = false;

  @override
  void initState() {
    super.initState();
    _email.text = appAuth.email;
  }

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  void _report(AuthResult result) {
    setState(() {
      _statusOk = result.ok;
      _status = result.message.isEmpty
          ? (result.ok ? 'בוצע' : 'הפעולה נכשלה')
          : result.message;
    });
  }

  Future<void> _submit() async {
    setState(() {
      _busy = true;
      _status = '';
    });
    final result = _registering
        ? await appAuth.signUp(_email.text, _password.text)
        : await appAuth.signIn(_email.text, _password.text);
    if (!mounted) return;
    setState(() => _busy = false);
    _report(result);
    if (result.ok && appAuth.ready) {
      await appSettings.setCloudAccount(appAuth.uid, appAuth.email);
      await _sync();
    }
  }

  Future<void> _sync() async {
    setState(() {
      _busy = true;
      _status = 'מסנכרן…';
      _statusOk = true;
    });
    final info = await PackageInfo.fromPlatform();
    final ok = await Cloud.synchronize(appVersion: info.version);
    if (!mounted) return;
    setState(() {
      _busy = false;
      _statusOk = ok;
      _status = ok
          ? 'הצלחה — הנתונים סונכרנו לענן'
          : 'לא ניתן לסנכרן כרגע. נסה שוב בעוד רגע.';
    });
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: appAuth,
      builder: (context, _) => Scaffold(
        backgroundColor: AppTheme.bg,
        appBar: const DetailTopBar('חשבון וסנכרון ענן'),
        body: ListView(
          padding: const EdgeInsets.fromLTRB(20, 14, 20, 40),
          children: [
            if (!appAuth.configured)
              _notice(
                'החשבון לא הוגדר בבנייה הזו. יש למלא את מפתח ה-Web של פרויקט '
                'Firebase בקובץ lib/config.dart לפני פרסום.',
              )
            else if (appAuth.ready)
              ..._signedIn()
            else
              ..._signedOut(),
          ],
        ),
      ),
    );
  }

  List<Widget> _signedIn() => [
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: AppTheme.card,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppTheme.divider),
          ),
          child: Row(
            children: [
              const Icon(Icons.verified_user, color: AppTheme.tintGreen, size: 28),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(appAuth.email,
                        style: TextStyle(
                            color: AppTheme.text,
                            fontSize: 14,
                            fontWeight: FontWeight.w600)),
                    Text('מייל מאומת · הנתונים נשמרים בחשבון זה',
                        style: TextStyle(
                            color: AppTheme.subtext, fontSize: 11.5)),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 18),
        FilledButton.icon(
          style: FilledButton.styleFrom(
              backgroundColor: AppTheme.accent,
              minimumSize: const Size.fromHeight(46)),
          onPressed: _busy ? null : _sync,
          icon: const Icon(Icons.sync, size: 18),
          label: Text(_busy ? 'מסנכרן…' : 'סנכרן עכשיו'),
        ),
        const SizedBox(height: 10),
        OutlinedButton.icon(
          style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(46)),
          onPressed: _busy
              ? null
              : () async {
                  await appAuth.signOut();
                  await appSettings.setCloudAccount('', '');
                  if (mounted) setState(() => _status = 'התנתקת');
                },
          icon: const Icon(Icons.logout, size: 18),
          label: const Text('התנתק'),
        ),
        const SizedBox(height: 10),
        TextButton(
          onPressed: _busy
              ? null
              : () async {
                  final confirmed = await showDialog<bool>(
                    context: context,
                    builder: (dialogContext) => AlertDialog(
                      title: const Text('למחוק את הנתונים במכשיר?'),
                      content: const Text(
                          'הספרייה, ההיסטוריה והאלבומים יימחקו מהמכשיר. '
                          'מה שכבר סונכרן לענן יישאר שם.'),
                      actions: [
                        TextButton(
                            onPressed: () => Navigator.pop(dialogContext, false),
                            child: const Text('בטל')),
                        TextButton(
                            onPressed: () => Navigator.pop(dialogContext, true),
                            child: const Text('מחק')),
                      ],
                    ),
                  );
                  if (confirmed != true) return;
                  await appLibrary.clearAccountData();
                  if (mounted) setState(() => _status = 'הנתונים נמחקו מהמכשיר');
                },
          child: Text('מחק את הנתונים במכשיר',
              style: TextStyle(color: AppTheme.subtext)),
        ),
        if (_status.isNotEmpty) _statusLine(),
      ];

  List<Widget> _signedOut() => [
        Text(
          'האפליקציה עובדת במלואה גם בלי חשבון. החשבון נדרש כדי לשמור את '
          'הספרייה בענן, לשחזר אותה במכשיר אחר, ולשלוח בקשות להוספת ערוצים.',
          style: TextStyle(color: AppTheme.subtext2, fontSize: 13, height: 1.6),
        ),
        const SizedBox(height: 18),
        TextField(
          controller: _email,
          keyboardType: TextInputType.emailAddress,
          decoration: const InputDecoration(labelText: 'כתובת מייל'),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _password,
          obscureText: true,
          decoration: const InputDecoration(labelText: 'סיסמה'),
        ),
        const SizedBox(height: 18),
        FilledButton(
          style: FilledButton.styleFrom(
              backgroundColor: AppTheme.accent,
              minimumSize: const Size.fromHeight(46)),
          onPressed: _busy ? null : _submit,
          child: Text(_busy
              ? 'רגע…'
              : (_registering ? 'הרשמה' : 'כניסה')),
        ),
        const SizedBox(height: 8),
        TextButton(
          onPressed: () => setState(() {
            _registering = !_registering;
            _status = '';
          }),
          child: Text(
              _registering ? 'כבר יש לי חשבון — כניסה' : 'אין לי חשבון — הרשמה',
              style: TextStyle(color: AppTheme.accent)),
        ),
        TextButton(
          onPressed: _busy
              ? null
              : () async {
                  final result = await appAuth.sendPasswordReset(_email.text);
                  if (mounted) _report(result);
                },
          child: Text('שכחתי סיסמה',
              style: TextStyle(color: AppTheme.subtext)),
        ),
        if (appAuth.signedIn && !appAuth.emailVerified) ...[
          const SizedBox(height: 8),
          OutlinedButton(
            onPressed: _busy
                ? null
                : () async {
                    await appAuth.sendVerificationEmail();
                    await appAuth.refreshProfile();
                    if (mounted) {
                      setState(() {
                        _statusOk = appAuth.emailVerified;
                        _status = appAuth.emailVerified
                            ? 'המייל אומת ✓'
                            : 'שלחנו מייל אימות. אחרי האישור חזור לכאן.';
                      });
                    }
                  },
            child: const Text('שלח שוב מייל אימות / בדוק אימות'),
          ),
        ],
        if (_status.isNotEmpty) _statusLine(),
        const SizedBox(height: 24),
        Text(
          'הנתונים נשמרים בפרויקט Firebase של FilterTube '
          '(${AppConfig.firebaseProjectId}). אין פרסומות ואין מעקב צד שלישי.',
          style: TextStyle(color: AppTheme.subtext, fontSize: 11.5, height: 1.5),
        ),
      ];

  Widget _statusLine() => Padding(
        padding: const EdgeInsets.only(top: 14),
        child: Text(_status,
            style: TextStyle(
                color: _statusOk ? AppTheme.tintGreen : AppTheme.tintRed,
                fontSize: 12.5)),
      );

  Widget _notice(String text) => Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: AppTheme.bg2,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: AppTheme.divider),
        ),
        child: Text(text,
            style:
                TextStyle(color: AppTheme.subtext2, fontSize: 12.5, height: 1.5)),
      );
}

import 'package:flutter/material.dart';

import '../data/app_state.dart';
import '../data/library_store.dart';
import '../data/settings_store.dart';
import '../models/channel.dart';
import '../theme.dart';
import 'widgets/common.dart';

/// ההיכרות הראשונה — שם, מגדר, רמת סינון ובחירת ערוצים אהובים.
///
/// אותם ארבעה שלבים כמו באפליקציה הראשית. השלב האחרון אינו קישוט: הרדיו
/// והמיקסים נבנים מהטעם של המשתמש, ובהתקנה טרייה אין ממה להסיק אותו.
class OnboardingScreen extends StatefulWidget {
  final VoidCallback onDone;
  const OnboardingScreen({super.key, required this.onDone});

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  final PageController _pages = PageController();
  final TextEditingController _name = TextEditingController();
  final TextEditingController _search = TextEditingController();

  int _step = 0;
  String _gender = '';
  int _level = 2;
  final Set<String> _picked = {};

  @override
  void dispose() {
    _pages.dispose();
    _name.dispose();
    _search.dispose();
    super.dispose();
  }

  void _next() {
    if (_step < 3) {
      setState(() => _step++);
      _pages.animateToPage(_step,
          duration: const Duration(milliseconds: 280), curve: Curves.easeOut);
    } else {
      _finish();
    }
  }

  void _back() {
    if (_step == 0) return;
    setState(() => _step--);
    _pages.animateToPage(_step,
        duration: const Duration(milliseconds: 280), curve: Curves.easeOut);
  }

  Future<void> _finish() async {
    await appSettings.setUserName(_name.text.trim());
    await appSettings.setUserGender(_gender);
    await appSettings.setFilterLevel(_level);
    await appLibrary.replaceLocalSubscriptions(_picked);
    await appSettings.setOnboardingDone(true);
    widget.onDone();
  }

  bool get _canContinue {
    switch (_step) {
      case 0:
        return _name.text.trim().isNotEmpty && _gender.isNotEmpty;
      default:
        return true;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bg,
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
              child: Row(
                children: List.generate(
                  4,
                  (i) => Expanded(
                    child: Container(
                      height: 3,
                      margin: const EdgeInsets.symmetric(horizontal: 3),
                      decoration: BoxDecoration(
                        color: i <= _step ? AppTheme.accent : AppTheme.divider,
                        borderRadius: BorderRadius.circular(3),
                      ),
                    ),
                  ),
                ),
              ),
            ),
            Expanded(
              child: PageView(
                controller: _pages,
                physics: const NeverScrollableScrollPhysics(),
                children: [_profile(), _filterLevel(), _artists(), _done()],
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(20),
              child: Row(
                children: [
                  if (_step > 0)
                    TextButton(
                        onPressed: _back,
                        child: Text('חזרה',
                            style: TextStyle(color: AppTheme.subtext))),
                  const Spacer(),
                  FilledButton(
                    style: FilledButton.styleFrom(
                      backgroundColor: AppTheme.accent,
                      minimumSize: const Size(150, 46),
                    ),
                    onPressed: _canContinue ? _next : null,
                    child: Text(_step == 3 ? 'כניסה לאפליקציה ✨' : 'המשך'),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _page({required String title, required List<Widget> children}) =>
      ListView(
        padding: const EdgeInsets.fromLTRB(22, 10, 22, 10),
        children: [
          Text(title,
              style: TextStyle(
                  color: AppTheme.text,
                  fontSize: 24,
                  fontWeight: FontWeight.w800,
                  height: 1.35)),
          const SizedBox(height: 18),
          ...children,
        ],
      );

  Widget _profile() => _page(
        title: 'בואו נתאים את FilterTube 👋',
        children: [
          TextField(
            controller: _name,
            onChanged: (_) => setState(() {}),
            decoration: const InputDecoration(labelText: 'שם'),
          ),
          const SizedBox(height: 20),
          Text('מגדר',
              style: TextStyle(
                  color: AppTheme.text,
                  fontSize: 14,
                  fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            children: [
              ChoiceChipBox(
                  label: '👨 זכר',
                  selected: _gender == 'male',
                  onTap: () => setState(() => _gender = 'male')),
              ChoiceChipBox(
                  label: '👩 נקבה',
                  selected: _gender == 'female',
                  onTap: () => setState(() => _gender = 'female')),
            ],
          ),
          const SizedBox(height: 16),
          Text(
            'המגדר קובע אילו ערוצים מוצגים. אפשר לשנות בכל רגע בהגדרות '
            'הסינון, עם קוד ההורים.',
            style:
                TextStyle(color: AppTheme.subtext, fontSize: 12, height: 1.5),
          ),
        ],
      );

  Widget _filterLevel() => _page(
        title: 'רמת סינון 🛡️',
        children: [
          _levelCard(
            1,
            'מחמיר',
            'כל התוכן מוצג כווידאו, ומוזיקה נשמעת כאודיו בלבד — בלי קליפים. '
                'ערוצי ״דתי לייט״ אינם מוצגים.',
          ),
          _levelCard(
            2,
            'רגיל',
            'כמו ״מחמיר״, אבל גם המוזיקה מוצגת כווידאו. '
                'ערוצי ״דתי לייט״ עדיין אינם מוצגים.',
          ),
          _levelCard(
            3,
            'דתי לייט',
            'מוסיף ערוצים נוספים, והם מתנגנים כאודיו בלבד — לעולם לא כווידאו.',
          ),
          const SizedBox(height: 14),
          Text(
            'כל ערוץ ברשימה נבדק ואושר ידנית על ידי אדם — לא על ידי בינה '
            'מלאכותית ולא באופן אוטומטי. מה שלא אושר, פשוט לא קיים באפליקציה.',
            style:
                TextStyle(color: AppTheme.subtext, fontSize: 12, height: 1.6),
          ),
        ],
      );

  Widget _levelCard(int value, String title, String description) => Padding(
        padding: const EdgeInsets.only(bottom: 10),
        child: GestureDetector(
          onTap: () => setState(() => _level = value),
          child: Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: AppTheme.card,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(
                color: _level == value ? AppTheme.accent : AppTheme.divider,
                width: _level == value ? 2 : 1,
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(
                        _level == value
                            ? Icons.radio_button_checked
                            : Icons.radio_button_unchecked,
                        color: _level == value
                            ? AppTheme.accent
                            : AppTheme.subtext,
                        size: 19),
                    const SizedBox(width: 8),
                    Text('רמה $value — $title',
                        style: TextStyle(
                            color: AppTheme.text,
                            fontSize: 14.5,
                            fontWeight: FontWeight.w700)),
                  ],
                ),
                const SizedBox(height: 6),
                Text(description,
                    style: TextStyle(
                        color: AppTheme.subtext, fontSize: 12, height: 1.5)),
              ],
            ),
          ),
        ),
      );

  Widget _artists() {
    final query = _search.text.trim().toLowerCase();
    final channels = appState.channels
        .visible(_level, _gender)
        .where((c) => kMusicCategories.contains(c.category))
        .where((c) => query.isEmpty || c.name.toLowerCase().contains(query))
        .take(60)
        .toList();

    return _page(
      title: 'מה אוהבים לשמוע 🎵',
      children: [
        Text(
          'בחר/י זמרים וערוצים — נתאים לך את הבית ונשלח התראות על סרטונים '
          'חדשים שלהם. אפשר לדלג ולעשות את זה אחר כך.',
          style: TextStyle(color: AppTheme.subtext, fontSize: 12.5, height: 1.5),
        ),
        const SizedBox(height: 14),
        TextField(
          controller: _search,
          onChanged: (_) => setState(() {}),
          decoration: const InputDecoration(labelText: 'חיפוש זמר / ערוץ'),
        ),
        const SizedBox(height: 14),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: channels
              .map((c) => ChoiceChipBox(
                    label: c.name.trim(),
                    selected: _picked.contains(c.youtubeChannelId),
                    onTap: () => setState(() {
                      if (!_picked.remove(c.youtubeChannelId)) {
                        _picked.add(c.youtubeChannelId);
                      }
                    }),
                  ))
              .toList(),
        ),
      ],
    );
  }

  Widget _done() => _page(
        title: _name.text.trim().isEmpty
            ? 'הכל מוכן!'
            : 'הכל מוכן, ${_name.text.trim()}!',
        children: [
          _bullet(Icons.home_rounded, 'בית מותאם אישית',
              'הפיד מדורג לפי מה שאתה שומע ומסמן — הכל מחושב במכשיר שלך.'),
          _bullet(Icons.shield_rounded, 'סינון עם קוד הורים',
              'רמת הסינון ננעלת בקוד, ורק מי שיודע אותו יכול לשנות אותה.'),
          _bullet(Icons.radio, 'רדיו ומיקסים',
              'תחנות שנבנות מהערוצים המאושרים בלבד, לפי הטעם שלך.'),
          _bullet(Icons.notifications_rounded, 'התראות על סרטונים חדשים',
              'מהערוצים שבחרת לעקוב אחריהם.'),
          const SizedBox(height: 14),
          Text(
            'FilterTube מציגה תוכן מ-YouTube דרך ה-API הרשמי ונגן ההטמעה '
            'הרשמי. אין הורדות ואין ניגון ברקע.',
            style:
                TextStyle(color: AppTheme.subtext, fontSize: 11.5, height: 1.5),
          ),
        ],
      );

  Widget _bullet(IconData icon, String title, String body) => Padding(
        padding: const EdgeInsets.only(bottom: 16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(
                color: AppTheme.accentSoft,
                borderRadius: BorderRadius.circular(11),
              ),
              child: Icon(icon, color: AppTheme.accent, size: 19),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title,
                      style: TextStyle(
                          color: AppTheme.text,
                          fontSize: 14,
                          fontWeight: FontWeight.w700)),
                  const SizedBox(height: 3),
                  Text(body,
                      style: TextStyle(
                          color: AppTheme.subtext, fontSize: 12, height: 1.45)),
                ],
              ),
            ),
          ],
        ),
      );
}

import 'package:flutter/material.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:share_plus/share_plus.dart';
import 'package:url_launcher/url_launcher.dart';

import '../config.dart';
import '../data/app_state.dart';
import '../data/auth.dart';
import '../data/library_store.dart';
import '../data/notifications.dart';
import '../data/settings_store.dart';
import '../theme.dart';
import 'account_screen.dart';
import 'admin_screen.dart';
import 'widgets/common.dart';

/// מסך ההגדרות — אותן קבוצות ואותם פריטים כמו באפליקציה הראשית, למעט מה
/// שאינו קיים בגרסת החנות: הורדות, ניגון ברקע, חלון צף, Premium, שיתוף
/// קובץ התקנה ועדכון מתוך האפליקציה. כל אלה אסורים או חסרי משמעות כשההפצה
/// היא דרך Google Play ו-App Store.
class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([appSettings, appAuth]),
      builder: (context, _) => ListView(
        padding: EdgeInsets.only(
            top: MediaQuery.of(context).padding.top + 26, bottom: 150),
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 6),
            child:
                Text('הגדרות', style: Theme.of(context).textTheme.displaySmall),
          ),

          // שיתוף יושב ראשון: זו הפעולה היחידה כאן שנעשית בשביל מישהו אחר,
          // ומי שמחפש אותה מחפש אותה עכשיו — לא אחרי שש קבוצות של הגדרות.
          const GroupHeader('שיתוף'),
          GroupCard(children: [
            GroupRow(
              icon: Icons.share,
              tint: AppTheme.tintBlue,
              title: 'שתף את FilterTube',
              subtitle: 'קישור לחנות · קוד QR',
              last: true,
              onTap: _openShare,
            ),
          ]),

          const GroupHeader('חשבון'),
          GroupCard(children: [
            GroupRow(
              icon: Icons.cloud_done,
              tint: AppTheme.tintBlue,
              title: 'חשבון וסנכרון ענן',
              subtitle: appAuth.ready
                  ? 'מחובר: ${appAuth.email}'
                  : 'שומר את הספרייה ומאפשר לבקש ערוצים',
              last: true,
              onTap: () => Navigator.push(
                context,
                MaterialPageRoute<void>(builder: (_) => const AccountScreen()),
              ),
            ),
          ]),

          const GroupHeader('סינון והגנה'),
          GroupCard(children: [
            GroupRow(
              icon: Icons.shield,
              tint: AppTheme.tintAmber,
              title: 'הגדרות סינון',
              subtitle: 'רמת סינון · מגדר · אודיו בלבד · Shorts',
              locked: true,
              last: true,
              onTap: _openFilterGate,
            ),
          ]),

          const GroupHeader('ניגון ומדיה'),
          GroupCard(children: [
            GroupRow(
              icon: Icons.graphic_eq,
              tint: AppTheme.tintGreen,
              title: 'נגן ושמע',
              subtitle: 'עיצוב הנגן · איכות · מהירות · פס ההתקדמות',
              onTap: _openPlayerSheet,
            ),
            GroupRow(
              icon: Icons.swipe,
              tint: AppTheme.tintViolet,
              title: 'מחוות בנגן',
              subtitle: 'החלפת סרטון, סגירת הנגן, והמיני-נגן',
              last: true,
              onTap: _openGestures,
            ),
          ]),

          const GroupHeader('תצוגה והתראות'),
          GroupCard(children: [
            GroupRow(
              icon: Icons.tune,
              tint: AppTheme.tintViolet,
              title: 'הגדרות תצוגה',
              subtitle: 'צבע ראשי · מצב כהה/בהיר · קצב רענון גבוה',
              onTap: _openDisplay,
            ),
            GroupRow(
              icon: Icons.notifications,
              tint: AppTheme.tintPink,
              title: 'התראות',
              subtitle: 'התראה על סרטון חדש בערוץ שאתה עוקב אחריו',
              last: true,
              onTap: _openNotifications,
            ),
          ]),

          const GroupHeader('על האפליקציה'),
          GroupCard(children: [
            GroupRow(
              icon: Icons.privacy_tip_outlined,
              tint: AppTheme.tintTeal,
              title: 'פרטיות ותנאים',
              subtitle: 'מדיניות הפרטיות · תנאי YouTube · פרטיות Google',
              onTap: _openLegal,
            ),
            GroupRow(
              icon: Icons.info_outline,
              tint: AppTheme.subtext,
              title: 'אודות',
              subtitle: 'FilterTube — רק ערוצים מאושרים',
              last: true,
              onTap: _openAbout,
            ),
          ]),

          if (appAuth.isAdmin) ...[
            const GroupHeader('ניהול'),
            GroupCard(children: [
              GroupRow(
                icon: Icons.admin_panel_settings,
                tint: AppTheme.tintAmber,
                title: 'ניהול ערוצים',
                subtitle: 'אישור בקשות והוספה/הסרה מהרשימה הלבנה',
                last: true,
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute<void>(builder: (_) => const AdminScreen()),
                ),
              ),
            ]),
          ],
        ],
      ),
    );
  }

  // ── שער קוד ההורים ─────────────────────────────────────────────────────

  /// הגדרות הסינון יושבות מאחורי קוד. רמת סינון שכל אחד יכול לשנות בשתי
  /// לחיצות אינה סינון — היא העדפה. הקוד הוא מה שהופך אותה להחלטה של מי
  /// שהתקין את האפליקציה.
  Future<void> _openFilterGate() async {
    final unlocked = await showDialog<bool>(
      context: context,
      builder: (_) => const _FilterGateDialog(),
    );
    if (unlocked == true && mounted) _openFilterSheet();
  }

  void _openFilterSheet() {
    _openSheet('הגדרות סינון', (setSheetState) {
      return [
        _levelRow(1, 'מחמיר', 'מוזיקה כאודיו בלבד · ״דתי לייט״ מוסתר',
            setSheetState),
        _levelRow(2, 'רגיל', 'הכל כווידאו · ״דתי לייט״ מוסתר', setSheetState),
        _levelRow(3, 'דתי לייט', 'כולל ״דתי לייט״ (אודיו בלבד)', setSheetState),
        Divider(height: 24, color: AppTheme.divider),

        // סרטונים שהמשתמש חסם לעצמו — כאן ולא בספרייה, ובכוונה: המסך הזה
        // כבר מאחורי קוד ההורים. ילד יכול לחסום לעצמו מה שירצה; לפתוח
        // בחזרה — רק מי שיודע את הקוד.
        ListTile(
          contentPadding: EdgeInsets.zero,
          leading: const Icon(Icons.block, color: AppTheme.tintRed),
          title: Text('סרטונים שחסמתי',
              style: TextStyle(color: AppTheme.text, fontSize: 14.5)),
          subtitle: Text(
              appLibrary.blockedVideos.isEmpty
                  ? 'עוד לא חסמת סרטונים'
                  : '${appLibrary.blockedVideos.length} סרטונים לא מוצגים',
              style: TextStyle(color: AppTheme.subtext, fontSize: 12)),
          trailing: Icon(Icons.chevron_left, color: AppTheme.divider),
          onTap: () => Navigator.push(
            context,
            MaterialPageRoute<void>(
              builder: (_) => _BlockedVideosScreen(),
            ),
          ),
        ),
        Divider(height: 24, color: AppTheme.divider),

        // אודיו בלבד יושב מעל רמות הסינון ולא בתוכן: זו בחירה שחלה על כל
        // רמה, ומי שמחפש אותה מחפש אותה כאן — ליד ההחלטה מה מותר לראות.
        SettingSwitch(
          title: 'אודיו בלבד',
          subtitle: 'בכל רמות הסינון — הסרטון נשמע ולא נראה.',
          value: appSettings.audioOnlyMode,
          onChanged: (v) async {
            await appSettings.setAudioOnlyMode(v);
            setSheetState(() {});
          },
        ),
        Divider(height: 24, color: AppTheme.divider),
        Text('תצוגת ערוצים לפי מגדר',
            style: TextStyle(
                color: AppTheme.text,
                fontSize: 14,
                fontWeight: FontWeight.w500)),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          children: [
            for (final option in const [
              ('', 'הכל'),
              ('male', 'זכר'),
              ('female', 'נקבה')
            ])
              ChoiceChipBox(
                label: option.$2,
                selected: appSettings.userGender == option.$1,
                onTap: () async {
                  await appSettings.setUserGender(option.$1);
                  setSheetState(() {});
                  await appState.onFilterChanged();
                },
              ),
          ],
        ),
        Divider(height: 24, color: AppTheme.divider),
        SettingSwitch(
          title: 'הצג לשונית Shorts',
          subtitle: 'סרטונים קצרים מהערוצים המאושרים',
          value: appSettings.shortsEnabled,
          onChanged: (v) async {
            await appSettings.setShortsEnabled(v);
            setSheetState(() {});
          },
        ),
        const SizedBox(height: 16),
        TextButton(
          onPressed: () {
            Navigator.pop(context);
            showDialog<void>(
                context: context, builder: (_) => const _ChangeCodeDialog());
          },
          child: Text('שנה קוד הורים',
              style: TextStyle(color: AppTheme.subtext)),
        ),
      ];
    });
  }

  Widget _levelRow(int value, String title, String description,
      void Function(VoidCallback) setSheetState) {
    return InkWell(
      onTap: () async {
        await appSettings.setFilterLevel(value);
        setSheetState(() {});
        await appState.onFilterChanged();
      },
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          children: [
            Padding(
              padding: const EdgeInsets.all(8),
              child: Icon(
                appSettings.filterLevel == value
                    ? Icons.radio_button_checked
                    : Icons.radio_button_unchecked,
                color: appSettings.filterLevel == value
                    ? AppTheme.accent
                    : AppTheme.subtext,
              ),
            ),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('רמה $value — $title',
                      style: TextStyle(
                          color: AppTheme.text,
                          fontSize: 14,
                          fontWeight: FontWeight.w500)),
                  Text(description,
                      style:
                          TextStyle(color: AppTheme.subtext, fontSize: 11.5)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ── נגן ושמע ───────────────────────────────────────────────────────────

  void _openPlayerSheet() {
    _openSheet('נגן ושמע', (setSheetState) {
      Widget styleRow(int value, String title, String description) => InkWell(
            onTap: () async {
              await appSettings.setPlayerStyle(value);
              setSheetState(() {});
            },
            child: Row(
              children: [
                Padding(
                  padding: const EdgeInsets.all(8),
                  child: Icon(
                    appSettings.playerStyle == value
                        ? Icons.radio_button_checked
                        : Icons.radio_button_unchecked,
                    color: appSettings.playerStyle == value
                        ? AppTheme.accent
                        : AppTheme.subtext,
                  ),
                ),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(title,
                          style: TextStyle(
                              color: AppTheme.text,
                              fontSize: 14,
                              fontWeight: FontWeight.w500)),
                      Text(description,
                          style: TextStyle(
                              color: AppTheme.subtext, fontSize: 11.5)),
                    ],
                  ),
                ),
              ],
            ),
          );

      return [
        _sectionTitle('עיצוב הנגן'),
        styleRow(1, 'מתנגן עכשיו', 'וידאו למעלה ובקרים גדולים מתחת'),
        styleRow(2, 'בקרים צמודים', 'בקרים מיד מתחת לווידאו, ״הבא בתור״ למטה'),
        Divider(height: 24, color: AppTheme.divider),
        _sectionTitle('איכות צפייה'),
        const SizedBox(height: 8),
        Wrap(
          spacing: 6,
          runSpacing: 6,
          children: [
            for (final q in const [0, 1080, 720, 480, 360, 240, 144])
              ChoiceChipBox(
                label: q == 0 ? 'אוטומטי' : '${q}p',
                selected: appSettings.preferredQuality == q,
                onTap: () async {
                  await appSettings.setPreferredQuality(q);
                  setSheetState(() {});
                },
              ),
          ],
        ),
        const SizedBox(height: 6),
        Text(
          'נמסר לנגן ההטמעה כהעדפה. יוטיוב שומרת לעצמה את ההחלטה הסופית '
          'לפי רוחב הפס, ולכן זו בקשה ולא נעילה.',
          style: TextStyle(color: AppTheme.subtext, fontSize: 11, height: 1.4),
        ),
        Divider(height: 24, color: AppTheme.divider),
        _sectionTitle('מהירות נגינה'),
        const SizedBox(height: 8),
        Wrap(
          spacing: 6,
          runSpacing: 6,
          children: [
            for (final speed in const [50, 75, 100, 125, 150, 175, 200])
              ChoiceChipBox(
                label:
                    '${(speed / 100).toStringAsFixed(2).replaceFirst(RegExp(r'\.?0+$'), '')}x',
                selected: appSettings.playbackSpeed == speed,
                onTap: () async {
                  await appSettings.setPlaybackSpeed(speed);
                  setSheetState(() {});
                },
              ),
          ],
        ),
        Divider(height: 24, color: AppTheme.divider),
        _sectionTitle('פס ההתקדמות'),
        const SizedBox(height: 8),
        Wrap(
          spacing: 6,
          children: [
            for (final shape in const [(0, 'ישר'), (1, 'גלי'), (2, 'מזוגזג')])
              ChoiceChipBox(
                label: shape.$2,
                selected: appSettings.seekBarShape == shape.$1,
                onTap: () async {
                  await appSettings.setSeekBarShape(shape.$1);
                  setSheetState(() {});
                },
              ),
          ],
        ),
        const SizedBox(height: 10),
        Row(
          children: [
            Text('עובי',
                style: TextStyle(color: AppTheme.text, fontSize: 13)),
            Expanded(
              child: Slider(
                min: 2,
                max: 8,
                divisions: 6,
                value: appSettings.seekBarThickness.toDouble().clamp(2, 8),
                label: '${appSettings.seekBarThickness}',
                onChanged: (v) async {
                  await appSettings.setSeekBarThickness(v.round());
                  setSheetState(() {});
                },
              ),
            ),
          ],
        ),
        SettingSwitch(
          title: 'זוהר על הפס',
          subtitle: 'הילה רכה בצבע ההדגשה',
          value: appSettings.seekBarGlow,
          onChanged: (v) async {
            await appSettings.setSeekBarGlow(v);
            setSheetState(() {});
          },
        ),
        Divider(height: 24, color: AppTheme.divider),
        _sectionTitle('תור'),
        SettingSwitch(
          title: 'רדיו אוטומטי בסוף התור',
          subtitle: 'כשהתור נגמר, ממשיכים באותו סגנון במקום לעצור',
          value: appSettings.autoRadioQueue,
          onChanged: (v) async {
            await appSettings.setAutoRadioQueue(v);
            setSheetState(() {});
          },
        ),
        const SizedBox(height: 12),
        SettingSwitch(
          title: 'מניעת כפילויות',
          subtitle: 'אותו סרטון לא ייכנס לתור פעמיים',
          value: appSettings.preventQueueDuplicates,
          onChanged: (v) async {
            await appSettings.setPreventQueueDuplicates(v);
            setSheetState(() {});
          },
        ),
        const SizedBox(height: 20),
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: AppTheme.bg2,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Text(
            'הורדות, ניגון ברקע וחלון צף אינם קיימים בגרסה זו: נגן ההטמעה '
            'הרשמי של YouTube אינו מתיר אותם, וזה מה שמאפשר לאפליקציה '
            'להתפרסם ב-Google Play וב-App Store.',
            style:
                TextStyle(color: AppTheme.subtext, fontSize: 11.5, height: 1.5),
          ),
        ),
      ];
    });
  }

  // ── מחוות ──────────────────────────────────────────────────────────────

  /// למה ארבעה מתגים ולא אחד: הנגן של המוזיקה והנגן של הווידאו הם שני
  /// שימושים שונים. במוזיקה החלקה היא הדרך הטבעית להחליף שיר; בווידאו
  /// אותה תנועה עוברת מעל תמונה שהמשתמש דווקא מסתכל בה.
  void _openGestures() {
    _openSheet('מחוות בנגן', (setSheetState) => [
          Text('השינוי נכנס לתוקף בפתיחה הבאה של הנגן.',
              style: TextStyle(color: AppTheme.subtext2, fontSize: 11.5)),
          const SizedBox(height: 18),
          _sectionTitle('FilterMusic'),
          const SizedBox(height: 8),
          SettingSwitch(
            title: 'החלקה להחלפת שיר',
            subtitle: 'שמאלה — השיר הבא · ימינה — השיר הקודם',
            value: appSettings.musicSwipeTrack,
            onChanged: (v) async {
              await appSettings.setMusicSwipeTrack(v);
              setSheetState(() {});
            },
          ),
          const SizedBox(height: 12),
          SettingSwitch(
            title: 'החלקה למטה סוגרת את הנגן',
            subtitle: 'מושכים את המסך למטה במקום ללחוץ על החץ',
            value: appSettings.musicSwipeDismiss,
            onChanged: (v) async {
              await appSettings.setMusicSwipeDismiss(v);
              setSheetState(() {});
            },
          ),
          Divider(height: 30, color: AppTheme.divider),
          _sectionTitle('FilterTube'),
          const SizedBox(height: 8),
          SettingSwitch(
            title: 'החלקה להחלפת סרטון',
            subtitle: 'שמאלה — הבא בתור · ימינה — הקודם',
            value: appSettings.videoSwipeTrack,
            onChanged: (v) async {
              await appSettings.setVideoSwipeTrack(v);
              setSheetState(() {});
            },
          ),
          const SizedBox(height: 12),
          SettingSwitch(
            title: 'החלקה למטה מכווצת את הנגן',
            subtitle: 'הניגון ממשיך במיני-נגן שלמטה',
            value: appSettings.videoSwipeDismiss,
            onChanged: (v) async {
              await appSettings.setVideoSwipeDismiss(v);
              setSheetState(() {});
            },
          ),
          Divider(height: 30, color: AppTheme.divider),
          _sectionTitle('המיני-נגן'),
          const SizedBox(height: 8),
          SettingSwitch(
            title: 'החלקה על המיני-נגן',
            subtitle: 'שמאלה — הבא · ימינה — הקודם · גרירה למטה עוצרת לגמרי',
            value: appSettings.miniPlayerSwipe,
            onChanged: (v) async {
              await appSettings.setMiniPlayerSwipe(v);
              setSheetState(() {});
            },
          ),
          if (appSettings.miniPlayerSwipe) ...[
            const SizedBox(height: 12),
            SettingSwitch(
              title: 'החלקה ימינה מתחילה את הסרטון מחדש',
              subtitle: 'במקום לעבור לקודם',
              value: appSettings.miniSwipeRightRestarts,
              onChanged: (v) async {
                await appSettings.setMiniSwipeRightRestarts(v);
                setSheetState(() {});
              },
            ),
          ],
          const SizedBox(height: 20),
          Text(
            'דאבל-טאפ על הסרטון לדילוג של 10 שניות אחורה או קדימה עובד תמיד, '
            'ואינו מושפע מהמתגים כאן.',
            style:
                TextStyle(color: AppTheme.subtext2, fontSize: 11, height: 1.5),
          ),
        ]);
  }

  // ── תצוגה ──────────────────────────────────────────────────────────────

  void _openDisplay() {
    _openSheet('הגדרות תצוגה', (setSheetState) {
      final systemDark =
          MediaQuery.platformBrightnessOf(context) == Brightness.dark;
      return [
        _sectionTitle('ערכת נושא'),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          children: [
            for (final mode in const [(0, 'מערכת'), (1, 'כהה'), (2, 'בהיר')])
              ChoiceChipBox(
                label: mode.$2,
                selected: appSettings.themeMode == mode.$1,
                onTap: () async {
                  await appSettings.setThemeMode(mode.$1,
                      systemDark: systemDark);
                  setSheetState(() {});
                },
              ),
          ],
        ),
        Divider(height: 26, color: AppTheme.divider),
        SettingSwitch(
          title: 'קצב רענון גבוה',
          subtitle: 'תצוגה חלקה במכשירים שתומכים (אנדרואיד) · '
              'החלפה נכנסת לתוקף בהפעלה הבאה',
          value: appSettings.highRefreshRate,
          onChanged: (v) async {
            await appSettings.setHighRefreshRate(v);
            setSheetState(() {});
          },
        ),
        Divider(height: 26, color: AppTheme.divider),
        _sectionTitle('ערכת צבע'),
        Text('נכנס לתוקף מיד',
            style: TextStyle(color: AppTheme.subtext, fontSize: 11)),
        const SizedBox(height: 12),
        Row(
          children: [
            for (final palette in kPalettes)
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.only(left: 10),
                  child: GestureDetector(
                    onTap: () async {
                      await appSettings.setAccent(
                          palette.accent.toARGB32(), palette.accent2.toARGB32());
                      setSheetState(() {});
                    },
                    child: Container(
                      padding: const EdgeInsets.all(11),
                      decoration: BoxDecoration(
                        color: AppTheme.card,
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(
                          color: appSettings.accentColor ==
                                  palette.accent.toARGB32()
                              ? palette.accent
                              : AppTheme.divider,
                          width: appSettings.accentColor ==
                                  palette.accent.toARGB32()
                              ? 2
                              : 1,
                        ),
                      ),
                      child: Column(
                        children: [
                          Container(
                            height: 40,
                            decoration: BoxDecoration(
                              gradient: LinearGradient(
                                  colors: [palette.accent, palette.accent2]),
                              borderRadius: BorderRadius.circular(11),
                            ),
                          ),
                          const SizedBox(height: 9),
                          Text(palette.name,
                              style: TextStyle(
                                  color: AppTheme.subtext2, fontSize: 12)),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
          ],
        ),
      ];
    });
  }

  // ── התראות ─────────────────────────────────────────────────────────────

  void _openNotifications() {
    showDialog<void>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (dialogContext, setDialogState) => AlertDialog(
          title: const Text('התראות'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              SettingSwitch(
                title: 'סרטון חדש בערוץ שאתה עוקב אחריו',
                subtitle:
                    'נבדק בכל פתיחה של האפליקציה, ומוצגת התראה על מה שהתחדש',
                value: appSettings.newVideoNotifications,
                onChanged: (v) async {
                  if (v) await AppNotifications.requestPermission();
                  await appSettings.setNewVideoNotifications(v);
                  if (!v) await AppNotifications.cancelAll();
                  setDialogState(() {});
                },
              ),
            ],
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(dialogContext),
                child: const Text('סגור')),
          ],
        ),
      ),
    );
  }

  // ── שיתוף ──────────────────────────────────────────────────────────────

  void _openShare() {
    const link = AppConfig.storeListingUrl;
    _openSheet('שתף את FilterTube', (setSheetState) => [
          Text(
            'כל מי שמקבל את הקישור יכול להתקין את האפליקציה מהחנות ולהתחיל '
            'משלו — עם אותה רשימת ערוצים מאושרים, ורמת סינון שהוא בוחר בעצמו.',
            style: TextStyle(
                color: AppTheme.subtext, fontSize: 12.5, height: 1.5),
          ),
          const SizedBox(height: 18),
          Center(
            child: Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(20),
              ),
              child: QrImageView(data: link, size: 190),
            ),
          ),
          const SizedBox(height: 8),
          Center(
            child: Text('מי שעומד מולך פשוט מצלם את הקוד',
                style: TextStyle(color: AppTheme.subtext2, fontSize: 11.5)),
          ),
          const SizedBox(height: 18),
          FilledButton.icon(
            style: FilledButton.styleFrom(
                backgroundColor: AppTheme.accent,
                minimumSize: const Size.fromHeight(46)),
            onPressed: () => SharePlus.instance.share(
              ShareParams(
                text: 'FilterTube — יוטיוב מסונן, רק ערוצים מאושרים.\n$link',
              ),
            ),
            icon: const Icon(Icons.share, size: 18),
            label: const Text('שתף קישור — וואטסאפ, SMS, מייל'),
          ),
        ]);
  }

  // ── פרטיות, תנאים ואודות ───────────────────────────────────────────────

  void _openLegal() {
    _openSheet('פרטיות ותנאים', (setSheetState) => [
          Text(
            'FilterTube מציגה תוכן מ-YouTube דרך ה-API הרשמי ונגן ההטמעה '
            'הרשמי. השימוש באפליקציה כפוף גם לתנאי השימוש של YouTube '
            'ולמדיניות הפרטיות של Google.',
            style: TextStyle(color: AppTheme.subtext2, fontSize: 13, height: 1.6),
          ),
          const SizedBox(height: 18),
          _linkRow('מדיניות הפרטיות של FilterTube', AppConfig.privacyPolicyUrl),
          _linkRow('תנאי השימוש של FilterTube', AppConfig.termsUrl),
          _linkRow('תנאי השימוש של YouTube', AppConfig.youtubeTermsUrl),
          _linkRow('מדיניות הפרטיות של Google', AppConfig.googlePrivacyUrl),
          const SizedBox(height: 18),
          Text(
            'מה נשמר: העדפות הסינון, הספרייה וההיסטוריה נשמרות במכשיר. '
            'עם חשבון — גם בענן, כדי לשחזר אותן במכשיר אחר. אין פרסומות '
            'ואין מעקב צד שלישי.',
            style: TextStyle(color: AppTheme.subtext, fontSize: 12, height: 1.6),
          ),
        ]);
  }

  Widget _linkRow(String label, String url) => ListTile(
        contentPadding: EdgeInsets.zero,
        dense: true,
        leading: Icon(Icons.open_in_new, size: 18, color: AppTheme.accent),
        title:
            Text(label, style: TextStyle(color: AppTheme.text, fontSize: 13.5)),
        onTap: () => launchUrl(Uri.parse(url),
            mode: LaunchMode.externalApplication),
      );

  Future<void> _openAbout() async {
    final info = await PackageInfo.fromPlatform();
    if (!mounted) return;
    showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        icon: Icon(Icons.info_outline, color: AppTheme.accent),
        title: const Text('FilterTube'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'פלטפורמת וידאו מסוננת — מציגה אך ורק ערוצים מאושרים. '
              'כל ערוץ ברשימה נבדק ואושר ידנית על ידי אדם.',
              style: TextStyle(
                  color: AppTheme.subtext2, fontSize: 13, height: 1.5),
            ),
            const SizedBox(height: 10),
            Text('גרסה ${info.version} (${info.buildNumber})',
                style: TextStyle(color: AppTheme.subtext, fontSize: 13)),
            const SizedBox(height: 8),
            Text('נוצרה על־ידי FilterPhone',
                style: TextStyle(
                    color: AppTheme.text,
                    fontSize: 13,
                    fontWeight: FontWeight.bold)),
            InkWell(
              onTap: () => launchUrl(Uri.parse(AppConfig.website),
                  mode: LaunchMode.externalApplication),
              child: Text('אתר: filterphone.com',
                  style: TextStyle(color: AppTheme.accent, fontSize: 13)),
            ),
            InkWell(
              onTap: () =>
                  launchUrl(Uri.parse('mailto:${AppConfig.supportEmail}')),
              child: Text('תמיכה: ${AppConfig.supportEmail}',
                  style: TextStyle(color: AppTheme.subtext2, fontSize: 13)),
            ),
          ],
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('סגור')),
        ],
      ),
    );
  }

  // ── תשתית הגיליונות ────────────────────────────────────────────────────

  Widget _sectionTitle(String title) => Text(title,
      style: TextStyle(
          color: AppTheme.accent, fontSize: 13, fontWeight: FontWeight.bold));

  /// פאנל ארוך נפתח כמסך מלא ולא כחלון: חלון צר שצריך לגלול בתוכו, עם
  /// כפתור "סגור" במקום שבו האגודל מצפה לחץ חזרה, הוא מה שהיה כאן קודם.
  void _openSheet(
      String title, List<Widget> Function(void Function(VoidCallback)) build) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => StatefulBuilder(
          builder: (sheetContext, setSheetState) => Scaffold(
            backgroundColor: AppTheme.bg,
            appBar: DetailTopBar(title),
            body: ListView(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 40),
              children: build(setSheetState),
            ),
          ),
        ),
      ),
    ).then((_) {
      if (mounted) setState(() {});
    });
  }
}

/// שער קוד ההורים — קובע קוד בפעם הראשונה, ומאמת אותו אחר כך.
class _FilterGateDialog extends StatefulWidget {
  const _FilterGateDialog();

  @override
  State<_FilterGateDialog> createState() => _FilterGateDialogState();
}

class _FilterGateDialogState extends State<_FilterGateDialog> {
  final _code = TextEditingController();
  final _confirm = TextEditingController();
  String _error = '';
  bool _busy = false;

  bool get _isSetup => !appSettings.hasFilterPassword;

  @override
  void dispose() {
    _code.dispose();
    _confirm.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_isSetup) {
      if (_code.text.length < 4) {
        setState(() => _error = 'קוד קצר מדי (לפחות 4 תווים)');
        return;
      }
      if (_code.text != _confirm.text) {
        setState(() => _error = 'הקודים אינם תואמים');
        return;
      }
      setState(() => _busy = true);
      await appSettings.setFilterPassword(_code.text);
      if (!mounted) return;
      Navigator.pop(context, true);
      return;
    }
    if (appSettings.checkFilterPassword(_code.text)) {
      Navigator.pop(context, true);
    } else {
      setState(() => _error = 'קוד ההורים שגוי');
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      icon: const Icon(Icons.lock_rounded, color: AppTheme.tintAmber),
      title: Text(_isSetup ? 'קביעת קוד הורים' : 'הזן קוד הורים'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            _isSetup
                ? 'הקוד הזה הוא מה שמגן על רמת הסינון. בלעדיו כל אחד יכול '
                    'לשנות אותה בשתי לחיצות.'
                : 'הזן את קוד ההורים כדי לשנות את הגדרות הסינון.',
            style: TextStyle(color: AppTheme.subtext2, fontSize: 12, height: 1.5),
          ),
          const SizedBox(height: 14),
          _PasswordField(controller: _code, label: 'קוד'),
          if (_isSetup) ...[
            const SizedBox(height: 10),
            _PasswordField(controller: _confirm, label: 'אימות קוד'),
          ],
          if (_error.isNotEmpty) ...[
            const SizedBox(height: 10),
            Text(_error,
                style: const TextStyle(color: AppTheme.tintRed, fontSize: 12)),
          ],
        ],
      ),
      actions: [
        TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('בטל')),
        FilledButton(
          style: FilledButton.styleFrom(backgroundColor: AppTheme.accent),
          onPressed: _busy ? null : _submit,
          child: Text(_isSetup ? 'שמור והמשך' : 'אישור'),
        ),
      ],
    );
  }
}

class _ChangeCodeDialog extends StatefulWidget {
  const _ChangeCodeDialog();

  @override
  State<_ChangeCodeDialog> createState() => _ChangeCodeDialogState();
}

class _ChangeCodeDialogState extends State<_ChangeCodeDialog> {
  final _current = TextEditingController();
  final _next = TextEditingController();
  final _confirm = TextEditingController();
  String _error = '';

  @override
  void dispose() {
    _current.dispose();
    _next.dispose();
    _confirm.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('שינוי קוד הורים'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _PasswordField(controller: _current, label: 'קוד נוכחי'),
          const SizedBox(height: 10),
          _PasswordField(controller: _next, label: 'קוד חדש'),
          const SizedBox(height: 10),
          _PasswordField(controller: _confirm, label: 'אימות קוד'),
          if (_error.isNotEmpty) ...[
            const SizedBox(height: 10),
            Text(_error,
                style: const TextStyle(color: AppTheme.tintRed, fontSize: 12)),
          ],
        ],
      ),
      actions: [
        TextButton(
            onPressed: () => Navigator.pop(context), child: const Text('בטל')),
        FilledButton(
          style: FilledButton.styleFrom(backgroundColor: AppTheme.accent),
          onPressed: () async {
            if (!appSettings.checkFilterPassword(_current.text)) {
              setState(() => _error = 'הקוד הנוכחי שגוי');
              return;
            }
            if (_next.text.length < 4) {
              setState(() => _error = 'קוד קצר מדי (לפחות 4 תווים)');
              return;
            }
            if (_next.text != _confirm.text) {
              setState(() => _error = 'הקודים אינם תואמים');
              return;
            }
            await appSettings.setFilterPassword(_next.text);
            if (context.mounted) Navigator.pop(context);
          },
          child: const Text('שמור'),
        ),
      ],
    );
  }
}

/// שדה קוד עם עין להצגה. קוד שמוקלד בעיוור נכשל שוב ושוב, והמשתמש לא יודע
/// אם טעה בספרה או שהקוד עצמו שגוי — העין מפרידה בין שתי השאלות.
class _PasswordField extends StatefulWidget {
  final TextEditingController controller;
  final String label;
  const _PasswordField({required this.controller, required this.label});

  @override
  State<_PasswordField> createState() => _PasswordFieldState();
}

class _PasswordFieldState extends State<_PasswordField> {
  bool _visible = false;

  @override
  Widget build(BuildContext context) => TextField(
        controller: widget.controller,
        obscureText: !_visible,
        decoration: InputDecoration(
          labelText: widget.label,
          suffixIcon: IconButton(
            tooltip: _visible ? 'הסתר קוד' : 'הצג קוד',
            icon: Icon(_visible ? Icons.visibility_off : Icons.visibility,
                color: AppTheme.subtext),
            onPressed: () => setState(() => _visible = !_visible),
          ),
        ),
      );
}

/// הסרטונים שהמשתמש חסם לעצמו, עם כפתור שחרור. נפתח רק מתוך גיליון
/// הסינון — שכבר עבר את קוד ההורים — ולכן אין כאן שער נוסף.
class _BlockedVideosScreen extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: appLibrary,
      builder: (context, _) {
        final blocked = appLibrary.blockedVideos;
        return Scaffold(
          backgroundColor: AppTheme.bg,
          appBar: DetailTopBar('סרטונים שחסמתי (${blocked.length})'),
          body: blocked.isEmpty
              ? const EmptyState(
                  icon: Icons.block,
                  title: 'לא חסמת אף סרטון',
                  body: 'בלחיצה ארוכה על סרטון אפשר לבחור '
                      '"אל תציג לי את זה יותר".',
                )
              : ListView(
                  children: blocked
                      .map((video) => ListTile(
                            title: Text(video.title,
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                    color: AppTheme.text, fontSize: 13.5)),
                            subtitle: Text(video.channelName,
                                style: TextStyle(
                                    color: AppTheme.subtext, fontSize: 11.5)),
                            trailing: TextButton(
                              onPressed: () =>
                                  appLibrary.unblockVideo(video.id),
                              child: Text('שחרר',
                                  style: TextStyle(color: AppTheme.accent)),
                            ),
                          ))
                      .toList(),
                ),
        );
      },
    );
  }
}

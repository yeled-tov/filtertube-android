import 'package:flutter/material.dart';

import '../../data/playback.dart';
import '../../data/settings_store.dart';
import '../../theme.dart';
import '../widgets/common.dart';

/// הגדרות FilterMusic.
///
/// ## למה חסרות כאן כמה הגדרות מהאפליקציה הראשית
/// "דילוג על שקט", "הגברת עוצמה", "גובה הצליל" ו"עמעום מוצלב" הן פעולות
/// על זרם האודיו עצמו. בגרסת החנות הניגון עובר דרך נגן ההטמעה הרשמי של
/// YouTube, שאינו נותן גישה לזרם — ולכן אין דרך לממש אותן. מתג שנראה
/// כאילו הוא עושה משהו ולא עושה כלום גרוע מהיעדרו.
class MusicSettingsScreen extends StatefulWidget {
  const MusicSettingsScreen({super.key});

  @override
  State<MusicSettingsScreen> createState() => _MusicSettingsScreenState();
}

class _MusicSettingsScreenState extends State<MusicSettingsScreen> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bg,
      appBar: const DetailTopBar('הגדרות FilterMusic'),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 40),
        children: [
          _title('ניגון'),
          const SizedBox(height: 10),
          Text('מהירות ניגון',
              style: TextStyle(color: AppTheme.text, fontSize: 14)),
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
                    await playback.setSpeed(speed);
                    setState(() {});
                  },
                ),
            ],
          ),
          Divider(height: 26, color: AppTheme.divider),
          _title('תור'),
          const SizedBox(height: 10),
          SettingSwitch(
            title: 'רדיו אוטומטי בסוף התור',
            subtitle: 'כשהתור נגמר, ממשיכים באותו סגנון במקום לעצור',
            value: appSettings.autoRadioQueue,
            onChanged: (v) async {
              await appSettings.setAutoRadioQueue(v);
              setState(() {});
            },
          ),
          const SizedBox(height: 14),
          SettingSwitch(
            title: 'מניעת כפילויות',
            subtitle: 'אותו שיר לא ייכנס לתור פעמיים',
            value: appSettings.preventQueueDuplicates,
            onChanged: (v) async {
              await appSettings.setPreventQueueDuplicates(v);
              setState(() {});
            },
          ),
          Divider(height: 26, color: AppTheme.divider),
          _title('כיבוי אוטומטי'),
          const SizedBox(height: 10),
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: [
              for (final minutes in const [0, 15, 30, 45, 60, 90])
                ChoiceChipBox(
                  label: minutes == 0 ? 'כבוי' : 'אחרי $minutes דקות',
                  selected: playback.sleepMinutesLeft == minutes,
                  onTap: () {
                    playback.setSleepTimer(minutes);
                    setState(() {});
                  },
                ),
            ],
          ),
          Divider(height: 26, color: AppTheme.divider),
          _title('סינון'),
          const SizedBox(height: 10),
          SettingSwitch(
            title: 'אודיו בלבד',
            subtitle: 'בכל רמות הסינון — הסרטון נשמע ולא נראה',
            value: appSettings.audioOnlyMode,
            onChanged: (v) async {
              await appSettings.setAudioOnlyMode(v);
              setState(() {});
            },
          ),
          const SizedBox(height: 24),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppTheme.bg2,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Text(
              'איכות שמע, דילוג על שקט, הגברת עוצמה ועמעום מוצלב אינם זמינים '
              'בגרסת החנות: הניגון עובר דרך נגן ההטמעה הרשמי של YouTube, '
              'שאינו נותן גישה לזרם האודיו עצמו.',
              style: TextStyle(
                  color: AppTheme.subtext, fontSize: 11.5, height: 1.5),
            ),
          ),
        ],
      ),
    );
  }

  Widget _title(String text) => Text(text,
      style: TextStyle(
          color: AppTheme.accent, fontSize: 13, fontWeight: FontWeight.bold));
}

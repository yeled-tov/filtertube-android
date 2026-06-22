import 'package:flutter/material.dart';
import '../theme.dart';
import '../settings.dart';

/// מסך הגדרות — סינון תוכן, תצוגה וחשבון. נפתח מהאווטאר בפינה הימנית-עליונה.
class SettingsScreen extends StatefulWidget {
  final AppSettings settings;
  final Future<void> Function(int level) onFilterLevelChanged;

  const SettingsScreen({
    super.key,
    required this.settings,
    required this.onFilterLevelChanged,
  });

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  static const _levels = [
    (1, 'מחמיר', 'מוזיקה כאודיו בלבד · ללא "דתי לייט"'),
    (2, 'רגיל', 'הכל וידאו · ללא "דתי לייט"'),
    (3, 'דתי לייט', 'כל הקטגוריות מוצגות'),
  ];

  @override
  Widget build(BuildContext context) {
    final s = widget.settings;
    return Scaffold(
      appBar: AppBar(title: const Text('הגדרות')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(14, 8, 14, 30),
        children: [
          _accountCard(),
          _sectionTitle('סינון תוכן'),
          ..._levels.map((l) => _levelTile(l.$1, l.$2, l.$3, s.filterLevel)),
          _sectionTitle('תצוגה'),
          _card(
            child: SwitchListTile(
              value: s.shortsEnabled,
              onChanged: (v) => setState(() => s.setShortsEnabled(v)),
              contentPadding: const EdgeInsets.symmetric(horizontal: 14),
              title: const Text('שורטס', style: TextStyle(color: AppTheme.text)),
              subtitle: const Text('הצגת לשונית שורטס',
                  style: TextStyle(color: AppTheme.subtext, fontSize: 12)),
            ),
          ),
          const SizedBox(height: 18),
          const Center(
            child: Text('FilterTube · יוטיוב מסונן',
                style: TextStyle(color: AppTheme.subtext, fontSize: 12)),
          ),
        ],
      ),
    );
  }

  Widget _accountCard() {
    return Container(
      margin: const EdgeInsets.only(top: 8, bottom: 6),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: AppTheme.accentGradient,
        borderRadius: BorderRadius.circular(18),
      ),
      child: const Row(
        children: [
          CircleAvatar(
            radius: 24,
            backgroundColor: Colors.white24,
            child: Icon(Icons.person, color: Colors.white, size: 28),
          ),
          SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('חשבון Google',
                    style: TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.bold,
                        fontSize: 15)),
                SizedBox(height: 2),
                Text('התחברות תביא מנויים ולייקים (בקרוב)',
                    style: TextStyle(color: Colors.white70, fontSize: 12)),
              ],
            ),
          ),
          Icon(Icons.chevron_left, color: Colors.white),
        ],
      ),
    );
  }

  Widget _sectionTitle(String t) => Padding(
        padding: const EdgeInsets.fromLTRB(4, 18, 4, 8),
        child: Text(t,
            style: const TextStyle(
                color: AppTheme.subtext2,
                fontSize: 13,
                fontWeight: FontWeight.bold)),
      );

  Widget _card({required Widget child}) => Container(
        margin: const EdgeInsets.only(bottom: 8),
        decoration: BoxDecoration(
          color: AppTheme.card,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppTheme.stroke),
        ),
        child: child,
      );

  Widget _levelTile(int level, String title, String sub, int current) {
    final selected = current == level;
    return GestureDetector(
      onTap: () {
        setState(() {});
        widget.onFilterLevelChanged(level);
      },
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        decoration: BoxDecoration(
          color: AppTheme.card,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
              color: selected ? AppTheme.accent : AppTheme.stroke,
              width: selected ? 1.6 : 1),
        ),
        child: Row(
          children: [
            Icon(selected ? Icons.radio_button_checked : Icons.radio_button_off,
                color: selected ? AppTheme.accent : AppTheme.subtext, size: 22),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title,
                      style: const TextStyle(
                          color: AppTheme.text,
                          fontSize: 14,
                          fontWeight: FontWeight.w600)),
                  const SizedBox(height: 2),
                  Text(sub,
                      style: const TextStyle(
                          color: AppTheme.subtext, fontSize: 11.5)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

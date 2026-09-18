import 'package:flutter/material.dart';

import '../data/app_state.dart';
import '../data/auth.dart';
import '../data/cloud.dart';
import '../models/channel.dart';
import '../theme.dart';
import 'widgets/common.dart';

/// טופס "בקשת ערוץ" — נשלח לאותה תור אישורים שהאפליקציה הראשית שולחת
/// אליו, ולכן ערוץ שיאושר יופיע בשתי הגרסאות.
///
/// הקישור נמצא מעצמו מהשם: מי שמבקש ערוץ יודע איך קוראים לו, אבל כמעט אף
/// אחד לא יודע להעתיק קישור ערוץ מיוטיוב בטלפון.
Future<void> showChannelRequestDialog(
  BuildContext context, {
  String prefillName = '',
  String prefillUrl = '',
}) {
  return showDialog<void>(
    context: context,
    builder: (_) => _ChannelRequestDialog(
      prefillName: prefillName,
      prefillUrl: prefillUrl,
    ),
  );
}

class _ChannelRequestDialog extends StatefulWidget {
  final String prefillName;
  final String prefillUrl;

  const _ChannelRequestDialog(
      {required this.prefillName, required this.prefillUrl});

  @override
  State<_ChannelRequestDialog> createState() => _ChannelRequestDialogState();
}

class _ChannelRequestDialogState extends State<_ChannelRequestDialog> {
  late final TextEditingController _name =
      TextEditingController(text: widget.prefillName);
  late final TextEditingController _url =
      TextEditingController(text: widget.prefillUrl);
  final TextEditingController _description = TextEditingController();

  String _category = 'general';
  String _gender = 'all';
  bool _sending = false;
  bool _locating = false;
  String _status = '';
  String _foundName = '';

  @override
  void initState() {
    super.initState();
    if (widget.prefillName.isNotEmpty && widget.prefillUrl.isEmpty) {
      _locate();
    }
  }

  @override
  void dispose() {
    _name.dispose();
    _url.dispose();
    _description.dispose();
    super.dispose();
  }

  Future<void> _locate() async {
    final name = _name.text.trim();
    if (name.isEmpty || _locating) return;
    setState(() {
      _locating = true;
      _status = '';
    });
    final found = await appState.api.findChannelByName(name);
    if (!mounted) return;
    setState(() {
      _locating = false;
      if (found == null) {
        _status = 'לא מצאנו ערוץ בשם הזה. אפשר להדביק קישור ידנית:';
      } else {
        _foundName = found.name;
        _url.text = 'https://www.youtube.com/channel/${found.youtubeChannelId}';
      }
    });
  }

  Future<void> _send() async {
    if (_name.text.trim().isEmpty || _url.text.trim().isEmpty) {
      setState(() => _status = 'יש למלא שם ערוץ וקישור');
      return;
    }
    setState(() => _sending = true);
    final result = await Cloud.submitChannelRequest(
      name: _name.text.trim(),
      url: _url.text.trim(),
      category: _category,
      gender: _gender,
      description: _description.text.trim(),
    );
    if (!mounted) return;
    setState(() => _sending = false);
    if (result['ok'] == true) {
      Navigator.pop(context);
      showToast(context, 'הבקשה נשלחה לאישור. תודה! נבדוק ונוסיף אם מתאים.');
    } else {
      setState(() => _status =
          (result['message'] as String?) ?? 'שליחת הבקשה נכשלה');
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!appAuth.ready) {
      return AlertDialog(
        title: const Text('בקשת ערוץ'),
        content: const Text(
          'כדי לשלוח בקשה להוספת ערוץ צריך חשבון FilterTube עם אימייל מאומת. '
          'אפשר להתחבר מהגדרות ← חשבון.',
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('סגור')),
        ],
      );
    }

    return AlertDialog(
      title: const Text('בקשת הוספת ערוץ'),
      content: SizedBox(
        width: 420,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('מלא/י את הפרטים כדי שיהיה קל לאשר:',
                  style: TextStyle(color: AppTheme.subtext, fontSize: 12)),
              const SizedBox(height: 12),
              TextField(
                controller: _name,
                onSubmitted: (_) => _locate(),
                decoration: InputDecoration(
                  labelText: 'שם הערוץ או הזמר',
                  helperText: 'הקלד/י שם והקישור יימצא אוטומטית',
                  helperStyle:
                      TextStyle(color: AppTheme.subtext, fontSize: 11),
                  suffixIcon: _locating
                      ? const Padding(
                          padding: EdgeInsets.all(12),
                          child: SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(strokeWidth: 2)),
                        )
                      : IconButton(
                          icon: Icon(Icons.travel_explore,
                              color: AppTheme.accent),
                          onPressed: _locate,
                        ),
                ),
              ),
              if (_foundName.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 6),
                  child: Text('זוהה: $_foundName',
                      style: const TextStyle(
                          color: AppTheme.tintGreen, fontSize: 11.5)),
                ),
              const SizedBox(height: 10),
              TextField(
                controller: _url,
                decoration:
                    const InputDecoration(labelText: 'קישור לערוץ ביוטיוב'),
              ),
              const SizedBox(height: 14),
              Text('קטגוריה',
                  style: TextStyle(
                      color: AppTheme.text,
                      fontSize: 13,
                      fontWeight: FontWeight.w600)),
              const SizedBox(height: 6),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: categoryLabels.entries
                    .map((e) => ChoiceChipBox(
                          label: e.value,
                          selected: _category == e.key,
                          onTap: () => setState(() => _category = e.key),
                        ))
                    .toList(),
              ),
              const SizedBox(height: 14),
              Text('מגדר',
                  style: TextStyle(
                      color: AppTheme.text,
                      fontSize: 13,
                      fontWeight: FontWeight.w600)),
              const SizedBox(height: 6),
              Wrap(
                spacing: 6,
                children: [
                  ChoiceChipBox(
                      label: 'הכל',
                      selected: _gender == 'all',
                      onTap: () => setState(() => _gender = 'all')),
                  ChoiceChipBox(
                      label: 'זכר',
                      selected: _gender == 'male',
                      onTap: () => setState(() => _gender = 'male')),
                  ChoiceChipBox(
                      label: 'נקבה',
                      selected: _gender == 'female',
                      onTap: () => setState(() => _gender = 'female')),
                ],
              ),
              const SizedBox(height: 14),
              TextField(
                controller: _description,
                minLines: 2,
                maxLines: 4,
                decoration: const InputDecoration(
                    labelText: 'מה הערוץ מכיל? (תוכן, קהל יעד)'),
              ),
              if (_status.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 10),
                  child: Text(_status,
                      style: TextStyle(color: AppTheme.accent, fontSize: 12)),
                ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: _sending ? null : () => Navigator.pop(context),
          child: const Text('ביטול'),
        ),
        FilledButton(
          style: FilledButton.styleFrom(backgroundColor: AppTheme.accent),
          onPressed: _sending ? null : _send,
          child: Text(_sending ? 'שולח…' : 'שלח בקשה'),
        ),
      ],
    );
  }
}

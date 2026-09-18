import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../data/cloud.dart';
import '../data/crash_log.dart';
import '../theme.dart';
import 'widgets/common.dart';

/// מוצג פעם אחת אחרי קריסה. המשתמש מחליט אם לשלוח — שום דבר לא נשלח
/// מעצמו.
Future<void> showCrashDialogIfNeeded(BuildContext context) async {
  final report = await CrashLog.lastCrash();
  if (report == null || !context.mounted) return;
  await showDialog<void>(
    context: context,
    builder: (_) => _CrashDialog(report: report),
  );
  await CrashLog.clear();
}

class _CrashDialog extends StatefulWidget {
  final String report;
  const _CrashDialog({required this.report});

  @override
  State<_CrashDialog> createState() => _CrashDialogState();
}

class _CrashDialogState extends State<_CrashDialog> {
  bool _sending = false;

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('האפליקציה התאוששה מתקלה'),
      content: SizedBox(
        width: 460,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'משהו נפל בפעם הקודמת. אפשר לשלוח את הדוח כדי שנתקן, '
                'או פשוט להמשיך — הנתונים שלך לא נפגעו.',
                style: TextStyle(
                    color: AppTheme.subtext2, fontSize: 12.5, height: 1.5),
              ),
              const SizedBox(height: 10),
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: AppTheme.bg2,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Text(widget.report,
                    style: TextStyle(color: AppTheme.subtext, fontSize: 10.5)),
              ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () {
            Clipboard.setData(ClipboardData(text: widget.report));
            showToast(context, 'הועתק');
          },
          child: Text('העתק', style: TextStyle(color: AppTheme.subtext)),
        ),
        TextButton(
          onPressed: _sending ? null : () => Navigator.pop(context),
          child: const Text('המשך'),
        ),
        FilledButton(
          style: FilledButton.styleFrom(backgroundColor: AppTheme.accent),
          onPressed: _sending
              ? null
              : () async {
                  setState(() => _sending = true);
                  final ok = await Cloud.submitBugReport(
                      'קריסה באפליקציית החנות', widget.report);
                  if (!context.mounted) return;
                  setState(() => _sending = false);
                  Navigator.pop(context);
                  showToast(context, ok ? 'הדוח נשלח ✓' : 'השליחה נכשלה');
                },
          child: Text(_sending ? 'שולח…' : 'שלח דוח'),
        ),
      ],
    );
  }
}

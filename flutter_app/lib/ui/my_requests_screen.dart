import 'package:flutter/material.dart';

import '../data/auth.dart';
import '../data/cloud.dart';
import '../theme.dart';
import 'channel_request_dialog.dart';
import 'widgets/common.dart';

/// הבקשות שהמשתמש שלח להוספת ערוצים, ומה עלה בגורלן.
class MyRequestsScreen extends StatefulWidget {
  const MyRequestsScreen({super.key});

  @override
  State<MyRequestsScreen> createState() => _MyRequestsScreenState();
}

class _MyRequestsScreenState extends State<MyRequestsScreen> {
  List<Map<String, dynamic>> _requests = [];
  bool _loading = true;
  String _error = '';

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    if (!appAuth.ready) {
      setState(() {
        _loading = false;
        _error = 'כדי לראות את הבקשות שלך צריך חשבון עם אימייל מאומת';
      });
      return;
    }
    final result = await Cloud.myChannelRequests();
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (result['ok'] == true) {
        _requests = ((result['requests'] as List?) ?? const [])
            .cast<Map<String, dynamic>>();
        _error = '';
      } else {
        _error = (result['message'] as String?) ?? 'לא ניתן לטעון את הבקשות';
      }
    });
  }

  static (String, Color) _status(String raw) {
    switch (raw) {
      case 'approved':
        return ('אושר', AppTheme.tintGreen);
      case 'rejected':
        return ('נדחה', AppTheme.tintRed);
      default:
        return ('ממתין', AppTheme.tintAmber);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bg,
      appBar: DetailTopBar(
        _requests.isEmpty ? 'הבקשות שלי' : 'הבקשות שלי (${_requests.length})',
        actions: [
          IconButton(
            onPressed: _load,
            icon: Icon(Icons.refresh, color: AppTheme.text),
          ),
        ],
      ),
      body: _loading
          ? const CenteredLoading('טוען בקשות…')
          : _error.isNotEmpty
              ? EmptyState(
                  icon: Icons.inbox_outlined, title: 'לא ניתן להציג', body: _error)
              : _requests.isEmpty
                  ? EmptyState(
                      icon: Icons.inbox_outlined,
                      title: 'עוד לא שלחת בקשות',
                      body: 'ערוץ שאינו מאושר לא מוצג באפליקציה. '
                          'אם מצאת אחד שמתאים — שלח בקשה ונבדוק אותו.',
                      action: FilledButton.icon(
                        style: FilledButton.styleFrom(
                            backgroundColor: AppTheme.accent),
                        onPressed: () => showChannelRequestDialog(context),
                        icon: const Icon(Icons.add, size: 18),
                        label: const Text('בקש להוסיף ערוץ'),
                      ),
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.only(bottom: 24),
                      itemCount: _requests.length,
                      itemBuilder: (context, i) {
                        final request = _requests[i];
                        final (label, color) =
                            _status((request['status'] as String?) ?? 'pending');
                        return ListTile(
                          title: Text((request['name'] as String?) ?? '(ללא שם)',
                              style: TextStyle(
                                  color: AppTheme.text, fontSize: 14.5)),
                          subtitle: Text(
                              (request['url'] as String?) ?? '',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                  color: AppTheme.subtext, fontSize: 11.5)),
                          trailing: Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 10, vertical: 4),
                            decoration: BoxDecoration(
                              color: color.withValues(alpha: 0.16),
                              borderRadius: BorderRadius.circular(50),
                            ),
                            child: Text(label,
                                style: TextStyle(color: color, fontSize: 11.5)),
                          ),
                        );
                      },
                    ),
    );
  }
}

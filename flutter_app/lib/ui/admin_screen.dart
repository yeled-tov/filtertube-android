import 'package:flutter/material.dart';

import '../data/app_state.dart';
import '../data/cloud.dart';
import '../models/channel.dart';
import '../theme.dart';
import 'widgets/common.dart';

/// פאנל הניהול — בקשות ערוץ ממתינות, אישור/דחייה, והוספה או הסרה ידנית
/// מהרשימה הלבנה. מדבר עם אותן פונקציות שרת שהפאנל באפליקציה הראשית
/// משתמש בהן, ולכן שינוי כאן מתפרסם לשתי הגרסאות.
class AdminScreen extends StatefulWidget {
  const AdminScreen({super.key});

  @override
  State<AdminScreen> createState() => _AdminScreenState();
}

class _AdminScreenState extends State<AdminScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs = TabController(length: 2, vsync: this);

  List<Map<String, dynamic>> _requests = [];

  /// המפתח שהשרת מחזיר הוא `summary`, לא `userCount`. קריאה לשם שאינו
  /// קיים הייתה מציגה מקף לנצח בלי שום סימן שמשהו לא בסדר.
  Map<String, dynamic> _summary = {};
  bool _loading = true;
  String _error = '';

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final requests = await Cloud.listChannelRequests();
    final dashboard = await Cloud.adminDashboard();
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (requests['ok'] == true) {
        _requests = ((requests['requests'] as List?) ?? const [])
            .cast<Map<String, dynamic>>();
        _error = '';
      } else {
        _error = (requests['message'] as String?) ?? 'לא ניתן לטעון בקשות';
      }
      if (dashboard['ok'] == true) {
        _summary = (dashboard['summary'] as Map<String, dynamic>?) ?? {};
      }
    });
  }

  Future<void> _resolve(Map<String, dynamic> request, String status) async {
    final id = (request['id'] as String?) ?? '';
    if (id.isEmpty) return;
    final result = await Cloud.resolveChannelRequest(id, status,
        category: (request['category'] as String?) ?? 'general',
        gender: (request['gender'] as String?) ?? 'all');
    if (!mounted) return;
    showToast(context,
        result['ok'] == true ? 'בוצע' : ((result['message'] as String?) ?? 'נכשל'));
    if (result['ok'] == true) {
      await appState.channels.load(force: true);
      await _load();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bg,
      appBar: AppBar(
        backgroundColor: AppTheme.bg,
        title: const Text('ניהול ערוצים'),
        actions: [
          IconButton(onPressed: _load, icon: Icon(Icons.refresh, color: AppTheme.text)),
        ],
        bottom: TabBar(
          controller: _tabs,
          labelColor: AppTheme.accent,
          unselectedLabelColor: AppTheme.subtext,
          indicatorColor: AppTheme.accent,
          tabs: const [
            Tab(text: 'בקשות'),
            Tab(text: 'רשימה לבנה'),
          ],
        ),
      ),
      body: _loading
          ? const CenteredLoading('טוען…')
          : _error.isNotEmpty
              ? CenteredError(_error, onRetry: _load)
              : TabBarView(
                  controller: _tabs,
                  children: [_requestsTab(), _whitelistTab()],
                ),
    );
  }

  Widget _requestsTab() {
    final pending = _requests
        .where((r) => ((r['status'] as String?) ?? 'pending') == 'pending')
        .toList();
    return ListView(
      padding: const EdgeInsets.only(bottom: 24),
      children: [
        if (_summary.isNotEmpty)
          Padding(
            padding: const EdgeInsets.all(16),
            child: Text(
              'חשבונות: ${_summary['totalAccounts'] ?? '—'} · '
              'מאומתים: ${_summary['verifiedAccounts'] ?? '—'} · '
              'מנויים: ${_summary['premiumAccounts'] ?? '—'} · '
              'בקשות ממתינות: ${pending.length}',
              style: TextStyle(color: AppTheme.subtext, fontSize: 12.5),
            ),
          ),
        if (pending.isEmpty)
          const Padding(
            padding: EdgeInsets.only(top: 60),
            child: EmptyState(
              icon: Icons.inbox_outlined,
              title: 'אין בקשות ממתינות',
            ),
          )
        else
          ...pending.map(
            (request) => Container(
              margin: const EdgeInsets.fromLTRB(14, 6, 14, 6),
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: AppTheme.card,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: AppTheme.divider),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text((request['name'] as String?) ?? '(ללא שם)',
                      style: TextStyle(
                          color: AppTheme.text,
                          fontSize: 14.5,
                          fontWeight: FontWeight.w600)),
                  const SizedBox(height: 4),
                  Text((request['url'] as String?) ?? '',
                      style:
                          TextStyle(color: AppTheme.subtext, fontSize: 11.5)),
                  if (((request['description'] as String?) ?? '').isNotEmpty) ...[
                    const SizedBox(height: 6),
                    Text(request['description'] as String,
                        style: TextStyle(
                            color: AppTheme.subtext2, fontSize: 12, height: 1.4)),
                  ],
                  const SizedBox(height: 6),
                  Text(
                    '${categoryLabelHe((request['category'] as String?) ?? 'general')}'
                    ' · ${(request['gender'] as String?) ?? 'all'}',
                    style: TextStyle(color: AppTheme.accent, fontSize: 11.5),
                  ),
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      FilledButton(
                        style: FilledButton.styleFrom(
                            backgroundColor: AppTheme.tintGreen),
                        onPressed: () => _resolve(request, 'approved'),
                        child: const Text('אשר'),
                      ),
                      const SizedBox(width: 10),
                      OutlinedButton(
                        onPressed: () => _resolve(request, 'rejected'),
                        child: const Text('דחה'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }

  Widget _whitelistTab() {
    final channels = [...appState.channels.all]
      ..sort((a, b) => a.name.trim().compareTo(b.name.trim()));
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(14),
          child: FilledButton.icon(
            style: FilledButton.styleFrom(
                backgroundColor: AppTheme.accent,
                minimumSize: const Size.fromHeight(44)),
            onPressed: _addChannel,
            icon: const Icon(Icons.add, size: 18),
            label: const Text('הוסף ערוץ לרשימה הלבנה'),
          ),
        ),
        Expanded(
          child: ListView.builder(
            itemCount: channels.length,
            itemBuilder: (context, i) {
              final channel = channels[i];
              return ListTile(
                title: Text(channel.name.trim(),
                    style: TextStyle(color: AppTheme.text, fontSize: 14)),
                subtitle: Text(
                    '${categoryLabelHe(channel.category)} · ${channel.youtubeChannelId}',
                    style: TextStyle(color: AppTheme.subtext, fontSize: 11)),
                trailing: IconButton(
                  icon: const Icon(Icons.delete_outline, color: AppTheme.tintRed),
                  onPressed: () async {
                    final result = await Cloud
                        .removeApprovedChannel(channel.youtubeChannelId);
                    if (!context.mounted) return;
                    showToast(context,
                        result['ok'] == true ? 'הוסר' : 'ההסרה נכשלה');
                    if (result['ok'] == true) {
                      await appState.channels.load(force: true);
                      if (mounted) setState(() {});
                    }
                  },
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  void _addChannel() {
    final id = TextEditingController();
    final name = TextEditingController();
    var category = 'general';
    var gender = 'all';
    showDialog<void>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (dialogContext, setDialogState) => AlertDialog(
          title: const Text('הוסף ערוץ'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                TextField(
                    controller: id,
                    decoration:
                        const InputDecoration(labelText: 'מזהה ערוץ (UC…)')),
                const SizedBox(height: 10),
                TextField(
                    controller: name,
                    decoration: const InputDecoration(labelText: 'שם הערוץ')),
                const SizedBox(height: 14),
                Wrap(
                  spacing: 6,
                  runSpacing: 6,
                  children: categoryLabels.entries
                      .map((e) => ChoiceChipBox(
                            label: e.value,
                            selected: category == e.key,
                            onTap: () => setDialogState(() => category = e.key),
                          ))
                      .toList(),
                ),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 6,
                  children: [
                    for (final option in const [
                      ('all', 'הכל'),
                      ('male', 'זכר'),
                      ('female', 'נקבה')
                    ])
                      ChoiceChipBox(
                        label: option.$2,
                        selected: gender == option.$1,
                        onTap: () => setDialogState(() => gender = option.$1),
                      ),
                  ],
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(dialogContext),
                child: const Text('בטל')),
            FilledButton(
              style: FilledButton.styleFrom(backgroundColor: AppTheme.accent),
              onPressed: () async {
                final result = await Cloud.upsertApprovedChannel(
                  youtubeChannelId: id.text.trim(),
                  name: name.text.trim(),
                  category: category,
                  gender: gender,
                );
                if (!dialogContext.mounted) return;
                Navigator.pop(dialogContext);
                showToast(dialogContext,
                    result['ok'] == true ? 'נוסף' : 'ההוספה נכשלה');
                if (result['ok'] == true) {
                  await appState.channels.load(force: true);
                  if (mounted) setState(() {});
                }
              },
              child: const Text('הוסף'),
            ),
          ],
        ),
      ),
    );
  }
}

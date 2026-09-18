import 'package:flutter/material.dart';

import '../data/app_state.dart';
import '../data/playback.dart';
import '../models/video.dart';
import '../theme.dart';
import 'widgets/common.dart';
import 'widgets/video_row.dart';

/// שידורים חיים פעילים בערוצים המאושרים.
class LiveScreen extends StatefulWidget {
  const LiveScreen({super.key});

  @override
  State<LiveScreen> createState() => _LiveScreenState();
}

class _LiveScreenState extends State<LiveScreen> {
  List<Video> _live = [];
  bool _loading = true;
  String _query = '';

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    // הפיד כבר טעון; השידורים החיים מזוהים מתוכו בבקשה אחת ל-50 מזהים,
    // במקום חיפוש נפרד לכל ערוץ.
    final list = await appState.api.liveFrom(appState.videos);
    if (!mounted) return;
    setState(() {
      _live = list;
      _loading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final shown = _query.isEmpty
        ? _live
        : _live
            .where((v) =>
                v.title.contains(_query) || v.channelName.contains(_query))
            .toList();

    return Scaffold(
      backgroundColor: AppTheme.bg,
      appBar: DetailTopBar('שידורים חיים', actions: [
        IconButton(
          tooltip: 'רענן שידורים חיים',
          onPressed: _load,
          icon: Icon(Icons.refresh, color: AppTheme.text),
        ),
      ]),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 4, 14, 10),
            child: TextField(
              onChanged: (v) => setState(() => _query = v.trim()),
              style: TextStyle(color: AppTheme.text, fontSize: 14),
              decoration: InputDecoration(
                hintText: 'חפש שידור חי בערוצים המאושרים…',
                hintStyle: TextStyle(color: AppTheme.subtext, fontSize: 13),
                prefixIcon: Icon(Icons.search, color: AppTheme.subtext),
                filled: true,
                fillColor: AppTheme.bg2,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(22),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
          ),
          Expanded(
            child: _loading
                ? const CenteredLoading('מחפש שידורים חיים פעילים…')
                : shown.isEmpty
                    ? EmptyState(
                        icon: Icons.live_tv_rounded,
                        title: _query.isEmpty
                            ? 'אין כרגע שידורים חיים פעילים'
                            : 'לא נמצא שידור חי פעיל שתואם לחיפוש',
                        body: _query.isEmpty
                            ? 'בערוצים המאושרים. הקש על רענון כדי לבדוק שוב.'
                            : '',
                      )
                    : ListView.builder(
                        padding: const EdgeInsets.only(bottom: 24),
                        itemCount: shown.length,
                        itemBuilder: (context, i) => VideoRow(
                          video: shown[i],
                          onTap: () => playback.playFromList(shown, i),
                        ),
                      ),
          ),
        ],
      ),
    );
  }
}

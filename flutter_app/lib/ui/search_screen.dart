import 'dart:async';

import 'package:flutter/material.dart';

import '../data/app_state.dart';
import '../data/library_store.dart';
import '../data/playback.dart';
import '../data/search_engine.dart';
import '../data/settings_store.dart';
import '../data/youtube_api.dart';
import '../models/video.dart';
import '../theme.dart';
import 'channel_request_dialog.dart';
import 'widgets/common.dart';
import 'widgets/video_row.dart';
import 'player_layer.dart';

enum _SearchStatus { idle, loading, empty, results, error }

/// חיפוש — היסטוריה, השלמות, תוצאות מיידיות ממה שכבר במכשיר, וחיפוש מלא
/// בערוצים המאושרים בלבד.
class SearchScreen extends StatefulWidget {
  const SearchScreen({super.key});

  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> {
  final TextEditingController _controller = TextEditingController();
  Timer? _debounce;

  _SearchStatus _status = _SearchStatus.idle;
  List<Video> _results = [];
  List<String> _suggestions = [];
  String _searching = '';

  @override
  void dispose() {
    _debounce?.cancel();
    _controller.dispose();
    super.dispose();
  }

  /// תוצאות מיידיות ממה שכבר נטען — מופיעות לפני שהרשת בכלל עונה.
  List<Video> get _instant {
    final q = _controller.text.trim();
    if (q.length < 2) return const [];
    final pool = <Video>[
      ...appState.videos,
      ...appLibrary.likes,
      ...appLibrary.localHistory,
    ];
    final seen = <String>{};
    return pool
        .where((v) =>
            !appLibrary.blockedIds.contains(v.id) &&
            (v.title.toLowerCase().contains(q.toLowerCase()) ||
                v.channelName.toLowerCase().contains(q.toLowerCase())))
        .where((v) => seen.add(v.id))
        .take(6)
        .toList();
  }

  void _onChanged(String value) {
    setState(() => _status = _SearchStatus.idle);
    _debounce?.cancel();
    final q = value.trim();
    if (q.isEmpty) {
      setState(() => _suggestions = []);
      return;
    }
    setState(() {
      _suggestions =
          SearchEngine.channelSuggestions(appState.visibleChannels, q);
    });
    _debounce = Timer(const Duration(milliseconds: 260), () async {
      final remote = await YoutubeSuggest.suggest(q);
      if (!mounted || _controller.text.trim() != q) return;
      setState(() => _suggestions =
          {..._suggestions, ...remote}.take(8).toList());
    });
  }

  Future<void> _run(String query) async {
    final trimmed = query.trim();
    if (trimmed.isEmpty) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _controller.text = trimmed;
      _status = _SearchStatus.loading;
      _searching = trimmed;
      _suggestions = [];
    });
    await appSettings.addSearchQuery(trimmed);

    final outcome = await appState.search.search(
      trimmed,
      appState.visibleChannels,
      onPartial: (partial) {
        if (!mounted || partial.isEmpty) return;
        setState(() {
          _results = partial;
          _status = _SearchStatus.results;
        });
      },
    );
    if (!mounted) return;
    setState(() {
      _results = outcome.videos
          .where((v) => !appLibrary.blockedIds.contains(v.id))
          .toList();
      if (_results.isNotEmpty) {
        _status = _SearchStatus.results;
      } else if (outcome.failed) {
        _status = _SearchStatus.error;
      } else {
        _status = _SearchStatus.empty;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        SizedBox(height: MediaQuery.of(context).padding.top + 16),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: TextField(
            controller: _controller,
            onChanged: _onChanged,
            onSubmitted: _run,
            textInputAction: TextInputAction.search,
            style: TextStyle(color: AppTheme.text),
            decoration: InputDecoration(
              hintText: 'חפש בערוצים המאושרים...',
              hintStyle: TextStyle(color: AppTheme.subtext),
              prefixIcon: Icon(Icons.search, color: AppTheme.subtext),
              suffixIcon: _controller.text.isEmpty
                  ? null
                  : IconButton(
                      tooltip: 'נקה',
                      icon: Icon(Icons.close, color: AppTheme.subtext),
                      onPressed: () => setState(() {
                        _controller.clear();
                        _status = _SearchStatus.idle;
                        _suggestions = [];
                      }),
                    ),
              filled: true,
              fillColor: AppTheme.bg2,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(24),
                borderSide: BorderSide.none,
              ),
            ),
          ),
        ),
        const SizedBox(height: 6),
        Expanded(child: _body()),
      ],
    );
  }

  Widget _body() {
    switch (_status) {
      case _SearchStatus.loading:
        return CenteredLoading('מחפש "$_searching"…');
      case _SearchStatus.error:
        return CenteredError(
          'לא ניתן לחפש כרגע. בדוק את החיבור לאינטרנט ונסה שוב.',
          onRetry: () => _run(_controller.text),
        );
      case _SearchStatus.empty:
        return EmptyState(
          icon: Icons.search,
          title: 'לא נמצאו סרטונים ל"${_controller.text.trim()}"',
          body: 'החיפוש מוגבל לערוצים המאושרים בלבד.',
          action: FilledButton.icon(
            style: FilledButton.styleFrom(backgroundColor: AppTheme.accent),
            onPressed: () => showChannelRequestDialog(context,
                prefillName: _controller.text.trim()),
            icon: const Icon(Icons.add, size: 18),
            label: const Text('בקש להוסיף ערוץ'),
          ),
        );
      case _SearchStatus.results:
        return ListView.builder(
          padding: EdgeInsets.only(
              top: 6, bottom: PlayerLayer.bottomInset(context)),
          itemCount: _results.length,
          itemBuilder: (context, i) => VideoRow(
            video: _results[i],
            onTap: () => playback.playFromList(_results, i),
          ),
        );
      case _SearchStatus.idle:
        return _controller.text.trim().isEmpty ? _history() : _instantResults();
    }
  }

  Widget _history() {
    final history = appSettings.searchHistory;
    if (history.isEmpty) {
      return const EmptyState(
        icon: Icons.history,
        title: 'עוד לא חיפשת כלום',
        body: 'החיפוש מוגבל לערוצים המאושרים — מה שלא אושר פשוט לא קיים כאן.',
      );
    }
    return ListView(
      padding: EdgeInsets.only(bottom: PlayerLayer.bottomInset(context)),
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 10, 8, 4),
          child: Row(
            children: [
              Text('חיפושים אחרונים',
                  style: TextStyle(
                      color: AppTheme.text,
                      fontSize: 13.5,
                      fontWeight: FontWeight.bold)),
              const Spacer(),
              TextButton(
                onPressed: () async {
                  await appSettings.clearSearchHistory();
                  if (mounted) setState(() {});
                },
                child: Text('נקה הכל',
                    style: TextStyle(color: AppTheme.subtext, fontSize: 12)),
              ),
            ],
          ),
        ),
        ...history.map(
          (q) => ListTile(
            dense: true,
            leading: Icon(Icons.history, color: AppTheme.subtext, size: 20),
            title: Text(q, style: TextStyle(color: AppTheme.text, fontSize: 14)),
            trailing: IconButton(
              icon: Icon(Icons.close, color: AppTheme.subtext, size: 17),
              onPressed: () async {
                await appSettings.removeSearchQuery(q);
                if (mounted) setState(() {});
              },
            ),
            onTap: () => _run(q),
          ),
        ),
      ],
    );
  }

  Widget _instantResults() {
    final instant = _instant;
    return ListView(
      padding: EdgeInsets.only(bottom: PlayerLayer.bottomInset(context)),
      children: [
        ..._suggestions.map(
          (s) => ListTile(
            dense: true,
            leading: Icon(Icons.north_west, color: AppTheme.subtext, size: 18),
            title: Text(s, style: TextStyle(color: AppTheme.text, fontSize: 14)),
            onTap: () => _run(s),
          ),
        ),
        if (instant.isNotEmpty) ...[
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
            child: Text('כבר אצלך',
                style: TextStyle(
                    color: AppTheme.subtext,
                    fontSize: 12,
                    fontWeight: FontWeight.bold)),
          ),
          ...instant.map((v) => VideoListTile(
                video: v,
                onTap: () => playback.play(v),
              )),
        ],
        Padding(
          padding: const EdgeInsets.all(16),
          child: FilledButton.icon(
            style: FilledButton.styleFrom(backgroundColor: AppTheme.accent),
            onPressed: () => _run(_controller.text),
            icon: const Icon(Icons.search, size: 18),
            label: Text('חפש "${_controller.text.trim()}" בכל הערוצים'),
          ),
        ),
      ],
    );
  }
}

import 'package:flutter/material.dart';
import '../models.dart';
import '../theme.dart';
import '../youtube_api.dart';
import '../channels_repo.dart';
import '../widgets/video_card.dart';
import 'player_screen.dart';

/// חיפוש — מבוסס search.list הרשמי, מסונן לרשימה הלבנה (רק תוצאות מערוצים מאושרים).
class SearchScreen extends StatefulWidget {
  final YoutubeApi api;
  final ChannelsRepo channels;

  const SearchScreen({super.key, required this.api, required this.channels});

  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> {
  final _controller = TextEditingController();
  List<Video> _results = [];
  bool _loading = false;
  bool _searched = false;

  Future<void> _doSearch() async {
    final q = _controller.text.trim();
    if (q.isEmpty) return;
    setState(() {
      _loading = true;
      _searched = true;
    });
    final res = await widget.api.search(q, widget.channels.isApproved);
    final playable = await widget.api.filterEmbeddable(res);
    if (!mounted) return;
    setState(() {
      _results = playable;
      _loading = false;
    });
  }

  void _open(Video v) {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => PlayerScreen(
        video: v,
        api: widget.api,
        channels: widget.channels,
      ),
    ));
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: TextField(
          controller: _controller,
          autofocus: false,
          textInputAction: TextInputAction.search,
          onSubmitted: (_) => _doSearch(),
          style: const TextStyle(color: AppTheme.text),
          decoration: const InputDecoration(
            hintText: 'חיפוש…',
            hintStyle: TextStyle(color: AppTheme.subtext),
            border: InputBorder.none,
          ),
        ),
        actions: [
          IconButton(
              icon: const Icon(Icons.search, color: AppTheme.text),
              onPressed: _doSearch),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: AppTheme.accent))
          : !_searched
              ? const Center(
                  child: Text('חפש סרטונים מהערוצים המאושרים',
                      style: TextStyle(color: AppTheme.subtext)))
              : _results.isEmpty
                  ? const Center(
                      child: Text('לא נמצאו תוצאות מאושרות',
                          style: TextStyle(color: AppTheme.subtext)))
                  : ListView.builder(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      itemCount: _results.length,
                      itemBuilder: (context, i) => VideoListTile(
                            video: _results[i],
                            onTap: () => _open(_results[i]),
                          )),
    );
  }
}

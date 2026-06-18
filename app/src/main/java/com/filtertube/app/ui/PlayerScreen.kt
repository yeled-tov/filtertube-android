package com.filtertube.app.ui

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.filtertube.app.ThemeState
import com.filtertube.app.data.GoogleAuth
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.StreamData
import com.filtertube.app.data.StreamTrack
import com.filtertube.app.data.Video
import com.filtertube.app.data.YouTubeAccountRepository
import com.filtertube.app.playback.Playback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    controller: MediaController?,
    ui: PlayerUiState,
    onCollapse: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    // מסך מלא — סיבוב + הסתרת סרגלים
    LaunchedEffect(isFullscreen) {
        activity?.let { act ->
            act.requestedOrientation = if (isFullscreen)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val ctrl = WindowCompat.getInsetsController(act.window, act.window.decorView)
            if (isFullscreen) {
                ctrl.hide(WindowInsetsCompat.Type.systemBars())
                ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else ctrl.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(act.window, act.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler { if (isFullscreen) isFullscreen = false else onCollapse() }

    if (controller == null || !ui.hasMedia) {
        Box(modifier = Modifier.fillMaxSize().background(ThemeState.bg), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ThemeState.accent)
        }
        return
    }

    val currentData = Playback.cachedData(ui.mediaId)
    val audioMode = ui.isAudio
    val store = remember { LibraryStore(context) }

    // המסך נשאר דלוק כל עוד מתנגן וידאו (לא אודיו). יוצאים מהמסך → נכבה כרגיל.
    KeepScreenOn(ui.isPlaying && !audioMode)
    fun currentVideo() = Video(
        id = ui.mediaId ?: "", title = ui.title, channelName = ui.artist,
        channelId = "", thumbnailUrl = ui.artworkUri?.toString() ?: "", publishedAt = System.currentTimeMillis(),
    )
    var liked by remember(ui.mediaId) { mutableStateOf(ui.mediaId?.let { store.isLiked(it) } ?: false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }
    var qualityIndex by remember(ui.mediaId) {
        mutableStateOf(currentData?.let { Playback.defaultQuality(it, settings.preferredQuality) } ?: 0)
    }
    var speed by remember(controller) { mutableStateOf(controller.playbackParameters.speed) }

    fun replaceCurrent(audio: Boolean, qIdx: Int) {
        val d = currentData ?: return
        val id = ui.mediaId ?: return
        val idx = controller.currentMediaItemIndex
        val pos = controller.currentPosition
        val pw = controller.playWhenReady
        controller.replaceMediaItem(idx, Playback.buildItem(d, id, audio, qIdx))
        controller.seekTo(idx, pos)
        controller.playWhenReady = pw
    }

    // עיצוב 2 — בקרים על הוידאו + "הבא בתור" מתחת
    if (!isFullscreen && settings.playerStyle == 2 && !audioMode) {
        OnVideoPlayerScreen(
            controller = controller, ui = ui, activity = activity,
            onCollapse = onCollapse, onFullscreen = { isFullscreen = true },
        )
        return
    }

    if (isFullscreen) {
        FullscreenVideo(
            controller = controller, ui = ui, activity = activity,
            onExit = { isFullscreen = false },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        // סרגל עליון + גרירה למטה לכיווץ
        Row(
            modifier = Modifier.fillMaxWidth()
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragEnd = { if (total > 160f) onCollapse(); total = 0f },
                        onVerticalDrag = { _, dy -> total += dy },
                    )
                }
                .padding(top = 24.dp, start = 4.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Default.KeyboardArrowDown, "כווץ", tint = ThemeState.text)
            }
            Text("מתנגן עכשיו", color = ThemeState.subtext2, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                liked = store.toggleLike(currentVideo())
                syncLikeToYoutube(context, scope, ui.mediaId ?: "", liked)
            }) {
                Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "אהבתי", tint = if (liked) ThemeState.accent else Color.White)
            }
            PlayerOverflowMenu(
                context = context,
                hasData = currentData != null,
                artist = ui.artist,
                videoId = ui.mediaId ?: "",
                onAddToPlaylist = { showAddToPlaylist = true },
                onDownload = { showDownload = true },
            )
        }

        if (showAddToPlaylist) {
            AddToPlaylistDialog(
                store = store,
                video = currentVideo(),
                onDismiss = { showAddToPlaylist = false },
            )
        }

        if (showDownload && currentData != null) {
            DownloadDialog(
                context = context,
                data = currentData,
                videoId = ui.mediaId ?: "",
                onDismiss = { showDownload = false },
            )
        }

        // אזור מדיה: וידאו או כריכה
        if (audioMode) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.fillMaxWidth(0.7f).aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp)).background(ThemeState.card),
                    contentAlignment = Alignment.Center,
                ) {
                    if (ui.artworkUri != null) {
                        AsyncImage(model = ui.artworkUri, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.MusicNote, null, tint = Color(0xFF555555), modifier = Modifier.size(64.dp))
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                VideoSurface(controller)
                VideoGestures(controller, activity, audioMode = false, onSwipeDown = onCollapse)
                if (ui.buffering) CircularProgressIndicator(color = ThemeState.accent, modifier = Modifier.align(Alignment.Center))
            }
        }

        // כותרת + אמן
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(ui.title, color = ThemeState.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(ui.artist, color = ThemeState.subtext2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        // פס התקדמות
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Slider(
                value = ui.position.toFloat().coerceIn(0f, ui.duration.toFloat().coerceAtLeast(0f)),
                onValueChange = { controller.seekTo(it.toLong()) },
                valueRange = 0f..ui.duration.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = ThemeState.accent, activeTrackColor = ThemeState.accent,
                    inactiveTrackColor = Color(0x55FFFFFF),
                ),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text(fmtTime(ui.position), color = ThemeState.subtext2, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(fmtTime(ui.duration), color = ThemeState.subtext2, fontSize = 11.sp)
            }
        }

        // בקרים ראשיים
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { controller.seekToPreviousMediaItem() }, enabled = ui.hasPrev) {
                Icon(Icons.Default.SkipPrevious, "הקודם", tint = if (ui.hasPrev) Color.White else Color(0xFF555555),
                    modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.width(20.dp))
            Box(
                modifier = Modifier.size(68.dp).clip(RoundedCornerShape(50)).background(ThemeState.accent)
                    .clickable { if (ui.isPlaying) controller.pause() else controller.play() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (ui.isPlaying) "השהה" else "נגן", tint = ThemeState.text, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.width(20.dp))
            IconButton(onClick = { controller.seekToNextMediaItem() }, enabled = ui.hasNext) {
                Icon(Icons.Default.SkipNext, "הבא", tint = if (ui.hasNext) Color.White else Color(0xFF555555),
                    modifier = Modifier.size(40.dp))
            }
        }

        // בקרים משניים: אודיו/וידאו, מהירות, איכות, מסך מלא
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currentData != null) {
                IconButton(onClick = { replaceCurrent(!audioMode, qualityIndex) }) {
                    Icon(if (audioMode) Icons.Default.Videocam else Icons.Default.Audiotrack,
                        if (audioMode) "וידאו" else "אודיו", tint = ThemeState.text)
                }
            }

            var speedMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { speedMenu = true }) { Icon(Icons.Default.Speed, "מהירות", tint = ThemeState.text) }
                DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                        DropdownMenuItem(
                            text = { Text("${sp}x" + if (sp == speed) "  ✓" else "") },
                            onClick = { speed = sp; controller.setPlaybackParameters(androidx.media3.common.PlaybackParameters(sp)); speedMenu = false },
                        )
                    }
                }
            }

            if (!audioMode && currentData != null && currentData.tracks.size > 1) {
                var qMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { qMenu = true }) { Icon(Icons.Default.HighQuality, "איכות", tint = ThemeState.text) }
                    DropdownMenu(expanded = qMenu, onDismissRequest = { qMenu = false }) {
                        currentData.tracks.forEachIndexed { i, t ->
                            DropdownMenuItem(
                                text = { Text(t.label + if (i == qualityIndex) "  ✓" else "") },
                                onClick = { qualityIndex = i; replaceCurrent(false, i); qMenu = false },
                            )
                        }
                    }
                }
            }

            if (!audioMode) {
                IconButton(onClick = { isFullscreen = true }) {
                    Icon(Icons.Default.Fullscreen, "מסך מלא", tint = ThemeState.text)
                }
            }
        }

        // הבא בתור
        QueueList(controller, ui, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun QueueList(controller: MediaController, ui: PlayerUiState, modifier: Modifier = Modifier) {
    val items = remember(ui.queueVersion, ui.mediaId) {
        val cur = controller.currentMediaItemIndex
        ((cur + 1) until controller.mediaItemCount).map { i -> i to controller.getMediaItemAt(i) }
    }
    Column(modifier = modifier) {
        if (items.isEmpty()) return@Column
        HorizontalDivider(color = Color(0xFF222222), modifier = Modifier.padding(vertical = 6.dp))
        Text("הבא בתור", color = ThemeState.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            itemsIndexed(items, key = { _, p -> p.first }) { _, (index, item) ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { controller.seekTo(index, 0L) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(ThemeState.divider),
                    contentAlignment = Alignment.Center,
                ) {
                    val art = item.mediaMetadata.artworkUri
                    if (art != null) AsyncImage(model = art, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.mediaMetadata.title?.toString() ?: "", color = ThemeState.text, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.mediaMetadata.artist?.toString() ?: "", color = ThemeState.subtext, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(controller: MediaController) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { it.player = controller },
        modifier = Modifier.fillMaxSize(),
    )
}

/** מחוות על משטח הוידאו: טאפ יחיד (להצגת בקרים), דאבל-טאפ לדילוג, החלקה למטה למזעור הנגן. */
@Composable
private fun VideoGestures(
    controller: MediaController,
    activity: Activity?,
    audioMode: Boolean,
    onSingleTap: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var feedback by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(feedback) { if (feedback != null) { kotlinx.coroutines.delay(800); feedback = null } }

    Box(
        modifier = Modifier.fillMaxSize().onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSingleTap?.invoke() },
                    onDoubleTap = { offset ->
                        val w = boxSize.width.coerceAtLeast(1)
                        if (offset.x < w / 2f) {
                            controller.seekTo((controller.currentPosition - 10_000).coerceAtLeast(0)); feedback = "⏪ 10 ש׳"
                        } else {
                            controller.seekTo(controller.currentPosition + 10_000); feedback = "10 ש׳ ⏩"
                        }
                    },
                )
            }
            .pointerInput(onSwipeDown) {
                val cb = onSwipeDown ?: return@pointerInput
                var total = 0f
                detectVerticalDragGestures(
                    onDragEnd = { if (total > 180f) cb(); total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                )
            },
    ) {
        feedback?.let {
            Text(it, color = ThemeState.text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC000000)).padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

/** שומר את המסך דלוק כל עוד [enabled] (בזמן צפייה בוידאו). */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun FullscreenVideo(
    controller: MediaController,
    ui: PlayerUiState,
    activity: Activity?,
    onExit: () -> Unit,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, ui.isPlaying) {
        if (controlsVisible && ui.isPlaying) { kotlinx.coroutines.delay(3500); controlsVisible = false }
    }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(controller)
        VideoGestures(controller, activity, audioMode = false, onSingleTap = { controlsVisible = !controlsVisible }, onSwipeDown = onExit)
        if (ui.buffering) CircularProgressIndicator(color = ThemeState.accent, modifier = Modifier.align(Alignment.Center))
        if (controlsVisible) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0x66000000))) {
                Box(
                    modifier = Modifier.size(72.dp).align(Alignment.Center).clip(RoundedCornerShape(50))
                        .background(ThemeState.accent)
                        .clickable { if (ui.isPlaying) controller.pause() else controller.play() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null,
                        tint = ThemeState.text, modifier = Modifier.size(44.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(fmtTime(ui.position), color = ThemeState.text, fontSize = 11.sp)
                    Slider(
                        value = ui.position.toFloat().coerceIn(0f, ui.duration.toFloat().coerceAtLeast(0f)),
                        onValueChange = { controller.seekTo(it.toLong()) },
                        valueRange = 0f..ui.duration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(thumbColor = ThemeState.accent,
                            activeTrackColor = ThemeState.accent, inactiveTrackColor = Color(0x55FFFFFF)),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(fmtTime(ui.duration), color = ThemeState.text, fontSize = 11.sp)
                    IconButton(onClick = onExit) { Icon(Icons.Default.FullscreenExit, "צא ממסך מלא", tint = ThemeState.text) }
                }
            }
        }
    }
}

@Composable
private fun PlayerOverflowMenu(
    context: Context,
    hasData: Boolean,
    artist: String,
    videoId: String,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "עוד", tint = ThemeState.text) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (hasData) {
                DropdownMenuItem(
                    text = { Text("הורד") },
                    leadingIcon = { Icon(Icons.Default.Download, null) },
                    onClick = { menu = false; onDownload() },
                )
            }
            DropdownMenuItem(
                text = { Text("הוסף לאלבום") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                onClick = { menu = false; onAddToPlaylist() },
            )
            DropdownMenuItem(
                text = { Text("הצג אמן") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                onClick = {
                    menu = false
                    Toast.makeText(context, artist.ifEmpty { "—" }, Toast.LENGTH_SHORT).show()
                },
            )
            DropdownMenuItem(
                text = { Text("העתק קישור") },
                leadingIcon = { Icon(Icons.Default.Link, null) },
                onClick = {
                    menu = false
                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clip.setPrimaryClip(android.content.ClipData.newPlainText("link", "https://youtu.be/$videoId"))
                    Toast.makeText(context, "הקישור הועתק", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

/** דיאלוג הורדה — אודיו (מקסימלי) או וידאו בכל האיכויות. וידאו מעל 720p הוא ללא קול. */
@Composable
private fun DownloadDialog(context: Context, data: StreamData, videoId: String, onDismiss: () -> Unit) {
    fun record() {
        LibraryStore(context).addDownload(
            Video(videoId, data.title, data.uploaderName, data.channelId,
                data.thumbnailUrl ?: "", System.currentTimeMillis()),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הורדה") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("אודיו", color = ThemeState.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (data.bestAudioUrl != null) {
                    DownloadRow("איכות מקסימלית (M4A / Opus)") {
                        record(); downloadStream(context, data.bestAudioUrl, data.title, isAudio = true); onDismiss()
                    }
                } else {
                    Text("לא זמין", color = ThemeState.subtext, fontSize = 12.sp)
                }
                HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 8.dp))
                Text("וידאו", color = ThemeState.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                data.tracks.forEach { t ->
                    val silent = t.audioUrl != null   // זרם וידאו-בלבד (DASH) = ללא קול
                    val label = t.label + if (silent) "  (ללא קול)" else ""
                    DownloadRow(label) {
                        record(); downloadStream(context, t.videoUrl, data.title, isAudio = false); onDismiss()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגור") } },
        containerColor = ThemeState.surface,
        titleContentColor = ThemeState.text, textContentColor = ThemeState.text,
    )
}

@Composable
private fun DownloadRow(label: String, onClick: () -> Unit) {
    Text(label, color = ThemeState.text, fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp))
}

@Composable
private fun AddToPlaylistDialog(store: LibraryStore, video: Video, onDismiss: () -> Unit) {
    var playlists by remember { mutableStateOf(store.playlists()) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                if (newName.isNotBlank()) { store.createPlaylist(newName); store.addToPlaylist(newName.trim(), video) }
                onDismiss()
            }) { Text("צור והוסף") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("סגור") } },
        title = { Text("הוסף לאלבום") },
        text = {
            Column {
                playlists.forEach { pl ->
                    Text(
                        pl.name,
                        color = ThemeState.text,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { store.addToPlaylist(pl.name, video); onDismiss() }
                            .padding(vertical = 10.dp),
                    )
                }
                if (playlists.isNotEmpty()) HorizontalDivider(color = Color(0xFF333333))
                OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true,
                    label = { Text("אלבום חדש") }, modifier = Modifier.padding(top = 8.dp))
            }
        },
        containerColor = ThemeState.surface,
        titleContentColor = ThemeState.text,
        textContentColor = ThemeState.text,
    )
}

/** עיצוב נגן 2 — וידאו עם בקרים עליו, ומתחת רשימת "הבא בתור". */
@OptIn(UnstableApi::class)
@Composable
private fun OnVideoPlayerScreen(
    controller: MediaController,
    ui: PlayerUiState,
    activity: Activity?,
    onCollapse: () -> Unit,
    onFullscreen: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { LibraryStore(context) }
    val currentData = Playback.cachedData(ui.mediaId)
    fun currentVideo() = Video(ui.mediaId ?: "", ui.title, ui.artist, "",
        ui.artworkUri?.toString() ?: "", System.currentTimeMillis())
    var liked by remember(ui.mediaId) { mutableStateOf(ui.mediaId?.let { store.isLiked(it) } ?: false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(controlsVisible, ui.isPlaying) {
        if (controlsVisible && ui.isPlaying) { kotlinx.coroutines.delay(3000); controlsVisible = false }
    }

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            VideoSurface(controller)
            VideoGestures(controller, activity, audioMode = false, onSingleTap = { controlsVisible = !controlsVisible }, onSwipeDown = onCollapse)
            if (ui.buffering) CircularProgressIndicator(color = ThemeState.accent, modifier = Modifier.align(Alignment.Center))
            if (controlsVisible) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0x66000000))) {
                    IconButton(onClick = onCollapse, modifier = Modifier.align(Alignment.TopStart)) {
                        Icon(Icons.Default.KeyboardArrowDown, "כווץ", tint = ThemeState.text)
                    }
                    Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { controller.seekToPreviousMediaItem() }, enabled = ui.hasPrev) {
                            Icon(Icons.Default.SkipPrevious, "הקודם",
                                tint = if (ui.hasPrev) Color.White else Color(0x88FFFFFF), modifier = Modifier.size(36.dp))
                        }
                        Spacer(Modifier.width(24.dp))
                        Box(modifier = Modifier.size(60.dp).clip(RoundedCornerShape(50)).background(ThemeState.accent)
                            .clickable { if (ui.isPlaying) controller.pause() else controller.play() },
                            contentAlignment = Alignment.Center) {
                            Icon(if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null,
                                tint = ThemeState.text, modifier = Modifier.size(36.dp))
                        }
                        Spacer(Modifier.width(24.dp))
                        IconButton(onClick = { controller.seekToNextMediaItem() }, enabled = ui.hasNext) {
                            Icon(Icons.Default.SkipNext, "הבא",
                                tint = if (ui.hasNext) Color.White else Color(0x88FFFFFF), modifier = Modifier.size(36.dp))
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(fmtTime(ui.position), color = ThemeState.text, fontSize = 11.sp)
                        Slider(value = ui.position.toFloat().coerceIn(0f, ui.duration.toFloat().coerceAtLeast(0f)),
                            onValueChange = { controller.seekTo(it.toLong()) },
                            valueRange = 0f..ui.duration.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(thumbColor = ThemeState.accent,
                                activeTrackColor = ThemeState.accent, inactiveTrackColor = Color(0x55FFFFFF)),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                        Text(fmtTime(ui.duration), color = ThemeState.text, fontSize = 11.sp)
                        IconButton(onClick = onFullscreen) { Icon(Icons.Default.Fullscreen, "מסך מלא", tint = ThemeState.text) }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(ui.title, color = ThemeState.text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                Text(ui.artist, color = ThemeState.subtext2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = {
                liked = store.toggleLike(currentVideo())
                syncLikeToYoutube(context, scope, ui.mediaId ?: "", liked)
            }) {
                Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "אהבתי",
                    tint = if (liked) ThemeState.accent else ThemeState.text)
            }
            PlayerOverflowMenu(context = context, hasData = currentData != null, artist = ui.artist,
                videoId = ui.mediaId ?: "", onAddToPlaylist = { showAddToPlaylist = true }, onDownload = { showDownload = true })
        }
        QueueList(controller, ui, Modifier.weight(1f).fillMaxWidth())
    }

    if (showAddToPlaylist) AddToPlaylistDialog(store = store, video = currentVideo(), onDismiss = { showAddToPlaylist = false })
    if (showDownload && currentData != null) {
        DownloadDialog(context = context, data = currentData, videoId = ui.mediaId ?: "", onDismiss = { showDownload = false })
    }
}

private fun syncLikeToYoutube(context: Context, scope: CoroutineScope, videoId: String, like: Boolean) {
    if (videoId.isEmpty()) return
    val accountStore = com.filtertube.app.data.AccountStore(context)
    scope.launch {
        runCatching {
            if (accountStore.isLoggedIn) {
                // סנכרון מלא דרך InnerTube (cookies)
                com.filtertube.app.data.InnerTube.rate(accountStore.cookies, videoId, like)
            } else {
                // נפילה ל-OAuth הרשמי
                val acct = GoogleAuth.lastAccount(context)?.account ?: return@runCatching
                val token = GoogleAuth.accessToken(context, acct)
                YouTubeAccountRepository.rate(token, videoId, like)
            }
        }
    }
}

private fun fmtTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return if (m >= 60) "%d:%02d:%02d".format(m / 60, m % 60, s) else "%d:%02d".format(m, s)
}

private fun downloadStream(context: Context, url: String, title: String, isAudio: Boolean) {
    try {
        val safeTitle = title.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(60).ifEmpty { "filtertube" }
        val ext = if (isAudio) "m4a" else "mp4"
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(safeTitle)
            setDescription("FilterTube — ${if (isAudio) "אודיו" else "וידאו"}")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$safeTitle.$ext")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(context, "ההורדה התחילה — בדוק בהתראות", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "שגיאה בהורדה: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

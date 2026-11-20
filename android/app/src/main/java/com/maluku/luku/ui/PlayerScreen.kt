package com.maluku.luku.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.maluku.luku.App
import com.maluku.luku.data.PlayInfo
import com.maluku.luku.data.StreamItem
import com.maluku.luku.data.SubtitleItem
import com.maluku.luku.player.Players
import com.maluku.luku.torrent.TorrentStream
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun PlayerScreen(nav: Nav, stream: StreamItem, info: PlayInfo) {
    val ctx = LocalContext.current
    val app = App.instance
    val scope = rememberCoroutineScope()
    val player = remember { Players.create(ctx) }
    var status by remember { mutableStateOf("Starten…") }
    var error by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var torrent by remember { mutableStateOf<TorrentStream?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showNext by remember { mutableStateOf(false) }
    var nextBusy by remember { mutableStateOf(false) }
    val next = info.next

    // Voortgang bijhouden (voor Verder kijken)
    val lastSave = remember { longArrayOf(0L, System.currentTimeMillis()) } // [laatste push-tijd, laatste meting]
    fun saveProgress(pushNow: Boolean) {
        val pos = player.currentPosition
        val dur = player.duration
        if (dur == C.TIME_UNSET || dur <= 0 || pos <= 0) return
        val nowT = System.currentTimeMillis()
        val watched = if (player.isPlaying) (nowT - lastSave[1]).coerceIn(0, 60_000) else 0
        lastSave[1] = nowT
        app.library.updateProgress(info, pos, dur, watched, pushNow)
    }

    // Naar de volgende aflevering: dezelfde add-on en kwaliteit als nu, als die er is
    fun playNext() {
        val n = next ?: return
        if (nextBusy) return
        nextBusy = true
        notice = "Volgende aflevering zoeken…"
        scope.launch {
            val list = runCatching { app.stremio.streams(info.type, n.first) }.getOrDefault(emptyList())
            val pick = list.firstOrNull { it.addonName == stream.addonName && it.quality == stream.quality }
                ?: list.firstOrNull { it.quality == stream.quality }
                ?: list.firstOrNull()
            val nextInfo = info.copy(videoId = n.first, label = n.second)
            if (pick != null) {
                nav.replace(Screen.Player(pick, nextInfo))
            } else {
                nav.replace(Screen.Streams(nextInfo))
            }
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
                if (state == Player.STATE_ENDED) {
                    saveProgress(true)
                    if (next != null) playNext()
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                error = "Afspelen mislukt: " + (e.cause?.message ?: e.errorCodeName)
            }
        }
        player.addListener(listener)
        onDispose {
            saveProgress(true)
            player.removeListener(listener)
            player.release()
            app.torrents.stop()
        }
    }

    LaunchedEffect(stream) {
        try {
            val resume = app.library.resumeFor(info.metaId, info.videoId).let { if (it < 5_000) 0L else it }
            // Ondertitels tegelijk ophalen, terwijl de torrent start
            val subsJob = async {
                withTimeoutOrNull(8_000) { runCatching { app.stremio.subtitles(info.type, info.videoId) }.getOrNull() } ?: emptyList<SubtitleItem>()
            }
            var t: TorrentStream? = null
            if (stream.isTorrent) {
                t = app.torrents.open(stream.infoHash!!, stream.fileIdx, stream.trackers) { status = it }
                torrent = t
            }
            status = "Bufferen…"
            val subs = subsJob.await()
            Players.play(player, t, stream.url, subs, resume)
            if (resume > 0) notice = "Verder vanaf " + formatTime(resume)
            if (subs.any { it.lang == "nl" }) notice = (notice?.let { "$it · " } ?: "") + "Nederlandse ondertitels gevonden"
        } catch (e: Exception) {
            error = e.message ?: "Starten mislukt"
        }
    }

    // Status tijdens bufferen, voortgang opslaan, en de knop "Volgende aflevering" in de laatste 90 seconden
    LaunchedEffect(torrent) {
        var tick = 0
        while (true) {
            torrent?.let { status = it.stats() }
            val dur = player.duration
            showNext = next != null && dur != C.TIME_UNSET && dur > 0 && dur - player.currentPosition < 90_000
            if (tick % 15 == 14) {
                val nowT = System.currentTimeMillis()
                val push = nowT - lastSave[0] > 60_000
                if (push) lastSave[0] = nowT
                saveProgress(push)
            }
            tick++
            delay(1000)
        }
    }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(5000)
            notice = null
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                    useController = true
                    setShowSubtitleButton(true)
                    keepScreenOn = true
                    requestFocus()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (error != null) {
            Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
                    Text(error!!, color = Color.White, fontSize = 18.sp)
                    Text(" ", fontSize = 8.sp)
                    LukuButton("Andere bron kiezen") { nav.back() }
                }
            }
        } else if (buffering) {
            InfoBox(Modifier.align(Alignment.TopStart), info.label, status)
        } else if (notice != null) {
            InfoBox(Modifier.align(Alignment.TopStart), info.label, notice!!)
        }

        if (showNext && error == null && next != null) {
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 110.dp)) {
                LukuButton(if (nextBusy) "Laden…" else "Volgende aflevering ▶") { playNext() }
            }
        }
    }
}

@Composable
private fun InfoBox(modifier: Modifier, title: String, line: String) {
    Box(
        modifier.padding(28.dp)
            .background(Color(0xAA000000), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(line, color = Z.dim, fontSize = 13.sp)
        }
    }
}

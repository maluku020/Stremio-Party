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
fun PlayerScreen(nav: Nav, startStream: StreamItem, info: PlayInfo, race: List<StreamItem> = emptyList()) {
    // Bij een race weten we pas na het starten welke bron gewonnen heeft
    var stream by remember { mutableStateOf(startStream) }
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
    var debug by remember { mutableStateOf("") }
    var stalled by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf("start") }
    var nextBusy by remember { mutableStateOf(false) }
    val next = info.next
    val party = app.party
    // Na een opdracht van een ander: eigen veranderingen even niet terugsturen
    val suppressUntil = remember { longArrayOf(0L) }
    val loadedOnce = remember { booleanArrayOf(false) }

    /** Opdracht van de groep uitvoeren (pauze/play/spoelen). */
    fun applyGroup(paused: Boolean, time: Double, action: String, by: String?) {
        if (player.duration == C.TIME_UNSET) return // video nog niet geladen; gebeurt zodra hij klaar is
        suppressUntil[0] = System.currentTimeMillis() + 1500
        if (player.playWhenReady == paused) player.playWhenReady = !paused
        val diff = kotlin.math.abs(player.currentPosition / 1000.0 - time)
        if ((action == "sync" && diff > 3) || (action != "sync" && diff > 1.5)) player.seekTo((time * 1000).toLong())
        if (by != null) {
            val label = when (action) { "pause" -> "heeft gepauzeerd ⏸"; "play" -> "drukte op play ▶️"; "seek" -> "heeft gespoeld ⏩"; else -> null }
            if (label != null) party.notice = "$by $label"
        }
    }

    fun sendOwn(action: String) {
        if (!party.active || System.currentTimeMillis() < suppressUntil[0]) return
        if (!party.canControl) {
            party.groupTime()?.let { (p, t) -> applyGroup(p, t, "join", null) }
            party.notice = "Alleen de host bedient nu"
            return
        }
        party.sendPlayback(!player.playWhenReady, player.currentPosition / 1000.0, action)
    }

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
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) sendOwn(if (playWhenReady) "play" else "pause")
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK && loadedOnce[0]) sendOwn("seek")
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && !loadedOnce[0]) {
                    loadedOnce[0] = true
                    // Samen kijken: meteen naar de plek van de groep, of als eerste de plek doorgeven
                    if (party.active) {
                        val g = party.groupTime()
                        if (g != null) applyGroup(g.first, g.second, "join", null)
                        else if (party.canControl) party.sendPlayback(false, player.currentPosition / 1000.0, "sync")
                        else party.requestState()
                    }
                }
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
        party.onPlayback = { p, t, a, by -> applyGroup(p, t, a, by) }
        onDispose {
            party.onPlayback = null
            saveProgress(true)
            player.removeListener(listener)
            player.release()
            app.torrents.stop()
        }
    }

    LaunchedEffect(startStream) {
        try {
            val resume = app.library.resumeFor(info.metaId, info.videoId).let { if (it < 5_000) 0L else it }
            // Ondertitels tegelijk ophalen, terwijl de torrent start
            val subsJob = async(kotlinx.coroutines.Dispatchers.IO) {
                withTimeoutOrNull(10_000) {
                    val list = runCatching { app.stremio.subtitles(info.type, info.videoId) }.getOrNull() ?: emptyList()
                    downloadSubs(ctx, list)
                } ?: emptyList<SubtitleItem>()
            }
            var t: TorrentStream? = null
            if (race.size > 1) {
                stage = "race met ${race.size} bronnen"
                val (wi, won) = app.torrents.openRace(race.map { Triple(it.infoHash!!, it.fileIdx, it.trackers) }) { status = it }
                stream = race[wi]
                t = won
                torrent = won
                notice = "🏁 Bron ${wi + 1} was het snelst"
            } else if (stream.isTorrent) {
                t = app.torrents.open(stream.infoHash!!, stream.fileIdx, stream.trackers) { status = it }
                torrent = t
            }
            status = "Bufferen…"
            stage = "ondertitels ophalen"
            val subs = withTimeoutOrNull(1_500) { subsJob.await() } ?: emptyList<SubtitleItem>()
            if (!subsJob.isCompleted) subsJob.cancel()
            stage = "speler gestart (${subs.size} ondertitels)"
            Players.play(player, t, stream.url, subs, resume)
            if (resume > 0) notice = "Verder vanaf " + formatTime(resume)
            val langs = listOf("nl" to "NL", "en" to "EN", "es" to "ES").filter { (c, _) -> subs.any { it.lang == c } }.joinToString(", ") { it.second }
            notice = (notice?.let { "$it · " } ?: "") + if (langs.isEmpty()) "Geen losse ondertitels gevonden" else "Ondertitels gevonden: $langs"
        } catch (e: Exception) {
            error = e.message ?: "Starten mislukt"
        }
    }

    // Status tijdens bufferen, voortgang opslaan, en de knop "Volgende aflevering" in de laatste 90 seconden
    LaunchedEffect(torrent) {
        var tick = 0
        while (true) {
            torrent?.let { status = it.stats(); debug = it.debug() }
            // Na 20 s nog niets binnen → deze bron wordt niet (genoeg) gedeeld
            stalled = torrent != null && tick >= 20 && torrent!!.downloaded() == 0L && player.currentPosition == 0L
            val st = when (player.playbackState) { Player.STATE_IDLE -> "IDLE"; Player.STATE_BUFFERING -> "BUFFERING"; Player.STATE_READY -> "READY"; else -> "ENDED" }
            debug = "Fase: $stage · speler $st · ${player.currentPosition / 1000}s · info na ${app.torrents.metaSeconds}s\n" + (torrent?.debug() ?: "gewone link")
            val dur = player.duration
            showNext = next != null && dur != C.TIME_UNSET && dur > 0 && dur - player.currentPosition < 90_000
            // Host geeft elke 5 s door waar hij is, zodat iedereen gelijk blijft lopen
            if (party.active && party.amHost && tick % 5 == 4 && loadedOnce[0] && player.playbackState == Player.STATE_READY &&
                System.currentTimeMillis() > suppressUntil[0]
            ) party.sendPlayback(!player.playWhenReady, player.currentPosition / 1000.0, "sync")
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

    // Melding pas laten verdwijnen 6 s nadat het beeld er is (anders mis je hem tijdens het laden)
    LaunchedEffect(notice, buffering) {
        if (notice != null && !buffering) {
            delay(6000)
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
            InfoBox(Modifier.align(Alignment.TopStart), info.label, status + (notice?.let { "\n$it" } ?: ""))
            Text(
                debug,
                color = Color(0xFFB0B0B0),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 140.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(6.dp)).padding(8.dp),
            )
        } else if (notice != null) {
            InfoBox(Modifier.align(Alignment.TopStart), info.label, notice!!)
        }

        if (stalled && error == null) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
                    Text("Deze bron komt niet op gang", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Na 20 seconden is er nog niets binnen. Er delen te weinig mensen deze bron. Kies een bron met meer 👤.",
                        color = Z.dim,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                    LukuButton("Andere bron kiezen") { nav.back() }
                }
            }
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


/**
 * Haalt ondertitels vooraf zelf op (max. een paar seconden per stuk) en geeft lokale bestanden terug.
 * Zo hoeft de speler nooit op een trage ondertitel-server te wachten.
 */
private suspend fun downloadSubs(ctx: android.content.Context, list: List<SubtitleItem>): List<SubtitleItem> =
    kotlinx.coroutines.coroutineScope {
        val dir = java.io.File(ctx.cacheDir, "subs").apply { deleteRecursively(); mkdirs() }
        val client = com.maluku.luku.data.Net.client.newBuilder()
            .callTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        list.mapIndexed { i, s ->
            async {
                runCatching {
                    val req = okhttp3.Request.Builder().url(s.url).header("User-Agent", "TeyfeyLuku/0.1").build()
                    client.newCall(req).execute().use { res ->
                        if (!res.isSuccessful) return@runCatching null
                        val bytes = res.body?.bytes() ?: return@runCatching null
                        if (bytes.size < 50) return@runCatching null
                        val head = String(bytes, 0, minOf(bytes.size, 16))
                        val ext = if (head.contains("WEBVTT")) "vtt" else "srt"
                        val f = java.io.File(dir, "sub$i.$ext")
                        f.writeBytes(bytes)
                        s.copy(url = android.net.Uri.fromFile(f).toString())
                    }
                }.getOrNull()
            }
        }.map { it.await() }.filterNotNull()
    }

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.maluku.luku.App
import com.maluku.luku.data.StreamItem
import com.maluku.luku.player.Players
import com.maluku.luku.torrent.TorrentStream
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(nav: Nav, stream: StreamItem, title: String) {
    val ctx = LocalContext.current
    val app = App.instance
    val player = remember { Players.create(ctx) }
    var status by remember { mutableStateOf("Starten…") }
    var error by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var torrent by remember { mutableStateOf<TorrentStream?>(null) }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
            }

            override fun onPlayerError(e: PlaybackException) {
                error = "Afspelen mislukt: " + (e.cause?.message ?: e.errorCodeName)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
            app.torrents.stop()
        }
    }

    LaunchedEffect(stream) {
        try {
            if (stream.isTorrent) {
                val t = app.torrents.open(stream.infoHash!!, stream.fileIdx, stream.trackers) { status = it }
                torrent = t
                status = "Bufferen…"
                Players.playTorrent(player, t)
            } else {
                Players.playUrl(ctx, player, stream.url!!)
            }
        } catch (e: Exception) {
            error = e.message ?: "Starten mislukt"
        }
    }

    // Snelheid en aantal deelnemers tonen tijdens het bufferen
    LaunchedEffect(torrent) {
        val t = torrent ?: return@LaunchedEffect
        while (true) {
            status = t.stats()
            delay(1000)
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
            Box(
                Modifier.align(Alignment.TopStart).padding(28.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Column {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(status, color = Z.dim, fontSize = 13.sp)
                }
            }
        }
    }
}

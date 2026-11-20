package com.maluku.luku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.maluku.luku.App
import com.maluku.luku.data.Meta
import com.maluku.luku.data.MetaPreview
import com.maluku.luku.data.PlayInfo
import com.maluku.luku.data.StreamItem

// ======================= Film / serie =======================

@Composable
fun DetailScreen(nav: Nav, type: String, id: String) {
    val app = App.instance
    var meta by remember(id) { mutableStateOf<Meta?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(id, retry) {
        error = null
        try {
            meta = app.stremio.meta(type, id)
        } catch (e: Exception) {
            error = e.message ?: "Kon de informatie niet laden"
        }
    }

    val m = meta
    when {
        error != null -> ErrorBox(error!!) { retry++ }
        m == null -> Loading()
        else -> DetailContent(nav, m)
    }
}

fun formatTime(ms: Long): String {
    val t = ms / 1000
    val h = t / 3600
    val mnt = (t % 3600) / 60
    val sec = t % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, mnt, sec) else String.format("%d:%02d", mnt, sec)
}

@Composable
private fun DetailContent(nav: Nav, m: Meta) {
    val app = App.instance
    val isSeries = m.type == "series" && m.videos.isNotEmpty()
    val libItems by app.library.items.collectAsState()
    val lib = libItems.firstOrNull { it.id == m.id }
    val episodes = remember(m.id) {
        m.videos.filter { it.season > 0 }.ifEmpty { m.videos }.map { it.id to "${m.name} · ${it.label}" }
    }
    fun infoFor(videoId: String, label: String) = PlayInfo(m.type, m.id, videoId, m.name, m.poster, label, episodes)
    // Seizoen van de laatst gekeken aflevering openen
    val lastSeason = lib?.videoId?.let { vid -> m.videos.firstOrNull { it.id == vid }?.season }
    var season by remember(m.id) { mutableIntStateOf(lastSeason ?: m.seasons.firstOrNull { it > 0 } ?: m.seasons.firstOrNull() ?: 1) }

    Box(Modifier.fillMaxSize()) {
        if (m.background != null) {
            AsyncImage(
                model = m.background,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.18f),
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.fillMaxWidth()) {
                    if (m.poster != null) {
                        AsyncImage(
                            model = m.poster,
                            contentDescription = m.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(170.dp).height(255.dp).background(Z.panel2, RoundedCornerShape(12.dp)),
                        )
                        Spacer(Modifier.width(24.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(m.name, color = Z.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        val facts = listOfNotNull(m.year, m.runtime, m.imdbRating?.let { "⭐ $it" }, m.genres.take(3).joinToString(" · ").ifBlank { null })
                        Text(facts.joinToString("   "), color = Z.dim, modifier = Modifier.padding(vertical = 8.dp))
                        if (m.description != null) {
                            Text(m.description, color = Z.text, fontSize = 15.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                        }
                        if (m.cast.isNotEmpty()) {
                            Text("Met: " + m.cast.take(5).joinToString(", "), color = Z.dim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(4.dp)) {
                            item { LukuButton("←  Terug", primary = false) { nav.back() } }
                            if (!isSeries) {
                                val resume = lib?.let { app.library.resumeFor(m.id, m.id) } ?: 0L
                                item {
                                    LukuButton(if (resume > 1000) "▶  Verder kijken (${formatTime(resume)})" else "▶  Bronnen zoeken") {
                                        nav.go(Screen.Streams(infoFor(m.id, m.name)))
                                    }
                                }
                            } else {
                                val vid = lib?.videoId
                                val v = m.videos.firstOrNull { it.id == vid }
                                if (v != null) {
                                    item {
                                        LukuButton("▶  Verder: S${v.season}E${v.episode}") {
                                            nav.go(Screen.Streams(infoFor(v.id, "${m.name} · ${v.label}")))
                                        }
                                    }
                                }
                            }
                            item {
                                val inLib = lib?.inLibrary == true
                                LukuButton(if (inLib) "✓  In bibliotheek" else "＋  Bibliotheek", primary = false) {
                                    app.library.toggleLibrary(MetaPreview(m.id, m.type, m.name, m.poster, m.year))
                                }
                            }
                        }
                    }
                }
            }
            if (isSeries) {
                item {
                    SectionTitle("Seizoenen")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(4.dp)) {
                        items(m.seasons) { s ->
                            Chip(if (s == 0) "Extra's" else "Seizoen $s", selected = s == season) { season = s }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                items(m.videos.filter { it.season == season }) { v ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .tv { nav.go(Screen.Streams(infoFor(v.id, "${m.name} · ${v.label}"))) }
                            .background(Z.panel, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(140.dp).height(79.dp).background(Z.panel2, RoundedCornerShape(8.dp))) {
                            if (v.thumbnail != null) {
                                AsyncImage(model = v.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Aflevering ${v.episode}", color = Z.dim, fontSize = 12.sp)
                            Text(v.title, color = Z.text, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// ======================= Bronnen =======================

@Composable
fun StreamsScreen(nav: Nav, info: PlayInfo) {
    val type = info.type
    val id = info.videoId
    val title = info.label
    val app = App.instance
    var streams by remember(id) { mutableStateOf<List<StreamItem>?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(id, retry) {
        streams = null
        streams = runCatching { app.stremio.streams(type, id) }.getOrDefault(emptyList())
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LukuButton("←", primary = false) { nav.back() }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = Z.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Kies een bron. Kleinere bestanden starten sneller.", color = Z.dim, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        val list = streams
        when {
            list == null -> Loading("Bronnen zoeken bij je add-ons…")
            list.isEmpty() -> ErrorBox(
                "Geen bronnen gevonden. Heb je een add-on met bronnen (zoals Torrentio) in je Stremio-account?",
            ) { retry++ }
            else -> {
                val free = app.torrents.freeBytes()
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                    items(list) { s -> StreamRow(s, free) { nav.go(Screen.Player(s, info)) } }
                }
            }
        }
    }
}

@Composable
private fun StreamRow(s: StreamItem, freeBytes: Long, onClick: () -> Unit) {
    val tooBig = s.isTorrent && (s.sizeBytes ?: 0L) > freeBytes - 200L * 1024 * 1024
    Row(
        Modifier
            .fillMaxWidth()
            .tv(onClick = onClick)
            .background(Z.panel, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(64.dp, 36.dp).background(if (s.quality == "4K") Z.accent else Z.panel2, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(s.quality.ifBlank { "?" }, color = Color.White, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (if (s.isTorrent) "🧲 " else "🌐 ") + s.name,
                color = Z.text,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(s.title, color = Z.dim, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (tooBig) Text("Te groot voor de opslag van dit apparaat", color = Z.bad, fontSize = 12.sp)
        }
    }
}

package com.maluku.luku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
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
    val lastSeason = lib?.videoId?.let { vid -> m.videos.firstOrNull { it.id == vid }?.season }
    var season by remember(m.id) { mutableIntStateOf(lastSeason ?: m.seasons.firstOrNull { it > 0 } ?: m.seasons.firstOrNull() ?: 1) }
    var seasonPicker by remember { mutableStateOf(false) }
    val wide = isTv() || LocalConfiguration.current.screenWidthDp >= 840

    // Hoofdknop: Bekijken / Verder kijken
    val (playLabel, playAction) = if (!isSeries) {
        val resume = app.library.resumeFor(m.id, m.id)
        (if (resume > 1000) "▶  Verder kijken (${formatTime(resume)})" else "▶  Bekijken") to
            { nav.go(Screen.Streams(infoFor(m.id, m.name))) }
    } else {
        val v = m.videos.firstOrNull { it.id == lib?.videoId }
            ?: m.videos.firstOrNull { it.season == season } ?: m.videos.first()
        val txt = if (v.id == lib?.videoId) "▶  Verder: S${v.season}E${v.episode}" else "▶  Bekijk S${v.season}E${v.episode}"
        txt to { nav.go(Screen.Streams(infoFor(v.id, "${m.name} · ${v.label}"))) }
    }
    val inLib = lib?.inLibrary == true
    val toggleLib = { app.library.toggleLibrary(MetaPreview(m.id, m.type, m.name, m.poster, m.year)) }
    val seasonEpisodes = m.videos.filter { it.season == season }
    // Films: bronnen meteen op de infopagina, zoals Stremio
    val (movieStreams, streamsDone) = if (!isSeries) rememberStreams(m.type, m.id) else (emptyList<StreamItem>() to true)
    val movieInfo = infoFor(m.id, m.name)
    val resumeMs = if (!isSeries) app.library.resumeFor(m.id, m.id) else 0L
    val free = remember { app.torrents.freeBytes() }

    Box(Modifier.fillMaxSize().background(Z.bg)) {
        if (wide) {
            // ---------- TV / breed scherm: achtergrond over alles, info links, afleveringen rechts ----------
            AsyncImage(
                model = m.background ?: m.poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.45f),
            )
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Z.bg, Z.bg.copy(alpha = 0.6f), Color.Transparent))))
            Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
                LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(bottom = 30.dp)) {
                    item { LukuButton("←", primary = false) { nav.back() } }
                    item { Spacer(Modifier.height(20.dp)); TitleOrLogo(m, Alignment.CenterStart, 110.dp) }
                    item { InfoBlock(m, maxDesc = 6) }
                    item {
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (isSeries) LukuButton(playLabel, onClick = playAction)
                            LukuButton(if (inLib) "✓  In bibliotheek" else "＋  Bibliotheek", primary = false, onClick = toggleLib)
                        }
                        if (resumeMs > 1000) Text("Je gaat verder vanaf ${formatTime(resumeMs)}", color = Z.dim, modifier = Modifier.padding(top = 10.dp))
                    }
                }
                if (!isSeries) {
                    Spacer(Modifier.width(24.dp))
                    Column(
                        Modifier.width(480.dp).fillMaxHeight()
                            .background(Z.panel.copy(alpha = 0.92f), RoundedCornerShape(18.dp))
                            .padding(16.dp),
                    ) {
                        Text("Bronnen", color = Z.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(movieStreams) { st -> StreamRow(st, free) { nav.go(Screen.Player(st, movieInfo)) } }
                            item { StreamsFooter(movieStreams.isEmpty(), streamsDone) }
                        }
                    }
                }
                if (isSeries) {
                    Spacer(Modifier.width(24.dp))
                    Column(
                        Modifier.width(460.dp).fillMaxHeight()
                            .background(Z.panel.copy(alpha = 0.92f), RoundedCornerShape(18.dp))
                            .padding(16.dp),
                    ) {
                        DropButton(seasonLabel(season), Modifier.fillMaxWidth()) { seasonPicker = true }
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(seasonEpisodes) { v ->
                                EpisodeRow(v, current = v.id == lib?.videoId) { nav.go(Screen.Streams(infoFor(v.id, "${m.name} · ${v.label}"))) }
                            }
                        }
                    }
                }
            }
        } else {
            // ---------- Telefoon: groot beeld bovenaan, alles eronder ----------
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
                item {
                    Box(Modifier.fillMaxWidth().height(300.dp)) {
                        AsyncImage(
                            model = m.background ?: m.poster,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, Z.bg))))
                        Box(
                            Modifier.padding(14.dp).size(44.dp).tv(RoundedCornerShape(22.dp)) { nav.back() }
                                .background(Color(0x99000000), RoundedCornerShape(22.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text("←", color = Color.White, fontSize = 22.sp) }
                        Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp)) {
                            TitleOrLogo(m, Alignment.Center, 90.dp)
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 18.dp)) {
                        InfoBlock(m, maxDesc = 5, center = true)
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isSeries) {
                                LukuButton(playLabel, modifier = Modifier.weight(1f), onClick = playAction)
                            } else {
                                Column(Modifier.weight(1f)) {
                                    Text("Bronnen", color = Z.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    if (resumeMs > 1000) Text("Je gaat verder vanaf ${formatTime(resumeMs)}", color = Z.dim, fontSize = 13.sp)
                                }
                            }
                            Box(
                                Modifier.size(48.dp).tv(RoundedCornerShape(12.dp), toggleLib)
                                    .background(if (inLib) Z.accent else Z.panel2, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (inLib) "✓" else "＋", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                if (!isSeries) {
                    item { Spacer(Modifier.height(10.dp)) }
                    items(movieStreams) { st ->
                        Box(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                            StreamRow(st, free) { nav.go(Screen.Player(st, movieInfo)) }
                        }
                    }
                    item { Box(Modifier.padding(horizontal = 18.dp)) { StreamsFooter(movieStreams.isEmpty(), streamsDone) } }
                }
                if (isSeries) {
                    item {
                        Box(Modifier.padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 10.dp)) {
                            DropButton(seasonLabel(season), Modifier.fillMaxWidth()) { seasonPicker = true }
                        }
                    }
                    items(seasonEpisodes) { v ->
                        Box(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                            EpisodeRow(v, current = v.id == lib?.videoId) { nav.go(Screen.Streams(infoFor(v.id, "${m.name} · ${v.label}"))) }
                        }
                    }
                }
            }
        }
    }

    if (seasonPicker) {
        PickerDialog("Seizoen", m.seasons.map { seasonLabel(it) }, m.seasons.indexOf(season).coerceAtLeast(0), { season = m.seasons[it] }) {
            seasonPicker = false
        }
    }
}

@Composable
private fun StreamsFooter(empty: Boolean, done: Boolean) {
    val text = when {
        !done && empty -> "Bronnen zoeken bij je add-ons…"
        !done -> "Meer bronnen laden…"
        empty -> "Geen bronnen gevonden. Heb je een add-on met bronnen (zoals Torrentio) in je Stremio-account?"
        else -> null
    }
    if (text != null) Text(text, color = Z.dim, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
}

private fun seasonLabel(s: Int) = if (s == 0) "Extra's" else "Seizoen $s"

/** Logo van de film (zoals Stremio), of anders de titel in grote letters. */
@Composable
private fun TitleOrLogo(m: Meta, align: Alignment, height: androidx.compose.ui.unit.Dp) {
    var logoFailed by remember(m.id) { mutableStateOf(m.logo == null) }
    if (!logoFailed) {
        AsyncImage(
            model = m.logo,
            contentDescription = m.name,
            contentScale = ContentScale.Fit,
            alignment = align,
            onError = { logoFailed = true },
            modifier = Modifier.fillMaxWidth().height(height),
        )
    } else {
        Text(
            m.name,
            color = Z.text,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 36.sp,
            textAlign = if (align == Alignment.Center) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Duur · jaar · IMDb, genres en beschrijving. Tik op de beschrijving om alles te lezen. */
@Composable
private fun InfoBlock(m: Meta, maxDesc: Int, center: Boolean = false) {
    var expanded by remember(m.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            m.runtime?.let { Text(it, color = Z.text, fontSize = 15.sp) }
            m.year?.let { Text(it, color = Z.text, fontSize = 15.sp) }
            m.imdbRating?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(it, color = Z.text, fontSize = 15.sp)
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.background(Color(0xFFF5C518), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                        Text("IMDb", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                }
            }
        }
        if (m.genres.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(m.genres.take(4)) { g ->
                    Box(Modifier.background(Z.panel2, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(com.maluku.luku.data.Folders.genreLabel(g), color = Z.text, fontSize = 13.sp)
                    }
                }
            }
        }
        if (m.description != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                m.description,
                color = Z.text.copy(alpha = 0.9f),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                maxLines = if (expanded) Int.MAX_VALUE else maxDesc,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (center) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.tv(RoundedCornerShape(6.dp)) { expanded = !expanded },
            )
        }
        if (m.cast.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Met: " + m.cast.take(4).joinToString(", "),
                color = Z.dim,
                fontSize = 13.sp,
                textAlign = if (center) TextAlign.Center else TextAlign.Start,
            )
        }
    }
}

@Composable
private fun EpisodeRow(v: com.maluku.luku.data.Video, current: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tv(RoundedCornerShape(10.dp), onClick)
            .background(if (current) Z.accent.copy(alpha = 0.25f) else Z.panel, RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(130.dp).height(73.dp).clip(RoundedCornerShape(8.dp)).background(Z.panel2)) {
            if (v.thumbnail != null) {
                AsyncImage(model = v.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${v.episode}. ${v.title}", color = Z.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val date = v.released?.take(10)
            if (date != null) Text(date, color = Z.dim, fontSize = 12.sp)
            if (current) Text("▶ Hier was je gebleven", color = Z.accent, fontSize = 12.sp)
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
    var retry by remember { mutableIntStateOf(0) }
    val (found, done) = rememberStreams(type, id, retry)

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
        when {
            found.isEmpty() && !done -> Loading("Bronnen zoeken bij je add-ons…")
            found.isEmpty() -> ErrorBox(
                "Geen bronnen gevonden. Heb je een add-on met bronnen (zoals Torrentio) in je Stremio-account?",
            ) { retry++ }
            else -> {
                val free = remember { app.torrents.freeBytes() }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                    items(found) { s -> StreamRow(s, free) { nav.go(Screen.Player(s, info)) } }
                    if (!done) item { Text("Meer bronnen laden…", color = Z.dim, fontSize = 13.sp, modifier = Modifier.padding(8.dp)) }
                }
            }
        }
    }
}

/** Bronnen ophalen: elke add-on verschijnt zodra hij antwoordt (zoals Stremio). */
@Composable
private fun rememberStreams(type: String, id: String, retry: Int = 0): Pair<List<StreamItem>, Boolean> {
    val app = App.instance
    var list by remember(id, retry) { mutableStateOf<List<StreamItem>>(emptyList()) }
    var done by remember(id, retry) { mutableStateOf(false) }
    LaunchedEffect(id, retry) {
        list = emptyList()
        done = false
        runCatching { app.stremio.streamsProgressive(type, id) { chunk -> list = list + chunk } }
        done = true
    }
    return list to done
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
            val seeds = s.seeders
            if (s.isTorrent && seeds != null) {
                val (col, txt) = when {
                    seeds >= 50 -> Z.good to "snel"
                    seeds >= 10 -> Color(0xFFFFB347) to "gemiddeld"
                    else -> Z.bad to "traag"
                }
                Text("👤 $seeds delers · $txt", color = col, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            if (tooBig) Text("Te groot voor de opslag van dit apparaat", color = Z.bad, fontSize = 12.sp)
        }
    }
}

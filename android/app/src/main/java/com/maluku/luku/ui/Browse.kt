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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.maluku.luku.App
import com.maluku.luku.data.CatalogDef
import com.maluku.luku.data.MetaPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
private fun TopBar(nav: Nav, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        LukuButton("←", primary = false) { nav.back() }
        Spacer(Modifier.width(12.dp))
        Text(title, color = Z.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

// ======================= Bibliotheek =======================

@Composable
fun LibraryScreen(nav: Nav) {
    val app = App.instance
    val all by app.library.items.collectAsState()
    var type by remember { mutableStateOf("all") }
    LaunchedEffect(Unit) { runCatching { app.library.refresh() } }
    val list = remember(all, type) {
        app.library.library().filter { type == "all" || it.type == type }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(nav, "Bibliotheek")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip("Alles", type == "all") { type = "all" }
            Chip("Films", type == "movie") { type = "movie" }
            Chip("Series", type == "series") { type = "series" }
        }
        Spacer(Modifier.height(14.dp))
        if (list.isEmpty()) {
            Text(
                "Je bibliotheek is nog leeg. Open een film of serie en druk op \"＋ Bibliotheek\".",
                color = Z.dim,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                items(list, key = { it.id }) { li -> Poster(li.preview) { nav.go(Screen.Detail(li.type, li.id)) } }
            }
        }
    }
}

// ======================= Ontdekken =======================

@Composable
fun DiscoverScreen(nav: Nav) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf("movie") }
    val catalogs = remember(type) { app.stremio.discoverCatalogs(type) }
    var catalog by remember(type) { mutableStateOf<CatalogDef?>(catalogs.firstOrNull()) }
    var genre by remember(catalog) { mutableStateOf<String?>(catalog?.genreExtra?.takeIf { it.required }?.options?.firstOrNull()) }
    var items by remember { mutableStateOf<List<MetaPreview>?>(null) }
    var more by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }

    LaunchedEffect(catalog, genre) {
        items = null
        more = true
        val c = catalog ?: run { items = emptyList(); return@LaunchedEffect }
        val filters = genre?.let { mapOf("genre" to it) } ?: emptyMap()
        items = runCatching { app.stremio.catalog(c, filters = filters) }.getOrDefault(emptyList())
        more = c.supportsSkip && (items?.size ?: 0) > 0
    }

    fun loadMore() {
        val c = catalog ?: return
        val cur = items ?: return
        if (loadingMore || !more) return
        loadingMore = true
        scope.launch {
            val filters = genre?.let { mapOf("genre" to it) } ?: emptyMap()
            val extra = runCatching { app.stremio.catalog(c, filters = filters, skip = cur.size) }.getOrDefault(emptyList())
            val merged = (cur + extra).distinctBy { it.id }
            more = merged.size > cur.size
            items = merged
            loadingMore = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(nav, "Ontdekken")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip("Films", type == "movie") { type = "movie" }
            Chip("Series", type == "series") { type = "series" }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(4.dp)) {
            items(catalogs) { c ->
                Chip(c.name.ifBlank { c.id } + " · " + c.addonName, c == catalog) { catalog = c }
            }
        }
        val g = catalog?.genreExtra
        if (g != null) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
                if (!g.required) item { Chip("Alle genres", genre == null) { genre = null } }
                items(g.options) { opt -> Chip(opt, genre == opt) { genre = opt } }
            }
        }
        Spacer(Modifier.height(12.dp))
        val list = items
        when {
            catalogs.isEmpty() -> Text("Geen catalogi gevonden bij je add-ons.", color = Z.dim)
            list == null -> Loading()
            list.isEmpty() -> Text("Niets gevonden.", color = Z.dim)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                items(list) { m -> Poster(m) { nav.go(Screen.Detail(m.type, m.id)) } }
                if (more) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            LukuButton(if (loadingMore) "Laden…" else "Meer laden") { loadMore() }
                        }
                    }
                }
            }
        }
    }
}

// ======================= Kalender =======================

private data class CalEntry(val day: Long, val seriesId: String, val seriesName: String, val poster: String?, val label: String)

@Composable
fun CalendarScreen(nav: Nav) {
    val app = App.instance
    var entries by remember { mutableStateOf<List<CalEntry>?>(null) }

    LaunchedEffect(Unit) {
        runCatching { app.library.refresh() }
        val series = app.library.library().filter { it.type == "series" }.take(40)
        val now = System.currentTimeMillis()
        val from = now - 7L * 86_400_000
        val to = now + 30L * 86_400_000
        val found = coroutineScope {
            series.map { li ->
                async {
                    runCatching {
                        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                        val m = app.stremio.meta("series", li.id)
                        m.videos.mapNotNull { v ->
                            val d = v.released?.let { runCatching { iso.parse(it.take(19)) }.getOrNull() } ?: return@mapNotNull null
                            if (d.time !in from..to || v.season == 0) return@mapNotNull null
                            CalEntry(startOfDay(d.time), m.id, m.name, m.poster, "S${v.season}E${v.episode} · ${v.title}")
                        }
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }
        entries = found.sortedWith(compareBy({ it.day }, { it.seriesName }))
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(nav, "Kalender")
        Text("Afleveringen van de series in je bibliotheek: vorige week tot over een maand.", color = Z.dim, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        val list = entries
        when {
            list == null -> Loading("Afleveringen ophalen…")
            list.isEmpty() -> Text("Geen afleveringen in deze periode. Zet series in je bibliotheek om ze hier te zien.", color = Z.dim)
            else -> {
                val today = startOfDay(System.currentTimeMillis())
                val dayFmt = SimpleDateFormat("EEEE d MMMM", Locale("nl", "NL"))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                    list.groupBy { it.day }.forEach { (day, rows) ->
                        item(key = "d$day") {
                            val label = when (day) {
                                today -> "Vandaag"
                                today + 86_400_000 -> "Morgen"
                                today - 86_400_000 -> "Gisteren"
                                else -> dayFmt.format(Date(day)).replaceFirstChar { it.uppercase() }
                            }
                            Text(
                                label,
                                color = if (day == today) Z.focus else Z.text,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                            )
                        }
                        items(rows) { e ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .tv { nav.go(Screen.Detail("series", e.seriesId)) }
                                    .background(Z.panel, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.width(46.dp).height(69.dp).background(Z.panel2, RoundedCornerShape(6.dp))) {
                                    if (e.poster != null) AsyncImage(model = e.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(e.seriesName, color = Z.text, fontWeight = FontWeight.Bold)
                                    Text(e.label, color = Z.dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

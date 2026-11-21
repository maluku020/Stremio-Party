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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
fun TopBar(nav: Nav, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        if (nav.stack.size > 1) {
            LukuButton("←", primary = false) { nav.back() }
            Spacer(Modifier.width(12.dp))
        }
        Text(title, color = Z.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

// ======================= Bibliotheek (zoals Stremio) =======================

@Composable
private fun posterColumns(): GridCells {
    val w = LocalConfiguration.current.screenWidthDp
    return if (w < 600) GridCells.Fixed(3) else GridCells.Adaptive(150.dp)
}

@Composable
fun LibraryScreen(nav: Nav) {
    val app = App.instance
    val all by app.library.items.collectAsState()
    var typeIdx by remember { mutableIntStateOf(0) }
    var sort by remember { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf(false) }
    val types = listOf("all" to "Alles", "movie" to "Films", "series" to "Series")
    LaunchedEffect(Unit) { runCatching { app.library.refresh() } }
    val list = remember(all, typeIdx, sort) {
        val t = types[typeIdx].first
        val base = app.library.library().filter { t == "all" || it.type == t }
        when (sort) {
            1 -> base.sortedBy { it.name.lowercase() }
            2 -> base.sortedByDescending { it.name.lowercase() }
            3 -> base.filter { it.lastWatched != null }.sortedByDescending { it.lastWatched }
            else -> base // al op "recent" gesorteerd
        }
    }

    Column(Modifier.fillMaxSize()) {
        SearchBarStub(nav)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, contentPadding = PaddingValues(4.dp)) {
            item { DropButton(types[typeIdx].second, Modifier.width(150.dp)) { picker = true } }
            item { Spacer(Modifier.width(6.dp)) }
            items(listOf("Recent", "A-Z", "Z-A", "Bekeken").size) { i ->
                Chip(listOf("Recent", "A-Z", "Z-A", "Bekeken")[i], sort == i) { sort = i }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (list.isEmpty()) {
            Text("Je bibliotheek is nog leeg. Open een film of serie en druk op \"＋ Bibliotheek\".", color = Z.dim)
        } else {
            LazyVerticalGrid(
                columns = posterColumns(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(4.dp, 4.dp, 4.dp, 30.dp),
            ) {
                items(list) { li ->
                    GridPoster(li.preview, progress = if (li.timeOffset > 0) li.progress else null) {
                        nav.go(Screen.Detail(li.type, li.id))
                    }
                }
            }
        }
    }
    if (picker) {
        PickerDialog("Type", types.map { it.second }, typeIdx, { typeIdx = it }) { picker = false }
    }
}

// ======================= Ontdekken (zoals Stremio) =======================

@Composable
fun DiscoverScreen(nav: Nav) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    val wide = isTv() || LocalConfiguration.current.screenWidthDp >= 840
    val types = listOf("movie" to "Films", "series" to "Series")
    var typeIdx by remember { mutableIntStateOf(0) }
    val type = types[typeIdx].first
    val catalogs = remember(type, app.stremio.addons) { app.stremio.discoverCatalogs(type) }
    var catIdx by remember(type) { mutableIntStateOf(0) }
    val catalog = catalogs.getOrNull(catIdx)
    val genreEx = catalog?.genreExtra
    var genre by remember(catalog) { mutableStateOf(if (genreEx?.required == true) genreEx.options.firstOrNull() else null) }
    var items by remember { mutableStateOf<List<MetaPreview>?>(null) }
    var more by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<String?>(null) }
    var focused by remember { mutableStateOf<MetaPreview?>(null) }
    val grid = rememberLazyGridState()

    fun filters(): Map<String, String> {
        val g = genre ?: return emptyMap()
        return mapOf((genreEx?.name ?: "genre") to g)
    }

    LaunchedEffect(catalog, genre) {
        items = null
        more = true
        focused = null
        val c = catalog ?: run { items = emptyList(); return@LaunchedEffect }
        val list = runCatching { app.stremio.catalog(c, filters = filters()) }.getOrDefault(emptyList())
        items = list
        focused = list.firstOrNull()
        more = c.supportsSkip && list.isNotEmpty()
        grid.scrollToItem(0)
    }

    fun loadMore() {
        val c = catalog ?: return
        val cur = items ?: return
        if (loadingMore || !more) return
        loadingMore = true
        scope.launch {
            val extra = runCatching { app.stremio.catalog(c, filters = filters(), skip = cur.size) }.getOrDefault(emptyList())
            val merged = (cur + extra).distinctBy { it.id }
            more = merged.size > cur.size
            items = merged
            loadingMore = false
        }
    }

    // Automatisch meer laden als je bijna onderaan bent (geen knop nodig)
    LaunchedEffect(grid, items) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= (items?.size ?: 0) - 9) loadMore() }
    }

    Column(Modifier.fillMaxSize()) {
        SearchBarStub(nav)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(4.dp)) {
            item { DropButton(types[typeIdx].second, Modifier.width(140.dp)) { picker = "type" } }
            if (catalogs.isNotEmpty()) {
                item { DropButton(catalog?.name?.ifBlank { catalog.id } ?: "Catalogus", Modifier.width(220.dp)) { picker = "catalog" } }
            }
            if (genreEx != null) {
                item { DropButton(genre?.let { com.maluku.luku.data.Folders.genreLabel(it) } ?: "Standaard", Modifier.width(170.dp)) { picker = "genre" } }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val list = items
                when {
                    catalogs.isEmpty() -> Text("Geen catalogi gevonden bij je add-ons.", color = Z.dim)
                    list == null -> Loading()
                    list.isEmpty() -> Text("Niets gevonden.", color = Z.dim)
                    else -> LazyVerticalGrid(
                        state = grid,
                        columns = posterColumns(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(4.dp, 4.dp, 4.dp, 30.dp),
                    ) {
                        items(list) { m ->
                            GridPoster(m, onFocus = { focused = m }) { nav.go(Screen.Detail(m.type, m.id)) }
                        }
                        if (loadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text("Meer laden…", color = Z.dim, modifier = Modifier.padding(16.dp))
                            }
                        }
                    }
                }
            }
            if (wide) {
                Spacer(Modifier.width(20.dp))
                PreviewPanel(nav, focused, Modifier.width(400.dp).fillMaxHeight())
            }
        }
    }

    when (picker) {
        "type" -> PickerDialog("Type", types.map { it.second }, typeIdx, { typeIdx = it }) { picker = null }
        "catalog" -> PickerDialog(
            "Catalogus",
            catalogs.map { c -> c.name.ifBlank { c.id } + if (catalogs.count { it.name == c.name } > 1) " · ${c.addonName}" else "" },
            catIdx,
            { catIdx = it },
        ) { picker = null }
        "genre" -> {
            val g = genreEx
            if (g != null) {
                val opts = (if (g.required) emptyList() else listOf<String?>(null)) + g.options
                PickerDialog(
                    "Genre",
                    opts.map { it?.let { o -> com.maluku.luku.data.Folders.genreLabel(o) } ?: "Standaard" },
                    opts.indexOf(genre).coerceAtLeast(0),
                    { genre = opts[it] },
                ) { picker = null }
            } else picker = null
        }
    }
}

/** Infopaneel rechts (tv en brede schermen), zoals in Stremio. */
@Composable
private fun PreviewPanel(nav: Nav, item: MetaPreview?, modifier: Modifier) {
    val app = App.instance
    var meta by remember { mutableStateOf<com.maluku.luku.data.Meta?>(null) }
    LaunchedEffect(item?.id) {
        meta = null
        val it = item ?: return@LaunchedEffect
        kotlinx.coroutines.delay(350) // niet bij elke stap van de afstandsbediening laden
        meta = runCatching { app.stremio.meta(it.type, it.id) }.getOrNull()
    }
    Box(modifier.background(Z.panel, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))) {
        val bg = meta?.background ?: item?.poster
        if (bg != null) {
            AsyncImage(model = bg, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.22f))
        }
        if (item == null) return@Box
        Column(Modifier.fillMaxSize().padding(26.dp)) {
            Text(item.name, color = Z.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            val m = meta
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                m?.runtime?.let { Text(it, color = Z.text, fontSize = 16.sp) }
                (m?.year ?: item.year)?.let { Text(it, color = Z.text, fontSize = 16.sp) }
                m?.imdbRating?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(it, color = Z.text, fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.background(Color(0xFFF5C518), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                            Text("IMDb", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (m?.description != null) {
                Text(m.description, color = Z.text, fontSize = 15.sp, maxLines = 9, overflow = TextOverflow.Ellipsis)
            } else if (m == null) {
                Text("Laden…", color = Z.dim)
            }
            if (m != null && m.genres.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("GENRES", color = Z.dim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    m.genres.take(3).forEach { g ->
                        Box(Modifier.background(Z.panel2, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text(com.maluku.luku.data.Folders.genreLabel(g), color = Z.text, fontSize = 13.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            LukuButton("▶  Bekijken", modifier = Modifier.fillMaxWidth()) { nav.go(Screen.Detail(item.type, item.id)) }
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
                val dayFmt = SimpleDateFormat("EEEE d MMMM", I18n.locale)
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


// ======================= Alles zien (één catalogus) =======================

@Composable
fun CatalogAllScreen(nav: Nav, c: CatalogDef, filters: Map<String, String> = emptyMap(), title: String? = null) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    var items by remember(c) { mutableStateOf<List<MetaPreview>?>(null) }
    var more by remember(c) { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }

    LaunchedEffect(c, filters) {
        val list = runCatching { app.stremio.catalog(c, filters = filters) }.getOrDefault(emptyList())
        items = list
        more = c.supportsSkip && list.isNotEmpty()
    }

    fun loadMore() {
        val cur = items ?: return
        if (loadingMore || !more) return
        loadingMore = true
        scope.launch {
            val extra = runCatching { app.stremio.catalog(c, filters = filters, skip = cur.size) }.getOrDefault(emptyList())
            val merged = (cur + extra).distinctBy { it.id }
            more = merged.size > cur.size
            items = merged
            loadingMore = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(nav, title ?: c.title)
        val list = items
        when {
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


// ======================= Mappen (Films / Series) =======================

@Composable
private fun FolderTile(emoji: String, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tv(RoundedCornerShape(12.dp), onClick)
            .background(Z.panel, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 26.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Z.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Z.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", color = Z.dim, fontSize = 24.sp)
    }
}

/** Tab Films of Series: hoofdmappen. */
@Composable
fun FoldersHome(nav: Nav, type: String) {
    val app = App.instance
    val folders = remember(type, app.stremio.addons) { com.maluku.luku.data.Folders.build(app.stremio, type) }
    Column(Modifier.fillMaxSize()) {
        SearchBarStub(nav)
        TopBar(nav, if (type == "movie") "Films" else "Series")
        if (folders.isEmpty()) {
            Text("Nog geen catalogi gevonden. Controleer je add-ons in Instellingen.", color = Z.dim)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(280.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                items(folders) { f ->
                    FolderTile(f.emoji, f.title, "${f.entries.size} " + if (f.entries.size == 1) "lijst" else "lijsten") {
                        nav.go(Screen.Folder(type, f.id))
                    }
                }
                item {
                    FolderTile("🧭", "Zelf filteren", "Catalogus en genre kiezen") { nav.go(Screen.Discover) }
                }
            }
        }
    }
}

/** Inhoud van één map: de lijsten (catalogi of genres). */
@Composable
fun FolderScreen(nav: Nav, type: String, folderId: String) {
    val app = App.instance
    val folder = remember(type, folderId) {
        com.maluku.luku.data.Folders.build(app.stremio, type).firstOrNull { it.id == folderId }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(nav, folder?.let { "${it.emoji}  ${it.title}" } ?: "Map")
        if (folder == null) {
            Text("Deze map is leeg.", color = Z.dim)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(240.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                items(folder.entries) { e ->
                    val sub = if (e.filters.isEmpty()) e.catalog.addonName else e.catalog.name.ifBlank { e.catalog.addonName }
                    FolderTile(e.emoji, e.title, sub) {
                        nav.go(Screen.CatalogAll(e.catalog, e.filters, "${e.emoji}  ${e.title}"))
                    }
                }
            }
        }
    }
}

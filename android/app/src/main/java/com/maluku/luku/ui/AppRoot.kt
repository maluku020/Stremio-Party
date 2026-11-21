package com.maluku.luku.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maluku.luku.App
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.maluku.luku.party.PartyPlace
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.vector.ImageVector
import com.maluku.luku.data.CatalogDef
import com.maluku.luku.data.PlayInfo
import com.maluku.luku.data.StreamItem

/** De vaste onderdelen van het menu (onderaan op de telefoon, links op de tv), net als in Stremio. */
enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    DISCOVER("Ontdekken", Icons.Filled.Search),
    LIBRARY("Bibliotheek", Icons.Filled.List),
    CALENDAR("Kalender", Icons.Filled.DateRange),
    PARTY("Samen", Icons.Filled.Person),
    SETTINGS("Instellingen", Icons.Filled.Settings),
}

sealed class Screen {
    data object Login : Screen()
    data class Main(val tab: Tab = Tab.HOME) : Screen()
    data object Search : Screen()
    data object Calendar : Screen()
    data object Addons : Screen()
    data object Discover : Screen()
    data class Folder(val type: String, val folderId: String) : Screen()
    data class CatalogAll(
        val catalog: CatalogDef,
        val filters: Map<String, String> = emptyMap(),
        val title: String? = null,
    ) : Screen()
    data class Detail(val type: String, val id: String) : Screen()
    data class Streams(val info: PlayInfo) : Screen()
    data class Player(val stream: StreamItem, val info: PlayInfo, val race: List<StreamItem> = emptyList()) : Screen()
}

class Nav(start: Screen) {
    val stack = mutableStateListOf(start)
    val current: Screen get() = stack.last()
    fun go(s: Screen) { stack.add(s) }
    fun back(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
    fun reset(s: Screen) { stack.clear(); stack.add(s) }
    fun replace(s: Screen) { stack[stack.lastIndex] = s }
}

@Composable
fun AppRoot() {
    val app = App.instance
    val nav = remember { Nav(if (app.prefs.loggedIn) Screen.Main() else Screen.Login) }
    val tv = isTv()

    BackHandler(enabled = nav.stack.size > 1) { nav.back() }

    // ---- Samen kijken: navigatie delen ----
    val party = app.party
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        party.onPlace = { p ->
            scope.launch {
                when (p) {
                    is PartyPlace.Detail -> openShared(nav, Screen.Detail(p.type, p.id))
                    is PartyPlace.Streams -> openShared(nav, Screen.Streams(withEpisodes(p.info)))
                    is PartyPlace.Player -> openShared(nav, Screen.Player(p.stream, withEpisodes(p.info), p.race))
                }
            }
        }
    }
    val cur = nav.current
    LaunchedEffect(cur) {
        if (!party.active) return@LaunchedEffect
        when (cur) {
            is Screen.Detail -> party.sendPlace(PartyPlace.Detail(cur.type, cur.id))
            is Screen.Streams -> party.sendPlace(PartyPlace.Streams(cur.info))
            is Screen.Player -> party.sendPlace(PartyPlace.Player(cur.stream, cur.info, cur.race))
            else -> {}
        }
    }
    LaunchedEffect(party.notice) {
        if (party.notice != null) { delay(4500); party.notice = null }
    }

    LukuTheme {
        val screen = nav.current
        // Op een tv een rand vrijlaten, anders valt de tekst buiten beeld (overscan)
        val pad = when {
            screen is Screen.Player || screen is Screen.Detail -> Modifier
            tv -> Modifier.padding(horizontal = 48.dp, vertical = 27.dp)
            screen is Screen.Main -> Modifier
            else -> Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        }
        Box(Modifier.fillMaxSize().background(Z.bg).then(pad)) {
            when (screen) {
                Screen.Login -> LoginScreen(onDone = { nav.reset(Screen.Main()) })
                is Screen.Main -> MainShell(nav, screen.tab)
                Screen.Search -> SearchScreen(nav)
                Screen.Calendar -> CalendarScreen(nav)
                Screen.Addons -> AddonsScreen(nav)
                Screen.Discover -> DiscoverScreen(nav)
                is Screen.Folder -> FolderScreen(nav, screen.type, screen.folderId)
                is Screen.CatalogAll -> CatalogAllScreen(nav, screen.catalog, screen.filters, screen.title)
                is Screen.Detail -> DetailScreen(nav, screen.type, screen.id)
                is Screen.Streams -> StreamsScreen(nav, screen.info)
                is Screen.Player -> PlayerScreen(nav, screen.stream, screen.info, screen.race)
            }
        }
        party.notice?.let { n ->
            Box(Modifier.fillMaxSize().padding(top = 40.dp, start = 20.dp, end = 20.dp), contentAlignment = Alignment.TopCenter) {
                Text(
                    "🎉 $n",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.background(Color(0xE0241F3A), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}


/** Iets openen dat een ander in de party koos (zonder het terug te sturen). */
private fun openShared(nav: Nav, s: Screen) {
    val cur = nav.current
    // Al op dezelfde plek? Niets doen.
    if (cur is Screen.Player && s is Screen.Player && cur.info.videoId == s.info.videoId) return
    if (cur == s) return
    if (cur is Screen.Player || (cur is Screen.Streams && s is Screen.Player)) nav.replace(s) else nav.go(s)
}

/** De afleveringenlijst opnieuw ophalen (zit niet in het party-bericht), zodat "volgende aflevering" werkt. */
private suspend fun withEpisodes(info: PlayInfo): PlayInfo {
    if (info.type != "series") return info
    val m = kotlinx.coroutines.withTimeoutOrNull(4000) {
        runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { App.instance.stremio.meta(info.type, info.metaId) } }.getOrNull()
    } ?: return info
    val eps = m.videos.filter { it.season > 0 }.ifEmpty { m.videos }.map { it.id to "${m.name} · ${it.label}" }
    return info.copy(episodes = eps)
}

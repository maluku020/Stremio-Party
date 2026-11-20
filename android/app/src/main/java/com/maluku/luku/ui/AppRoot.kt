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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.maluku.luku.data.CatalogDef
import com.maluku.luku.data.PlayInfo
import com.maluku.luku.data.StreamItem

/** De vaste onderdelen van het menu (onderaan op de telefoon, links op de tv), net als in Stremio. */
enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    MOVIES("Films", Icons.Filled.PlayArrow),
    SERIES("Series", Icons.Filled.List),
    LIBRARY("Bibliotheek", Icons.Filled.Favorite),
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
    data class Player(val stream: StreamItem, val info: PlayInfo) : Screen()
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

    LukuTheme {
        val screen = nav.current
        // Op een tv een rand vrijlaten, anders valt de tekst buiten beeld (overscan)
        val pad = when {
            screen is Screen.Player -> Modifier
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
                is Screen.Player -> PlayerScreen(nav, screen.stream, screen.info)
            }
        }
    }
}

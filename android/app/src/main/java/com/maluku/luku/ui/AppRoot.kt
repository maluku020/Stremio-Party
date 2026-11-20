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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.maluku.luku.data.CatalogDef
import com.maluku.luku.data.PlayInfo
import com.maluku.luku.data.StreamItem

/** De vaste onderdelen van het menu (onderaan op de telefoon, links op de tv), net als in Stremio. */
enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    DISCOVER("Ontdekken", Icons.Filled.Star),
    LIBRARY("Bibliotheek", Icons.Filled.List),
    ADDONS("Add-ons", Icons.Filled.Build),
    SETTINGS("Instellingen", Icons.Filled.Settings),
}

sealed class Screen {
    data object Login : Screen()
    data class Main(val tab: Tab = Tab.HOME) : Screen()
    data object Search : Screen()
    data object Calendar : Screen()
    data class CatalogAll(val catalog: CatalogDef) : Screen()
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
                is Screen.CatalogAll -> CatalogAllScreen(nav, screen.catalog)
                is Screen.Detail -> DetailScreen(nav, screen.type, screen.id)
                is Screen.Streams -> StreamsScreen(nav, screen.info)
                is Screen.Player -> PlayerScreen(nav, screen.stream, screen.info)
            }
        }
    }
}

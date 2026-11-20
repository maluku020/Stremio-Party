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
import com.maluku.luku.data.PlayInfo
import com.maluku.luku.data.StreamItem

sealed class Screen {
    data object Login : Screen()
    data object Home : Screen()
    data object Search : Screen()
    data object Settings : Screen()
    data object Library : Screen()
    data object Discover : Screen()
    data object Calendar : Screen()
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
    val nav = remember { Nav(if (app.prefs.loggedIn) Screen.Home else Screen.Login) }
    val tv = isTv()

    BackHandler(enabled = nav.stack.size > 1) { nav.back() }

    LukuTheme {
        val screen = nav.current
        // Op een tv een rand vrijlaten, anders valt de tekst buiten beeld (overscan)
        val pad = if (tv && screen !is Screen.Player) Modifier.padding(horizontal = 48.dp, vertical = 27.dp) else Modifier
        Box(Modifier.fillMaxSize().background(Z.bg).then(pad)) {
            when (screen) {
                Screen.Login -> LoginScreen(onDone = { nav.reset(Screen.Home) })
                Screen.Home -> HomeScreen(nav)
                Screen.Search -> SearchScreen(nav)
                Screen.Settings -> SettingsScreen(nav)
                Screen.Library -> LibraryScreen(nav)
                Screen.Discover -> DiscoverScreen(nav)
                Screen.Calendar -> CalendarScreen(nav)
                is Screen.Detail -> DetailScreen(nav, screen.type, screen.id)
                is Screen.Streams -> StreamsScreen(nav, screen.info)
                is Screen.Player -> PlayerScreen(nav, screen.stream, screen.info)
            }
        }
    }
}

package com.maluku.luku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Hoofdscherm met vast menu, zoals Stremio:
 * telefoon = menubalk onderaan, tv = menu links. Zoekbalk bovenaan de inhoud.
 */
@Composable
fun MainShell(nav: Nav, tab: Tab) {
    val tv = isTv()
    val select: (Tab) -> Unit = { t -> if (t != tab) nav.replace(Screen.Main(t)) }
    if (tv) {
        Row(Modifier.fillMaxSize()) {
            SideRail(tab, select)
            Spacer(Modifier.width(24.dp))
            Box(Modifier.weight(1f).fillMaxHeight()) { TabContent(nav, tab) }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp)) { TabContent(nav, tab) }
            BottomBar(tab, select)
        }
    }
}

@Composable
private fun TabContent(nav: Nav, tab: Tab) {
    when (tab) {
        Tab.HOME -> HomeScreen(nav)
        Tab.DISCOVER -> DiscoverScreen(nav)
        Tab.LIBRARY -> LibraryScreen(nav)
        Tab.ADDONS -> AddonsScreen(nav)
        Tab.SETTINGS -> SettingsScreen(nav)
    }
}

@Composable
private fun BottomBar(tab: Tab, select: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Z.panel).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (t in Tab.entries) {
            val on = t == tab
            Column(
                Modifier.weight(1f).tv(RoundedCornerShape(12.dp)) { select(t) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(t.icon, contentDescription = t.label, tint = if (on) Z.accent else Z.dim, modifier = Modifier.size(26.dp))
                Text(t.label, color = if (on) Z.text else Z.dim, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SideRail(tab: Tab, select: (Tab) -> Unit) {
    Column(
        Modifier.width(96.dp).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(42.dp).background(Z.accent, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Text("M", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
        Spacer(Modifier.height(18.dp))
        for (t in Tab.entries) {
            val on = t == tab
            Column(
                Modifier
                    .width(88.dp)
                    .tv(RoundedCornerShape(12.dp)) { select(t) }
                    .background(if (on) Z.panel2 else Color.Transparent, RoundedCornerShape(12.dp))
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(t.icon, contentDescription = t.label, tint = if (on) Z.accent else Z.dim, modifier = Modifier.size(28.dp))
                Text(t.label, color = if (on) Z.text else Z.dim, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

/** Zoekbalk bovenaan (zoals Stremio). Klikken opent het zoekscherm. */
@Composable
fun SearchBarStub(nav: Nav) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isTv()) {
            Box(Modifier.size(40.dp).background(Z.accent, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text("M", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
        }
        Row(
            Modifier
                .weight(1f)
                .tv(RoundedCornerShape(26.dp)) { nav.go(Screen.Search) }
                .background(Z.panel2, RoundedCornerShape(26.dp))
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Zoek films en series…", color = Z.dim, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Search, contentDescription = "Zoeken", tint = Z.text)
        }
    }
}

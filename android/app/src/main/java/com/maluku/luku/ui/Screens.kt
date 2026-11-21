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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maluku.luku.App
import com.maluku.luku.data.CatalogDef
import com.maluku.luku.data.LibItem
import com.maluku.luku.data.MetaPreview
import kotlinx.coroutines.launch

@Composable
fun lukuFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Z.focus,
    unfocusedBorderColor = Z.panel2,
    focusedTextColor = Z.text,
    unfocusedTextColor = Z.text,
    cursorColor = Z.focus,
    focusedLabelColor = Z.focus,
    unfocusedLabelColor = Z.dim,
)

// ======================= Inloggen =======================

@Composable
fun LoginScreen(onDone: () -> Unit) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(app.prefs.email ?: "") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun login() {
        if (busy || email.isBlank() || password.isBlank()) return
        busy = true
        error = null
        scope.launch {
            try {
                app.stremio.login(email.trim(), password)
                app.stremio.refreshAddons()
                onDone()
            } catch (e: Exception) {
                error = e.message ?: "Inloggen mislukt"
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 460.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Brand()
            Text(
                "Log in met je Stremio-account. Dan staan je add-ons (zoals Torrentio) er meteen in.",
                color = Z.dim,
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-mail") },
                singleLine = true,
                colors = lukuFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Wachtwoord") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = lukuFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { login() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) Text(error!!, color = Z.bad)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LukuButton(if (busy) "Bezig…" else "Inloggen", enabled = !busy) { login() }
                LukuButton("Zonder account", primary = false) {
                    app.stremio.useGuest()
                    scope.launch {
                        runCatching { app.stremio.refreshAddons() }
                        onDone()
                    }
                }
            }
            Text(
                "Je wachtwoord wordt alleen naar Stremio gestuurd en niet bewaard.",
                color = Z.dim,
                fontSize = 12.sp,
            )
        }
    }
}

// ======================= Home =======================

@Composable
fun HomeScreen(nav: Nav) {
    val app = App.instance
    var catalogs by remember { mutableStateOf(app.stremio.homeCatalogs()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val libItems by app.library.items.collectAsState()
    val continueList = remember(libItems) { app.library.continueWatching() }

    LaunchedEffect(reload) {
        try {
            app.stremio.refreshAddons()
            catalogs = app.stremio.homeCatalogs()
            error = null
        } catch (e: Exception) {
            if (catalogs.isEmpty()) error = e.message
        }
    }
    LaunchedEffect(Unit) { runCatching { app.library.refresh() } }

    Column(Modifier.fillMaxSize()) {
        SearchBarStub(nav)
        when {
            error != null -> ErrorBox(error!!) { reload++ }
            catalogs.isEmpty() && continueList.isEmpty() -> Loading("Je add-ons laden…")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
                if (continueList.isNotEmpty()) {
                    item(key = "continue") {
                        SectionTitle("Verder kijken")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(4.dp)) {
                            items(continueList, key = { it.id }) { li ->
                                ContinueCard(li) { nav.go(Screen.Detail(li.type, li.id)) }
                            }
                        }
                    }
                }
                items(catalogs) { c -> CatalogRow(nav, c) }
            }
        }
    }
}

@Composable
fun ContinueCard(li: LibItem, onClick: () -> Unit) {
    Column(Modifier.width(130.dp)) {
        Box(
            Modifier
                .tv(RoundedCornerShape(10.dp), onClick)
                .width(130.dp)
                .height(195.dp)
                .background(Z.panel2, RoundedCornerShape(10.dp)),
        ) {
            if (li.poster != null) {
                coil.compose.AsyncImage(
                    model = li.poster,
                    contentDescription = li.name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(5.dp).background(Z.panel2))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(li.progress.coerceAtLeast(0.03f)).height(5.dp).background(Z.accent))
        }
        Text(li.name, color = Z.text, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
        val ep = li.videoId?.split(":")?.takeIf { it.size >= 3 }?.let { "S${it[1]}E${it[2]}" }
        if (ep != null) Text(ep, color = Z.dim, fontSize = 11.sp)
    }
}

@Composable
fun CatalogRow(nav: Nav, c: CatalogDef) {
    val app = App.instance
    var items by remember(c) { mutableStateOf<List<MetaPreview>?>(null) }
    LaunchedEffect(c) {
        items = runCatching { app.stremio.catalog(c) }.getOrDefault(emptyList())
    }
    val list = items
    if (list != null && list.isEmpty()) return
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionTitle(c.title) }
            Box(
                Modifier
                    .tv(RoundedCornerShape(8.dp)) { nav.go(Screen.CatalogAll(c)) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) { Text("ALLES ZIEN ›", color = Z.dim, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
        if (list == null) {
            Box(Modifier.height(60.dp)) { Text("Laden…", color = Z.dim) }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(4.dp)) {
                items(list) { m -> Poster(m, width = 140, showTitle = false) { nav.go(Screen.Detail(m.type, m.id)) } }
            }
        }
    }
}

// ======================= Zoeken =======================

@Composable
fun SearchScreen(nav: Nav) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MetaPreview>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    fun run() {
        if (query.isBlank()) return
        busy = true
        scope.launch {
            results = runCatching { app.stremio.search(query.trim()) }.getOrDefault(emptyList())
            busy = false
        }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LukuButton("←", primary = false) { nav.back() }
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Zoek een film of serie") },
                singleLine = true,
                colors = lukuFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { run() }),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            Spacer(Modifier.width(12.dp))
            LukuButton("Zoeken") { run() }
        }
        Spacer(Modifier.height(16.dp))
        val r = results
        when {
            busy -> Loading("Zoeken…")
            r == null -> Text("Typ een titel en druk op Zoeken.", color = Z.dim)
            r.isEmpty() -> Text("Niets gevonden.", color = Z.dim)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                items(r) { m -> Poster(m) { nav.go(Screen.Detail(m.type, m.id)) } }
            }
        }
    }
}

// ======================= Add-ons =======================

@Composable
fun AddonsScreen(nav: Nav) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    var addonUrl by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var addons by remember { mutableStateOf(app.stremio.addons) }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            TopBar(nav, "Add-ons (${addons.size})")
            Text(
                "Add-ons komen uit je Stremio-account. Pas je ze aan in Stremio, druk dan hier op Vernieuwen.",
                color = Z.dim,
                fontSize = 13.sp,
            )
        }
        items(addons) { a ->
            Row(
                Modifier.fillMaxWidth().background(Z.panel, RoundedCornerShape(10.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(a.name, color = Z.text, fontWeight = FontWeight.Bold)
                    Text(a.resources.joinToString { it.name }, color = Z.dim, fontSize = 12.sp)
                }
                if (a.transportUrl in app.prefs.extraAddons) {
                    LukuButton("Verwijder", primary = false) {
                        app.stremio.removeExtraAddon(a.transportUrl)
                        scope.launch { runCatching { app.stremio.refreshAddons() }; addons = app.stremio.addons }
                    }
                }
            }
        }
        item {
            SectionTitle("Add-on toevoegen via link")
            OutlinedTextField(
                value = addonUrl,
                onValueChange = { addonUrl = it },
                label = { Text("https://…/manifest.json") },
                singleLine = true,
                colors = lukuFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LukuButton("Toevoegen") {
                    scope.launch {
                        msg = try {
                            app.stremio.addAddon(addonUrl)
                            addons = app.stremio.addons
                            addonUrl = ""
                            "Toegevoegd ✓"
                        } catch (e: Exception) {
                            "Lukt niet: ${e.message}"
                        }
                    }
                }
                LukuButton("Vernieuwen", primary = false) {
                    scope.launch {
                        msg = try {
                            app.stremio.refreshAddons(); addons = app.stremio.addons; "Vernieuwd ✓"
                        } catch (e: Exception) {
                            "Lukt niet: ${e.message}"
                        }
                    }
                }
            }
            if (msg != null) Text(msg!!, color = Z.dim, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// ======================= Instellingen =======================

@Composable
fun SettingsScreen(nav: Nav) {
    val app = App.instance
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            TopBar(nav, "Instellingen")
            SectionTitle("Taal")
            androidx.compose.foundation.layout.Row {
                listOf("nl" to "🇳🇱 Nederlands", "en" to "🇬🇧 English", "es" to "🇪🇸 Español").forEach { (code, label) ->
                    Chip(label, I18n.lang == code) {
                        I18n.lang = code
                        app.prefs.lang = code
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
            SectionTitle("Account")
            Text(
                app.prefs.email?.let { "Ingelogd als $it" } ?: "Zonder account (alleen Cinemeta + zelf toegevoegde add-ons)",
                color = Z.dim,
            )
            Spacer(Modifier.height(8.dp))
            LukuButton("Uitloggen", primary = false) {
                app.prefs.logout()
                nav.reset(Screen.Login)
            }
        }
        item {
            SectionTitle("Add-ons")
            Text("${app.stremio.addons.size} add-ons uit je Stremio-account", color = Z.dim, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            LukuButton("🧩  Add-ons beheren", primary = false) { nav.go(Screen.Addons) }
        }
        item {
            SectionTitle("Opslag")
            Text(
                "Vrij op dit apparaat: " + String.format("%.1f GB", app.torrents.freeBytes() / 1e9) +
                    ". Films worden tijdelijk opgeslagen en na het kijken weer verwijderd.",
                color = Z.dim,
                fontSize = 13.sp,
            )
        }
        item {
            SectionTitle("Over")
            val ver = remember { runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrDefault("") }
            Text("Teyfey Luku $ver · gemaakt voor MALUKU", color = Z.dim, fontSize = 13.sp)
        }
    }
}

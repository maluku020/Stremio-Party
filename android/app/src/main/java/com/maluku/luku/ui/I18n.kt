package com.maluku.luku.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.util.Locale

/** Taal van de app: "nl", "en" of "es". */
object I18n {
    var lang by mutableStateOf("nl")
    val locale: Locale get() = when (lang) { "en" -> Locale.ENGLISH; "es" -> Locale("es", "ES"); else -> Locale("nl", "NL") }
}

/**
 * Alle teksten in de app gaan via deze Text, zodat ze automatisch vertaald worden.
 * (Vervangt material3.Text in dit pakket.)
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current,
) = androidx.compose.material3.Text(
    tr(text), modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration,
    textAlign, lineHeight, overflow, softWrap, maxLines, minLines, null, style,
)

/** Vertaal een Nederlandse tekst naar de gekozen taal. Onbekende teksten (zoals filmtitels) blijven hetzelfde. */
fun tr(s: String): String {
    val l = I18n.lang
    if (l == "nl" || s.isEmpty()) return s
    whole(s, l)?.let { return it }
    if (s.contains('\n')) return s.split('\n').joinToString("\n") { tr(it) }
    if (s.contains(" · ")) return s.split(" · ").joinToString(" · ") { whole(it, l) ?: it }
    return s
}

private fun whole(s: String, l: String): String? {
    val lead = s.takeWhile { it == ' ' }
    val core = s.trim()
    WORDS[core]?.let { return lead + (if (l == "en") it.first else it.second) }
    for ((re, en, es) in PATTERNS) {
        val m = re.matchEntire(core) ?: continue
        var out = if (l == "en") en else es
        m.groupValues.drop(1).forEachIndexed { i, g -> out = out.replace("$${i + 1}", tr(g)) }
        return lead + out
    }
    return null
}

private fun p(re: String, en: String, es: String) = Triple(Regex(re), en, es)

private val PATTERNS = listOf(
    p("""🏁  Race: (\d+) beste bronnen tegelijk, snelste wint""", "🏁  Race: $1 best sources at once, fastest wins", "🏁  Carrera: $1 mejores fuentes a la vez, gana la más rápida"),
    p("""⚡  Snelste bron  \((.*)\)""", "⚡  Fastest source  ($1)", "⚡  Fuente más rápida  ($1)"),
    p("""🏁 Race: (.*)""", "🏁 Race: $1", "🏁 Carrera: $1"),
    p("""(\d+): zoeken…""", "$1: searching…", "$1: buscando…"),
    p("""🏁 Bron (\d+) won!""", "🏁 Source $1 won!", "🏁 ¡Ganó la fuente $1!"),
    p("""🏁 Bron (\d+) was het snelst""", "🏁 Source $1 was the fastest", "🏁 La fuente $1 fue la más rápida"),
    p("""Torrent zoeken… \((\d+) deelnemers\)""", "Searching torrent… ($1 peers)", "Buscando torrent… ($1 pares)"),
    p("""(\d+) deelnemers""", "$1 peers", "$1 pares"),
    p("""(.*) MB binnen""", "$1 MB received", "$1 MB recibidos"),
    p("""👤 (\d+) delers""", "👤 $1 seeders", "👤 $1 compartiendo"),
    p("""Dit bestand is te groot voor de opslag van dit apparaat \((.*) nodig, (.*) vrij\)\. Kies een kleinere bron\.""",
        "This file is too big for this device's storage ($1 needed, $2 free). Pick a smaller source.",
        "Este archivo es demasiado grande para este dispositivo ($1 necesarios, $2 libres). Elige una fuente más pequeña."),
    p("""Verder vanaf (.*)""", "Resuming from $1", "Continuando desde $1"),
    p("""Je gaat verder vanaf (.*)""", "You'll continue from $1", "Continuarás desde $1"),
    p("""▶  Verder kijken \((.*)\)""", "▶  Continue watching ($1)", "▶  Seguir viendo ($1)"),
    p("""▶  Verder: (.*)""", "▶  Continue: $1", "▶  Seguir: $1"),
    p("""▶  Bekijk (.*)""", "▶  Watch $1", "▶  Ver $1"),
    p("""Seizoen (\d+)""", "Season $1", "Temporada $1"),
    p("""Aflevering (\d+)""", "Episode $1", "Episodio $1"),
    p("""Ondertitels gevonden: (.*)""", "Subtitles found: $1", "Subtítulos encontrados: $1"),
    p("""Afspelen mislukt: (.*)""", "Playback failed: $1", "Error al reproducir: $1"),
    p("""Lukt niet: (.*)""", "Didn't work: $1", "No funcionó: $1"),
    p("""Ingelogd als (.*)""", "Logged in as $1", "Sesión iniciada como $1"),
    p("""(\d+) add-ons uit je Stremio-account""", "$1 add-ons from your Stremio account", "$1 complementos de tu cuenta de Stremio"),
    p("""Add-ons \((\d+)\)""", "Add-ons ($1)", "Complementos ($1)"),
    p("""Vrij op dit apparaat: (.*)\. Films worden tijdelijk opgeslagen en na het kijken weer verwijderd\.""",
        "Free on this device: $1. Movies are stored temporarily and deleted after watching.",
        "Libre en este dispositivo: $1. Las películas se guardan temporalmente y se borran después de verlas."),
    p("""Teyfey Luku (.*) · gemaakt voor MALUKU""", "Teyfey Luku $1 · made for MALUKU", "Teyfey Luku $1 · hecho para MALUKU"),
    p("""In de party \((\d+)\)""", "In the party ($1)", "En la fiesta ($1)"),
    p("""(.*) \(jij\)""", "$1 (you)", "$1 (tú)"),
    p("""(.*) koos: (.*)""", "$1 picked: $2", "$1 eligió: $2"),
    p("""(.*) heeft gepauzeerd ⏸""", "$1 paused ⏸", "$1 pausó ⏸"),
    p("""(.*) drukte op play ▶️""", "$1 pressed play ▶️", "$1 le dio a play ▶️"),
    p("""(.*) heeft gespoeld ⏩""", "$1 skipped ⏩", "$1 adelantó ⏩"),
    p("""(.*) heeft de party verlaten""", "$1 left the party", "$1 salió de la fiesta"),
    p("""(.*) doet mee""", "$1 joined", "$1 se unió"),
    p("""Jij: (.*)""", "You: $1", "Tú: $1"),
    p("""🎉 (.*)""", "🎉 $1", "🎉 $1"),
    p("""Met: (.*)""", "With: $1", "Con: $1"),
)

private val WORDS: Map<String, Pair<String, String>> = mapOf(
    // menu
    "Home" to ("Home" to "Inicio"),
    "Ontdekken" to ("Discover" to "Descubrir"),
    "Bibliotheek" to ("Library" to "Biblioteca"),
    "Kalender" to ("Calendar" to "Calendario"),
    "Samen" to ("Together" to "Juntos"),
    "Instellingen" to ("Settings" to "Ajustes"),
    // algemeen
    "Laden…" to ("Loading…" to "Cargando…"),
    "Meer laden…" to ("Loading more…" to "Cargando más…"),
    "Meer laden" to ("Load more" to "Cargar más"),
    "Niets gevonden." to ("Nothing found." to "No se encontró nada."),
    "Er ging iets mis" to ("Something went wrong" to "Algo salió mal"),
    "Opnieuw proberen" to ("Try again" to "Intentar de nuevo"),
    "Zoeken" to ("Search" to "Buscar"),
    "Zoeken…" to ("Searching…" to "Buscando…"),
    "Zoek films en series…" to ("Search movies and series…" to "Buscar películas y series…"),
    "Zoek een film of serie" to ("Search for a movie or series" to "Busca una película o serie"),
    "Typ een titel en druk op Zoeken." to ("Type a title and press Search." to "Escribe un título y pulsa Buscar."),
    "Films" to ("Movies" to "Películas"),
    "Series" to ("Series" to "Series"),
    "Kanalen" to ("Channels" to "Canales"),
    "Alles" to ("All" to "Todo"),
    "Type" to ("Type" to "Tipo"),
    "Catalogus" to ("Catalog" to "Catálogo"),
    "Genre" to ("Genre" to "Género"),
    "GENRES" to ("GENRES" to "GÉNEROS"),
    "Genres" to ("Genres" to "Géneros"),
    "Standaard" to ("Default" to "Predeterminado"),
    "Recent" to ("Recent" to "Reciente"),
    "Bekeken" to ("Watched" to "Vistos"),
    "ALLES ZIEN ›" to ("SEE ALL ›" to "VER TODO ›"),
    "Verder kijken" to ("Continue watching" to "Seguir viendo"),
    "Je add-ons laden…" to ("Loading your add-ons…" to "Cargando tus complementos…"),
    "▶  Bekijken" to ("▶  Watch" to "▶  Ver"),
    "Geen catalogi gevonden bij je add-ons." to ("No catalogs found in your add-ons." to "No se encontraron catálogos en tus complementos."),
    "Nog geen catalogi gevonden. Controleer je add-ons in Instellingen." to ("No catalogs found yet. Check your add-ons in Settings." to "Aún no hay catálogos. Revisa tus complementos en Ajustes."),
    "Je bibliotheek is nog leeg. Open een film of serie en druk op \"＋ Bibliotheek\"." to
        ("Your library is still empty. Open a movie or series and press \"＋ Library\"." to "Tu biblioteca está vacía. Abre una película o serie y pulsa \"＋ Biblioteca\"."),
    "Zelf filteren" to ("Filter yourself" to "Filtrar"),
    "Catalogus en genre kiezen" to ("Choose catalog and genre" to "Elegir catálogo y género"),
    "Deze map is leeg." to ("This folder is empty." to "Esta carpeta está vacía."),
    // kalender
    "Afleveringen van de series in je bibliotheek: vorige week tot over een maand." to
        ("Episodes of the series in your library: from last week to a month ahead." to "Episodios de las series de tu biblioteca: desde la semana pasada hasta dentro de un mes."),
    "Afleveringen ophalen…" to ("Fetching episodes…" to "Obteniendo episodios…"),
    "Geen afleveringen in deze periode. Zet series in je bibliotheek om ze hier te zien." to
        ("No episodes in this period. Add series to your library to see them here." to "No hay episodios en este periodo. Añade series a tu biblioteca para verlos aquí."),
    "Vandaag" to ("Today" to "Hoy"),
    "Morgen" to ("Tomorrow" to "Mañana"),
    "Gisteren" to ("Yesterday" to "Ayer"),
    // detail
    "Kon de informatie niet laden" to ("Couldn't load the information" to "No se pudo cargar la información"),
    "✓  In bibliotheek" to ("✓  In library" to "✓  En la biblioteca"),
    "＋  Bibliotheek" to ("＋  Library" to "＋  Biblioteca"),
    "Bronnen" to ("Sources" to "Fuentes"),
    "Seizoen" to ("Season" to "Temporada"),
    "Extra's" to ("Extras" to "Extras"),
    "Bronnen zoeken bij je add-ons…" to ("Looking for sources in your add-ons…" to "Buscando fuentes en tus complementos…"),
    "Meer bronnen laden…" to ("Loading more sources…" to "Cargando más fuentes…"),
    "Geen bronnen gevonden. Heb je een add-on met bronnen (zoals Torrentio) in je Stremio-account?" to
        ("No sources found. Do you have an add-on with sources (like Torrentio) in your Stremio account?" to "No se encontraron fuentes. ¿Tienes un complemento con fuentes (como Torrentio) en tu cuenta de Stremio?"),
    "▶ Hier was je gebleven" to ("▶ You stopped here" to "▶ Te quedaste aquí"),
    "Kies een bron. Kleinere bestanden starten sneller." to ("Pick a source. Smaller files start faster." to "Elige una fuente. Los archivos más pequeños empiezan antes."),
    "snel" to ("fast" to "rápido"),
    "gemiddeld" to ("average" to "medio"),
    "traag" to ("slow" to "lento"),
    "Te groot voor de opslag van dit apparaat" to ("Too big for this device's storage" to "Demasiado grande para este dispositivo"),
    // speler
    "Starten…" to ("Starting…" to "Iniciando…"),
    "Bufferen…" to ("Buffering…" to "Cargando…"),
    "Torrent zoeken…" to ("Searching torrent…" to "Buscando torrent…"),
    "Torrent-info gevonden…" to ("Torrent info found…" to "Información del torrent encontrada…"),
    "Verbinden met deelnemers…" to ("Connecting to peers…" to "Conectando con pares…"),
    "Volgende aflevering zoeken…" to ("Looking for the next episode…" to "Buscando el siguiente episodio…"),
    "Volgende aflevering ▶" to ("Next episode ▶" to "Siguiente episodio ▶"),
    "Andere bron kiezen" to ("Pick another source" to "Elegir otra fuente"),
    "Deze bron komt niet op gang" to ("This source isn't starting" to "Esta fuente no arranca"),
    "Na 20 seconden is er nog niets binnen. Er delen te weinig mensen deze bron. Kies een bron met meer 👤." to
        ("Nothing received after 20 seconds. Too few people are sharing this source. Pick a source with more 👤." to "No se ha recibido nada en 20 segundos. Muy poca gente comparte esta fuente. Elige una con más 👤."),
    "Geen losse ondertitels gevonden" to ("No separate subtitles found" to "No se encontraron subtítulos aparte"),
    "Starten mislukt" to ("Starting failed" to "Error al iniciar"),
    "Geen van de bronnen kwam op gang. Probeer een andere bron." to ("None of the sources started. Try another source." to "Ninguna fuente arrancó. Prueba otra fuente."),
    "Na 2 minuten had geen enkele bron het begin van de film binnen." to ("After 2 minutes no source had the start of the movie." to "Tras 2 minutos ninguna fuente tenía el inicio de la película."),
    "Geen mensen gevonden die deze film delen. Kies een andere bron." to ("Nobody found sharing this movie. Pick another source." to "No se encontró a nadie compartiendo esta película. Elige otra fuente."),
    "Te traag: na 3 minuten nog niets binnen" to ("Too slow: nothing received after 3 minutes" to "Demasiado lento: nada recibido tras 3 minutos"),
    "Alleen de host bedient nu" to ("Only the host controls now" to "Ahora solo controla el anfitrión"),
    "Iedereen mag weer bedienen" to ("Everyone can control again" to "Todos pueden controlar de nuevo"),
    // login
    "Inloggen mislukt" to ("Login failed" to "Error al iniciar sesión"),
    "Log in met je Stremio-account. Dan staan je add-ons (zoals Torrentio) er meteen in." to
        ("Log in with your Stremio account. Your add-ons (like Torrentio) will be there right away." to "Inicia sesión con tu cuenta de Stremio. Tus complementos (como Torrentio) aparecerán al instante."),
    "E-mail" to ("Email" to "Correo"),
    "Wachtwoord" to ("Password" to "Contraseña"),
    "Bezig…" to ("Working…" to "Un momento…"),
    "Inloggen" to ("Log in" to "Iniciar sesión"),
    "Zonder account" to ("Without account" to "Sin cuenta"),
    "Je wachtwoord wordt alleen naar Stremio gestuurd en niet bewaard." to ("Your password is only sent to Stremio and not stored." to "Tu contraseña solo se envía a Stremio y no se guarda."),
    // add-ons & instellingen
    "Add-ons komen uit je Stremio-account. Pas je ze aan in Stremio, druk dan hier op Vernieuwen." to
        ("Add-ons come from your Stremio account. If you change them in Stremio, press Refresh here." to "Los complementos vienen de tu cuenta de Stremio. Si los cambias en Stremio, pulsa Actualizar aquí."),
    "Verwijder" to ("Remove" to "Eliminar"),
    "Add-on toevoegen via link" to ("Add add-on via link" to "Añadir complemento por enlace"),
    "Toevoegen" to ("Add" to "Añadir"),
    "Toegevoegd ✓" to ("Added ✓" to "Añadido ✓"),
    "Vernieuwen" to ("Refresh" to "Actualizar"),
    "Vernieuwd ✓" to ("Refreshed ✓" to "Actualizado ✓"),
    "Account" to ("Account" to "Cuenta"),
    "Zonder account (alleen Cinemeta + zelf toegevoegde add-ons)" to ("Without account (only Cinemeta + add-ons you added)" to "Sin cuenta (solo Cinemeta + complementos añadidos)"),
    "Uitloggen" to ("Log out" to "Cerrar sesión"),
    "Add-ons" to ("Add-ons" to "Complementos"),
    "🧩  Add-ons beheren" to ("🧩  Manage add-ons" to "🧩  Gestionar complementos"),
    "Opslag" to ("Storage" to "Almacenamiento"),
    "Over" to ("About" to "Acerca de"),
    "Taal" to ("Language" to "Idioma"),
    // party
    "Samen kijken" to ("Watch together" to "Ver juntos"),
    "Maak een party en stuur de code naar je vrienden. Daarna ziet iedereen hetzelfde: kiest iemand een film, dan opent hij bij iedereen. Pauzeert iemand, dan pauzeert het bij iedereen." to
        ("Create a party and send the code to your friends. Then everyone sees the same: if someone picks a movie, it opens for everyone. If someone pauses, it pauses for everyone." to
            "Crea una fiesta y envía el código a tus amigos. Así todos ven lo mismo: si alguien elige una película, se abre para todos. Si alguien pausa, se pausa para todos."),
    "Jouw naam" to ("Your name" to "Tu nombre"),
    "Nieuwe party" to ("New party" to "Nueva fiesta"),
    "Verbinden…" to ("Connecting…" to "Conectando…"),
    "🎉  Party maken" to ("🎉  Create party" to "🎉  Crear fiesta"),
    "Meedoen met een code" to ("Join with a code" to "Unirse con un código"),
    "Code, bijv. B4QD of PAPA" to ("Code, e.g. B4QD or PAPA" to "Código, p. ej. B4QD o PAPA"),
    "Eigen code (mag leeg, bijv. PAPA)" to ("Own code (optional, e.g. PAPA)" to "Código propio (opcional, p. ej. PAPA)"),
    "Eigen code moet 3 tot 12 letters of cijfers zijn" to ("Own code must be 3 to 12 letters or digits" to "El código debe tener de 3 a 12 letras o números"),
    "Deze code is al in gebruik. Kies een andere." to ("This code is already in use. Pick another one." to "Este código ya está en uso. Elige otro."),
    "Meedoen" to ("Join" to "Unirse"),
    "Verbinden met de party-server… De eerste keer kan dit tot 30 seconden duren (de server wordt dan wakker)." to
        ("Connecting to the party server… The first time this can take up to 30 seconds (the server is waking up)." to "Conectando con el servidor… La primera vez puede tardar hasta 30 segundos (el servidor se está despertando)."),
    "Verbinden met de party-server… (kan bij de eerste keer ±30 s duren)" to
        ("Connecting to the party server… (can take ±30 s the first time)" to "Conectando con el servidor… (la primera vez puede tardar ±30 s)"),
    "Party-code" to ("Party code" to "Código de la fiesta"),
    "Stuur deze code naar je vrienden" to ("Send this code to your friends" to "Envía este código a tus amigos"),
    "Opnieuw verbinden…" to ("Reconnecting…" to "Reconectando…"),
    "Bediening" to ("Controls" to "Control"),
    "Iedereen bedient" to ("Everyone controls" to "Todos controlan"),
    "Alleen ik" to ("Only me" to "Solo yo"),
    "De host bedient nu alleen. Jij kijkt mee." to ("Only the host controls now. You're watching along." to "Ahora solo controla el anfitrión. Tú miras."),
    "Hoe werkt het?" to ("How does it work?" to "¿Cómo funciona?"),
    "Ga gewoon naar Home of Ontdekken en kies iets. Wat jij opent, opent bij iedereen. Pauze, play en spoelen gaan ook bij iedereen. Iedereen haalt de film zelf binnen, dus wie trager internet heeft, kan even achterlopen; de app trekt het daarna gelijk." to
        ("Just go to Home or Discover and pick something. What you open, opens for everyone. Pause, play and skipping also happen for everyone. Everyone downloads the movie themselves, so someone with slower internet may lag a bit; the app syncs them afterwards." to
            "Ve a Inicio o Descubrir y elige algo. Lo que abras se abre para todos. Pausar, reproducir y adelantar también pasa para todos. Cada uno descarga la película, así que quien tenga internet más lento puede retrasarse un poco; la app lo sincroniza después."),
    "Chat" to ("Chat" to "Chat"),
    "Bericht" to ("Message" to "Mensaje"),
    "Stuur" to ("Send" to "Enviar"),
    "Party verlaten" to ("Leave party" to "Salir de la fiesta"),
    "Party niet gevonden. Klopt de code?" to ("Party not found. Is the code right?" to "Fiesta no encontrada. ¿El código es correcto?"),
    "Deze party is vol" to ("This party is full" to "Esta fiesta está llena"),
    "een titel" to ("a title" to "un título"),
    "Gast" to ("Guest" to "Invitado"),
    // genres en mappen
    "Actie" to ("Action" to "Acción"),
    "Avontuur" to ("Adventure" to "Aventura"),
    "Animatie" to ("Animation" to "Animación"),
    "Biografie" to ("Biography" to "Biografía"),
    "Komedie" to ("Comedy" to "Comedia"),
    "Misdaad" to ("Crime" to "Crimen"),
    "Documentaire" to ("Documentary" to "Documental"),
    "Familie" to ("Family" to "Familia"),
    "Kinderen" to ("Kids" to "Infantil"),
    "Geschiedenis" to ("History" to "Historia"),
    "Muziek" to ("Music" to "Música"),
    "Mysterie" to ("Mystery" to "Misterio"),
    "Romantiek" to ("Romance" to "Romance"),
    "Sciencefiction" to ("Science fiction" to "Ciencia ficción"),
    "Oorlog" to ("War" to "Guerra"),
    "Spelshow" to ("Game show" to "Concurso"),
    "Nieuws" to ("News" to "Noticias"),
    "Sport" to ("Sport" to "Deportes"),
    "Populair & nieuw" to ("Popular & new" to "Popular y nuevo"),
    "Streamingdiensten" to ("Streaming services" to "Servicios de streaming"),
    "Per jaar" to ("By year" to "Por año"),
    "Overig" to ("Other" to "Otros"),
    "Map" to ("Folder" to "Carpeta"),
)

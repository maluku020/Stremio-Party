package com.maluku.luku.data

/**
 * Zet alle catalogi van de add-ons in overzichtelijke mappen (zoals de landenmappen in Teyfey),
 * in plaats van één lange lijst.
 */
data class CatEntry(
    val title: String,
    val emoji: String,
    val catalog: CatalogDef,
    val filters: Map<String, String> = emptyMap(),
)

data class Folder(
    val id: String,
    val title: String,
    val emoji: String,
    val entries: List<CatEntry>,
)

object Folders {

    private val streaming = listOf(
        "netflix", "disney", "prime", "amazon", "hbo", "max ", "paramount", "hulu", "peacock",
        "apple tv", "appletv", "videoland", "viaplay", "skyshowtime", "crunchyroll", "discovery", "starz", "showtime",
    )
    private val popular = listOf(
        "trending", "popular", "populair", "top", "latest", "new", "nieuw", "cinema", "bioscoop",
        "featured", "best", "rated", "imdb", "week", "today",
    )

    /** Engelse genrenaam → (Nederlandse naam, emoji) */
    private val genres = linkedMapOf(
        "action" to ("Actie" to "💥"),
        "adventure" to ("Avontuur" to "🗺️"),
        "animation" to ("Animatie" to "🎨"),
        "anime" to ("Anime" to "🍥"),
        "biography" to ("Biografie" to "📖"),
        "comedy" to ("Komedie" to "😂"),
        "crime" to ("Misdaad" to "🕵️"),
        "documentary" to ("Documentaire" to "🎥"),
        "drama" to ("Drama" to "🎭"),
        "family" to ("Familie" to "👨‍👩‍👧"),
        "kids" to ("Kinderen" to "🧸"),
        "fantasy" to ("Fantasy" to "🧙"),
        "history" to ("Geschiedenis" to "🏛️"),
        "horror" to ("Horror" to "👻"),
        "music" to ("Muziek" to "🎵"),
        "musical" to ("Musical" to "🎶"),
        "mystery" to ("Mysterie" to "🔍"),
        "romance" to ("Romantiek" to "❤️"),
        "science fiction" to ("Sciencefiction" to "🚀"),
        "sci-fi" to ("Sciencefiction" to "🚀"),
        "thriller" to ("Thriller" to "😱"),
        "war" to ("Oorlog" to "⚔️"),
        "western" to ("Western" to "🤠"),
        "sport" to ("Sport" to "⚽"),
        "reality" to ("Reality" to "📺"),
        "talk" to ("Talkshow" to "🎙️"),
        "game show" to ("Spelshow" to "🎲"),
        "news" to ("Nieuws" to "📰"),
        "soap" to ("Soap" to "🧼"),
    )

    private fun genreKey(name: String): String? {
        val n = name.lowercase().trim()
        return genres.keys.firstOrNull { n == it || n.startsWith("$it ") || n.startsWith("$it-") }
    }

    private fun dutchGenre(key: String, fallback: String): Pair<String, String> = genres[key] ?: (fallback to "🎬")

    /** "Action" → "Actie" (onbekend: ongewijzigd). */
    fun genreLabel(opt: String): String {
        val key = genreKey(opt) ?: return opt
        return genres[key]?.first ?: opt
    }

    private fun isYear(s: String) = s.length == 4 && s.all { it.isDigit() }

    private fun streamingEmoji(n: String): String = when {
        "netflix" in n -> "🔴"
        "disney" in n -> "🏰"
        "prime" in n || "amazon" in n -> "📦"
        "hbo" in n || "max" in n -> "🟣"
        "apple" in n -> "🍏"
        else -> "📺"
    }

    fun build(repo: StremioRepo, type: String): List<Folder> {
        val cats = repo.discoverCatalogs(type)
        val pop = mutableListOf<CatEntry>()
        val stream = mutableListOf<CatEntry>()
        val genreMap = LinkedHashMap<String, CatEntry>()
        val years = LinkedHashMap<String, CatEntry>()
        val other = mutableListOf<CatEntry>()

        for (c in cats) {
            val name = c.name.ifBlank { c.id }
            val low = " ${name.lowercase()} "
            val g = genreKey(name)
            val ge = c.genreExtra
            val numericOptions = ge != null && ge.options.count { isYear(it) } > ge.options.size / 2
            when {
                streaming.any { it.trim() in low && (it != "max " || low.contains(" max ")) } ->
                    stream += CatEntry(name, streamingEmoji(low), c)
                g != null -> {
                    val (nl, emoji) = dutchGenre(g, name)
                    genreMap.putIfAbsent(nl, CatEntry(nl, emoji, c))
                }
                numericOptions -> {
                    for (opt in ge!!.options) years.putIfAbsent(opt, CatEntry(opt, "📅", c, mapOf(ge.name to opt)))
                }
                popular.any { it in low } -> pop += CatEntry(name, "🔥", c)
                else -> other += CatEntry(name, "📦", c)
            }
            // Genre-keuzes van deze catalogus worden losse genremappen
            if (ge != null && !numericOptions) {
                for (opt in ge.options) {
                    val key = genreKey(opt) ?: opt.lowercase()
                    val (nl, emoji) = dutchGenre(key, opt)
                    genreMap.putIfAbsent(nl, CatEntry(nl, emoji, c, mapOf(ge.name to opt)))
                }
            }
        }

        val out = mutableListOf<Folder>()
        if (pop.isNotEmpty()) out += Folder("popular", "Populair & nieuw", "🔥", pop)
        if (stream.isNotEmpty()) out += Folder("streaming", "Streamingdiensten", "📺", stream)
        if (genreMap.isNotEmpty()) out += Folder("genres", "Genres", "🎭", genreMap.values.sortedBy { it.title })
        if (years.isNotEmpty()) out += Folder("years", "Per jaar", "📅", years.values.sortedByDescending { it.title })
        if (other.isNotEmpty()) out += Folder("other", "Overig", "📦", other)
        return out
    }
}

package com.maluku.luku.data

import org.json.JSONArray
import org.json.JSONObject

/** Eén resource uit een add-on-manifest, bijv. "stream" voor ["movie","series"] met idPrefixes ["tt"]. */
data class Resource(val name: String, val types: List<String>?, val idPrefixes: List<String>?)

/** Een filter van een catalogus, bijv. genre met opties ["Action","Comedy",...]. */
data class ExtraDef(val name: String, val options: List<String>, val required: Boolean)

/** Een catalogus die de app als rij kan tonen. */
data class CatalogDef(
    val addonName: String,
    val base: String,
    val type: String,
    val id: String,
    val name: String,
    val needsExtra: Boolean,
    val supportsSearch: Boolean,
    val extras: List<ExtraDef> = emptyList(),
) {
    val genreExtra: ExtraDef? get() = extras.firstOrNull { it.name == "genre" && it.options.isNotEmpty() }
    val supportsSkip: Boolean get() = extras.any { it.name == "skip" }

    val title: String get() = if (name.isNotBlank()) "$name · ${typeLabel(type)}" else typeLabel(type)
}

data class Addon(
    val id: String,
    val name: String,
    val transportUrl: String,
    val types: List<String>,
    val idPrefixes: List<String>?,
    val resources: List<Resource>,
    val catalogs: List<CatalogDef>,
) {
    val base: String get() = transportUrl.removeSuffix("/manifest.json").removeSuffix("/")

    fun supports(resource: String, type: String, id: String): Boolean {
        val r = resources.firstOrNull { it.name == resource } ?: return false
        val rTypes = r.types ?: types
        if (rTypes.isNotEmpty() && type !in rTypes) return false
        val prefixes = r.idPrefixes ?: idPrefixes
        if (prefixes != null && prefixes.isNotEmpty() && prefixes.none { id.startsWith(it) }) return false
        return true
    }
}

data class MetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val year: String?,
)

data class Video(
    val id: String,
    val title: String,
    val season: Int,
    val episode: Int,
    val thumbnail: String?,
    val released: String? = null,
) {
    val label: String get() = if (season > 0) "S${season}E$episode · $title" else title
}

/** Een ondertitel van een add-on (bijv. OpenSubtitles). */
data class SubtitleItem(val url: String, val lang: String, val addonName: String)

/**
 * Alles wat de speler moet weten over wat er speelt:
 * welke film/serie, welke aflevering, en welke afleveringen daarna komen.
 */
data class PlayInfo(
    val type: String,
    val metaId: String,
    val videoId: String,
    val name: String,
    val poster: String?,
    val label: String,
    val episodes: List<Pair<String, String>> = emptyList(), // (videoId, label) op volgorde
) {
    val next: Pair<String, String>?
        get() {
            val i = episodes.indexOfFirst { it.first == videoId }
            return if (i >= 0 && i + 1 < episodes.size) episodes[i + 1] else null
        }
}

data class Meta(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val background: String?,
    val logo: String? = null,
    val description: String?,
    val year: String?,
    val runtime: String?,
    val genres: List<String>,
    val cast: List<String>,
    val imdbRating: String?,
    val videos: List<Video>,
) {
    val seasons: List<Int> get() = videos.map { it.season }.distinct().sortedWith(compareBy({ it == 0 }, { it }))
}

data class StreamItem(
    val addonName: String,
    val name: String,
    val title: String,
    val url: String?,
    val infoHash: String?,
    val fileIdx: Int?,
    val trackers: List<String>,
    val fileName: String?,
) {
    val isTorrent: Boolean get() = url == null && infoHash != null
    val sizeText: String? get() = Regex("""💾\s*([\d.,]+\s*[KMGT]B)""").find(title)?.groupValues?.get(1)
    val sizeBytes: Long?
        get() {
            val m = Regex("""([\d.,]+)\s*([KMGT])B""").find(sizeText ?: return null) ?: return null
            val n = m.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return null
            val mult = when (m.groupValues[2]) { "K" -> 1e3; "M" -> 1e6; "G" -> 1e9; else -> 1e12 }
            return (n * mult).toLong()
        }
    val quality: String
        get() {
            val t = "$name $title".uppercase()
            return when {
                "2160" in t || "4K" in t || "UHD" in t -> "4K"
                "1080" in t -> "1080p"
                "720" in t -> "720p"
                "480" in t -> "480p"
                else -> ""
            }
        }
}

fun typeLabel(type: String) = when (type) {
    "movie" -> "Films"
    "series" -> "Series"
    "channel" -> "Kanalen"
    "tv" -> "TV"
    else -> type.replaceFirstChar { it.uppercase() }
}

// ---------- JSON-hulpjes ----------

fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it, null) }
}

fun JSONObject.str(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

fun parseManifest(transportUrl: String, m: JSONObject): Addon {
    val types = m.optJSONArray("types").strings()
    val idPrefixes = if (m.has("idPrefixes")) m.optJSONArray("idPrefixes").strings() else null
    val resources = mutableListOf<Resource>()
    m.optJSONArray("resources")?.let { arr ->
        for (i in 0 until arr.length()) {
            val r = arr.get(i)
            if (r is String) resources += Resource(r, null, null)
            else if (r is JSONObject) resources += Resource(
                r.optString("name"),
                if (r.has("types")) r.optJSONArray("types").strings() else null,
                if (r.has("idPrefixes")) r.optJSONArray("idPrefixes").strings() else null,
            )
        }
    }
    val name = m.optString("name", "Add-on")
    val base = transportUrl.removeSuffix("/manifest.json").removeSuffix("/")
    val catalogs = mutableListOf<CatalogDef>()
    m.optJSONArray("catalogs")?.let { arr ->
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val requiredNames = c.optJSONArray("extraRequired").strings().toMutableSet()
            var search = c.optJSONArray("extraSupported").strings().contains("search")
            val extras = mutableListOf<ExtraDef>()
            c.optJSONArray("extra")?.let { ex ->
                for (j in 0 until ex.length()) {
                    val e = ex.optJSONObject(j) ?: continue
                    val en = e.optString("name")
                    if (en == "search") search = true
                    if (e.optBoolean("isRequired", false)) requiredNames += en
                    extras += ExtraDef(en, e.optJSONArray("options").strings(), e.optBoolean("isRequired", false))
                }
            }
            for (en in c.optJSONArray("extraSupported").strings()) {
                if (extras.none { it.name == en }) extras += ExtraDef(en, emptyList(), en in requiredNames)
            }
            val needsExtra = requiredNames.isNotEmpty()
            catalogs += CatalogDef(
                addonName = name,
                base = base,
                type = c.optString("type"),
                id = c.optString("id"),
                name = c.optString("name", ""),
                needsExtra = needsExtra,
                supportsSearch = search,
                extras = extras,
            )
        }
    }
    return Addon(m.optString("id"), name, transportUrl, types, idPrefixes, resources, catalogs)
}

fun parsePreview(o: JSONObject): MetaPreview? {
    val id = o.str("id") ?: o.str("imdb_id") ?: return null
    return MetaPreview(
        id = id,
        type = o.optString("type", "movie"),
        name = o.optString("name", "?"),
        poster = o.str("poster"),
        year = o.str("releaseInfo") ?: o.str("year"),
    )
}

fun parseMeta(o: JSONObject): Meta {
    val videos = mutableListOf<Video>()
    o.optJSONArray("videos")?.let { arr ->
        for (i in 0 until arr.length()) {
            val v = arr.optJSONObject(i) ?: continue
            val id = v.str("id") ?: continue
            videos += Video(
                id = id,
                title = v.str("title") ?: v.str("name") ?: "Aflevering ${v.optInt("episode")}",
                season = v.optInt("season", 0),
                episode = v.optInt("episode", v.optInt("number", 0)),
                thumbnail = v.str("thumbnail"),
                released = v.str("released") ?: v.str("firstAired"),
            )
        }
    }
    return Meta(
        id = o.optString("id"),
        type = o.optString("type", "movie"),
        name = o.optString("name", "?"),
        poster = o.str("poster"),
        background = o.str("background"),
        logo = o.str("logo"),
        description = o.str("description"),
        year = o.str("releaseInfo") ?: o.str("year"),
        runtime = o.str("runtime"),
        genres = o.optJSONArray("genres").strings().ifEmpty { o.optJSONArray("genre").strings() },
        cast = o.optJSONArray("cast").strings(),
        imdbRating = o.str("imdbRating"),
        videos = videos.sortedWith(compareBy({ it.season }, { it.episode })),
    )
}

fun parseStream(addonName: String, o: JSONObject): StreamItem? {
    val url = o.str("url")
    val hash = o.str("infoHash")
    if (url == null && hash == null) return null
    val trackers = o.optJSONArray("sources").strings()
        .filter { it.startsWith("tracker:") }
        .map { it.removePrefix("tracker:") }
    val hints = o.optJSONObject("behaviorHints")
    return StreamItem(
        addonName = addonName,
        name = o.optString("name", addonName).replace("\n", " "),
        title = (o.str("title") ?: o.str("description") ?: "").trim(),
        url = url,
        infoHash = hash?.lowercase(),
        fileIdx = if (o.has("fileIdx") && !o.isNull("fileIdx")) o.optInt("fileIdx") else null,
        trackers = trackers,
        fileName = hints?.str("filename"),
    )
}

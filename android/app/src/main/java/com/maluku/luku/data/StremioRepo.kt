package com.maluku.luku.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Praat met het Stremio-account (api.strem.io) en met de add-ons zelf.
 * Het add-on-protocol van Stremio is openbaar: elke add-on is gewoon een webadres met JSON.
 */
class StremioRepo(private val prefs: Prefs) {

    companion object {
        const val API = "https://api.strem.io/api"
        const val CINEMETA = "https://v3-cinemeta.strem.io/manifest.json"
    }

    @Volatile
    var addons: List<Addon> = emptyList()
        private set

    // ---------- Account ----------

    suspend fun login(email: String, password: String) {
        val body = JSONObject()
            .put("type", "Login")
            .put("email", email)
            .put("password", password)
            .put("facebook", false)
        val res = Net.postJson("$API/login", body)
        res.optJSONObject("error")?.let { throw IOException(it.optString("message", "Inloggen mislukt")) }
        val key = res.optJSONObject("result")?.str("authKey")
            ?: throw IOException("Inloggen mislukt (geen sleutel ontvangen)")
        prefs.authKey = key
        prefs.email = email
        prefs.guest = false
    }

    fun useGuest() {
        prefs.guest = true
        prefs.authKey = null
    }

    // ---------- Add-ons ----------

    /** Laadt eerst snel uit de cache; [refresh] haalt de nieuwste lijst op. */
    fun loadCached() {
        val cached = prefs.addonCache ?: return
        runCatching { addons = parseCollection(JSONArray(cached)) }
    }

    suspend fun refreshAddons() {
        val list = JSONArray()
        val key = prefs.authKey
        if (key != null) {
            val body = JSONObject()
                .put("type", "AddonCollectionGet")
                .put("authKey", key)
                .put("update", true)
            val res = Net.postJson("$API/addonCollectionGet", body)
            res.optJSONObject("error")?.let { throw IOException(it.optString("message", "Add-ons ophalen mislukt")) }
            val arr = res.optJSONObject("result")?.optJSONArray("addons") ?: JSONArray()
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.put(it) }
        } else {
            // Zonder account: Cinemeta (catalogus + info)
            list.put(JSONObject().put("transportUrl", CINEMETA).put("manifest", Net.getJson(CINEMETA)))
        }
        // Zelf toegevoegde add-ons
        for (url in prefs.extraAddons) {
            if ((0 until list.length()).any { list.getJSONObject(it).optString("transportUrl") == url }) continue
            runCatching { list.put(JSONObject().put("transportUrl", url).put("manifest", Net.getJson(url))) }
        }
        prefs.addonCache = list.toString()
        addons = parseCollection(list)
    }

    private fun parseCollection(arr: JSONArray): List<Addon> {
        val out = mutableListOf<Addon>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.str("transportUrl") ?: continue
            val m = o.optJSONObject("manifest") ?: continue
            runCatching { out += parseManifest(url, m) }
        }
        return out
    }

    suspend fun addAddon(url: String) {
        val clean = url.trim().replace("stremio://", "https://")
        val full = if (clean.endsWith("manifest.json")) clean else clean.trimEnd('/') + "/manifest.json"
        Net.getJson(full) // controleren of hij bestaat
        prefs.extraAddons = (prefs.extraAddons + full).distinct()
        refreshAddons()
    }

    fun removeExtraAddon(url: String) {
        prefs.extraAddons = prefs.extraAddons - url
    }

    // ---------- Catalogi ----------

    fun homeCatalogs(): List<CatalogDef> =
        addons.flatMap { it.catalogs }
            .filter { !it.needsExtra && it.type in listOf("movie", "series") }
            .take(12)

    fun searchCatalogs(): List<CatalogDef> =
        addons.flatMap { it.catalogs }.filter { it.supportsSearch && it.type in listOf("movie", "series") }

    suspend fun catalog(c: CatalogDef, search: String? = null): List<MetaPreview> {
        val extra = if (search != null) "/search=" + enc(search) else ""
        val json = Net.getJson("${c.base}/catalog/${c.type}/${c.id}$extra.json")
        val arr = json.optJSONArray("metas") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::parsePreview) }
    }

    suspend fun search(query: String): List<MetaPreview> = coroutineScope {
        searchCatalogs().map { c ->
            async { withTimeoutOrNull(15_000) { runCatching { catalog(c, query) }.getOrNull() } ?: emptyList() }
        }.awaitAll().flatten().distinctBy { it.id }
    }

    // ---------- Info en bronnen ----------

    suspend fun meta(type: String, id: String): Meta {
        val candidates = addons.filter { it.supports("meta", type, id) }
            .ifEmpty { listOf(parseManifest(CINEMETA, JSONObject().put("name", "Cinemeta").put("types", JSONArray().put(type)).put("resources", JSONArray().put("meta")))) }
        var last: Exception? = null
        for (a in candidates) {
            try {
                val json = Net.getJson("${a.base}/meta/$type/${id}.json")
                val m = json.optJSONObject("meta") ?: continue
                return parseMeta(m)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("Geen informatie gevonden")
    }

    /** Vraagt alle add-ons met "stream" om bronnen, tegelijk, en voegt alles samen. */
    suspend fun streams(type: String, id: String): List<StreamItem> = coroutineScope {
        addons.filter { it.supports("stream", type, id) }.map { a ->
            async {
                withTimeoutOrNull(25_000) {
                    runCatching {
                        val json = Net.getJson("${a.base}/stream/$type/${id}.json")
                        val arr = json.optJSONArray("streams") ?: JSONArray()
                        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let { o -> parseStream(a.name, o) } }
                    }.getOrNull()
                } ?: emptyList()
            }
        }.awaitAll().flatten()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

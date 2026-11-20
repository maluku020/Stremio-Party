package com.maluku.luku.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Eén item uit de bibliotheek (zelfde als in de Stremio-apps). */
data class LibItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val removed: Boolean,
    val temp: Boolean,
    val mtime: String,
    val timeOffset: Long,
    val duration: Long,
    val videoId: String?,
    val lastWatched: String?,
) {
    val inLibrary: Boolean get() = !removed && !temp
    val progress: Float get() = if (duration > 0) (timeOffset.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val preview: MetaPreview get() = MetaPreview(id, type, name, poster, null)
}

/**
 * Bibliotheek en "Verder kijken".
 * Met een Stremio-account wordt alles gesynchroniseerd via de officiële datastore (collection "libraryItem"),
 * zodat het ook in de gewone Stremio-apps klopt. Zonder account blijft het alleen op dit apparaat.
 *
 * Belangrijk: bestaande items worden als ruwe JSON bewaard en alleen de velden die wij veranderen worden aangepast,
 * zodat we nooit velden weggooien die andere Stremio-apps nodig hebben.
 */
class Library(ctx: Context, private val repo: StremioRepo) {
    private val sp = ctx.getSharedPreferences("luku_library", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val raw = LinkedHashMap<String, JSONObject>()
    private val _items = MutableStateFlow<List<LibItem>>(emptyList())
    val items: StateFlow<List<LibItem>> = _items

    init {
        runCatching {
            val arr = JSONArray(sp.getString("items", "[]"))
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { o -> o.str("_id")?.let { raw[it] = o } }
        }
        publish()
    }

    // ---------- Lezen ----------

    fun continueWatching(): List<LibItem> =
        _items.value.filter { (!it.removed || it.temp) && it.timeOffset > 0 && it.type in listOf("movie", "series") }
            .sortedByDescending { it.lastWatched ?: "" }

    fun library(): List<LibItem> = _items.value.filter { it.inLibrary }.sortedByDescending { it.mtime }

    fun get(id: String): LibItem? = _items.value.firstOrNull { it.id == id }

    /** Hervat-positie in ms voor deze aflevering/film, of 0. */
    fun resumeFor(metaId: String, videoId: String): Long {
        val it = get(metaId) ?: return 0
        if (it.videoId != null && it.videoId != videoId) return 0
        return if (it.duration > 0 && it.timeOffset > it.duration * 0.95) 0 else it.timeOffset
    }

    // ---------- Synchroniseren ----------

    suspend fun refresh() {
        val key = repo.authKey ?: return
        val body = JSONObject()
            .put("authKey", key)
            .put("collection", "libraryItem")
            .put("ids", JSONArray())
            .put("all", true)
        val res = Net.postJson("${StremioRepo.API}/datastoreGet", body)
        val arr = res.optJSONArray("result") ?: return
        synchronized(raw) {
            raw.clear()
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { o -> o.str("_id")?.let { raw[it] = o } }
        }
        save()
        publish()
    }

    private fun push(item: JSONObject) {
        val key = repo.authKey ?: return
        scope.launch {
            runCatching {
                val body = JSONObject()
                    .put("authKey", key)
                    .put("collection", "libraryItem")
                    .put("changes", JSONArray().put(item))
                Net.postJson("${StremioRepo.API}/datastorePut", body)
            }
        }
    }

    // ---------- Aanpassen ----------

    fun toggleLibrary(info: MetaPreview) {
        val o = getOrCreate(info.id, info.type, info.name, info.poster)
        val inLib = !o.optBoolean("removed", false) && !o.optBoolean("temp", false)
        o.put("removed", inLib)
        o.put("temp", false)
        touch(o)
        commit(o)
    }

    /**
     * Voortgang opslaan. [posMs]/[durMs] in milliseconden.
     * Bij >90% gekeken: de film/aflevering telt als gezien en Verder kijken springt naar de volgende aflevering.
     */
    fun updateProgress(info: PlayInfo, posMs: Long, durMs: Long, watchedMs: Long, pushNow: Boolean) {
        if (durMs <= 0) return
        val o = getOrCreate(info.metaId, info.type, info.name, info.poster)
        val state = o.optJSONObject("state") ?: newState().also { o.put("state", it) }
        val finished = posMs > durMs * 0.9
        state.put("lastWatched", now())
        state.put("timeWatched", state.optLong("timeWatched") + watchedMs)
        state.put("overallTimeWatched", state.optLong("overallTimeWatched") + watchedMs)
        state.put("duration", durMs)
        if (finished) {
            state.put("timesWatched", state.optInt("timesWatched") + 1)
            state.put("flaggedWatched", 1)
            val next = info.next
            if (next != null) {
                state.put("video_id", next.first)
                state.put("timeOffset", 1) // 1 ms: verschijnt in Verder kijken bij de volgende aflevering
                state.put("duration", 0)
            } else {
                state.put("video_id", info.videoId)
                state.put("timeOffset", 0)
            }
        } else {
            state.put("video_id", info.videoId)
            state.put("timeOffset", posMs)
        }
        touch(o)
        commit(o, push = pushNow)
    }

    fun removeFromContinue(id: String) {
        val o = synchronized(raw) { raw[id] } ?: return
        o.optJSONObject("state")?.put("timeOffset", 0)
        touch(o)
        commit(o)
    }

    // ---------- Intern ----------

    private fun getOrCreate(id: String, type: String, name: String, poster: String?): JSONObject = synchronized(raw) {
        raw[id] ?: JSONObject().apply {
            put("_id", id)
            put("name", name)
            put("type", type)
            put("poster", poster ?: JSONObject.NULL)
            put("posterShape", "poster")
            put("removed", false)
            put("temp", true)
            put("_ctime", now())
            put("_mtime", now())
            put("state", newState())
            put(
                "behaviorHints",
                JSONObject()
                    .put("defaultVideoId", JSONObject.NULL)
                    .put("featuredVideoId", JSONObject.NULL)
                    .put("hasScheduledVideos", false),
            )
        }.also { raw[id] = it }
    }

    private fun newState() = JSONObject()
        .put("lastWatched", JSONObject.NULL)
        .put("timeWatched", 0)
        .put("timeOffset", 0)
        .put("overallTimeWatched", 0)
        .put("timesWatched", 0)
        .put("flaggedWatched", 0)
        .put("duration", 0)
        .put("video_id", JSONObject.NULL)
        .put("watched", JSONObject.NULL)
        .put("noNotif", false)

    private fun touch(o: JSONObject) {
        o.put("_mtime", now())
    }

    private fun commit(o: JSONObject, push: Boolean = true) {
        save()
        publish()
        if (push) push(o)
    }

    private fun save() {
        val arr = JSONArray()
        synchronized(raw) { raw.values.forEach { arr.put(it) } }
        sp.edit().putString("items", arr.toString()).apply()
    }

    private fun publish() {
        val list = synchronized(raw) { raw.values.mapNotNull { parse(it) } }
        _items.value = list
    }

    private fun parse(o: JSONObject): LibItem? {
        val st = o.optJSONObject("state") ?: JSONObject()
        return LibItem(
            id = o.str("_id") ?: return null,
            type = o.optString("type"),
            name = o.optString("name"),
            poster = o.str("poster"),
            removed = o.optBoolean("removed", false),
            temp = o.optBoolean("temp", false),
            mtime = o.optString("_mtime", ""),
            timeOffset = st.optLong("timeOffset", 0),
            duration = st.optLong("duration", 0),
            videoId = st.str("video_id"),
            lastWatched = st.str("lastWatched"),
        )
    }

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
}

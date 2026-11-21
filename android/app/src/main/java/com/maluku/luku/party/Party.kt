package com.maluku.luku.party

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.maluku.luku.data.Net
import com.maluku.luku.data.PlayInfo
import com.maluku.luku.data.StreamItem
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class PartyMember(val id: String, val name: String, val host: Boolean)

/** Een gedeelde plek in de app (wat iedereen in de party ziet). */
sealed class PartyPlace {
    data class Detail(val type: String, val id: String) : PartyPlace()
    data class Streams(val info: PlayInfo) : PartyPlace()
    data class Player(val stream: StreamItem, val info: PlayInfo, val race: List<StreamItem>) : PartyPlace()

    /** Kort kenmerk: dezelfde plek = hetzelfde kenmerk (om dubbel sturen te voorkomen). */
    val key: String
        get() = when (this) {
            is Detail -> "d|$type|$id"
            is Streams -> "s|${info.videoId}"
            is Player -> "p|${info.videoId}"
        }
}

/**
 * Samen kijken: verbinding met de party-server (dezelfde als de pc-versie).
 * Iedereen kan bladeren, kiezen, pauzeren en spoelen; dat gebeurt dan bij iedereen.
 */
class Party {
    var serverUrl = "wss://party-server-cvcz.onrender.com"

    var code by mutableStateOf<String?>(null); private set
    var connected by mutableStateOf(false); private set
    var connecting by mutableStateOf(false); private set
    var myId by mutableStateOf<String?>(null); private set
    var hostOnly by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
    val members = mutableStateListOf<PartyMember>()
    val chat = mutableStateListOf<String>()

    val active: Boolean get() = code != null
    val amHost: Boolean get() = members.any { it.id == myId && it.host }
    val canControl: Boolean get() = !hostOnly || amHost

    /** Afspeelstatus van de groep (tijd in seconden). */
    var playback: JSONObject? = null; private set

    /** Wordt gezet door het scherm dat de navigatie regelt. */
    var onPlace: ((PartyPlace) -> Unit)? = null
    /** Wordt gezet door de speler. */
    var onPlayback: ((paused: Boolean, time: Double, action: String, by: String?) -> Unit)? = null

    var lastPlaceKey: String? = null

    private val main = Handler(Looper.getMainLooper())
    private val client = Net.client.newBuilder().pingInterval(20, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()
    private var ws: WebSocket? = null
    private var wantRoom = false
    private var name = "Gast"
    private var retry = 0
    private val queue = mutableListOf<JSONObject>()

    fun create(myName: String, ownCode: String = "") {
        name = myName.ifBlank { "Gast" }
        wantRoom = true
        error = null
        val msg = JSONObject().put("type", "create").put("name", name)
        val own = ownCode.uppercase().filter { it.isLetterOrDigit() }
        if (own.isNotEmpty()) msg.put("code", own)
        send(msg)
    }

    fun join(joinCode: String, myName: String) {
        name = myName.ifBlank { "Gast" }
        wantRoom = true
        error = null
        code = null
        // Streepje en "MLK" mogen weg: "b4qd", "mlkb4qd" en "MLK-B4QD" werken allemaal
        val raw = joinCode.uppercase().filter { it.isLetterOrDigit() }
        val core = if (raw.length == 7 && raw.startsWith("MLK")) raw.drop(3) else raw
        pendingCode = core
        send(JSONObject().put("type", "join").put("code", pendingCode).put("name", name))
    }

    private var pendingCode: String? = null

    fun leave() {
        wantRoom = false
        runCatching { ws?.send(JSONObject().put("type", "leave").toString()) }
        code = null
        members.clear()
        playback = null
        lastPlaceKey = null
        runCatching { ws?.close(1000, "bye") }
        ws = null
        connected = false
    }

    fun changeHostOnly(v: Boolean) = send(JSONObject().put("type", "hostOnly").put("value", v))

    fun sendChat(text: String) {
        if (text.isBlank()) return
        send(JSONObject().put("type", "chat").put("text", text))
        chat.add("Jij: $text")
    }

    /** Eigen navigatie doorgeven. */
    fun sendPlace(p: PartyPlace) {
        if (!active || !canControl) return
        if (p.key == lastPlaceKey) return
        lastPlaceKey = p.key
        playback = null
        send(JSONObject().put("type", "nav").put("path", encode(p)))
    }

    fun sendPlayback(paused: Boolean, timeSec: Double, action: String) {
        if (!active) return
        playback = JSONObject().put("paused", paused).put("time", timeSec).put("updatedAt", System.currentTimeMillis())
        send(JSONObject().put("type", "playback").put("paused", paused).put("time", timeSec).put("action", action))
    }

    /** Verwachte tijd van de groep nu (seconden), of null. */
    fun groupTime(): Pair<Boolean, Double>? {
        val pb = playback ?: return null
        val paused = pb.optBoolean("paused", true)
        var t = pb.optDouble("time", Double.NaN)
        if (t.isNaN()) return null
        if (!paused) t += (System.currentTimeMillis() - pb.optLong("updatedAt", System.currentTimeMillis())) / 1000.0
        return paused to t
    }

    // ---------------- verbinding ----------------

    private fun send(msg: JSONObject) {
        val w = ws
        if (w != null && connected) {
            w.send(msg.toString())
        } else {
            queue.add(msg)
            connect()
        }
    }

    private fun connect() {
        if (ws != null && (connected || connecting)) return
        connecting = true
        val req = Request.Builder().url(serverUrl).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = main.post {
                if (ws !== webSocket) return@post
                connected = true
                connecting = false
                retry = 0
                // Na herverbinden: terug in dezelfde party
                val c = code
                if (wantRoom && c != null) webSocket.send(JSONObject().put("type", "join").put("code", c).put("name", name).toString())
                val q = queue.toList()
                queue.clear()
                q.forEach { webSocket.send(it.toString()) }
            }.let { }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
                main.post { if (ws === webSocket) handle(msg) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = main.post { dropped(webSocket) }.let { }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = main.post {
                if (ws === webSocket && !connected && retry == 0 && wantRoom) {
                    notice = "Verbinden met de party-server… (kan bij de eerste keer ±30 s duren)"
                }
                dropped(webSocket)
            }.let { }
        })
    }

    private fun dropped(w: WebSocket) {
        if (ws !== w) return
        ws = null
        connected = false
        connecting = false
        if (wantRoom) {
            val delay = minOf(10_000L, 500L shl minOf(retry++, 5))
            main.postDelayed({ if (wantRoom && ws == null) connect() }, delay)
        }
    }

    private fun handle(msg: JSONObject) {
        when (msg.optString("type")) {
            "joined" -> {
                myId = msg.optString("you")
                val st = msg.optJSONObject("state") ?: return
                code = st.optString("code")
                pendingCode = null
                applyMembers(st.optJSONArray("members"), st.optBoolean("hostOnly"))
                playback = st.optJSONObject("playback")?.also { it.put("updatedAt", System.currentTimeMillis()) }
                val path = st.optString("path", "")
                if (path.isNotBlank() && path != "null") decode(path)?.let { p -> lastPlaceKey = p.key; onPlace?.invoke(p) }
            }
            "members" -> applyMembers(msg.optJSONArray("members"), msg.optBoolean("hostOnly"))
            "notice" -> notice = msg.optString("text")
            "error" -> {
                error = msg.optString("text")
                if (code == null) {
                    wantRoom = false
                    // Verbinding sluiten: de volgende poging maakt een verse verbinding (handig na een server-update)
                    val w = ws
                    ws = null
                    connected = false
                    connecting = false
                    runCatching { w?.close(1000, "retry") }
                }
            }
            "left" -> { code = null; members.clear() }
            "nav" -> {
                val p = decode(msg.optString("path")) ?: return
                playback = null
                lastPlaceKey = p.key
                notice = "${msg.optString("by", "Iemand")} koos: " + label(p)
                onPlace?.invoke(p)
            }
            "playback" -> {
                playback = JSONObject().put("paused", msg.optBoolean("paused")).put("time", msg.optDouble("time"))
                    .put("updatedAt", System.currentTimeMillis())
                val by = msg.optString("by").takeIf { it.isNotBlank() && it != "null" }
                onPlayback?.invoke(msg.optBoolean("paused"), msg.optDouble("time"), msg.optString("action", "sync"), by)
            }
            "state" -> {
                val st = msg.optJSONObject("state") ?: return
                playback = st.optJSONObject("playback")?.also { it.put("updatedAt", System.currentTimeMillis()) }
                playback?.let { onPlayback?.invoke(it.optBoolean("paused"), it.optDouble("time"), "join", null) }
            }
            "chat" -> {
                chat.add("${msg.optString("by")}: ${msg.optString("text")}")
                notice = "💬 ${msg.optString("by")}: ${msg.optString("text")}"
            }
        }
    }

    fun requestState() = send(JSONObject().put("type", "stateRequest"))

    private fun applyMembers(arr: JSONArray?, ho: Boolean) {
        hostOnly = ho
        members.clear()
        if (arr != null) for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            members.add(PartyMember(o.optString("id"), o.optString("name"), o.optBoolean("host")))
        }
    }

    private fun label(p: PartyPlace) = when (p) {
        is PartyPlace.Detail -> "een titel"
        is PartyPlace.Streams -> p.info.label
        is PartyPlace.Player -> "▶ " + p.info.label
    }

    // ---------------- plek <-> tekst ----------------
    // De server accepteert max. 4000 tekens, dus geen afleveringenlijst en weinig trackers.

    private fun streamJson(s: StreamItem) = JSONObject()
        .put("a", s.addonName).put("n", s.name.take(120)).put("t", s.title.take(300))
        .put("u", s.url ?: JSONObject.NULL).put("h", s.infoHash ?: JSONObject.NULL)
        .put("f", s.fileIdx ?: JSONObject.NULL).put("fn", (s.fileName ?: "").take(150))

    private fun streamFrom(o: JSONObject) = StreamItem(
        addonName = o.optString("a"), name = o.optString("n"), title = o.optString("t"),
        url = o.optString("u").takeIf { !o.isNull("u") && it.isNotBlank() },
        infoHash = o.optString("h").takeIf { !o.isNull("h") && it.isNotBlank() },
        fileIdx = if (o.isNull("f") || !o.has("f")) null else o.optInt("f"),
        trackers = emptyList(),
        fileName = o.optString("fn").ifBlank { null },
    )

    private fun infoJson(i: PlayInfo) = JSONObject().put("ty", i.type).put("m", i.metaId).put("v", i.videoId)
        .put("n", i.name.take(120)).put("p", i.poster ?: "").put("l", i.label.take(200))

    private fun infoFrom(o: JSONObject) = PlayInfo(
        type = o.optString("ty"), metaId = o.optString("m"), videoId = o.optString("v"), name = o.optString("n"),
        poster = o.optString("p").ifBlank { null }, label = o.optString("l"),
    )

    private fun encode(p: PartyPlace): String = when (p) {
        is PartyPlace.Detail -> JSONObject().put("s", "detail").put("type", p.type).put("id", p.id)
        is PartyPlace.Streams -> JSONObject().put("s", "streams").put("i", infoJson(p.info))
        is PartyPlace.Player -> JSONObject().put("s", "player").put("i", infoJson(p.info)).put("st", streamJson(p.stream))
            .put("r", JSONArray().apply { p.race.forEach { put(streamJson(it)) } })
    }.toString()

    private fun decode(path: String): PartyPlace? = runCatching {
        val o = JSONObject(path)
        when (o.optString("s")) {
            "detail" -> PartyPlace.Detail(o.getString("type"), o.getString("id"))
            "streams" -> PartyPlace.Streams(infoFrom(o.getJSONObject("i")))
            "player" -> {
                val r = o.optJSONArray("r")
                PartyPlace.Player(
                    streamFrom(o.getJSONObject("st")),
                    infoFrom(o.getJSONObject("i")),
                    if (r == null) emptyList() else (0 until r.length()).map { streamFrom(r.getJSONObject(it)) },
                )
            }
            else -> null
        }
    }.getOrNull()
}

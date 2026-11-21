package com.maluku.luku.torrent

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.swig.settings_pack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URLEncoder

/**
 * Ingebouwde torrent-motor (libtorrent). Haalt één bestand uit een torrent op,
 * met voorrang voor het stuk dat de speler nu nodig heeft.
 */
class TorrentEngine(ctx: Context) {

    val dir: File = File(ctx.cacheDir, "torrents")
    private var session: SessionManager? = null
    private var current: TorrentStream? = null

    init {
        // Oude films van vorige keer opruimen: de Chromecast heeft weinig opslag
        dir.deleteRecursively()
        dir.mkdirs()
    }

    @Synchronized
    private fun session(): SessionManager {
        session?.let { return it }
        val s = SessionManager(false)
        // Instellingen voor snel streamen (standaard is libtorrent voorzichtig)
        val sp = SettingsPack()
        runCatching {
            sp.connectionsLimit(300)
            // DHT-"adresboek": hiermee vindt de motor andere delers (anders vaak maar een paar)
            sp.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), true)
            sp.setString(
                settings_pack.string_types.dht_bootstrap_nodes.swigValue(),
                "dht.libtorrent.org:25401,router.bittorrent.com:6881,router.utorrent.com:6881," +
                    "dht.transmissionbt.com:6881,router.silotis.us:6881,dht.aelitis.com:6881",
            )
            sp.setInteger(settings_pack.int_types.active_downloads.swigValue(), 8)
            sp.setInteger(settings_pack.int_types.active_limit.swigValue(), 16)
            sp.setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true)
            sp.setInteger(settings_pack.int_types.connection_speed.swigValue(), 200)
            sp.setInteger(settings_pack.int_types.torrent_connect_boost.swigValue(), 80)
            sp.setInteger(settings_pack.int_types.max_out_request_queue.swigValue(), 1500)
            sp.setInteger(settings_pack.int_types.request_timeout.swigValue(), 10)
            sp.setInteger(settings_pack.int_types.peer_connect_timeout.swigValue(), 7)
        }
        s.start(SessionParams(sp))
        session = s
        return s
    }

    /** Motor alvast starten bij het openen van de app, zodat hij het netwerk al kent als je op een bron drukt. */
    fun warmUp() {
        Thread { runCatching { session() } }.start()
    }

    /** Hoe lang het zoeken naar de torrent-info duurde (voor de meetbalk). */
    @Volatile var metaSeconds = 0.0

    fun freeBytes(): Long = runCatching { StatFs(dir.absolutePath).availableBytes }.getOrDefault(0L)

    /**
     * Zoekt de torrent op, kiest het juiste bestand en start het downloaden.
     * [status] krijgt korte Nederlandse statusberichten voor op het scherm.
     */
    suspend fun open(
        infoHash: String,
        fileIdx: Int?,
        trackers: List<String>,
        status: (String) -> Unit,
    ): TorrentStream = withContext(Dispatchers.IO) {
        stop()
        val s = session()
        val magnet = buildString {
            append("magnet:?xt=urn:btih:").append(infoHash)
            // Trackers van de add-on + bekende openbare trackers: sneller deelnemers vinden
            for (t in (trackers + PUBLIC_TRACKERS).distinct().take(30)) append("&tr=").append(URLEncoder.encode(t, "UTF-8"))
        }
        val t0 = System.currentTimeMillis()
        status("Torrent zoeken…")
        // Magnet direct toevoegen: de gevonden deelnemers blijven verbonden (sneller dan eerst los de info ophalen)
        s.download(magnet, dir, TorrentFlags.SEQUENTIAL_DOWNLOAD)
        var handle: TorrentHandle? = null
        var waited = 0
        while (true) {
            if (handle == null || !handle.isValid) handle = s.find(Sha1Hash.parseHex(infoHash))
            val h = handle
            if (h != null && h.isValid && h.status().hasMetadata()) break
            if (waited >= 60_000) {
                runCatching { if (h != null) s.remove(h) }
                throw IOException("Geen mensen gevonden die deze film delen. Kies een andere bron.")
            }
            if (waited % 1000 == 0 && h != null && h.isValid) {
                status("Torrent zoeken… (" + h.status().numPeers() + " deelnemers)")
            }
            Thread.sleep(250)
            waited += 250
        }
        metaSeconds = (System.currentTimeMillis() - t0) / 1000.0
        val th: TorrentHandle = handle!!
        val ti: TorrentInfo = th.torrentFile()
        val fs = ti.files()
        val n = fs.numFiles()
        val idx = fileIdx?.takeIf { it in 0 until n } ?: (0 until n).maxByOrNull { fs.fileSize(it) } ?: 0
        val size = fs.fileSize(idx)

        val free = freeBytes()
        if (size > free - 200L * 1024 * 1024) {
            runCatching { s.remove(th) }
            throw IOException(
                "Dit bestand is te groot voor de opslag van dit apparaat (" +
                    gb(size) + " nodig, " + gb(free) + " vrij). Kies een kleinere bron."
            )
        }

        status("Verbinden met deelnemers…")
        val prios = Array(n) { if (it == idx) Priority.DEFAULT else Priority.IGNORE }
        th.prioritizeFiles(prios)
        th.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
        th.resume()

        val stream = TorrentStream(
            handle = th,
            file = File(dir, fs.filePath(idx)),
            size = size,
            fileOffset = fs.fileOffset(idx),
            pieceLength = ti.pieceLength().toLong(),
            numPieces = ti.numPieces(),
            name = fs.fileName(idx),
        )
        // Begin en eind van het bestand eerst (daar staat de info die de speler nodig heeft)
        stream.prioritize(0)
        current = stream
        stream
    }

    @Synchronized
    fun stop() {
        val c = current ?: return
        current = null
        c.closed = true
        runCatching { session?.remove(c.handle) }
        runCatching { c.close() }
        // Bestanden weggooien, anders loopt de opslag vol
        runCatching { dir.listFiles()?.forEach { it.deleteRecursively() } }
    }

    private fun gb(b: Long) = String.format("%.1f GB", b / 1e9)
}

private val PUBLIC_TRACKERS = listOf(
    "udp://tracker.opentrackr.org:1337/announce",
    "udp://open.demonii.com:1337/announce",
    "udp://open.stealth.si:80/announce",
    "udp://tracker.torrent.eu.org:451/announce",
    "udp://exodus.desync.com:6969/announce",
    "udp://explodie.org:6969/announce",
    "udp://tracker.openbittorrent.com:6969/announce",
    "udp://tracker.dler.org:6969/announce",
    "udp://open.tracker.cl:1337/announce",
    "https://tracker.tamersunion.org:443/announce",
)

class TorrentStream(
    val handle: TorrentHandle,
    val file: File,
    val size: Long,
    val fileOffset: Long,
    val pieceLength: Long,
    val numPieces: Int,
    val name: String,
) {
    @Volatile var closed = false
    private var raf: RandomAccessFile? = null
    private var lastPiece = -1

    // Meetgegevens voor op het scherm (om problemen te vinden)
    @Volatile var dbgReads = 0
    @Volatile var dbgPos = -1L
    @Volatile var dbgWaitPiece = -1
    @Volatile var dbgLast = "nog niet gelezen"
    @Volatile var dbgFills = 0
    val dbgStart = System.currentTimeMillis()
    @Volatile var dbgFirstPieceMs = -1L

    private fun pieceAt(pos: Long): Int = ((fileOffset + pos) / pieceLength).toInt().coerceIn(0, numPieces - 1)

    /** Aantal keer dat de speler al iets gelezen heeft: in het begin kleine vensters (snel eerste beeld), daarna ruim. */
    @Volatile private var started = false

    /**
     * Geeft de stukken vanaf [pos] voorrang, STRIKT op volgorde: het stuk dat de speler nu nodig heeft
     * krijgt deadline 0, de volgende pas veel later. Zo haalt de motor één stuk helemaal binnen
     * in plaats van vier stukken half. Vóór het eerste beeld: alleen dit stuk + 1 reserve.
     * Daarna (als het beeld loopt): ±32 MB vooruit.
     */
    fun prioritize(pos: Long) {
        val first = pieceAt(pos)
        val window = if (started) (32L * 1024 * 1024 / pieceLength).toInt().coerceIn(4, 80) else 2
        for (i in 0 until window) {
            val p = first + i
            if (p >= numPieces) break
            if (!handle.havePiece(p)) {
                handle.setPieceDeadline(p, if (i == 0) 0 else if (started) 1500 + i * 400 else 4000)
            }
        }
    }

    /** Alleen het laatste beetje van het bestand (±1,5 MB): daar kijkt de speler bij MKV/MP4 even naar. */
    fun prioritizeTail() {
        val last = pieceAt(size - 1)
        val tail = (1_500_000L / pieceLength).toInt().coerceIn(1, 4)
        for (p in (last - tail + 1).coerceAtLeast(0)..last) handle.setPieceDeadline(p, 400)
    }

    // Leesbuffer: de speler leest vaak in kruimels van een paar bytes (bijv. de inhoudsopgave aan het eind
    // van een MKV). Elke kruimel los uit de opslag halen is heel traag; daarom halen we halve MB's tegelijk.
    private val cache = ByteArray(512 * 1024)
    private var cacheStart = -1L
    private var cacheLen = 0
    private val havePieces = java.util.BitSet() // stukken die al binnen zijn (blijven binnen)

    private fun have(p: Int): Boolean {
        if (havePieces.get(p)) return true
        val h = handle.havePiece(p)
        if (h) havePieces.set(p)
        return h
    }

    /** Leest bytes; wacht tot het benodigde stuk binnen is. Geeft -1 aan het eind. */
    @Synchronized
    fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int {
        dbgReads++
        dbgPos = pos
        if (pos >= size) { dbgLast = "einde bestand"; return -1 }

        // 1) Snel: uit de leesbuffer in het geheugen
        if (cacheStart >= 0 && pos >= cacheStart && pos < cacheStart + cacheLen) {
            val n = minOf(len.toLong(), cacheStart + cacheLen - pos).toInt()
            System.arraycopy(cache, (pos - cacheStart).toInt(), buf, off, n)
            return n
        }

        // 2) Stuk nodig dat nog niet in de buffer zit
        val piece = pieceAt(pos)
        if (piece != lastPiece) {
            // Echt gesprongen (spoelen)? Oude voorrang wissen. Begin/eind van het bestand lezen is geen spoelen.
            val nearEdge = piece < 8 || piece > numPieces - 14
            if (!nearEdge && lastPiece >= 0 && (piece < lastPiece || piece > lastPiece + 2)) handle.clearPieceDeadlines()
            prioritize(pos)
            lastPiece = piece
        }
        var waited = 0
        dbgWaitPiece = piece
        while (!have(piece)) {
            if (closed) throw IOException("Gestopt")
            if (waited > 180_000) throw IOException("Te traag: na 3 minuten nog niets binnen")
            Thread.sleep(50)
            waited += 50
            if (waited % 3000 == 0) prioritize(pos)
        }

        // 3) Buffer vullen: vanaf pos, zo ver als de stukken aaneengesloten binnen zijn (max. 512 KB)
        val want = minOf(size, pos + cache.size)
        var availEnd = minOf((piece + 1L) * pieceLength - fileOffset, size)
        var p = piece + 1
        while (availEnd < want && p < numPieces && have(p)) {
            availEnd = minOf((p + 1L) * pieceLength - fileOffset, size)
            p++
        }
        val toRead = (minOf(want, availEnd) - pos).toInt()
        if (toRead <= 0) return -1
        val r = raf ?: run {
            var tries = 0
            while (!file.exists() && tries < 100) { Thread.sleep(50); tries++ }
            RandomAccessFile(file, "r").also { raf = it }
        }
        if (dbgFirstPieceMs < 0) dbgFirstPieceMs = System.currentTimeMillis() - dbgStart
        r.seek(pos)
        var got = 0
        while (got < toRead) {
            val k = r.read(cache, got, toRead - got)
            if (k < 0) break
            got += k
        }
        if (got <= 0) return -1
        cacheStart = pos
        cacheLen = got
        if (!started && pos > 3L * 1024 * 1024) { started = true; prioritize(pos) }
        dbgWaitPiece = -1
        dbgFills++
        dbgLast = "buffer ${dbgFills}× gevuld (${got / 1024} KB)"
        val n = minOf(len, got)
        System.arraycopy(cache, 0, buf, off, n)
        return n
    }

    fun debug(): String {
        val w = dbgWaitPiece
        val wait = if (w >= 0) "wacht op stuk $w (heb: ${runCatching { handle.havePiece(w) }.getOrDefault(false)})" else "niet aan het wachten"
        val t = (System.currentTimeMillis() - dbgStart) / 1000
        val first = if (dbgFirstPieceMs >= 0) "eerste stuk na ${dbgFirstPieceMs / 100 / 10.0}s" else "eerste stuk nog niet"
        return "${t}s sinds info binnen · $first · Lezen: ${dbgReads}× · plek ${dbgPos / 1_000_000} MB van ${size / 1_000_000} MB · $wait · $dbgLast · " +
            "stuk ${pieceLength / 1024} KB, eerste stuk ${pieceAt(0)}, laatste ${pieceAt(size - 1)}"
    }

    /** Totaal binnengehaalde bytes (om vastgelopen bronnen te herkennen). */
    fun downloaded(): Long = runCatching { handle.status().totalDone() }.getOrDefault(0L)

    fun stats(): String = runCatching {
        val st = handle.status()
        val kb = st.downloadPayloadRate() / 1024
        val speed = if (kb > 1024) String.format("%.1f MB/s", kb / 1024.0) else "$kb KB/s"
        val mb = st.totalDone() / (1024 * 1024)
        "${st.numPeers()} deelnemers · $speed · $mb MB binnen"
    }.getOrDefault("")

    fun close() {
        runCatching { raf?.close() }
        raf = null
    }
}

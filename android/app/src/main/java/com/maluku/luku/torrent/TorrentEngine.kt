package com.maluku.luku.torrent

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
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
        s.start()
        session = s
        return s
    }

    /** Motor alvast starten bij het openen van de app, zodat hij het netwerk al kent als je op een bron drukt. */
    fun warmUp() {
        Thread { runCatching { session() } }.start()
    }

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
            for (t in trackers.take(20)) append("&tr=").append(URLEncoder.encode(t, "UTF-8"))
        }
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
        stream.prioritizeTail()
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

    private fun pieceAt(pos: Long): Int = ((fileOffset + pos) / pieceLength).toInt().coerceIn(0, numPieces - 1)

    /** Geeft de stukken vanaf [pos] voorrang (de speler heeft ze zo nodig). */
    fun prioritize(pos: Long) {
        val first = pieceAt(pos)
        val window = (32L * 1024 * 1024 / pieceLength).toInt().coerceIn(6, 80) // ±32 MB vooruit
        for (i in 0 until window) {
            val p = first + i
            if (p >= numPieces) break
            if (!handle.havePiece(p)) handle.setPieceDeadline(p, i * 40)
        }
    }

    fun prioritizeTail() {
        // Laatste ±10 MB: daar staat bij MKV/MP4 vaak de inhoudsopgave die de speler eerst wil lezen
        val last = pieceAt(size - 1)
        val tail = (10L * 1024 * 1024 / pieceLength).toInt().coerceIn(2, 12)
        for (p in (last - tail + 1).coerceAtLeast(0)..last) handle.setPieceDeadline(p, 100)
    }

    /** Leest bytes; wacht tot het benodigde stuk binnen is. Geeft -1 aan het eind. */
    fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int {
        if (pos >= size) return -1
        val piece = pieceAt(pos)
        if (piece != lastPiece) {
            // Echt gesprongen (spoelen)? Oude voorrang wissen. Begin/eind van het bestand lezen is geen spoelen.
            val nearEdge = piece < 8 || piece > numPieces - 14
            if (!nearEdge && lastPiece >= 0 && (piece < lastPiece || piece > lastPiece + 2)) handle.clearPieceDeadlines()
            prioritize(pos)
            lastPiece = piece
        }
        var waited = 0
        while (!handle.havePiece(piece)) {
            if (closed) throw IOException("Gestopt")
            if (waited > 180_000) throw IOException("Te traag: na 3 minuten nog niets binnen")
            Thread.sleep(100)
            waited += 100
            if (waited % 3000 == 0) prioritize(pos)
        }
        val pieceEndInFile = (piece + 1L) * pieceLength - fileOffset
        val n = minOf(len.toLong(), pieceEndInFile - pos, size - pos).toInt()
        if (n <= 0) return -1
        val r = raf ?: run {
            var tries = 0
            while (!file.exists() && tries < 50) { Thread.sleep(100); tries++ }
            RandomAccessFile(file, "r").also { raf = it }
        }
        r.seek(pos)
        return r.read(buf, off, n)
    }

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

package com.maluku.luku.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.maluku.luku.torrent.TorrentStream
import java.io.IOException
import kotlin.math.min

/** Laat ExoPlayer rechtstreeks uit de torrent lezen, zonder tussenserver. */
class TorrentDataSource(private val stream: TorrentStream) : BaseDataSource(true) {
    private var uri: Uri? = null
    private var pos = 0L
    private var remaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        pos = dataSpec.position
        if (pos > stream.size) throw IOException("Positie buiten het bestand")
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else stream.size - pos
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining <= 0) return C.RESULT_END_OF_INPUT
        val n = stream.read(pos, buffer, offset, min(length.toLong(), remaining).toInt())
        if (n < 0) return C.RESULT_END_OF_INPUT
        pos += n
        remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
        uri = null
    }
}

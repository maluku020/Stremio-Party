package com.maluku.luku.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.maluku.luku.data.SubtitleItem
import com.maluku.luku.torrent.TorrentStream

object Players {

    /** ExoPlayer met FFmpeg voor geluid dat Android zelf niet kan (AC3, E-AC3, DTS, TrueHD). */
    fun create(ctx: Context): ExoPlayer {
        appContext = ctx.applicationContext
        val renderers = DefaultRenderersFactory(ctx)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(10_000, 60_000, 1_000, 3_000)
            .build()
        val player = ExoPlayer.Builder(ctx, renderers)
            .setLoadControl(load)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        // Ondertitels: Nederlands heeft voorrang
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguage("nl")
            .build()
        return player
    }

    /**
     * Speelt een torrent ([torrent] != null) of een gewone link ([url]) af,
     * met ondertitels van add-ons erbij.
     */
    lateinit var appContext: Context

    fun play(
        player: ExoPlayer,
        torrent: TorrentStream?,
        url: String?,
        subtitles: List<SubtitleItem>,
        startMs: Long,
    ) {
        val mainUri = if (torrent != null) {
            "torrent://local/" + Uri.encode(torrent.name)
        } else {
            url ?: return
        }
        val subs = subtitles.map { s ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url))
                .setMimeType(if (s.url.endsWith(".vtt", ignoreCase = true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
                .setLanguage(s.lang)
                .setLabel(when (s.lang) { "nl" -> "Nederlands"; "es" -> "Spaans"; else -> "Engels" } + " · " + s.addonName)
                .build()
        }
        val item = MediaItem.Builder().setUri(mainUri).setSubtitleConfigurations(subs).build()
        val factory = DataSource.Factory { RoutingDataSource(appContext, torrent) }
        val source = DefaultMediaSourceFactory(factory).createMediaSource(item)
        player.setMediaSource(source, startMs)
        player.prepare()
        player.playWhenReady = true
    }
}

/** Stuurt torrent:// naar de torrent-motor en al het andere (links, ondertitels) naar internet. */
class RoutingDataSource(ctx: Context, private val torrent: TorrentStream?) : DataSource {
    // Gewone bron: links (http) én lokale bestanden (de vooraf opgehaalde ondertitels)
    private val http: DataSource = DefaultDataSource.Factory(
        ctx,
        DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("TeyfeyLuku/0.1"),
    ).createDataSource()
    private var current: DataSource? = null
    private val listeners = mutableListOf<TransferListener>()

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        http.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val ds: DataSource = if (dataSpec.uri.scheme == "torrent" && torrent != null) {
            TorrentDataSource(torrent).also { t -> listeners.forEach { t.addTransferListener(it) } }
        } else {
            http
        }
        current = ds
        return ds.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        current?.read(buffer, offset, length) ?: throw IllegalStateException("niet geopend")

    override fun getUri(): Uri? = current?.uri

    override fun close() {
        try {
            current?.close()
        } finally {
            current = null
        }
    }
}

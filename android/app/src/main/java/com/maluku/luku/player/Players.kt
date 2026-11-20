package com.maluku.luku.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.maluku.luku.torrent.TorrentStream

object Players {

    /** ExoPlayer met FFmpeg voor geluid dat Android zelf niet kan (AC3, E-AC3, DTS, TrueHD). */
    fun create(ctx: Context): ExoPlayer {
        val renderers = DefaultRenderersFactory(ctx)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 2_500, 5_000)
            .build()
        return ExoPlayer.Builder(ctx, renderers)
            .setLoadControl(load)
            .build()
    }

    fun playTorrent(player: ExoPlayer, stream: TorrentStream, startMs: Long = 0) {
        val factory = DataSource.Factory { TorrentDataSource(stream) }
        val source = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri("torrent://local/" + stream.name.replace(" ", "_")))
        player.setMediaSource(source, startMs)
        player.prepare()
        player.playWhenReady = true
    }

    fun playUrl(ctx: Context, player: ExoPlayer, url: String, startMs: Long = 0) {
        val source = DefaultMediaSourceFactory(ctx).createMediaSource(MediaItem.fromUri(url))
        player.setMediaSource(source, startMs)
        player.prepare()
        player.playWhenReady = true
    }
}

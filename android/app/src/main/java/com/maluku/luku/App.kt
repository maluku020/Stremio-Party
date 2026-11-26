package com.maluku.luku

import android.app.Application
import com.maluku.luku.data.Library
import com.maluku.luku.data.Prefs
import com.maluku.luku.data.StremioRepo
import com.maluku.luku.torrent.TorrentEngine

class App : Application(), coil.ImageLoaderFactory {
    /** Lichte instellingen voor plaatjes: minder geheugen en werk, vooral op de Chromecast. */
    override fun newImageLoader(): coil.ImageLoader {
        val tv = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        return coil.ImageLoader.Builder(this)
            .crossfade(!tv)
            .allowRgb565(tv)
            .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(if (tv) 0.15 else 0.25).build() }
            .diskCache { coil.disk.DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(150L * 1024 * 1024).build() }
            .respectCacheHeaders(false)
            .build()
    }

    lateinit var prefs: Prefs
    lateinit var stremio: StremioRepo
    lateinit var torrents: TorrentEngine
    lateinit var library: Library
    val party = com.maluku.luku.party.Party()

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        com.maluku.luku.ui.I18n.lang = prefs.lang
        stremio = StremioRepo(prefs)
        torrents = TorrentEngine(this)
        library = Library(this, stremio)
        stremio.loadCached()
        torrents.warmUp()
    }

    companion object {
        lateinit var instance: App
            private set
    }
}

package com.maluku.luku

import android.app.Application
import com.maluku.luku.data.Library
import com.maluku.luku.data.Prefs
import com.maluku.luku.data.StremioRepo
import com.maluku.luku.torrent.TorrentEngine

class App : Application() {
    lateinit var prefs: Prefs
    lateinit var stremio: StremioRepo
    lateinit var torrents: TorrentEngine
    lateinit var library: Library

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
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

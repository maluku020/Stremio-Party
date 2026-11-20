package com.maluku.luku.data

import android.content.Context
import org.json.JSONArray

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("luku", Context.MODE_PRIVATE)

    var authKey: String?
        get() = sp.getString("authKey", null)
        set(v) = sp.edit().putString("authKey", v).apply()

    var email: String?
        get() = sp.getString("email", null)
        set(v) = sp.edit().putString("email", v).apply()

    /** true = gebruiker koos "zonder account" */
    var guest: Boolean
        get() = sp.getBoolean("guest", false)
        set(v) = sp.edit().putBoolean("guest", v).apply()

    /** Zelf toegevoegde add-on-links (manifest.json) */
    var extraAddons: List<String>
        get() = JSONArray(sp.getString("extraAddons", "[]")).strings()
        set(v) = sp.edit().putString("extraAddons", JSONArray(v).toString()).apply()

    /** Laatst opgehaalde add-on-collectie (cache, zodat de app snel start) */
    var addonCache: String?
        get() = sp.getString("addonCache", null)
        set(v) = sp.edit().putString("addonCache", v).apply()

    var partyName: String
        get() = sp.getString("partyName", "") ?: ""
        set(v) = sp.edit().putString("partyName", v).apply()

    val loggedIn: Boolean get() = authKey != null || guest

    fun logout() {
        sp.edit().remove("authKey").remove("email").remove("guest").remove("addonCache").apply()
    }
}

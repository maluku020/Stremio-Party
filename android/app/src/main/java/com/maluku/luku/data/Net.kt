package com.maluku.luku.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object Net {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", "TeyfeyLuku/0.1").build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code} bij ${shortUrl(url)}")
            JSONObject(res.body?.string() ?: "{}")
        }
    }

    suspend fun postJson(url: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .post(body.toString().toRequestBody(JSON))
            .header("User-Agent", "TeyfeyLuku/0.1")
            .build()
        client.newCall(req).execute().use { res ->
            val text = res.body?.string() ?: "{}"
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            JSONObject(text)
        }
    }

    private fun shortUrl(url: String) = url.substringAfter("://").substringBefore("/")
}

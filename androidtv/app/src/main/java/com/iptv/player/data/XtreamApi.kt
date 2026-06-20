package com.iptv.player.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Minimal Xtream Codes API client. All network calls are synchronous OkHttp
 * requests wrapped in [withContext] on the IO dispatcher.
 */
class XtreamApi(
    private val server: String,
    private val username: String,
    private val password: String,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val base = server.trimEnd('/')

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun apiUrl(action: String, extra: String = ""): String =
        "$base/player_api.php?username=${enc(username)}&password=${enc(password)}" +
            "&action=$action$extra"

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body?.string() ?: ""
        }
    }

    private suspend fun getArray(action: String, extra: String = ""): JSONArray {
        val body = get(apiUrl(action, extra))
        return try {
            JSONArray(body)
        } catch (e: Exception) {
            JSONArray()
        }
    }

    /** Returns true if the credentials are valid. */
    suspend fun login(): Boolean {
        val body = get("$base/player_api.php?username=${enc(username)}&password=${enc(password)}")
        return try {
            val obj = JSONObject(body)
            val info = obj.optJSONObject("user_info")
            info != null && info.optString("auth") == "1"
        } catch (e: Exception) {
            false
        }
    }

    // ----- Live TV -----

    suspend fun getLiveCategories(): List<Category> =
        parseCategories(getArray("get_live_categories"))

    suspend fun getLiveStreams(categoryId: String? = null): List<LiveChannel> {
        val extra = if (categoryId != null) "&category_id=$categoryId" else ""
        val arr = getArray("get_live_streams", extra)
        val out = ArrayList<LiveChannel>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                LiveChannel(
                    streamId = o.readId("stream_id"),
                    name = o.optString("name"),
                    icon = o.optString("stream_icon").ifBlank { null },
                    epgChannelId = o.optString("epg_channel_id").ifBlank { null },
                    categoryId = o.optString("category_id").ifBlank { null },
                    number = o.optInt("num", -1).takeIf { it >= 0 },
                )
            )
        }
        return out
    }

    // ----- VOD (movies) -----

    suspend fun getVodCategories(): List<Category> =
        parseCategories(getArray("get_vod_categories"))

    suspend fun getVodStreams(categoryId: String? = null): List<Movie> {
        val extra = if (categoryId != null) "&category_id=$categoryId" else ""
        val arr = getArray("get_vod_streams", extra)
        val out = ArrayList<Movie>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Movie(
                    streamId = o.readId("stream_id"),
                    name = o.optString("name"),
                    icon = o.optString("stream_icon").ifBlank { null },
                    categoryId = o.optString("category_id").ifBlank { null },
                    containerExtension = o.optString("container_extension").ifBlank { null },
                    rating = o.optString("rating").ifBlank { null },
                )
            )
        }
        return out
    }

    // ----- Series -----

    suspend fun getSeriesCategories(): List<Category> =
        parseCategories(getArray("get_series_categories"))

    suspend fun getSeries(categoryId: String? = null): List<Series> {
        val extra = if (categoryId != null) "&category_id=$categoryId" else ""
        val arr = getArray("get_series", extra)
        val out = ArrayList<Series>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Series(
                    seriesId = o.readId("series_id"),
                    name = o.optString("name"),
                    cover = o.optString("cover").ifBlank { null },
                    categoryId = o.optString("category_id").ifBlank { null },
                    plot = o.optString("plot").ifBlank { null },
                    rating = o.optString("rating").ifBlank { null },
                )
            )
        }
        return out
    }

    suspend fun getSeriesInfo(seriesId: String): List<Season> {
        val body = get(apiUrl("get_series_info", "&series_id=$seriesId"))
        val seasons = sortedMapOf<Int, MutableList<Episode>>()
        try {
            val obj = JSONObject(body)
            val episodes = obj.optJSONObject("episodes") ?: return emptyList()
            val keys = episodes.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val seasonNum = key.toIntOrNull() ?: continue
                val epArr = episodes.optJSONArray(key) ?: continue
                val list = seasons.getOrPut(seasonNum) { mutableListOf() }
                for (i in 0 until epArr.length()) {
                    val e = epArr.optJSONObject(i) ?: continue
                    val info = e.optJSONObject("info")
                    list.add(
                        Episode(
                            id = e.readId("id"),
                            title = e.optString("title").ifBlank { "Episode ${e.optString("episode_num")}" },
                            seasonNumber = seasonNum,
                            episodeNumber = e.optInt("episode_num", 0),
                            containerExtension = e.optString("container_extension").ifBlank { null },
                            plot = info?.optString("plot")?.ifBlank { null },
                            cover = info?.optString("movie_image")?.ifBlank { null },
                        )
                    )
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return seasons.map { (num, eps) ->
            Season(num, eps.sortedBy { it.episodeNumber })
        }
    }

    // ----- EPG -----

    suspend fun getShortEpg(streamId: String, limit: Int = 2): List<EpgEntry> {
        val body = get(apiUrl("get_short_epg", "&stream_id=$streamId&limit=$limit"))
        val out = ArrayList<EpgEntry>()
        try {
            val obj = JSONObject(body)
            val arr = obj.optJSONArray("epg_listings") ?: return emptyList()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    EpgEntry(
                        title = decodeBase64(o.optString("title")),
                        description = decodeBase64(o.optString("description")),
                        startMillis = o.optString("start_timestamp").toLongOrNull()?.times(1000) ?: 0L,
                        endMillis = o.optString("stop_timestamp").toLongOrNull()?.times(1000) ?: 0L,
                    )
                )
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return out
    }

    // ----- Stream URL builders -----

    fun liveUrl(streamId: String, ext: String = "m3u8"): String =
        "$base/live/${enc(username)}/${enc(password)}/$streamId.$ext"

    fun movieUrl(streamId: String, ext: String?): String =
        "$base/movie/${enc(username)}/${enc(password)}/$streamId.${ext ?: "mp4"}"

    fun episodeUrl(episodeId: String, ext: String?): String =
        "$base/series/${enc(username)}/${enc(password)}/$episodeId.${ext ?: "mp4"}"

    // ----- Helpers -----

    private fun parseCategories(arr: JSONArray): List<Category> {
        val out = ArrayList<Category>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Category(
                    id = o.optString("category_id"),
                    name = o.optString("category_name").ifBlank { "Unnamed" },
                )
            )
        }
        return out
    }

    private fun decodeBase64(s: String): String = try {
        if (s.isBlank()) "" else String(Base64.decode(s, Base64.DEFAULT))
    } catch (e: Exception) {
        s
    }

    /** Reads an id field that may be encoded as a string or an int. */
    private fun JSONObject.readId(key: String): String {
        val s = optString(key)
        if (s.isNotBlank() && s != "null") return s
        val n = optInt(key, -1)
        return if (n >= 0) n.toString() else ""
    }
}

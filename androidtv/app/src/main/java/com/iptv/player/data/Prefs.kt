package com.iptv.player.data

import android.content.Context

/** Simple persistent storage for credentials, favorites and recents. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)

    var server: String
        get() = sp.getString("server", "") ?: ""
        set(value) = sp.edit().putString("server", value).apply()

    var username: String
        get() = sp.getString("username", "") ?: ""
        set(value) = sp.edit().putString("username", value).apply()

    var password: String
        get() = sp.getString("password", "") ?: ""
        set(value) = sp.edit().putString("password", value).apply()

    val isLoggedIn: Boolean
        get() = server.isNotBlank() && username.isNotBlank()

    fun saveCredentials(server: String, username: String, password: String) {
        sp.edit()
            .putString("server", server)
            .putString("username", username)
            .putString("password", password)
            .apply()
    }

    fun clearCredentials() {
        sp.edit()
            .remove("server")
            .remove("username")
            .remove("password")
            .apply()
    }

    // --- Favorites (live channels), stored as a set of stream ids ---

    fun favorites(): Set<String> =
        sp.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()

    fun isFavorite(streamId: String): Boolean = favorites().contains(streamId)

    /** Replaces the stored set. The in-memory list in AppViewModel is the source of truth. */
    fun setFavorites(ids: Set<String>) {
        sp.edit().putStringSet("favorites", ids).apply()
    }

    // --- Recently watched (live channels), most recent first ---

    fun recents(): List<String> {
        val raw = sp.getString("recents", "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split(",")
    }

    /** Replaces the stored list. The in-memory list in AppViewModel is the source of truth. */
    fun setRecents(ids: List<String>) {
        sp.edit().putString("recents", ids.joinToString(",")).apply()
    }

    var lastChannelId: String
        get() = sp.getString("last_channel", "") ?: ""
        set(value) = sp.edit().putString("last_channel", value).apply()

    // --- Resume playback positions for VOD / episodes ---

    fun savePosition(key: String, positionMs: Long, durationMs: Long) {
        // Only keep a resume point if we're past the intro and not at the very end
        if (positionMs in 10_000 until (durationMs - 10_000).coerceAtLeast(10_000)) {
            sp.edit().putLong("pos_$key", positionMs).apply()
        } else {
            clearPosition(key)
        }
    }

    fun getPosition(key: String): Long = sp.getLong("pos_$key", 0L)

    fun clearPosition(key: String) {
        sp.edit().remove("pos_$key").apply()
    }

    fun clearFavorites() {
        sp.edit().remove("favorites").apply()
    }

    fun clearRecents() {
        sp.edit().remove("recents").remove("last_channel").apply()
    }
}

package com.iptv.player

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import com.iptv.player.data.Category
import com.iptv.player.data.LiveChannel
import com.iptv.player.data.Movie
import com.iptv.player.data.PlaybackItem
import com.iptv.player.data.Prefs
import com.iptv.player.data.Series
import com.iptv.player.data.XtreamApi

/** Top-level navigation destinations. */
sealed interface Screen {
    data object Login : Screen
    data object Home : Screen
    data class SeriesDetail(val series: Series) : Screen
    data class LivePlayer(val channels: List<LiveChannel>, val index: Int) : Screen
    data class VodPlayer(val item: PlaybackItem) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)

    var api: XtreamApi? = null
        private set

    private val backStack = mutableStateListOf<Screen>()

    val currentScreen: Screen
        get() = backStack.lastOrNull() ?: Screen.Login

    val isPlayerScreen: Boolean
        get() = currentScreen is Screen.LivePlayer || currentScreen is Screen.VodPlayer

    /** True while the activity is in picture-in-picture mode. */
    var inPipMode by androidx.compose.runtime.mutableStateOf(false)

    init {
        if (prefs.isLoggedIn) {
            api = XtreamApi(prefs.server, prefs.username, prefs.password)
            backStack.add(Screen.Home)
        } else {
            backStack.add(Screen.Login)
        }
    }

    fun onLoginSuccess(server: String, username: String, password: String) {
        prefs.saveCredentials(server, username, password)
        api = XtreamApi(server, username, password)
        // The lists are the source of truth and write through on every change, so they
        // MUST be re-seeded from disk here. logout() empties them but leaves the stored
        // values alone; without this the first favourite or channel opened after a
        // re-login would overwrite the whole saved set with a single entry.
        favorites.clear()
        favorites.addAll(prefs.favorites())
        recents.clear()
        recents.addAll(prefs.recents())
        backStack.clear()
        backStack.add(Screen.Home)
    }

    fun logout() {
        prefs.clearCredentials()
        api = null
        favorites.clear()
        recents.clear()
        liveCatsCache = null
        movieCatsCache = null
        seriesCatsCache = null
        allLiveCache = null
        allMoviesCache = null
        allSeriesCache = null
        backStack.clear()
        backStack.add(Screen.Login)
    }

    fun navigate(screen: Screen) {
        backStack.add(screen)
    }

    /** Returns false when there is nothing left to pop (caller should exit). */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.size - 1)
        return true
    }

    // --- Favorites / recents ---

    /**
     * Snapshot-backed so the UI actually recomposes when a channel is favourited.
     * Reading it straight from SharedPreferences left the star frozen until some
     * unrelated recomposition happened to re-read it.
     */
    val favorites: SnapshotStateList<String> =
        mutableStateListOf<String>().apply { addAll(prefs.favorites()) }

    fun isFavorite(streamId: String): Boolean = favorites.contains(streamId)

    fun toggleFavorite(streamId: String) {
        if (!favorites.remove(streamId)) favorites.add(streamId)
        prefs.setFavorites(favorites.toSet())
    }

    /** Recents are snapshot-backed for the same reason. */
    val recents: SnapshotStateList<String> =
        mutableStateListOf<String>().apply { addAll(prefs.recents()) }

    fun addRecent(streamId: String) {
        recents.remove(streamId)
        recents.add(0, streamId)
        while (recents.size > 30) recents.removeAt(recents.size - 1)
        prefs.setRecents(recents.toList())
        prefs.lastChannelId = streamId
    }

    // --- Resume playback ---

    fun savePosition(key: String, positionMs: Long, durationMs: Long) =
        prefs.savePosition(key, positionMs, durationMs)
    fun getPosition(key: String) = prefs.getPosition(key)
    fun clearPosition(key: String) = prefs.clearPosition(key)

    // --- Settings actions ---

    fun clearFavorites() {
        favorites.clear()
        prefs.clearFavorites()
    }

    fun clearRecents() {
        recents.clear()
        prefs.clearRecents()
    }

    // --- Cached catalog data (loaded once per session) ---

    private var liveCatsCache: List<Category>? = null
    private var movieCatsCache: List<Category>? = null
    private var seriesCatsCache: List<Category>? = null
    private var allLiveCache: List<LiveChannel>? = null
    private var allMoviesCache: List<Movie>? = null
    private var allSeriesCache: List<Series>? = null

    suspend fun liveCategories(): List<Category> =
        liveCatsCache ?: (api?.getLiveCategories() ?: emptyList()).also { liveCatsCache = it }

    suspend fun allLive(): List<LiveChannel> =
        allLiveCache ?: (api?.getLiveStreams() ?: emptyList()).also { allLiveCache = it }

    suspend fun movieCategories(): List<Category> =
        movieCatsCache ?: (api?.getVodCategories() ?: emptyList()).also { movieCatsCache = it }

    suspend fun allMovies(): List<Movie> =
        allMoviesCache ?: (api?.getVodStreams() ?: emptyList()).also { allMoviesCache = it }

    suspend fun seriesCategories(): List<Category> =
        seriesCatsCache ?: (api?.getSeriesCategories() ?: emptyList()).also { seriesCatsCache = it }

    suspend fun allSeries(): List<Series> =
        allSeriesCache ?: (api?.getSeries() ?: emptyList()).also { allSeriesCache = it }
}

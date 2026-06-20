package com.iptv.player

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
        backStack.clear()
        backStack.add(Screen.Home)
    }

    fun logout() {
        prefs.clearCredentials()
        api = null
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

    // --- Favorites / recents passthrough ---

    fun isFavorite(streamId: String) = prefs.isFavorite(streamId)
    fun toggleFavorite(streamId: String) = prefs.toggleFavorite(streamId)
    fun favorites() = prefs.favorites()
    fun addRecent(streamId: String) {
        prefs.addRecent(streamId)
        prefs.lastChannelId = streamId
    }
    fun recents() = prefs.recents()

    // --- Resume playback ---

    fun savePosition(key: String, positionMs: Long, durationMs: Long) =
        prefs.savePosition(key, positionMs, durationMs)
    fun getPosition(key: String) = prefs.getPosition(key)
    fun clearPosition(key: String) = prefs.clearPosition(key)

    // --- Settings actions ---

    fun clearFavorites() = prefs.clearFavorites()
    fun clearRecents() = prefs.clearRecents()

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

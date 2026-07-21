package com.iptv.player

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.iptv.player.ui.HomeScreen
import com.iptv.player.ui.LoginScreen
import com.iptv.player.ui.PlayerScreen
import com.iptv.player.ui.SeriesDetailScreen
import com.iptv.player.ui.theme.IptvTvTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IptvTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    colors = androidx.tv.material3.SurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    AppRoot(vm) { finish() }
                }
            }
        }
    }

    /** Enter PiP when the user leaves the app while watching. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        maybeEnterPip()
    }

    fun maybeEnterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!vm.isPlayerScreen) return
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        try {
            enterPictureInPictureMode(params)
        } catch (e: Exception) {
            // Device may not support PiP in this state
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        vm.inPipMode = isInPictureInPictureMode
    }
}

@androidx.compose.runtime.Composable
private fun AppRoot(vm: AppViewModel, onExit: () -> Unit) {
    val screen = vm.currentScreen

    BackHandler(enabled = true) {
        if (!vm.back()) onExit()
    }

    // Opening a player replaces the whole tree, which used to discard every
    // rememberSaveable underneath it - coming back landed you on tab 0 at the top
    // of "Senaste". The holder keeps each screen's saveable state keyed by screen.
    val stateHolder = rememberSaveableStateHolder()
    val screenKey = when (screen) {
        is Screen.Login -> "login"
        is Screen.Home -> "home"
        is Screen.SeriesDetail -> "series:${screen.series.seriesId}"
        is Screen.LivePlayer -> "live"
        is Screen.VodPlayer -> "vod:${screen.item.kind}:${screen.item.id}"
    }

    // Logging out must not leave the previous session's tab and category behind for
    // the next login - "Logga ut" lives on tab 4, so it would reopen on Inställningar.
    if (screen is Screen.Login) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            stateHolder.removeState("home")
        }
    }

    stateHolder.SaveableStateProvider(screenKey) {
        when (screen) {
            is Screen.Login -> LoginScreen(vm)
            is Screen.Home -> HomeScreen(vm)
            is Screen.SeriesDetail -> SeriesDetailScreen(vm, screen.series)
            is Screen.LivePlayer -> PlayerScreen(vm, screen)
            is Screen.VodPlayer -> PlayerScreen(vm, screen)
        }
    }
}

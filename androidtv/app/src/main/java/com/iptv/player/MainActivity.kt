package com.iptv.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.remember
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
}

@androidx.compose.runtime.Composable
private fun AppRoot(vm: AppViewModel, onExit: () -> Unit) {
    val screen = vm.currentScreen

    BackHandler(enabled = true) {
        if (!vm.back()) onExit()
    }

    when (screen) {
        is Screen.Login -> LoginScreen(vm)
        is Screen.Home -> HomeScreen(vm)
        is Screen.SeriesDetail -> SeriesDetailScreen(vm, screen.series)
        is Screen.LivePlayer -> PlayerScreen(vm, screen)
        is Screen.VodPlayer -> PlayerScreen(vm, screen)
    }
}

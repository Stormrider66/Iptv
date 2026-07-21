package com.iptv.player.ui

import android.view.KeyEvent
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.iptv.player.AppViewModel
import com.iptv.player.R
import com.iptv.player.Screen
import com.iptv.player.data.LiveChannel
import com.iptv.player.data.StreamKind
import com.iptv.player.ui.theme.Accent
import com.iptv.player.ui.theme.TextPrimary
import com.iptv.player.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: AppViewModel, screen: Screen) {
    val context = LocalContext.current
    val activity = context as? com.iptv.player.MainActivity
    val api = vm.api ?: return

    // Resolve playback info from the screen type
    val isLive = screen is Screen.LivePlayer

    // Resume key for VOD / episodes (null for live)
    val resumeKey = remember(screen) {
        (screen as? Screen.VodPlayer)?.let { "${it.item.kind}_${it.item.id}" }
    }
    var channelIndex by remember { mutableIntStateOf((screen as? Screen.LivePlayer)?.index ?: 0) }
    val channels = (screen as? Screen.LivePlayer)?.channels ?: emptyList()

    val currentUrl = remember(screen, channelIndex) {
        when (screen) {
            is Screen.LivePlayer -> {
                val ch = channels.getOrNull(channelIndex) ?: return@remember ""
                api.liveUrl(ch.streamId)
            }
            is Screen.VodPlayer -> when (screen.item.kind) {
                StreamKind.MOVIE -> api.movieUrl(screen.item.id, screen.item.containerExtension)
                StreamKind.EPISODE -> api.episodeUrl(screen.item.id, screen.item.containerExtension)
                else -> ""
            }
            else -> ""
        }
    }

    val currentTitle = remember(screen, channelIndex) {
        when (screen) {
            is Screen.LivePlayer -> channels.getOrNull(channelIndex)?.name ?: ""
            is Screen.VodPlayer -> screen.item.title
            else -> ""
        }
    }

    // ExoPlayer instance
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    // OSD visibility
    var showOsd by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Hide OSD after 4 seconds
    LaunchedEffect(showOsd, channelIndex) {
        if (showOsd) {
            delay(4000)
            showOsd = false
        }
    }

    // Load media when URL changes
    LaunchedEffect(currentUrl) {
        if (currentUrl.isBlank()) return@LaunchedEffect
        errorMsg = null
        showOsd = true
        player.setMediaItem(MediaItem.fromUri(currentUrl))
        player.prepare()
        // Resume from saved position for VOD / episodes
        if (resumeKey != null) {
            val savedPos = vm.getPosition(resumeKey)
            if (savedPos > 0) player.seekTo(savedPos)
        }
    }

    // Periodically persist playback position for VOD / episodes
    LaunchedEffect(resumeKey) {
        if (resumeKey == null) return@LaunchedEffect
        while (true) {
            delay(5000)
            val dur = player.duration
            if (dur > 0 && player.isPlaying) {
                vm.savePosition(resumeKey, player.currentPosition, dur)
            }
        }
    }

    // Error listener
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Try TS fallback for live
                if (isLive) {
                    val ch = channels.getOrNull(channelIndex)
                    if (ch != null && currentUrl.endsWith(".m3u8")) {
                        val tsUrl = api.liveUrl(ch.streamId, "ts")
                        player.setMediaItem(MediaItem.fromUri(tsUrl))
                        player.prepare()
                        return
                    }
                }
                errorMsg = "Uppspelningsfel: ${error.localizedMessage ?: "okänt fel"}"
            }
        }
        player.addListener(listener)
        onDispose {
            // Save final position for VOD / episodes before releasing
            if (resumeKey != null) {
                val dur = player.duration
                if (dur > 0) vm.savePosition(resumeKey, player.currentPosition, dur)
            }
            player.removeListener(listener)
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    // Channel up/down for live TV
                    KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP -> {
                        if (isLive && channels.isNotEmpty()) {
                            channelIndex = (channelIndex - 1 + channels.size) % channels.size
                            val ch = channels[channelIndex]
                            vm.addRecent(ch.streamId)
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (isLive && channels.isNotEmpty()) {
                            channelIndex = (channelIndex + 1) % channels.size
                            val ch = channels[channelIndex]
                            vm.addRecent(ch.streamId)
                            true
                        } else false
                    }
                    // Toggle play/pause
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER -> {
                        if (player.isPlaying) player.pause() else player.play()
                        showOsd = true
                        true
                    }
                    // Show/hide OSD on OK
                    KeyEvent.KEYCODE_ENTER -> {
                        showOsd = !showOsd
                        true
                    }
                    // Enter picture-in-picture
                    KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_WINDOW -> {
                        activity?.maybeEnterPip()
                        true
                    }
                    // Seek forward/back for VOD
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (!isLive) {
                            player.seekTo(player.currentPosition + 15_000)
                            showOsd = true
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (!isLive) {
                            player.seekTo(maxOf(0, player.currentPosition - 15_000))
                            showOsd = true
                            true
                        } else false
                    }
                    else -> false
                }
            },
    ) {
        // ExoPlayer view — inflated from XML so it uses a TextureView surface.
        // A SurfaceView lands in its own layer below the app window and the
        // Compose content above it covers the picture (audio only, black screen).
        AndroidView(
            factory = { ctx ->
                val view = LayoutInflater.from(ctx)
                    .inflate(R.layout.player_view, null) as PlayerView
                view.apply {
                    this.player = player
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // OSD overlay (hidden in picture-in-picture)
        if (showOsd && !vm.inPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color(0xCC000000))
                    .padding(horizontal = 32.dp, vertical = 16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Text(
                            text = currentTitle,
                            color = TextPrimary,
                            fontSize = 20.sp,
                        )
                        if (isLive) {
                            Text(
                                text = "Kanal ${channelIndex + 1} / ${channels.size}",
                                color = TextSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    if (isLive) {
                        Text(
                            text = "LIVE",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .background(Color.Red, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }

        // Error message
        if (errorMsg != null && !vm.inPipMode) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = errorMsg ?: "",
                    color = Accent,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                        .padding(24.dp),
                )
            }
        }
    }
}

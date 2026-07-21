package com.iptv.player.ui

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.WindowManager
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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

    // Candidate URLs to try, in order. Xtream panels serve the same stream under
    // several container extensions and often report no container_extension at all
    // for VOD, in which case the extension below is a guess: asking for the wrong
    // one yields an error page that no extractor can read
    // (UnrecognizedInputFormatException). Each failure advances to the next
    // candidate; when they run out the user finally sees an error.
    val candidates: List<String> = remember(screen, channelIndex) {
        when (screen) {
            is Screen.LivePlayer -> {
                val ch = channels.getOrNull(channelIndex) ?: return@remember emptyList<String>()
                listOf(api.liveUrl(ch.streamId, "m3u8"), api.liveUrl(ch.streamId, "ts"))
            }
            is Screen.VodPlayer -> {
                val item = screen.item
                // A reported extension is authoritative; otherwise try the common ones.
                val exts = item.containerExtension?.let { listOf(it) }
                    ?: listOf("mp4", "mkv", "avi")
                exts.mapNotNull { ext ->
                    when (item.kind) {
                        StreamKind.MOVIE -> api.movieUrl(item.id, ext)
                        StreamKind.EPISODE -> api.episodeUrl(item.id, ext)
                        else -> null
                    }
                }
            }
            else -> emptyList()
        }
    }

    // Reset the position in the candidate list whenever the list itself changes
    // (channel change, new item). Keying the remember does this during composition,
    // so currentUrl below never briefly points at the previous item's fallback.
    val candidateIndex = remember(candidates) { mutableIntStateOf(0) }
    val playbackStarted = remember(candidates) { mutableStateOf(false) }

    // The error listener is registered once for the player's whole lifetime, so it
    // must read these through rememberUpdatedState rather than capturing them.
    val latestCandidates by rememberUpdatedState(candidates)
    val latestIndex by rememberUpdatedState(candidateIndex)
    val latestStarted by rememberUpdatedState(playbackStarted)

    val currentUrl = candidates.getOrNull(candidateIndex.intValue) ?: ""

    val currentTitle = remember(screen, channelIndex) {
        when (screen) {
            is Screen.LivePlayer -> channels.getOrNull(channelIndex)?.name ?: ""
            is Screen.VodPlayer -> screen.item.title
            else -> ""
        }
    }

    // ExoPlayer instance. handleAudioFocus makes it duck/pause for other apps and
    // system sounds instead of talking over them.
    val player = remember {
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .build()
            .apply { playWhenReady = true }
    }

    // OSD visibility
    var showOsd by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    // Bumped to re-run the load effect when the user retries after an error.
    var retryToken by remember { mutableIntStateOf(0) }

    // Pause when the activity stops, unless we stopped because we entered PiP.
    // Without this the player keeps decoding and playing audio in the background
    // on any path that does not route through onUserLeaveHint.
    val lifecycle = activity?.lifecycle
    DisposableEffect(lifecycle) {
        if (lifecycle == null) return@DisposableEffect onDispose { }
        var pausedByLifecycle = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (!vm.inPipMode && player.isPlaying) {
                        pausedByLifecycle = true
                        player.pause()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (pausedByLifecycle) {
                        pausedByLifecycle = false
                        player.play()
                    }
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

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

    // Load media when the URL changes, or when the user asks to retry
    LaunchedEffect(currentUrl, retryToken) {
        if (currentUrl.isBlank()) return@LaunchedEffect
        errorMsg = null
        showOsd = true
        playbackStarted.value = false
        player.setMediaItem(MediaItem.fromUri(currentUrl))
        player.prepare()
        // A previous pause() cleared this, and it survives a channel change.
        player.playWhenReady = true
        // Resume from saved position for VOD / episodes
        if (resumeKey != null) {
            val savedPos = vm.getPosition(resumeKey)
            if (savedPos > 0) player.seekTo(savedPos)
        }
    }

    // Record the channel you actually settled on, not every one you zapped past.
    LaunchedEffect(channelIndex) {
        if (!isLive) return@LaunchedEffect
        delay(2000)
        channels.getOrNull(channelIndex)?.let { vm.addRecent(it.streamId) }
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
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) latestStarted.value = true
            }

            // Nothing resets the idle timer while a film plays (there is no
            // controller taking input), so hold the display awake ourselves.
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val window = activity?.window ?: return
                if (isPlaying) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Only fall through to another container while the stream has never
                // played - a mid-playback network blip must not switch URLs.
                val canFallBack = !latestStarted.value &&
                    latestIndex.intValue < latestCandidates.lastIndex
                if (canFallBack) {
                    // Advancing the index re-runs LaunchedEffect(currentUrl), which
                    // loads the next candidate. No unbounded retry of a dead URL.
                    latestIndex.intValue += 1
                    return
                }
                errorMsg = "Uppspelningsfel: ${error.localizedMessage ?: "okänt fel"}"
            }
        }
        player.addListener(listener)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                // Auto-repeat arrives as further ACTION_DOWN events with repeatCount > 0.
                // Holding the D-pad used to fire ~15 channel changes a second, each one a
                // prepare() against the provider and a recents write. Swallow the repeats.
                if (event.nativeKeyEvent.repeatCount > 0) return@onKeyEvent true
                when (event.nativeKeyEvent.keyCode) {
                    // Channel up/down for live TV
                    KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP -> {
                        if (isLive && channels.isNotEmpty()) {
                            channelIndex = (channelIndex - 1 + channels.size) % channels.size
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (isLive && channels.isNotEmpty()) {
                            channelIndex = (channelIndex + 1) % channels.size
                            true
                        } else false
                    }
                    // Toggle play/pause, or retry after a failure
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER -> {
                        if (errorMsg != null) {
                            // The player is IDLE after an error - play() would be a no-op.
                            // Start over from the first candidate.
                            errorMsg = null
                            candidateIndex.intValue = 0
                            retryToken += 1
                        } else {
                            if (player.isPlaying) player.pause() else player.play()
                        }
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
                    text = (errorMsg ?: "") + "\n\nTryck OK för att försöka igen.",
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

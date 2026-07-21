package com.iptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.iptv.player.AppViewModel
import com.iptv.player.Screen
import com.iptv.player.data.LiveChannel
import com.iptv.player.data.Movie
import com.iptv.player.data.PlaybackItem
import com.iptv.player.data.Series
import com.iptv.player.data.StreamKind
import com.iptv.player.ui.theme.Accent
import com.iptv.player.ui.theme.Surface2
import com.iptv.player.ui.theme.TextPrimary
import com.iptv.player.ui.theme.TextSecondary

@Composable
fun SearchTab(vm: AppViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var allChannels by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    var allMovies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var allSeries by remember { mutableStateOf<List<Series>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        allChannels = vm.allLive()
        allMovies = vm.allMovies()
        allSeries = vm.allSeries()
        loaded = true
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp)) {
        // Search input
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .background(Surface2, RoundedCornerShape(12.dp))
                .border(2.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                interactionSource = interaction,
                textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("Sök kanaler, filmer, serier...", color = TextSecondary, fontSize = 16.sp)
                    }
                    inner()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))

        if (!loaded) {
            Text("Laddar...", color = TextSecondary)
            return@Column
        }

        val q = query.trim().lowercase()
        if (q.length < 2) {
            Text("Skriv minst 2 tecken för att söka.", color = TextSecondary, fontSize = 14.sp)
            return@Column
        }

        val matchedChannels = remember(q, allChannels) {
            allChannels.filter { it.name.lowercase().contains(q) }.take(50)
        }
        val matchedMovies = remember(q, allMovies) {
            allMovies.filter { it.name.lowercase().contains(q) }.take(50)
        }
        val matchedSeries = remember(q, allSeries) {
            allSeries.filter { it.name.lowercase().contains(q) }.take(50)
        }

        val total = matchedChannels.size + matchedMovies.size + matchedSeries.size
        if (total == 0) {
            Text("Inga resultat för \"$query\".", color = TextSecondary, fontSize = 14.sp)
            return@Column
        }

        Text("$total resultat", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (matchedChannels.isNotEmpty()) {
                item { SectionHeader("Live-TV") }
                itemsIndexed(matchedChannels, key = { _, c -> "live-${c.streamId}" }) { index, ch ->
                    FocusRow(
                        onClick = {
                            vm.addRecent(ch.streamId)
                            vm.navigate(Screen.LivePlayer(matchedChannels, index))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { f ->
                        SearchResultRow(ch.name, ch.icon, "Kanal", f)
                    }
                }
            }
            if (matchedMovies.isNotEmpty()) {
                item { SectionHeader("Filmer") }
                itemsIndexed(matchedMovies, key = { _, m -> "movie-${m.streamId}" }) { _, movie ->
                    FocusRow(
                        onClick = {
                            vm.navigate(Screen.VodPlayer(
                                PlaybackItem(StreamKind.MOVIE, movie.streamId, movie.name, movie.containerExtension)
                            ))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { f ->
                        SearchResultRow(movie.name, movie.icon, "Film", f)
                    }
                }
            }
            if (matchedSeries.isNotEmpty()) {
                item { SectionHeader("Serier") }
                itemsIndexed(matchedSeries, key = { _, s -> "series-${s.seriesId}" }) { _, series ->
                    FocusRow(
                        onClick = { vm.navigate(Screen.SeriesDetail(series)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { f ->
                        SearchResultRow(series.name, series.cover, "Serie", f)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Accent,
        fontSize = 16.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SearchResultRow(name: String, imageUrl: String?, type: String, focused: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface2),
            contentAlignment = Alignment.Center,
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                )
            } else {
                Text(type.take(2), color = TextSecondary, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = if (focused) Color.White else TextPrimary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(type, color = TextSecondary, fontSize = 11.sp)
        }
    }
}

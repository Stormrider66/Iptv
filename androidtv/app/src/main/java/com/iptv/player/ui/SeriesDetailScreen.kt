package com.iptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.iptv.player.AppViewModel
import com.iptv.player.Screen
import com.iptv.player.data.PlaybackItem
import com.iptv.player.data.Season
import com.iptv.player.data.Series
import com.iptv.player.data.StreamKind
import com.iptv.player.ui.theme.Accent
import com.iptv.player.ui.theme.Surface1
import com.iptv.player.ui.theme.TextPrimary
import com.iptv.player.ui.theme.TextSecondary

@Composable
fun SeriesDetailScreen(vm: AppViewModel, series: Series) {
    var seasons by remember { mutableStateOf<List<Season>?>(null) }
    var selectedSeason by remember { mutableStateOf(0) }

    LaunchedEffect(series.seriesId) {
        seasons = try {
            vm.api?.getSeriesInfo(series.seriesId) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Text(series.name, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (!series.plot.isNullOrBlank()) {
            Text(
                series.plot,
                color = TextSecondary,
                fontSize = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(0.8f),
            )
        }

        val list = seasons
        when {
            list == null -> {
                Text("Laddar avsnitt...", color = TextSecondary, modifier = Modifier.padding(top = 24.dp))
            }
            list.isEmpty() -> {
                Text("Inga avsnitt hittades.", color = TextSecondary, modifier = Modifier.padding(top = 24.dp))
            }
            else -> {
                // Season selector
                LazyRow(
                    modifier = Modifier.padding(top = 20.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list.size) { idx ->
                        val season = list[idx]
                        FocusRow(onClick = { selectedSeason = idx }) { focused ->
                            Text(
                                text = "Säsong ${season.seasonNumber}",
                                color = when {
                                    focused -> Color.White
                                    selectedSeason == idx -> Accent
                                    else -> TextPrimary
                                },
                                fontSize = 15.sp,
                                fontWeight = if (selectedSeason == idx) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }

                // Episodes
                val season = list.getOrNull(selectedSeason) ?: return@Column
                LazyColumn(
                    modifier = Modifier.padding(top = 12.dp).fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(season.episodes, key = { it.id }) { ep ->
                        FocusRow(
                            onClick = {
                                vm.navigate(
                                    Screen.VodPlayer(
                                        PlaybackItem(
                                            kind = StreamKind.EPISODE,
                                            id = ep.id,
                                            title = "${series.name} — ${ep.title}",
                                            containerExtension = ep.containerExtension,
                                        )
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { focused ->
                            Text(
                                text = "${ep.episodeNumber}. ${ep.title}",
                                color = if (focused) Color.White else TextPrimary,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

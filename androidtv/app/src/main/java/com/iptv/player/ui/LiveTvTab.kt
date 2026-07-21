package com.iptv.player.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.iptv.player.AppViewModel
import com.iptv.player.Screen
import com.iptv.player.data.Category
import com.iptv.player.data.EpgEntry
import com.iptv.player.data.LiveChannel
import com.iptv.player.ui.theme.Accent2
import com.iptv.player.ui.theme.Surface1
import com.iptv.player.ui.theme.Surface2
import com.iptv.player.ui.theme.TextPrimary
import com.iptv.player.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val CAT_RECENT = "__recent__"
private const val CAT_FAV = "__fav__"

@Composable
fun LiveTvTab(vm: AppViewModel) {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var allChannels by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // rememberSaveable so returning from the player restores the browsed category
    var selectedCat by rememberSaveable { mutableStateOf(CAT_RECENT) }
    var focused by remember { mutableStateOf<LiveChannel?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        val cats = vm.liveCategories()
        val chans = vm.allLive()
        categories = buildList {
            add(Category(CAT_RECENT, "Senaste"))
            add(Category(CAT_FAV, "Favoriter"))
            addAll(cats)
        }
        allChannels = chans
        loading = false
    }

    if (loading) {
        CenterMessage("Laddar kanaler...")
        return
    }

    val byId = remember(allChannels) { allChannels.associateBy { it.streamId } }
    // Read the snapshot lists here so favouriting / watching re-runs this block.
    val favSnapshot = vm.favorites.toList()
    val recentSnapshot = vm.recents.toList()
    val visible = remember(selectedCat, allChannels, favSnapshot, recentSnapshot) {
        when (selectedCat) {
            CAT_RECENT -> recentSnapshot.mapNotNull { byId[it] }
            CAT_FAV -> favSnapshot.mapNotNull { byId[it] }
            else -> allChannels.filter { it.categoryId == selectedCat }
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        CategoryPanel(
            categories = categories,
            selectedId = selectedCat,
            onSelect = { selectedCat = it.id },
        )

        // Channel list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (visible.isEmpty()) {
                item {
                    Text(
                        text = when (selectedCat) {
                            CAT_RECENT -> "Inga nyligen sedda kanaler ännu."
                            CAT_FAV -> "Inga favoriter ännu. Håll OK på en kanal för att lägga till."
                            else -> "Inga kanaler i den här kategorin."
                        },
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            itemsIndexed(visible, key = { _, c -> c.streamId }) { index, channel ->
                ChannelRow(
                    channel = channel,
                    isFavorite = favSnapshot.contains(channel.streamId),
                    onClick = {
                        vm.addRecent(channel.streamId)
                        vm.navigate(Screen.LivePlayer(visible, index))
                    },
                    onLongClick = { vm.toggleFavorite(channel.streamId) },
                    onFocus = { focused = channel },
                )
            }
        }

        // Detail / EPG panel
        ChannelDetail(vm, focused)
    }
}

@Composable
private fun ChannelRow(
    channel: LiveChannel,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocus: () -> Unit,
) {
    FocusRow(
        onClick = onClick,
        onLongClick = onLongClick,
        onFocus = onFocus,
        modifier = Modifier.fillMaxWidth(),
    ) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface2),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.icon.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.icon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                    )
                } else {
                    Text("TV", color = TextSecondary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = channel.name,
                color = if (focused) androidx.compose.ui.graphics.Color.White else TextPrimary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isFavorite) {
                Text("★", color = Accent2, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun ChannelDetail(vm: AppViewModel, channel: LiveChannel?) {
    Column(
        modifier = Modifier
            .width(360.dp)
            .fillMaxHeight()
            .background(Surface1)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (channel == null) {
            Text("Välj en kanal", color = TextSecondary)
            return@Column
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface2),
            contentAlignment = Alignment.Center,
        ) {
            if (!channel.icon.isNullOrBlank()) {
                AsyncImage(
                    model = channel.icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                )
            } else {
                Text("TV", color = TextSecondary, fontSize = 22.sp)
            }
        }

        Text(channel.name, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)

        var epg by remember(channel.streamId) { mutableStateOf<List<EpgEntry>?>(null) }
        LaunchedEffect(channel.streamId) {
            epg = try {
                vm.api?.getShortEpg(channel.streamId, 3)
            } catch (e: Exception) {
                emptyList()
            }
        }

        val list = epg
        when {
            list == null -> Text("Laddar program...", color = TextSecondary, fontSize = 13.sp)
            list.isEmpty() -> Text("Ingen programinformation.", color = TextSecondary, fontSize = 13.sp)
            else -> list.forEachIndexed { i, e ->
                Column {
                    Text(
                        text = (if (i == 0) "NU: " else "") + timeRange(e) + "  " + e.title,
                        color = if (i == 0) Accent2 else TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun timeRange(e: EpgEntry): String {
    if (e.startMillis <= 0L) return ""
    val start = timeFmt.format(Date(e.startMillis))
    val end = if (e.endMillis > 0L) timeFmt.format(Date(e.endMillis)) else ""
    return if (end.isNotBlank()) "$start-$end" else start
}

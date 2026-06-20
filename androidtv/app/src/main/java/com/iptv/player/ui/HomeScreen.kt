package com.iptv.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.iptv.player.AppViewModel

private val tabs = listOf("Live-TV", "Filmer", "Serier", "Sök", "Inställningar")

@Composable
fun HomeScreen(vm: AppViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.padding(start = 32.dp, top = 18.dp, bottom = 6.dp),
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onFocus = { selectedTab = index },
                    onClick = { selectedTab = index },
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().fillMaxSize()) {
            when (selectedTab) {
                0 -> LiveTvTab(vm)
                1 -> MoviesTab(vm)
                2 -> SeriesTab(vm)
                3 -> SearchTab(vm)
                4 -> SettingsTab(vm)
            }
        }
    }
}

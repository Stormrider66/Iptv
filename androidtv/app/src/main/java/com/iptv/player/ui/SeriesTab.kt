package com.iptv.player.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iptv.player.AppViewModel
import com.iptv.player.Screen
import com.iptv.player.data.Category
import com.iptv.player.data.Series

@Composable
fun SeriesTab(vm: AppViewModel) {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var allSeries by remember { mutableStateOf<List<Series>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedCat by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        val cats = vm.seriesCategories()
        val series = vm.allSeries()
        categories = cats
        allSeries = series
        if (selectedCat == null) selectedCat = cats.firstOrNull()?.id
        loading = false
    }

    if (loading) {
        CenterMessage("Laddar serier...")
        return
    }
    if (allSeries.isEmpty()) {
        CenterMessage("Inga serier tillgängliga.")
        return
    }

    val visible = remember(selectedCat, allSeries) {
        allSeries.filter { selectedCat == null || it.categoryId == selectedCat }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        CategoryPanel(
            categories = categories,
            selectedId = selectedCat,
            onSelect = { selectedCat = it.id },
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            items(visible, key = { it.seriesId }) { series ->
                PosterCard(
                    title = series.name,
                    imageUrl = series.cover,
                    onClick = { vm.navigate(Screen.SeriesDetail(series)) },
                )
            }
        }
    }
}

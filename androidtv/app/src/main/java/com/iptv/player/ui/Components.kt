package com.iptv.player.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.iptv.player.data.Category
import com.iptv.player.ui.theme.Accent
import com.iptv.player.ui.theme.Surface1
import com.iptv.player.ui.theme.Surface2
import com.iptv.player.ui.theme.TextPrimary
import com.iptv.player.ui.theme.TextSecondary

/** A focusable, clickable row that highlights when focused. */
@Composable
fun FocusRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    content: @Composable (focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    if (onFocus != null) {
        androidx.compose.runtime.LaunchedEffect(focused) {
            if (focused) onFocus()
        }
    }
    // combinedClickable only raises onLongClick from pointer input and accessibility -
    // its key handling knows about clicks alone. On a D-pad remote there is no pointer,
    // so holding OK just produced a click and long-press actions were unreachable.
    // Detect the hold ourselves from the auto-repeat, ahead of combinedClickable.
    val longPressFired = remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Accent else Color.Transparent)
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                val isSelect = native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    native.keyCode == KeyEvent.KEYCODE_ENTER ||
                    native.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                if (onLongClick == null || !isSelect) return@onPreviewKeyEvent false
                when (native.action) {
                    KeyEvent.ACTION_DOWN -> when {
                        // Swallow every repeat after the one that triggered the hold.
                        longPressFired.value -> true
                        native.repeatCount >= 1 -> {
                            longPressFired.value = true
                            onLongClick()
                            true
                        }
                        // Let the initial press through so a tap still clicks.
                        else -> false
                    }
                    // Eat the release that would otherwise fire onClick as well.
                    KeyEvent.ACTION_UP -> if (longPressFired.value) {
                        longPressFired.value = false
                        true
                    } else {
                        false
                    }
                    else -> false
                }
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        content(focused)
    }
}

/** Left-hand category list used by every browse tab. */
@Composable
fun CategoryPanel(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(Surface1)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(categories, key = { it.id }) { cat ->
            FocusRow(
                onClick = { onSelect(cat) },
                modifier = Modifier.fillMaxWidth(),
            ) { focused ->
                val selected = cat.id == selectedId
                Text(
                    text = cat.name,
                    color = if (focused) Color.White else if (selected) Accent else TextPrimary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun CenterMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = TextSecondary, fontSize = 16.sp)
    }
}

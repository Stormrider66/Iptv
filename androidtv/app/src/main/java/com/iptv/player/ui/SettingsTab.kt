package com.iptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.iptv.player.AppViewModel
import com.iptv.player.ui.theme.Accent
import com.iptv.player.ui.theme.Surface1
import com.iptv.player.ui.theme.TextPrimary
import com.iptv.player.ui.theme.TextSecondary

@Composable
fun SettingsTab(vm: AppViewModel) {
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Inställningar", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Column(
            modifier = Modifier
                .width(520.dp)
                .background(Surface1, RoundedCornerShape(14.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Konto", color = TextSecondary, fontSize = 13.sp)
            Text(vm.prefs.server, color = TextPrimary, fontSize = 15.sp)
            Text("Användare: ${vm.prefs.username}", color = TextPrimary, fontSize = 15.sp)
        }

        SettingsButton("Rensa favoriter") {
            vm.clearFavorites()
            message = "Favoriter rensade."
        }
        SettingsButton("Rensa senaste & fortsätt titta") {
            vm.clearRecents()
            message = "Historik rensad."
        }
        SettingsButton("Logga ut") {
            vm.logout()
        }

        if (message.isNotBlank()) {
            Text(message, color = Accent, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        }

        Text(
            "Tips: Tryck MENY-knappen under uppspelning för bild-i-bild. " +
                "Upp/Ner byter kanal, OK pausar, Vänster/Höger spolar i filmer.",
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 16.dp).width(520.dp),
        )
    }
}

@Composable
private fun SettingsButton(label: String, onClick: () -> Unit) {
    FocusRow(onClick = onClick, modifier = Modifier.width(520.dp)) { focused ->
        Text(
            text = label,
            color = if (focused) androidx.compose.ui.graphics.Color.White else TextPrimary,
            fontSize = 16.sp,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

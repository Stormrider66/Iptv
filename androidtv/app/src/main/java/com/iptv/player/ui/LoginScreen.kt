package com.iptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptv.player.AppViewModel
import com.iptv.player.data.XtreamApi
import com.iptv.player.ui.theme.Accent
import com.iptv.player.ui.theme.Surface1
import com.iptv.player.ui.theme.Surface2
import com.iptv.player.ui.theme.TextPrimary
import com.iptv.player.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(vm: AppViewModel) {
    var server by remember { mutableStateOf(vm.prefs.server.ifBlank { "http://" }) }
    var username by remember { mutableStateOf(vm.prefs.username) }
    var password by remember { mutableStateOf(vm.prefs.password) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .background(Surface1, RoundedCornerShape(20.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "IPTV Player",
                color = TextPrimary,
                fontSize = 26.sp,
            )
            Text(
                text = "Anslut till din IPTV-tjänst",
                color = TextSecondary,
                fontSize = 14.sp,
            )

            LabeledField("Server URL", server, KeyboardType.Uri) { server = it }
            LabeledField("Användarnamn", username, KeyboardType.Text) { username = it }
            LabeledField("Lösenord", password, KeyboardType.Password, isPassword = true) { password = it }

            Button(
                onClick = {
                    if (loading) return@Button
                    val s = server.trim().trimEnd('/')
                    val u = username.trim()
                    val p = password.trim()
                    if (s.isBlank() || u.isBlank() || p.isBlank()) {
                        error = "Fyll i alla fält"
                        return@Button
                    }
                    loading = true
                    error = "Ansluter..."
                    scope.launch {
                        val ok = try {
                            XtreamApi(s, u, p).login()
                        } catch (e: Exception) {
                            false
                        }
                        loading = false
                        if (ok) {
                            vm.onLoginSuccess(s, u, p)
                        } else {
                            error = "Kunde inte ansluta. Kontrollera uppgifterna."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Text(if (loading) "Ansluter..." else "Anslut")
            }

            if (error.isNotBlank()) {
                Text(text = error, color = Accent, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
    onChange: (String) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = TextSecondary, fontSize = 12.sp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .background(Surface2, RoundedCornerShape(10.dp))
                .border(
                    width = 2.dp,
                    color = if (focused) Accent else Surface2,
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                interactionSource = interaction,
                textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

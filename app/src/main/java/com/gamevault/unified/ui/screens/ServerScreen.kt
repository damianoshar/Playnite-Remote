package com.gamevault.unified.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gamevault.unified.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(vm: LibraryViewModel) {
    val state by vm.ui.collectAsState()
    val appContext = LocalContext.current.applicationContext
    var baseUrl by remember(state.server) { mutableStateOf(state.server?.baseUrl ?: "http://192.168.1.50:19821") }
    var token by remember(state.server) { mutableStateOf(state.server?.token ?: "") }

    // Zero insets here: the outer AppNav Scaffold already pads for the status
    // and navigation bars. Consuming them again in this nested Scaffold
    // stacked a phantom 104px strip above the bottom bar (plus a dead gap
    // under the status bar from the TopAppBar's own inset padding).
    Scaffold(
        topBar = { TopAppBar(title = { Text("PC Server") }, windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)) },
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Playnite Bridge connection", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("PC address") },
                        placeholder = { Text("http://192.168.1.50:19821") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Bearer token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.connectionTest?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        enabled = baseUrl.isNotBlank() && token.isNotBlank(),
                        onClick = { vm.saveServer(baseUrl, token) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save & Connect") }
                    if (state.server != null) {
                        OutlinedButton(
                            onClick = { vm.disconnect() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Disconnect") }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Full offline mode", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Downloads every game's info plus all cover art to the phone. " +
                            "Takes a while on big libraries — keep the app open until it finishes.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (state.fullOffline) "On" else "Off")
                        Switch(
                            checked = state.fullOffline,
                            onCheckedChange = {
                                if (it) {
                                    vm.setFullOffline(true)
                                    vm.startFullOfflineSync(appContext)
                                } else {
                                    vm.cancelFullOfflineSync()
                                    vm.setFullOffline(false)
                                }
                            }
                        )
                    }
                    val progress = state.offlineProgress
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = {
                                if (progress.total > 0) progress.done.toFloat() / progress.total else 0f
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${progress.phase}: ${progress.done}/${progress.total}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedButton(onClick = { vm.cancelFullOfflineSync() }) {
                                Text("Cancel")
                            }
                        }
                    } else if (state.lastFullSync.isNotBlank()) {
                        Text(
                            "Offline-ready • synced ${state.lastFullSync}.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(
                            onClick = { vm.startFullOfflineSync(appContext) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Refresh offline data") }
                    }
                    var confirmDelete by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Delete offline data") }
                    if (state.lastDeleted.isNotBlank()) {
                        Text(
                            state.lastDeleted,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (confirmDelete) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { confirmDelete = false },
                            title = { Text("Delete offline data?") },
                            text = {
                                Text(
                                    "Removes the library snapshot, cached details and all " +
                                        "downloaded covers. Your server connection is kept."
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = { confirmDelete = false; vm.clearOfflineData(appContext) },
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) { Text("Delete") }
                            },
                            dismissButton = {
                                OutlinedButton(onClick = { confirmDelete = false }) { Text("Keep") }
                            }
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("PC setup", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "1. Install Playnite on your PC and link your stores.\n" +
                            "2. Install the playnite-bridge plugin (.pext from its GitHub releases) and restart Playnite.\n" +
                            "3. In Playnite: Settings → Plugins → Playnite Bridge → copy the token.\n" +
                            "4. Allow TCP 19821 in Windows Firewall; find your PC IP with ipconfig.\n" +
                            "5. Paste IP + token above. Same Wi-Fi or Tailscale required.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Security", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "The token grants full control (launch, install, delete, remote code eval). " +
                            "Use only on trusted home LAN or Tailscale. Never expose port 19821 to the internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

package com.quantummpv.app.ui.browser.networkstreaming

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quantummpv.app.domain.syncplay.SyncplayManager
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncplaySheet(
    onDismiss: () -> Unit,
    syncplayManager: SyncplayManager = koinInject()
) {
    val state by syncplayManager.state.collectAsState()
    
    var host by remember { mutableStateOf("syncplay.pl") }
    var port by remember { mutableStateOf("8999") }
    var username by remember { mutableStateOf("MpvRxUser") }
    var room by remember { mutableStateOf("TestRoom") }
    var password by remember { mutableStateOf("") }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        windowInsets = WindowInsets.navigationBars
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Syncplay",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (state.isConnected) {
                Text("Connected as ${state.username} in room '${state.room}'")
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Users in room:", style = MaterialTheme.typography.titleMedium)
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(state.users) { user ->
                        Text("- $user", modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { syncplayManager.disconnect() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Disconnect")
                }
            } else {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Server Host") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Room Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (state.error != null) {
                    Text(
                        text = "Error: ${state.error}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                Button(
                    onClick = { 
                        syncplayManager.connect(host, port.toIntOrNull() ?: 8999, username, room, password)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect")
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

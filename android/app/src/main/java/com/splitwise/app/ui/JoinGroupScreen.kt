package com.splitwise.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitwise.app.data.JoinResult

/** Opened from an invitation link: confirms the group and joins it with one tap. */
@Composable
fun JoinGroupScreen(vm: JoinViewModel, onJoined: (JoinResult) -> Unit, onDismiss: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.joined) { state.joined?.let(onJoined) }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        when (val info = state.info) {
            Load.Loading -> CircularProgressIndicator()
            is Load.Error -> {
                Text("Invitation unavailable", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(info.message, color = MaterialTheme.split.fg2, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                PrimaryButton("Continue", onDismiss)
            }
            is Load.Ready -> {
                Box(Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.split.primaryBg), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.GroupAdd, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(20.dp))
                Text("Join \"${info.data.groupName}\"?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("${info.data.inviterName} invited you to share expenses in this group.", color = MaterialTheme.split.fg2, textAlign = TextAlign.Center)
                state.error?.let { Spacer(Modifier.height(16.dp)); ErrorBanner(it) }
                Spacer(Modifier.height(24.dp))
                PrimaryButton("Join group", vm::join, busy = state.joining)
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
        }
    }
}

package com.splitwise.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.splitwise.app.data.Group
import com.splitwise.app.data.Member

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupSettingsScreen(
    group: Group, members: List<Member>, userId: Int?,
    onSave: (String, String, String) -> Unit, onRemove: (Int) -> Unit, onDelete: () -> Unit, onBack: () -> Unit,
) {
    val isOwner = group.createdBy == userId
    var name by rememberSaveable { mutableStateOf(group.name) }
    var description by rememberSaveable { mutableStateOf(group.description) }
    var icon by rememberSaveable { mutableStateOf(group.emoji()) }
    var removing by remember { mutableStateOf<Member?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val changed = name != group.name || description != group.description || icon != group.emoji()

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        NavBar("Group Settings", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IconField(name, { name = it }, "Group name", Icons.Default.Edit)
            IconField(description, { description = it }, "Description", Icons.Default.Edit)
            Text("Icon", style = MaterialTheme.typography.titleSmall)
            IconPicker(icon) { icon = it }
            PrimaryButton("Save changes", { onSave(name, description, icon) }, enabled = changed && name.isNotBlank())

            SectionHeader("Members", Modifier.padding(horizontal = 0.dp))
            SplitCard(Modifier.fillMaxWidth()) {
                members.forEachIndexed { i, m ->
                    if (i > 0) Divider16()
                    PersonRow(m.id, m.username, m.name) {
                        when {
                            m.id == group.createdBy -> Text("Creator", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            isOwner -> SmallButton("Remove", { removing = m }, primary = false)
                        }
                    }
                }
            }
            if (!isOwner) Text("Only the group creator can remove members or delete the group.", color = MaterialTheme.split.fg3, style = MaterialTheme.typography.bodySmall)

            if (isOwner) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).clickable { confirmDelete = true },
                    contentAlignment = Alignment.Center,
                ) { Text("Delete group", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    removing?.let { m ->
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Remove ${m.username}?") },
            text = { Text("They'll lose access to this group and its expenses.") },
            confirmButton = { TextButton(onClick = { removing = null; onRemove(m.id) }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Delete \"${group.name}\"?") },
        text = { Text("This permanently deletes the group, all its expenses and payments for every member. This cannot be undone.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete group", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

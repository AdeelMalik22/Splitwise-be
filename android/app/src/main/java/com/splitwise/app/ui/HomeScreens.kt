package com.splitwise.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitwise.app.data.Group
import java.math.BigDecimal

// ───────────────────────── Home ─────────────────────────

@Composable
fun HomeScreen(overview: Overview, userId: Int?, onOpenGroup: (Group) -> Unit, onOpenGroups: () -> Unit, onOpenInvites: () -> Unit, onOpenActivity: () -> Unit, onRespond: (com.splitwise.app.data.Invite, Boolean) -> Unit, onCreateGroup: () -> Unit) {
    val me = overview.me
    val pending = overview.pendingFor(userId)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp, 16.dp, 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (overview.groups.isEmpty()) "Welcome," else "Hello,", color = MaterialTheme.split.fg2, style = MaterialTheme.typography.bodySmall)
                    Text(me?.name?.ifBlank { null }?.substringBefore(' ') ?: me?.username ?: "there", style = MaterialTheme.typography.headlineSmall)
                }
                if (me != null) Avatar(initials(me.name, me.username), me.id, 40.dp)
            }
        }
        if (overview.groups.isEmpty()) {
            item { EmptyHome(onCreateGroup) }
            return@LazyColumn
        }
        item { BalanceCard(overview) }
        if (pending.isNotEmpty()) item { InviteBanner(pending.first(), pending.size, onRespond, onOpenInvites) }
        item { SectionHeader("Your Groups", link = "See all", onLink = onOpenGroups) }
        item {
            SplitCard(Modifier.padding(horizontal = 16.dp)) {
                overview.groups.take(4).forEachIndexed { i, g ->
                    if (i > 0) Divider16()
                    GroupRow(g, overview, onClick = { onOpenGroup(g) })
                }
            }
        }
        item { SectionHeader("Recent Activity", link = "All activity", onLink = onOpenActivity) }
        item {
            val recent = overview.notifications.take(3)
            SplitCard(Modifier.padding(horizontal = 16.dp)) {
                if (recent.isEmpty()) EmptyState("No activity yet.")
                recent.forEachIndexed { i, n ->
                    if (i > 0) Divider16()
                    NotificationRow(n.message, dayLabel(n.createdAt), unread = n.readAt == null)
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(o: Overview) {
    val owe = o.owe(); val owed = o.owed(); val net = owed - owe
    Box(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primary),
    ) {
        Box(Modifier.align(Alignment.TopEnd).offset(32.dp, (-32).dp).size(160.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)))
        Column(Modifier.padding(20.dp)) {
            Text("Overall balance", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium))
            Text(
                (if (net.signum() < 0) "−" else "") + formatRs(net.abs(), forceDecimals = true),
                Modifier.padding(top = 6.dp), color = Color.White,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, letterSpacing = (-1.2).sp),
            )
            Text(
                when { net.signum() > 0 -> "You are owed overall"; net.signum() < 0 -> "You owe overall"; else -> "You're all settled up" },
                Modifier.padding(top = 4.dp, bottom = 20.dp), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            Row(Modifier.padding(top = 16.dp)) {
                BalanceCell("You owe", formatRs(owe), Color(0xFFFCD34D), Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(36.dp).background(Color.White.copy(alpha = 0.15f)))
                BalanceCell("Owed to you", formatRs(owed), Color(0xFF86EFAC), Modifier.weight(1f).padding(start = 16.dp))
            }
        }
    }
}

@Composable
private fun BalanceCell(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium))
        Text(value, Modifier.padding(top = 4.dp), color = color, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun InviteBanner(invite: com.splitwise.app.data.Invite, count: Int, onRespond: (com.splitwise.app.data.Invite, Boolean) -> Unit, onOpen: () -> Unit) {
    val c = MaterialTheme.split
    Column(
        Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.primaryBg)
            .clickable(onClick = onOpen).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.GroupAdd, null, Modifier.size(18.dp), tint = Color.White)
            }
            Column {
                Text("${invite.inviterName.ifBlank { invite.inviterUsername }} invited you to ${invite.groupName}", style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp))
                Text(if (count > 1) "+${count - 1} more invite${if (count > 2) "s" else ""}" else "Tap to view", color = c.fg2, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Accept", { onRespond(invite, true) }, Modifier.weight(1f))
            SmallButton("Decline", { onRespond(invite, false) }, primary = false)
        }
    }
}

@Composable
fun GroupRow(group: Group, overview: Overview, onClick: () -> Unit) {
    val net = overview.net(group.id)
    val members = overview.members[group.id].orEmpty()
    val c = MaterialTheme.split
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.primaryBg), contentAlignment = Alignment.Center) {
            Text(emojiFor(group.id), fontSize = 22.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(group.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text("${members.size} member${if (members.size == 1) "" else "s"}", color = c.fg2, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            when {
                net.signum() > 0 -> { Text(formatRs(net), color = c.owed, style = MaterialTheme.typography.titleMedium); Text("you get back", color = c.owed, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)) }
                net.signum() < 0 -> { Text(formatRs(net.abs()), color = c.owe, style = MaterialTheme.typography.titleMedium); Text("you owe", color = c.owe, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)) }
                else -> Text("settled up", color = c.fg3, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
fun NotificationRow(message: String, time: String, unread: Boolean, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.split.primaryBg), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Receipt, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(message, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text(time, color = MaterialTheme.split.fg3, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal), modifier = Modifier.padding(top = 2.dp))
        }
        if (unread) Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        trailing?.invoke()
    }
}

@Composable
private fun EmptyHome(onCreateGroup: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.padding(top = 24.dp).size(88.dp).clip(CircleShape).background(MaterialTheme.split.primaryBg), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Groups, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text("You're all set!", style = MaterialTheme.typography.headlineSmall)
        Text("Start by creating a group, then add shared expenses to keep track automatically.", color = MaterialTheme.split.fg2, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        PrimaryButton("Create your first group", onCreateGroup)
        SplitCard(Modifier.fillMaxWidth()) {
            SectionHeader("How it works")
            listOf("Create a group" to "Add a name and invite your friends.", "Log expenses" to "Tap + to add what was spent and who paid.", "Settle up" to "See exactly who owes whom and clear debts.").forEachIndexed { i, (t, d) ->
                Row(Modifier.padding(16.dp, 4.dp, 16.dp, 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Text("${i + 1}", color = Color.White, style = MaterialTheme.typography.labelMedium) }
                    Column { Text(t, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp)); Text(d, color = MaterialTheme.split.fg2, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

// ───────────────────────── Groups list ─────────────────────────

@Composable
fun GroupsScreen(overview: Overview, onOpenGroup: (Group) -> Unit, onNew: () -> Unit, onInvites: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = overview.groups.filter { it.name.contains(query.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        NavBar("Groups", onBack = null) {
            SmallButton("Invites", onInvites, primary = false)
            SmallButton("+ New", onNew)
        }
        OutlinedTextField(
            query, { query = it }, singleLine = true, placeholder = { Text("Search groups", color = MaterialTheme.split.fg3) },
            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = MaterialTheme.split.fg3) },
            shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.split.input, focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedBorderColor = MaterialTheme.colorScheme.outline),
        )
        Spacer(Modifier.height(16.dp))
        if (shown.isEmpty()) EmptyState(if (overview.groups.isEmpty()) "No groups yet. Tap + New to create one." else "No groups match your search.")
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shown, key = { it.id }) { g ->
                SplitCard { GroupRow(g, overview, onClick = { onOpenGroup(g) }) }
            }
            item {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onNew)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("+  Create a new group", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun CreateGroupDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("New group", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Group name") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, singleLine = true, shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, description) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

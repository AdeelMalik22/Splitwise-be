package com.splitwise.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitwise.app.data.Group
import com.splitwise.app.data.Invite
import java.math.BigDecimal
import java.math.RoundingMode

// ───────────────────────── Add expense ─────────────────────────

@Composable
fun AddExpenseScreen(vm: AddExpenseViewModel, groups: List<Group>, initialGroupId: Int?, userId: Int?, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    LaunchedEffect(groups, initialGroupId) {
        (initialGroupId?.takeIf { id -> groups.any { it.id == id } } ?: groups.firstOrNull()?.id)?.let(vm::selectGroup)
    }

    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var paidBy by rememberSaveable { mutableStateOf<Int?>(userId) }
    var splitOn by rememberSaveable(state.groupId) { mutableStateOf<List<Int>?>(null) }
    val c = MaterialTheme.split

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        NavBar("Add Expense", onBack) {
            val members = (state.members as? Load.Ready)?.data.orEmpty()
            SmallButton("Save", { vm.save(name, description, amount, paidBy, (splitOn ?: members.map { it.id }).toSet()) })
        }
        if (groups.isEmpty()) return@Column EmptyState("Create a group first, then add expenses to it.")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SplitCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total amount", color = c.fg2, style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rs", color = c.fg2, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(6.dp))
                        BasicAmountField(amount) { amount = it }
                    }
                }
            }
            state.error?.let { ErrorBanner(it) }
            IconField(name, { name = it }, "Expense name", Icons.Default.Receipt)
            IconField(description, { description = it }, "Description (optional)", Icons.Default.Notes)

            Text("Group", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { g ->
                    SelectChip("${emojiFor(g.id)}  ${g.name}", selected = g.id == state.groupId) { vm.selectGroup(g.id); paidBy = userId }
                }
            }

            LoadView(state.members, onRetry = { state.groupId?.let(vm::selectGroup) }, modifier = Modifier.heightIn(min = 120.dp)) { members ->
                val selected = splitOn ?: members.map { it.id }
                val total = amount.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: BigDecimal.ZERO
                val each = if (selected.isEmpty()) BigDecimal.ZERO else total.divide(BigDecimal(selected.size), 2, RoundingMode.HALF_UP)
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Paid by", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        members.forEach { m -> PersonPick(m.id, initials(m.name, m.username), if (m.id == userId) "You" else m.username, paidBy == m.id) { paidBy = m.id } }
                    }
                    Text("Split between", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        members.forEach { m ->
                            val on = m.id in selected
                            PersonPick(m.id, initials(m.name, m.username), if (m.id == userId) "You" else m.username, on) {
                                splitOn = if (on) selected - m.id else selected + m.id
                            }
                        }
                    }
                    if (selected.isNotEmpty() && total.signum() > 0) {
                        SplitCard(Modifier.fillMaxWidth()) {
                            Text("Equally split — ${formatRs(each, forceDecimals = true)} each", Modifier.padding(16.dp, 14.dp, 16.dp, 6.dp), color = c.fg2, style = MaterialTheme.typography.titleSmall)
                            members.filter { it.id in selected }.forEach { m ->
                                Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Avatar(initials(m.name, m.username), m.id, 32.dp)
                                    Text(m.username + if (m.id == userId) " (you)" else "", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(formatRs(each, forceDecimals = true), style = MaterialTheme.typography.titleSmall)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
            PrimaryButton("Save expense", { 
                val members = (state.members as? Load.Ready)?.data.orEmpty()
                vm.save(name, description, amount, paidBy, (splitOn ?: members.map { it.id }).toSet())
            }, busy = state.busy)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BasicAmountField(value: String, onChange: (String) -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = { v -> if (v.matches(Regex("""\d{0,9}([.]\d{0,2})?"""))) onChange(v) },
        singleLine = true,
        textStyle = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (value.isEmpty()) Text("0", color = MaterialTheme.split.fg3, style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp)); inner() } },
        modifier = Modifier.widthIn(min = 60.dp, max = 220.dp).width(IntrinsicSize.Min),
    )
}

@Composable
private fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.split
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) c.primaryBg else MaterialTheme.colorScheme.surface)
            .border(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
    ) { Text(text, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall) }
}

@Composable
private fun PersonPick(id: Int, initials: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            Avatar(initials, id, 48.dp, Modifier.then(if (selected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier.background(Color.Transparent)))
            if (selected) Box(Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, Modifier.size(12.dp), tint = Color.White)
            }
        }
        Text(label, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.split.fg3, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

// ───────────────────────── Invites ─────────────────────────

@Composable
fun InvitesScreen(overview: Overview, userId: Int?, search: InviteSearchViewModel, onRespond: (Invite, Boolean) -> Unit, onSent: () -> Unit, onBack: () -> Unit) {
    val state by search.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); search.messageShown() } }
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var groupId by rememberSaveable { mutableStateOf(overview.groups.firstOrNull()?.id) }
    val received = overview.invites.filter { it.invitee == userId }
    val sent = overview.invites.filter { it.inviter == userId }
    val pendingCount = received.count { it.status == "pending" }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.padding(padding).systemBarsPadding().fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { NavBar("Invites", onBack) }
            item {
                OutlinedTextField(
                    query, { query = it; search.search(it) }, singleLine = true,
                    placeholder = { Text("Search by name or @username", color = MaterialTheme.split.fg3) },
                    leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = MaterialTheme.split.fg3) },
                    shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.split.input, focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedBorderColor = MaterialTheme.colorScheme.outline),
                )
            }
            if (query.trim().length >= 2) {
                item {
                    if (overview.groups.isNotEmpty()) {
                        Text("Invite to", Modifier.padding(16.dp, 16.dp, 16.dp, 6.dp), color = MaterialTheme.split.fg2, style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            overview.groups.forEach { g -> SelectChip("${emojiFor(g.id)}  ${g.name}", g.id == groupId) { groupId = g.id } }
                        }
                    }
                }
                if (state.searched && state.results.isEmpty()) item { EmptyState("No users match \"$query\".") }
                items(state.results, key = { "s${it.id}" }) { u ->
                    PersonRow(u.id, u.username, u.name) {
                        SmallButton("Invite", { groupId?.let { gid -> search.invite(gid, u) { query = ""; onSent() } } })
                    }
                }
            }
            item {
                Row(Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip("Received ($pendingCount)", tab == 0) { tab = 0 }
                    SelectChip("Sent (${sent.size})", tab == 1) { tab = 1 }
                }
            }
            val list = if (tab == 0) received else sent
            if (list.isEmpty()) item { EmptyState(if (tab == 0) "No invites yet." else "You haven't invited anyone yet.") }
            items(list, key = { "i${it.id}" }) { invite -> InviteCard(invite, received = tab == 0, onRespond = onRespond) }
        }
    }
}

@Composable
private fun InviteCard(invite: Invite, received: Boolean, onRespond: (Invite, Boolean) -> Unit) {
    val who = if (received) invite.inviterName.ifBlank { invite.inviterUsername } else invite.inviteeUsername
    SplitCard(Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp).fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(initials("", who), if (received) invite.inviter else invite.invitee, 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(if (received) "$who invited you to join ${invite.groupName}" else "You invited $who to ${invite.groupName}", style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp))
                }
                if (!received || invite.status != "pending") StatusChip(invite.status.replaceFirstChar { it.uppercase() }, when (invite.status) { "accepted" -> ChipKind.Done; "declined" -> ChipKind.Declined; else -> ChipKind.Pending })
            }
            if (received && invite.status == "pending") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("Accept", { onRespond(invite, true) }, Modifier.weight(1f))
                    SmallButton("Decline", { onRespond(invite, false) }, Modifier.weight(1f), primary = false)
                }
            }
        }
    }
}

// ───────────────────────── Activity ─────────────────────────

@Composable
fun ActivityScreen(overview: Overview, onMarkRead: (com.splitwise.app.data.AppNotification) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val unread = overview.notifications.filter { it.readAt == null }
    Column(Modifier.fillMaxSize()) {
        NavBar("Activity", onBack = null) {
            if (unread.isNotEmpty()) Text("Mark all read", Modifier.clickable { unread.forEach(onMarkRead) }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectChip("Activity", tab == 0) { tab = 0 }
            SelectChip("Notifications${if (unread.isNotEmpty()) " (${unread.size})" else ""}", tab == 1) { tab = 1 }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)) {
            if (tab == 0) {
                if (overview.activity.isEmpty()) item { EmptyState("Your actions will show up here.") }
                else item {
                    SplitCard {
                        overview.activity.forEachIndexed { i, a ->
                            if (i > 0) Divider16()
                            NotificationRow("You ${a.action} ${a.entityType}${a.entityId?.let { " #$it" } ?: ""}", dayLabel(a.createdAt), unread = false)
                        }
                    }
                }
            } else {
                if (overview.notifications.isEmpty()) item { EmptyState("No notifications.") }
                else item {
                    SplitCard {
                        overview.notifications.forEachIndexed { i, n ->
                            if (i > 0) Divider16()
                            Box(Modifier.clickable(enabled = n.readAt == null) { onMarkRead(n) }) { NotificationRow(n.message, dayLabel(n.createdAt), unread = n.readAt == null) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ───────────────────────── Account ─────────────────────────

@Composable
fun AccountScreen(overview: Overview, dark: Boolean, onDarkChange: (Boolean) -> Unit, onLogout: () -> Unit) {
    val me = overview.me
    val c = MaterialTheme.split
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        NavBar("Account", onBack = null)
        if (me != null) SplitCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Avatar(initials(me.name, me.username), me.id, 56.dp)
                Column {
                    Text(me.name.ifBlank { me.username }, style = MaterialTheme.typography.titleLarge)
                    Text("@${me.username}", color = c.fg2, style = MaterialTheme.typography.bodySmall)
                    Text(me.email, color = c.fg2, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        SectionHeader("Preferences")
        SplitCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Row(Modifier.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Dark Mode", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Text("Switch to dark theme", color = c.fg2, style = MaterialTheme.typography.bodySmall)
                }
                Switch(dark, onDarkChange, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary))
            }
            Divider16()
            Row(Modifier.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Currency", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Text("Amounts displayed in PKR (Rs)", color = c.fg2, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp))
                .border(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).clickable { confirmLogout = true },
            contentAlignment = Alignment.Center,
        ) { Text("Log Out", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge) }
        Spacer(Modifier.height(24.dp))
    }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Log out?") },
        text = { Text("You'll need to sign in again to see your groups.") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("Log Out", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
    )
}

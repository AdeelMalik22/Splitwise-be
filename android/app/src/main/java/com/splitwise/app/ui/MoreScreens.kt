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

// ───────────────────────── Add / edit expense ─────────────────────────

@Composable
fun AddExpenseScreen(vm: AddExpenseViewModel, groups: List<Group>, initialGroupId: Int?, editExpenseId: Int?, userId: Int?, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val editing = editExpenseId != null
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    LaunchedEffect(editExpenseId) { editExpenseId?.let(vm::startEditing) }
    LaunchedEffect(groups, initialGroupId, editing) {
        if (!editing) (initialGroupId?.takeIf { id -> groups.any { it.id == id } } ?: groups.firstOrNull()?.id)?.let(vm::selectGroup)
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        if (editing) {
            when (val e = state.editing) {
                null, Load.Loading -> { NavBar("Edit Expense", onBack); SkeletonList() }
                is Load.Error -> { NavBar("Edit Expense", onBack); LoadView(e, onRetry = { editExpenseId?.let(vm::startEditing) }) {} }
                is Load.Ready -> ExpenseForm(vm, state, groups, e.data, userId, onBack)
            }
        } else {
            ExpenseForm(vm, state, groups, null, userId, onBack)
        }
    }
}

@Composable
private fun ExpenseForm(vm: AddExpenseViewModel, state: AddExpenseViewModel.State, groups: List<Group>, existing: com.splitwise.app.data.Expense?, userId: Int?, onBack: () -> Unit) {
    val c = MaterialTheme.split
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(existing?.description.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(existing?.amount?.toMoney()?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var paidBy by rememberSaveable { mutableStateOf<Int?>(existing?.paidBy?.firstOrNull() ?: userId) }
    var splitOn by rememberSaveable(state.groupId) { mutableStateOf<List<Int>?>(existing?.splitOn?.takeIf { it.isNotEmpty() }) }
    var mode by rememberSaveable {
        mutableStateOf(
            when {
                existing?.splitDetails?.any { it.amount != null } == true -> SplitMode.Exact
                existing?.splitDetails?.any { it.percentage != null } == true -> SplitMode.Percent
                else -> SplitMode.Equal
            }.name
        )
    }
    val values = remember {
        mutableStateMapOf<Int, String>().apply {
            existing?.splitDetails?.forEach { d -> (d.amount ?: d.percentage)?.let { put(d.userId, it.toMoney().stripTrailingZeros().toPlainString()) } }
        }
    }
    val splitMode = SplitMode.valueOf(mode)

    NavBar(if (existing != null) "Edit Expense" else "Add Expense", onBack) {
        val members = (state.members as? Load.Ready)?.data.orEmpty()
        SmallButton("Save", { vm.save(name, description, amount, paidBy, SplitInput(splitMode, (splitOn ?: members.map { it.id }).toSet(), values.toMap())) })
    }
    if (groups.isEmpty() && existing == null) return EmptyState("Create a group first, then add expenses to it.")
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

        if (existing == null) {
            Text("Group", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { g -> SelectChip("${g.emoji()}  ${g.name}", selected = g.id == state.groupId) { vm.selectGroup(g.id); paidBy = userId; values.clear() } }
            }
        }

        LoadView(state.members, onRetry = { state.groupId?.let(vm::selectGroup) }, modifier = Modifier.heightIn(min = 120.dp)) { members ->
            val selected = splitOn ?: members.map { it.id }
            val total = amount.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: BigDecimal.ZERO
            fun equalValue(forMode: SplitMode): String = when {
                selected.isEmpty() -> ""
                forMode == SplitMode.Percent -> BigDecimal(100).divide(BigDecimal(selected.size), 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                else -> total.divide(BigDecimal(selected.size), 2, RoundingMode.HALF_UP).toPlainString()
            }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(SplitMode.Equal to "Equally", SplitMode.Exact to "Amounts", SplitMode.Percent to "Percent").forEach { (m, label) ->
                        SelectChip(label, splitMode == m) {
                            mode = m.name
                            values.clear()
                            if (m != SplitMode.Equal) { val v = equalValue(m); selected.forEach { values[it] = v } }
                        }
                    }
                }
                if (selected.isNotEmpty()) SplitCard(Modifier.fillMaxWidth()) {
                    val shown = members.filter { it.id in selected }
                    val sum = shown.fold(BigDecimal.ZERO) { a, m -> a + (values[m.id]?.toBigDecimalOrNull() ?: BigDecimal.ZERO) }
                    Text(
                        when (splitMode) {
                            SplitMode.Equal -> if (total.signum() > 0) "Equally split — ${formatRs(total.divide(BigDecimal(selected.size), 2, RoundingMode.HALF_UP), true)} each" else "Equally split"
                            SplitMode.Exact -> "Enter each share — ${formatRs(sum, true)} of ${formatRs(total, true)}"
                            SplitMode.Percent -> "Enter each share — ${sum.stripTrailingZeros().toPlainString()}% of 100%"
                        },
                        Modifier.padding(16.dp, 14.dp, 16.dp, 6.dp), color = c.fg2, style = MaterialTheme.typography.titleSmall,
                    )
                    shown.forEach { m ->
                        Row(Modifier.fillMaxWidth().padding(16.dp, 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Avatar(initials(m.name, m.username), m.id, 32.dp)
                            Text(m.username + if (m.id == userId) " (you)" else "", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            if (splitMode == SplitMode.Equal) {
                                Text(formatRs(total.divide(BigDecimal(selected.size), 2, RoundingMode.HALF_UP), true), style = MaterialTheme.typography.titleSmall)
                            } else {
                                OutlinedTextField(
                                    values[m.id].orEmpty(), { v -> if (v.matches(Regex("""\d{0,9}([.]\d{0,2})?"""))) values[m.id] = v },
                                    singleLine = true, modifier = Modifier.width(110.dp), shape = RoundedCornerShape(10.dp),
                                    suffix = { if (splitMode == SplitMode.Percent) Text("%") },
                                    prefix = { if (splitMode == SplitMode.Exact) Text("Rs ") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        PrimaryButton(if (existing != null) "Save changes" else "Save expense", {
            val members = (state.members as? Load.Ready)?.data.orEmpty()
            vm.save(name, description, amount, paidBy, SplitInput(splitMode, (splitOn ?: members.map { it.id }).toSet(), values.toMap()))
        }, busy = state.busy)
        Spacer(Modifier.height(16.dp))
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
    var showEmailInvite by rememberSaveable { mutableStateOf(false) }
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
                            overview.groups.forEach { g -> SelectChip("${g.emoji()}  ${g.name}", g.id == groupId) { groupId = g.id } }
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
    if (showEmailInvite) EmailInviteDialog(
        groups = overview.groups, initialGroupId = groupId,
        onDismiss = { showEmailInvite = false },
        onSend = { gid, email -> search.inviteByEmail(gid, email) { showEmailInvite = false; onSent() } },
    )
}

@Composable
private fun EmailInviteDialog(groups: List<Group>, initialGroupId: Int?, onDismiss: () -> Unit, onSend: (Int, String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var gid by rememberSaveable { mutableStateOf(initialGroupId ?: groups.first().id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Invite by email") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("They get an email with a link. If they don't have SplitEase yet, they'll create an account with that address and join automatically.", color = MaterialTheme.split.fg2, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(email, { email = it }, label = { Text("Friend's email") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                Text("Group", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { g -> SelectChip("${g.emoji()}  ${g.name}", g.id == gid) { gid = g.id } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSend(gid, email) }, enabled = email.isNotBlank()) { Text("Send invite") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun InviteCard(invite: Invite, received: Boolean, onRespond: (Invite, Boolean) -> Unit) {
    val who = if (received) invite.inviterName.ifBlank { invite.inviterUsername } else invite.inviteeUsername.ifBlank { invite.email }
    SplitCard(Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp).fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(initials("", who), (if (received) invite.inviter else invite.invitee) ?: 0, 40.dp)
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
fun ActivityScreen(overview: Overview, onMarkRead: (com.splitwise.app.data.AppNotification) -> Unit, onOpenGroup: (Group) -> Unit) {
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
                        val groupIds = overview.groups.map { it.id }.toSet()
                        overview.activity.forEachIndexed { i, a ->
                            if (i > 0) Divider16()
                            val target = activityGroupId(a, groupIds)?.let { id -> overview.groups.firstOrNull { it.id == id } }
                            Box(Modifier.then(if (target != null) Modifier.clickable { onOpenGroup(target) } else Modifier)) {
                                NotificationRow(describeActivity(a, overview.me?.id), dayLabel(a.createdAt), unread = false)
                            }
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
fun AccountScreen(
    overview: Overview, dark: Boolean, onDarkChange: (Boolean) -> Unit, alertsOn: Boolean, onAlertsChange: (Boolean) -> Unit, onLogout: () -> Unit,
    onEditProfile: () -> Unit, onChangePassword: () -> Unit, onPayments: () -> Unit, onDeleteAccount: (String) -> Unit,
) {
    val me = overview.me
    val c = MaterialTheme.split
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
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
                    Text("Notifications", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Text("Unread badge for expense alerts and settlements", color = c.fg2, style = MaterialTheme.typography.bodySmall)
                }
                Switch(alertsOn, onAlertsChange, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary))
            }
            Divider16()
            Row(Modifier.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Currency", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Text("Amounts displayed in PKR (Rs)", color = c.fg2, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        SectionHeader("Account")
        SplitCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            NavRow("Edit Profile", "Name and email", onEditProfile)
            Divider16()
            NavRow("Change Password", "Update your password", onChangePassword)
            Divider16()
            NavRow("Payment History", "Payments you sent and received", onPayments)
        }
        SectionHeader("Danger Zone")
        SplitCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            NavRow("Log Out", null, { confirmLogout = true }, danger = true)
            Divider16()
            NavRow("Delete Account", "Permanently remove your data", { confirmDelete = true }, danger = true)
        }
        Spacer(Modifier.height(24.dp))
    }
    if (confirmDelete) DeleteAccountDialog(onDismiss = { confirmDelete = false }, onConfirm = { confirmDelete = false; onDeleteAccount(it) })
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Log out?") },
        text = { Text("You'll need to sign in again to see your groups.") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("Log Out", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
    )
}

@Composable
private fun NavRow(title: String, subtitle: String?, onClick: () -> Unit, danger: Boolean = false) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            subtitle?.let { Text(it, color = MaterialTheme.split.fg2, style = MaterialTheme.typography.bodySmall) }
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.split.fg3)
    }
}

@Composable
private fun DeleteAccountDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Delete Account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("This permanently deletes your account, your expenses and your memberships. This cannot be undone.")
                OutlinedTextField(password, { password = it }, label = { Text("Enter your password") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(password) }, enabled = password.isNotEmpty()) { Text("Yes, delete my account", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

package com.splitwise.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.splitwise.app.data.Expense
import com.splitwise.app.data.Member
import com.splitwise.app.data.Settlements
import com.splitwise.app.data.UserSummary
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    vm: GroupDetailViewModel, groupId: Int, title: String, userId: Int?, onBack: () -> Unit, onAddExpense: () -> Unit,
    onSettle: (SettleTarget) -> Unit, onLeave: () -> Unit, emoji: String, onSettings: () -> Unit, onEditExpense: (Expense) -> Unit,
    isAdmin: Boolean, onDeleteGroup: () -> Unit, onOpenSettleHub: () -> Unit, imageUrl: String? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteGroup by rememberSaveable { mutableStateOf(false) }
    var openExpenseId by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf<Expense?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }

    // Refresh when returning from Add Expense, and poll while visible so other
    // members' expenses appear without any action.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.refresh(silent = true)
            while (true) {
                kotlinx.coroutines.delay(10_000)
                vm.refresh(silent = true)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab == 0) FloatingActionButton(onClick = onAddExpense, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White, shape = androidx.compose.foundation.shape.CircleShape) {
                Icon(Icons.Default.Add, "Add expense")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).systemBarsPadding().fillMaxSize()) {
            NavBar(title, onBack) {
                IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "Refresh", tint = MaterialTheme.split.fg2) }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.split.fg2) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Group settings") }, onClick = { menuOpen = false; onSettings() })
                        DropdownMenuItem(
                            text = { Text(if (isAdmin) "Delete group" else "Leave group", color = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; if (isAdmin) confirmDeleteGroup = true else confirmLeave = true },
                        )
                    }
                }
            }
            val members = (state.members as? Load.Ready)?.data.orEmpty()
            val expenses = (state.expenses as? Load.Ready)?.data.orEmpty()
            val settlements = (state.settlements as? Load.Ready)?.data
            GroupHeader(emoji, imageUrl, title, members, expenses, settlements, onOpenSettleHub, userId)
            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.primary) {
                listOf("Expenses", "Balances", "Members").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label, style = MaterialTheme.typography.titleSmall) },
                        selectedContentColor = MaterialTheme.colorScheme.primary, unselectedContentColor = MaterialTheme.split.fg3)
                }
            }
            val names = members.associate { it.id to it }
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::pullRefresh, modifier = Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> LoadView(state.expenses, { vm.refresh() }) { ExpensesTab(it, names, userId) { e -> openExpenseId = e.id } }
                    1 -> LoadView(state.settlements, { vm.refresh() }) { BalancesTab(it, members, groupId, title, onSettle) }
                    else -> LoadView(state.members, { vm.refresh() }) {
                        MembersTab(it, state.searchResults, vm::search, vm::invite, isAdmin, vm::inviteByEmail) { if (isAdmin) confirmDeleteGroup = true else confirmLeave = true }
                    }
                }
            }
        }
    }

    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Leave \"$title\"?") },
        text = {
            val s = (state.settlements as? Load.Ready)?.data
            val hasBalance = s != null && (s.youOwe.isNotEmpty() || s.owedToYou.isNotEmpty())
            Text(if (hasBalance) "You still have an active balance. Settle up before leaving to avoid confusion." else "You'll stop seeing this group and its expenses.")
        },
        confirmButton = { TextButton(onClick = { confirmLeave = false; onLeave() }) { Text("Leave Group", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel") } },
    )
    if (confirmDeleteGroup) AlertDialog(
        onDismissRequest = { confirmDeleteGroup = false },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Delete \"$title\"?") },
        text = { Text("This permanently deletes the group, all its expenses and payments for every member. This cannot be undone.") },
        confirmButton = { TextButton(onClick = { confirmDeleteGroup = false; onDeleteGroup() }) { Text("Delete group", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDeleteGroup = false }) { Text("Cancel") } },
    )
    confirmDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Delete \"${e.name}\"?") },
            text = { Text("This removes the expense for everyone in the group and updates balances.") },
            confirmButton = { TextButton(onClick = { confirmDelete = null; openExpenseId = null; vm.deleteExpense(e) }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
    val openExpense = (state.expenses as? Load.Ready)?.data?.firstOrNull { it.id == openExpenseId }
    if (openExpense != null) ExpenseSheet(
        openExpense, (state.members as? Load.Ready)?.data.orEmpty(), userId,
        onDismiss = { openExpenseId = null }, onDelete = { confirmDelete = openExpense },
        onEdit = { openExpenseId = null; onEditExpense(openExpense) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseSheet(e: Expense, members: List<Member>, userId: Int?, onDismiss: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit) {
    val c = MaterialTheme.split
    val byId = members.associateBy { it.id }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(e.name, style = MaterialTheme.typography.headlineSmall)
            if (e.description.isNotBlank()) Text(e.description, color = c.fg2, style = MaterialTheme.typography.bodyMedium)
            Text(formatRs(e.amount.toMoney(), forceDecimals = true), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Text("${dayLabel(e.createdAt)} · paid by " + e.paidBy.joinToString { if (it == userId) "you" else byId[it]?.username ?: "User $it" }, color = c.fg2, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text("SPLIT", color = c.fg3, style = MaterialTheme.typography.labelSmall)
            e.splitOn.forEach { id ->
                val m = byId[id]
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(initials(m?.name.orEmpty(), m?.username ?: "?"), id, 32.dp, imageUrl = m?.avatar)
                    Text((m?.username ?: "User $id") + if (id == userId) " (you)" else "", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(formatRs(shareOf(e, id), forceDecimals = true), style = MaterialTheme.typography.titleSmall)
                }
            }
            Spacer(Modifier.height(4.dp))
            PrimaryButton("Edit expense", onEdit)
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) { Text("Delete expense", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall) }
        }
    }
}

@Composable
private fun GroupHeader(emoji: String, imageUrl: String?, title: String, members: List<Member>, expenses: List<Expense>, s: Settlements?, onSettleHub: () -> Unit, userId: Int?) {
    val c = MaterialTheme.split
    val total = expenses.fold(BigDecimal.ZERO) { a, e -> a + e.amount.toMoney() }
    val owe = s?.youOwe.orEmpty().fold(BigDecimal.ZERO) { a, l -> a + l.amount.toMoney() }
    val owed = s?.owedToYou.orEmpty().fold(BigDecimal.ZERO) { a, l -> a + l.amount.toMoney() }
    SplitCard(Modifier.padding(16.dp, 4.dp, 16.dp, 12.dp).fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GroupBadge(emoji, imageUrl, 48.dp)
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AvatarStack(members)
                    Text("${members.size} member${if (members.size == 1) "" else "s"}", color = c.fg2, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(Modifier.padding(16.dp)) {
            Stat("Total", formatRs(total), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            Stat("You owe", formatRs(owe), c.owe, Modifier.weight(1f))
            Stat("You get", formatRs(owed), c.owed, Modifier.weight(1f))
        }
        if (userId != null) {
            val mine = spendingFor(expenses, userId)
            Text(
                "Your share ${formatRs(mine.share)} · You paid ${formatRs(mine.paid)}",
                Modifier.padding(16.dp, 0.dp, 16.dp, 12.dp), color = c.fg2, style = MaterialTheme.typography.bodySmall,
            )
        }
        if (owe.signum() > 0) Box(Modifier.padding(16.dp, 0.dp, 16.dp, 16.dp)) { PrimaryButton("Settle up", onSettleHub) }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = MaterialTheme.split.fg3, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium))
        Text(value, Modifier.padding(top = 2.dp), color = color, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ExpensesTab(expenses: List<Expense>, names: Map<Int, Member>, userId: Int?, onOpen: (Expense) -> Unit) {
    if (expenses.isEmpty()) return EmptyState("No expenses yet. Tap + to add one.")
    val c = MaterialTheme.split
    val byDay = expenses.groupBy { dayLabel(it.createdAt) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        byDay.forEach { (day, list) ->
            item(key = "d$day") { Text(day, Modifier.padding(16.dp, 14.dp, 16.dp, 6.dp), color = c.fg2, style = MaterialTheme.typography.titleSmall) }
            items(list, key = { it.id }) { e ->
                val payer = e.paidBy.joinToString { if (it == userId) "You" else names[it]?.username ?: "User $it" }
                val net = if (userId != null) netFor(e, userId) else BigDecimal.ZERO
                val involved = userId != null && hasSplitMember(e, userId)
                Row(Modifier.fillMaxWidth().clickable { onOpen(e) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.primaryBg), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Receipt, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(e.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text("$payer paid ${formatRs(e.amount.toMoney())}", color = c.fg2, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        when {
                            !involved -> Text("not involved", color = c.fg3, style = MaterialTheme.typography.labelMedium)
                            net.signum() > 0 -> { Text("you lent", color = c.owed, style = MaterialTheme.typography.labelMedium); Text(formatRs(net), color = c.owed, style = MaterialTheme.typography.titleMedium) }
                            net.signum() < 0 -> { Text("you borrowed", color = c.owe, style = MaterialTheme.typography.labelMedium); Text(formatRs(net.abs()), color = c.owe, style = MaterialTheme.typography.titleMedium) }
                            else -> Text("settled", color = c.fg3, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalancesTab(s: Settlements, members: List<Member>, groupId: Int, groupName: String, onSettle: (SettleTarget) -> Unit) {
    val c = MaterialTheme.split
    if (s.youOwe.isEmpty() && s.owedToYou.isEmpty()) return EmptyState("You're all settled up.")
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(s.youOwe) { line ->
            val payee = members.firstOrNull { it.username == line.toUser }
            BalanceCard2("You owe ${line.toUser}", line.amount.toMoney(), c.oweBg, c.oweRing, c.owe,
                action = payee?.let { { SmallButton("Settle up", { onSettle(SettleTarget(groupId, groupName, it.id, it.username, line.amount)) }) } })
        }
        items(s.owedToYou) { line ->
            BalanceCard2("${line.fromUser} owes you", line.amount.toMoney(), c.owedBg, c.owedRing, c.owed)
        }
    }
}

@Composable
private fun BalanceCard2(text: String, amount: BigDecimal, bg: Color, ring: Color, fg: Color, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = fg, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text(formatRs(amount), color = fg, style = MaterialTheme.typography.titleLarge)
        }
        action?.invoke()
    }
}

@Composable
private fun MembersTab(
    members: List<Member>, results: List<UserSummary>, onSearch: (String) -> Unit, onInvite: (UserSummary) -> Unit,
    isAdmin: Boolean, onInviteByEmail: (String, () -> Unit) -> Unit, onDangerAction: () -> Unit,
) {
    var showEmailInvite by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val memberIds = members.map { it.id }.toSet()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            OutlinedTextField(
                query, { query = it; onSearch(it) }, singleLine = true,
                placeholder = { Text("Invite by username", color = MaterialTheme.split.fg3) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = MaterialTheme.split.fg3) },
                shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.split.input, focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedBorderColor = MaterialTheme.colorScheme.outline),
            )
        }
        item {
            Box(
                Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .clickable { showEmailInvite = true }.padding(14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("✉  Invite a friend by email", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall) }
        }
        items(results.filter { it.id !in memberIds }, key = { "r${it.id}" }) { u ->
            PersonRow(u.id, u.username, u.name, u.avatar) { SmallButton("Invite", { onInvite(u); query = "" }) }
        }
        item { SectionHeader("Members") }
        items(members, key = { "m${it.id}" }) { m -> PersonRow(m.id, m.username, m.name, m.avatar) {} }
        item {
            Box(
                Modifier.padding(16.dp, 24.dp, 16.dp, 8.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).clickable(onClick = onDangerAction),
                contentAlignment = Alignment.Center,
            ) { Text(if (isAdmin) "Delete group" else "Leave group", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge) }
            Text(
                if (isAdmin) "You created this group, so you can delete it for everyone." else "You can leave at any time. Your past expenses stay in the group.",
                Modifier.padding(horizontal = 16.dp), color = MaterialTheme.split.fg3, style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (showEmailInvite) EmailInviteDialog(onDismiss = { showEmailInvite = false }, onSend = { email -> onInviteByEmail(email) { showEmailInvite = false } })
}

@Composable
fun PersonRow(id: Int, username: String, name: String, avatar: String? = null, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(initials(name, username), id, 40.dp, imageUrl = avatar)
        Column(Modifier.weight(1f)) {
            Text(name.ifBlank { username }, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text("@$username", color = MaterialTheme.split.fg2, style = MaterialTheme.typography.bodySmall)
        }
        trailing()
    }
}

@Composable
private fun EmailInviteDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Invite by email") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("They get an email with a link that opens SplitEase. If they're new, they create an account with that address and join this group automatically.",
                    color = MaterialTheme.split.fg2, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(email, { email = it }, label = { Text("Friend's email") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email))
            }
        },
        confirmButton = { TextButton(onClick = { onSend(email) }, enabled = email.isNotBlank()) { Text("Send invite") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

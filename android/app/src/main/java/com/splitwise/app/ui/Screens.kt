package com.splitwise.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitwise.app.data.*

// ───────────────────────── Auth ─────────────────────────

@Composable
fun AuthScreen(vm: AuthViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var register by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).imePadding(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Splitwise", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(if (register) "Create your account" else "Sign in to continue", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (register) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            password, { password = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { if (register) vm.register(username, name, email, password) else vm.login(username, password) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(if (register) "Create account" else "Sign in")
        }
        TextButton(onClick = { register = !register; vm.clearError() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (register) "Have an account? Sign in" else "New here? Create an account")
        }
    }
}

// ───────────────────────── Groups ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    vm: GroupsViewModel,
    onOpenGroup: (Group) -> Unit,
    onOpenInbox: () -> Unit,
    onLogout: () -> Unit,
) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    val error by vm.actionError.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); vm.dismissError() } }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Groups") }, actions = {
                IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Refresh") }
                IconButton(onClick = onOpenInbox) { Icon(Icons.Default.Notifications, "Invites and notifications") }
                IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, "Sign out") }
            })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, "New group") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LoadView(groups, vm::refresh, Modifier.padding(padding)) { list ->
            if (list.isEmpty()) EmptyState("No groups yet. Tap + to create one.")
            else LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(list, key = { it.id }) { group ->
                    ListItem(
                        headlineContent = { Text(group.name) },
                        supportingContent = group.description.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                        modifier = Modifier.clickable { onOpenGroup(group) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showCreate) CreateGroupDialog(
        onDismiss = { showCreate = false },
        onCreate = { n, d -> vm.create(n, d) { showCreate = false } },
    )
}

@Composable
private fun CreateGroupDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New group") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, description) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ───────────────────────── Group detail ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(vm: GroupDetailViewModel, title: String, onBack: () -> Unit, onAddExpense: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }

    // Pick up expenses added on the Add Expense screen.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Refresh") } },
            )
        },
        floatingActionButton = {
            if (tab == 0) FloatingActionButton(onClick = onAddExpense) { Icon(Icons.Default.Add, "Add expense") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                listOf("Expenses", "Balances", "Members").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            val names = (state.members as? Load.Ready)?.data.orEmpty().associate { it.id to it.username }
            when (tab) {
                0 -> LoadView(state.expenses, vm::refresh) { ExpensesTab(it, names) }
                1 -> LoadView(state.settlements, vm::refresh) { BalancesTab(it) }
                else -> LoadView(state.members, vm::refresh) { MembersTab(it, state.searchResults, vm::search, vm::invite) }
            }
        }
    }
}

@Composable
private fun ExpensesTab(expenses: List<Expense>, names: Map<Int, String>) {
    if (expenses.isEmpty()) return EmptyState("No expenses yet. Tap + to add one.")
    LazyColumn(Modifier.fillMaxSize()) {
        items(expenses, key = { it.id }) { e ->
            val payer = e.paidBy.joinToString { names[it] ?: "User $it" }
            ListItem(
                headlineContent = { Text(e.name) },
                supportingContent = { Text("Paid by $payer · split ${e.splitOn.size} way${if (e.splitOn.size == 1) "" else "s"}") },
                trailingContent = { Text(e.amount, style = MaterialTheme.typography.titleMedium) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun BalancesTab(s: Settlements) {
    if (s.youOwe.isEmpty() && s.owedToYou.isEmpty()) return EmptyState("You're all settled up.")
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(s.youOwe) { line ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                ListItem(
                    headlineContent = { Text("You owe ${line.toUser}") },
                    trailingContent = { Text(line.amount, style = MaterialTheme.typography.titleMedium) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
        items(s.owedToYou) { line ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                ListItem(
                    headlineContent = { Text("${line.fromUser} owes you") },
                    trailingContent = { Text(line.amount, style = MaterialTheme.typography.titleMedium) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun MembersTab(
    members: List<Member>,
    results: List<UserSummary>,
    onSearch: (String) -> Unit,
    onInvite: (UserSummary) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val memberIds = members.map { it.id }.toSet()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                query, { query = it; onSearch(it) },
                label = { Text("Invite by username") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
        items(results.filter { it.id !in memberIds }, key = { "r${it.id}" }) { u ->
            ListItem(
                headlineContent = { Text(u.username) },
                supportingContent = u.name.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                trailingContent = { TextButton(onClick = { onInvite(u); query = "" }) { Text("Invite") } },
            )
        }
        item { Text("Members", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall) }
        items(members, key = { "m${it.id}" }) { m ->
            ListItem(
                headlineContent = { Text(m.username) },
                supportingContent = m.name.takeIf { it.isNotBlank() }?.let { { Text(it) } },
            )
        }
    }
}

// ───────────────────────── Add expense ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(vm: AddExpenseViewModel, currentUserId: Int?, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var paidBy by rememberSaveable { mutableStateOf<Int?>(currentUserId) }
    var splitOn by rememberSaveable { mutableStateOf<List<Int>?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Add expense") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { padding ->
        LoadView(state.members, onRetry = {}, modifier = Modifier.padding(padding)) { members ->
            val selected = splitOn ?: members.map { it.id } // default: split with everyone
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).imePadding()) {
                OutlinedTextField(name, { name = it }, label = { Text("What was it for?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(description, { description = it }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    amount, { amount = it }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(16.dp))
                Text("Paid by", style = MaterialTheme.typography.titleSmall)
                members.forEach { m ->
                    Row(Modifier.fillMaxWidth().clickable { paidBy = m.id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = paidBy == m.id, onClick = { paidBy = m.id })
                        Text(m.username + if (m.id == currentUserId) " (you)" else "")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Split equally between", style = MaterialTheme.typography.titleSmall)
                members.forEach { m ->
                    val checked = m.id in selected
                    Row(
                        Modifier.fillMaxWidth().clickable { splitOn = if (checked) selected - m.id else selected + m.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { splitOn = if (checked) selected - m.id else selected + m.id })
                        Text(m.username)
                    }
                }
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { vm.save(name, description, amount, paidBy, selected.toSet()) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Save expense")
                }
            }
        }
    }
}

// ───────────────────────── Inbox ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(vm: InboxViewModel, currentUserId: Int?, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invites & notifications") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Refresh") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LoadView(state.invites, vm::refresh, Modifier.padding(padding)) { invites ->
            val pending = invites.filter { it.status == "pending" && it.invitee == currentUserId }
            val notifications = (state.notifications as? Load.Ready)?.data.orEmpty()
            if (pending.isEmpty() && notifications.isEmpty()) return@LoadView EmptyState("Nothing here yet.")
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                if (pending.isNotEmpty()) item { SectionTitle("Pending invites") }
                items(pending, key = { "i${it.id}" }) { invite ->
                    ListItem(
                        headlineContent = { Text("Invitation to group #${invite.group}") },
                        supportingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.respond(invite, true) }) { Text("Accept") }
                                OutlinedButton(onClick = { vm.respond(invite, false) }) { Text("Decline") }
                            }
                        },
                    )
                }
                if (notifications.isNotEmpty()) item { SectionTitle("Notifications") }
                items(notifications, key = { "n${it.id}" }) { n ->
                    ListItem(
                        headlineContent = { Text(n.message) },
                        trailingContent = if (n.readAt == null) {
                            { TextButton(onClick = { vm.markRead(n) }) { Text("Mark read") } }
                        } else null,
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

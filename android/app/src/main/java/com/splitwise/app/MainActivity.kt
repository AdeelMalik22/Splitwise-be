package com.splitwise.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.splitwise.app.data.Repository
import com.splitwise.app.ui.*

/** What a `splitease://` link asked for. */
sealed interface DeepLink {
    data object Verified : DeepLink
    data class Invite(val token: String) : DeepLink
}

fun parseDeepLink(uri: android.net.Uri?): DeepLink? {
    if (uri?.scheme != "splitease") return null
    return when (uri.host) {
        "login" -> if (uri.getQueryParameter("verified") == "1") DeepLink.Verified else null
        "invite" -> uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }?.let { DeepLink.Invite(it) }
        else -> null
    }
}

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val deepLink = mutableStateOf<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLink.value = parseDeepLink(intent?.data)
        setContent { AppRoot(deepLink.value) }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseDeepLink(intent.data)?.let { deepLink.value = it }
    }
}

@Composable
private inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (Repository) -> VM): VM {
    val repo = (LocalContext.current.applicationContext as SplitwiseApp).repository
    return viewModel(key = key, factory = viewModelFactory { initializer { create(repo) } })
}

@Composable
private fun AppRoot(deepLink: DeepLink?) {
    val session = appViewModel { SessionViewModel(it) }
    // An invitation link is remembered until it is used, so it survives sign-up, email verification and login.
    LaunchedEffect(deepLink) { (deepLink as? DeepLink.Invite)?.let { session.setPendingInvite(it.token) } }
    val pendingInvite by session.pendingInvite.collectAsStateWithLifecycle(initialValue = null)
    val dark by session.darkMode.collectAsStateWithLifecycle(initialValue = null)
    SplitEaseTheme(darkOverride = dark) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // null = still reading the stored session; avoids flashing the login screen.
            val loggedIn by session.loggedIn.collectAsStateWithLifecycle(initialValue = null)
            when (loggedIn) {
                null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                false -> AuthScreen(appViewModel { AuthViewModel(it) }, justVerified = deepLink is DeepLink.Verified, pendingInvite = pendingInvite)
                true -> {
                    val epoch by session.epoch.collectAsStateWithLifecycle()
                    MainNav(session, dark ?: isSystemInDarkTheme(), epoch, pendingInvite)
                }
            }
        }
    }
}

private val tabRoutes = mapOf("home" to Tab.Home, "groups" to Tab.Groups, "activity" to Tab.Activity, "account" to Tab.Account)

@Composable
private fun MainNav(session: SessionViewModel, isDark: Boolean, epoch: Int, pendingInvite: String?) {
    val nav = rememberNavController()
    val userId by session.userId.collectAsStateWithLifecycle(initialValue = null)
    // One overview shared by every tab; re-created on sign-in because MainNav leaves composition on logout.
    val overviewVm = appViewModel(key = "overview-$epoch") { OverviewViewModel(it) }
    val load by overviewVm.state.collectAsStateWithLifecycle()
    val message by overviewVm.message.collectAsStateWithLifecycle()
    val refreshing by overviewVm.refreshing.collectAsStateWithLifecycle()
    val alertsOn by session.alertsOn.collectAsStateWithLifecycle(initialValue = true)
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val tab = tabRoutes[route]
    var showCreateGroup by remember { mutableStateOf(false) }

    // Fingerprint login: enrolment (after a password login) and the one-time offer.
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val enrolled by session.biometric.collectAsStateWithLifecycle(initialValue = null)
    val offered by session.biometricOffered.collectAsStateWithLifecycle(initialValue = true)
    val enrollFingerprint: () -> Unit = {
        val activity = context.findFragmentActivity()
        scope.launch {
            val refresh = session.refreshToken()
            if (activity != null && refresh != null) {
                Biometric.encrypt(activity, refresh.toByteArray(), onDone = { blob, iv -> session.saveBiometric(blob, iv) },
                    onError = { overviewVm.showMessage(it) })
            }
        }
    }
    if (!offered && enrolled == null && Biometric.state(context) == BiometricState.Ready) {
        AlertDialog(
            onDismissRequest = { session.markBiometricOffered() },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Log in with your fingerprint?") },
            text = { Text("Skip typing your password next time. You can change this any time in Account.") },
            confirmButton = { TextButton(onClick = { session.markBiometricOffered(); enrollFingerprint() }) { Text("Turn on") } },
            dismissButton = { TextButton(onClick = { session.markBiometricOffered() }) { Text("Not now") } },
        )
    }
    LaunchedEffect(pendingInvite) { if (pendingInvite != null) nav.navigate("join") { launchSingleTop = true } }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); overviewVm.messageShown() } }

    // Keep the tabs fresh while they are on screen.
    LaunchedEffect(tab != null) {
        while (tab != null) {
            kotlinx.coroutines.delay(15_000)
            overviewVm.refresh(silent = true)
        }
    }

    val ready = (load as? Load.Ready)?.data
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (tab != null) BottomBar(
                selected = tab,
                onSelect = { t ->
                    nav.navigate(tabRoutes.entries.first { it.value == t }.key) {
                        popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true
                    }
                },
                onAdd = { nav.navigate("add") },
                unread = alertsOn && ready?.notifications?.any { it.readAt == null } == true,
            )
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(if (tab != null) padding else PaddingValues(0.dp))) {
            composable("home") {
                TabFrame(load, refreshing, overviewVm::pullRefresh, overviewVm::refresh) { o ->
                    HomeScreen(
                        o, userId,
                        onOpenGroup = { nav.navigate("group/${it.id}?name=${android.net.Uri.encode(it.name)}") },
                        onOpenGroups = { nav.navigate("groups") },
                        onOpenInvites = { nav.navigate("invites") },
                        onOpenActivity = { nav.navigate("activity") },
                        onRespond = overviewVm::respond,
                        onCreateGroup = { showCreateGroup = true },
                        onSettleUp = { nav.navigate("settle") },
                        onConfirmPayment = overviewVm::confirmPayment,
                    )
                }
            }
            composable("groups") {
                TabFrame(load, refreshing, overviewVm::pullRefresh, overviewVm::refresh) { o ->
                    GroupsScreen(
                        o,
                        onOpenGroup = { nav.navigate("group/${it.id}?name=${android.net.Uri.encode(it.name)}") },
                        onNew = { showCreateGroup = true },
                        onInvites = { nav.navigate("invites") },
                    )
                }
            }
            composable("activity") { TabFrame(load, refreshing, overviewVm::pullRefresh, overviewVm::refresh) { ActivityScreen(it, overviewVm::markRead) { g -> nav.navigate("group/${g.id}?name=${android.net.Uri.encode(g.name)}") } } }
            composable("account") {
                TabFrame(load, refreshing, overviewVm::pullRefresh, overviewVm::refresh) {
                    AccountScreen(
                        it, isDark, session::setDarkMode, alertsOn, session::setAlertsOn, session::logout,
                        currency = activeCurrency, onCurrencyChange = { c -> session.setCurrency(c.code) },
                        fingerprint = Biometric.state(androidx.compose.ui.platform.LocalContext.current),
                        fingerprintOn = enrolled != null,
                        onFingerprintChange = { on -> if (on) enrollFingerprint() else session.disableBiometric() },
                        onEditProfile = { nav.navigate("profile/edit") },
                        onChangePassword = { nav.navigate("profile/password") },
                        onPayments = { nav.navigate("settle") },
                        onDeleteAccount = overviewVm::deleteAccount,
                    )
                }
            }
            composable(
                "group/{id}?name={name}",
                arguments = listOf(navArgument("id") { type = NavType.IntType }, navArgument("name") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                val id = entry.arguments!!.getInt("id")
                GroupDetailScreen(
                    vm = appViewModel(key = "group$id-$epoch") { GroupDetailViewModel(it, id) },
                    groupId = id,
                    title = entry.arguments!!.getString("name").orEmpty().ifBlank { "Group" },
                    userId = userId,
                    onBack = { nav.popBackStack() },
                    onAddExpense = { nav.navigate("add?group=$id") },
                    onSettle = { t ->
                        nav.navigate(
                            "settle?group=${t.groupId}&gname=${android.net.Uri.encode(t.groupName)}&to=${t.payeeId}" +
                                "&pname=${android.net.Uri.encode(t.payeeName)}&amount=${t.owed}"
                        )
                    },
                    onLeave = { overviewVm.leaveGroup(id) { nav.popBackStack("home", false) } },
                    emoji = ready?.groups?.firstOrNull { it.id == id }?.emoji() ?: emojiFor(id),
                    onSettings = { nav.navigate("group/$id/settings") },
                    onEditExpense = { e -> nav.navigate("add?group=$id&edit=${e.id}") },
                    isAdmin = ready?.groups?.firstOrNull { it.id == id }?.createdBy == userId,
                    onDeleteGroup = { overviewVm.deleteGroup(id) { nav.popBackStack("home", false) } },
                    onOpenSettleHub = { nav.navigate("settle") },
                )
            }
            composable(
                "add?group={group}&edit={edit}",
                arguments = listOf(
                    navArgument("group") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("edit") { type = NavType.IntType; defaultValue = -1 },
                ),
            ) { entry ->
                AddExpenseScreen(
                    vm = appViewModel(key = "add-${entry.arguments!!.getInt("edit")}-$epoch") { AddExpenseViewModel(it) },
                    groups = ready?.groups.orEmpty(),
                    initialGroupId = entry.arguments!!.getInt("group").takeIf { it >= 0 },
                    editExpenseId = entry.arguments!!.getInt("edit").takeIf { it >= 0 },
                    userId = userId,
                    onBack = { overviewVm.refresh(silent = true); nav.popBackStack() },
                )
            }
            composable("group/{id}/settings", arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
                val id = entry.arguments!!.getInt("id")
                TabFrame(load, refreshing, overviewVm::pullRefresh, overviewVm::refresh) { o ->
                    val group = o.groups.firstOrNull { it.id == id }
                    if (group == null) EmptyState("This group is no longer available.")
                    else GroupSettingsScreen(
                        group, o.members[id].orEmpty(), userId,
                        onSave = { n, d, i -> overviewVm.updateGroup(id, n, d, i) { nav.popBackStack() } },
                        onRemove = { uid -> overviewVm.removeMember(id, uid) },
                        onDelete = { overviewVm.deleteGroup(id) { nav.popBackStack("home", false) } },
                        onBack = { nav.popBackStack() },
                    )
                }
            }
            composable(
                "settle?group={group}&gname={gname}&to={to}&pname={pname}&amount={amount}",
                arguments = listOf(
                    navArgument("group") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("gname") { type = NavType.StringType; defaultValue = "" },
                    navArgument("to") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("pname") { type = NavType.StringType; defaultValue = "" },
                    navArgument("amount") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val a = entry.arguments!!
                val target = if (a.getInt("group") >= 0 && a.getInt("to") >= 0) {
                    SettleTarget(a.getInt("group"), a.getString("gname").orEmpty(), a.getInt("to"), a.getString("pname").orEmpty(), a.getString("amount").orEmpty())
                } else null
                TabFrame(load, refreshing, overviewVm::pullRefresh, overviewVm::refresh) { o ->
                    SettleUpScreen(
                        o, userId, target,
                        onPay = { g, to, amt -> overviewVm.pay(g, to, amt) { nav.popBackStack() } },
                        onConfirm = overviewVm::confirmPayment, onCancel = overviewVm::cancelPayment,
                        onBack = { nav.popBackStack() },
                        onSettle = { t ->
                            nav.navigate(
                                "settle?group=${t.groupId}&gname=${android.net.Uri.encode(t.groupName)}&to=${t.payeeId}" +
                                    "&pname=${android.net.Uri.encode(t.payeeName)}&amount=${t.owed}"
                            )
                        },
                    )
                }
            }
            composable("profile/edit") {
                TabFrame(load, refreshing, overviewVm::pullRefresh, overviewVm::refresh) { o ->
                    EditProfileScreen(o, onSave = { id, n, e -> overviewVm.updateProfile(id, n, e) { nav.popBackStack() } }, onBack = { nav.popBackStack() })
                }
            }
            composable("profile/password") {
                ChangePasswordScreen(onSave = { old, new -> overviewVm.changePassword(old, new) { nav.popBackStack() } }, onBack = { nav.popBackStack() })
            }
            composable("join") {
                val token = pendingInvite
                if (token != null) JoinGroupScreen(
                    vm = appViewModel(key = "join-$token-$epoch") { JoinViewModel(it, token) },
                    onJoined = { r ->
                        session.setPendingInvite(null)
                        overviewVm.refresh(silent = true)
                        nav.popBackStack()
                        nav.navigate("group/${r.groupId}?name=${android.net.Uri.encode(r.groupName)}")
                    },
                    onDismiss = { session.setPendingInvite(null); nav.popBackStack() },
                )
            }
            composable("invites") {
                TabFrame(load, refreshing, overviewVm::pullRefresh, overviewVm::refresh) { o ->
                    InvitesScreen(o, userId, appViewModel(key = "invite-search-$epoch") { InviteSearchViewModel(it) }, overviewVm::respond, onSent = { overviewVm.refresh(silent = true) }, onBack = { nav.popBackStack() })
                }
            }
        }
    }

    if (showCreateGroup) CreateGroupDialog(
        onDismiss = { showCreateGroup = false },
        onCreate = { n, d, icon -> overviewVm.createGroup(n, d, icon) { showCreateGroup = false } },
    )
}

/** Shared loading/error handling, pull-to-refresh and system-bar padding for every screen backed by [Overview]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabFrame(load: Load<Overview>, refreshing: Boolean, onPull: () -> Unit, onRetry: () -> Unit, content: @Composable (Overview) -> Unit) {
    PullToRefreshBox(
        isRefreshing = refreshing, onRefresh = onPull,
        modifier = Modifier.fillMaxSize().systemBarsPadding().consumeWindowInsets(WindowInsets.systemBars),
    ) {
        LoadView(load, onRetry) { content(it) }
    }
}

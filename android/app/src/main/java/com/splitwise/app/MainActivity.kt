package com.splitwise.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.ViewModel
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot() }
    }
}

@Composable
private inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (Repository) -> VM): VM {
    val repo = (LocalContext.current.applicationContext as SplitwiseApp).repository
    return viewModel(key = key, factory = viewModelFactory { initializer { create(repo) } })
}

@Composable
private fun AppRoot() {
    val session = appViewModel { SessionViewModel(it) }
    val dark by session.darkMode.collectAsStateWithLifecycle(initialValue = null)
    SplitEaseTheme(darkOverride = dark) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // null = still reading the stored session; avoids flashing the login screen.
            val loggedIn by session.loggedIn.collectAsStateWithLifecycle(initialValue = null)
            when (loggedIn) {
                null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                false -> AuthScreen(appViewModel { AuthViewModel(it) })
                true -> MainNav(session, dark ?: isSystemInDarkTheme())
            }
        }
    }
}

private val tabRoutes = mapOf("home" to Tab.Home, "groups" to Tab.Groups, "activity" to Tab.Activity, "account" to Tab.Account)

@Composable
private fun MainNav(session: SessionViewModel, isDark: Boolean) {
    val nav = rememberNavController()
    val userId by session.userId.collectAsStateWithLifecycle(initialValue = null)
    // One overview shared by every tab; re-created on sign-in because MainNav leaves composition on logout.
    val overviewVm = appViewModel(key = "overview") { OverviewViewModel(it) }
    val load by overviewVm.state.collectAsStateWithLifecycle()
    val message by overviewVm.message.collectAsStateWithLifecycle()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val tab = tabRoutes[route]
    var showCreateGroup by remember { mutableStateOf(false) }
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
                unread = ready?.notifications?.any { it.readAt == null } == true,
            )
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(if (tab != null) padding else PaddingValues(0.dp))) {
            composable("home") {
                TabFrame(load, overviewVm::refresh) { o ->
                    HomeScreen(
                        o, userId,
                        onOpenGroup = { nav.navigate("group/${it.id}?name=${android.net.Uri.encode(it.name)}") },
                        onOpenGroups = { nav.navigate("groups") },
                        onOpenInvites = { nav.navigate("invites") },
                        onOpenActivity = { nav.navigate("activity") },
                        onRespond = overviewVm::respond,
                        onCreateGroup = { showCreateGroup = true },
                    )
                }
            }
            composable("groups") {
                TabFrame(load, overviewVm::refresh) { o ->
                    GroupsScreen(
                        o,
                        onOpenGroup = { nav.navigate("group/${it.id}?name=${android.net.Uri.encode(it.name)}") },
                        onNew = { showCreateGroup = true },
                        onInvites = { nav.navigate("invites") },
                    )
                }
            }
            composable("activity") { TabFrame(load, overviewVm::refresh) { ActivityScreen(it, overviewVm::markRead) } }
            composable("account") {
                TabFrame(load, overviewVm::refresh) { AccountScreen(it, isDark, session::setDarkMode, session::logout) }
            }
            composable(
                "group/{id}?name={name}",
                arguments = listOf(navArgument("id") { type = NavType.IntType }, navArgument("name") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                val id = entry.arguments!!.getInt("id")
                GroupDetailScreen(
                    vm = appViewModel(key = "group$id") { GroupDetailViewModel(it, id) },
                    groupId = id,
                    title = entry.arguments!!.getString("name").orEmpty().ifBlank { "Group" },
                    userId = userId,
                    onBack = { nav.popBackStack() },
                    onAddExpense = { nav.navigate("add?group=$id") },
                )
            }
            composable("add?group={group}", arguments = listOf(navArgument("group") { type = NavType.IntType; defaultValue = -1 })) { entry ->
                AddExpenseScreen(
                    vm = appViewModel { AddExpenseViewModel(it) },
                    groups = ready?.groups.orEmpty(),
                    initialGroupId = entry.arguments!!.getInt("group").takeIf { it >= 0 },
                    userId = userId,
                    onBack = { overviewVm.refresh(silent = true); nav.popBackStack() },
                )
            }
            composable("invites") {
                TabFrame(load, overviewVm::refresh) { o ->
                    InvitesScreen(o, userId, appViewModel { InviteSearchViewModel(it) }, overviewVm::respond, onSent = { overviewVm.refresh(silent = true) }, onBack = { nav.popBackStack() })
                }
            }
        }
    }

    if (showCreateGroup) CreateGroupDialog(
        onDismiss = { showCreateGroup = false },
        onCreate = { n, d -> overviewVm.createGroup(n, d) { showCreateGroup = false } },
    )
}

/** Shared loading/error handling and system-bar padding for every screen backed by [Overview]. */
@Composable
private fun TabFrame(load: Load<Overview>, onRetry: () -> Unit, content: @Composable (Overview) -> Unit) {
    Box(Modifier.fillMaxSize().systemBarsPadding().consumeWindowInsets(WindowInsets.systemBars)) {
        LoadView(load, onRetry) { content(it) }
    }
}

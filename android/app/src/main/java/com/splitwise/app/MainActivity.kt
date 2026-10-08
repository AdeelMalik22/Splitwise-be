package com.splitwise.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.splitwise.app.data.Repository
import com.splitwise.app.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { AppRoot() }
            }
        }
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
    // null = still reading stored session; avoids flashing the login screen.
    val loggedIn by session.loggedIn.collectAsStateWithLifecycle(initialValue = null)
    when (loggedIn) {
        null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        false -> AuthScreen(appViewModel { AuthViewModel(it) })
        true -> MainNav(session)
    }
}

@Composable
private fun MainNav(session: SessionViewModel) {
    val nav = rememberNavController()
    val userId by session.userId.collectAsStateWithLifecycle(initialValue = null)
    val groupArgs = listOf(navArgument("id") { type = NavType.IntType }, navArgument("name") { type = NavType.StringType; defaultValue = "" })

    NavHost(nav, startDestination = "groups") {
        composable("groups") {
            GroupsScreen(
                vm = appViewModel { GroupsViewModel(it) },
                onOpenGroup = { nav.navigate("group/${it.id}?name=${android.net.Uri.encode(it.name)}") },
                onOpenInbox = { nav.navigate("inbox") },
                onLogout = session::logout,
            )
        }
        composable("group/{id}?name={name}", arguments = groupArgs) { entry ->
            val id = entry.arguments!!.getInt("id")
            GroupDetailScreen(
                vm = appViewModel(key = "group$id") { GroupDetailViewModel(it, id) },
                title = entry.arguments!!.getString("name").orEmpty().ifBlank { "Group" },
                onBack = { nav.popBackStack() },
                onAddExpense = { nav.navigate("group/$id/expense") },
            )
        }
        composable("group/{id}/expense", arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
            val id = entry.arguments!!.getInt("id")
            AddExpenseScreen(
                vm = appViewModel(key = "expense$id") { AddExpenseViewModel(it, id) },
                currentUserId = userId,
                onBack = { nav.popBackStack() },
            )
        }
        composable("inbox") {
            InboxScreen(appViewModel { InboxViewModel(it) }, userId, onBack = { nav.popBackStack() })
        }
    }
}

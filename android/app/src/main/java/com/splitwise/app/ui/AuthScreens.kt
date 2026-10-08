package com.splitwise.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AuthScreen(vm: AuthViewModel) {
    var register by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (register) RegisterForm(vm, onLogin = { register = false; vm.clearError() })
        else LoginForm(vm, onRegister = { register = true; vm.clearError() })
    }
}

@Composable
private fun LoginForm(vm: AuthViewModel, onRegister: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding(),
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LogoMark()
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append("Split")
                    withStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("Ease") }
                },
                Modifier.padding(top = 12.dp), style = MaterialTheme.typography.headlineSmall,
            )
            Text("Split expenses, stay friends.", Modifier.padding(top = 4.dp), color = MaterialTheme.split.fg2, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp))
        }
        state.error?.let { ErrorBanner(it, Modifier.padding(bottom = 20.dp)) }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IconField(username, { username = it }, "Username", Icons.Default.Person, error = state.error != null)
            IconField(password, { password = it }, "Password", Icons.Default.Lock, password = true, error = state.error != null, onDone = { vm.login(username, password) })
            PrimaryButton("Log In", { vm.login(username, password) }, busy = state.busy, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
                Text("Don't have an account? ", color = MaterialTheme.split.fg2, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp))
                Text("Create account", Modifier.clickable(onClick = onRegister), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium))
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun RegisterForm(vm: AuthViewModel, onLogin: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        NavBar("Create Account", onBack = onLogin)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Join SplitEase to start tracking shared expenses with friends and groups.", color = MaterialTheme.split.fg2, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp))
            state.error?.let { ErrorBanner(it) }
            IconField(name, { name = it }, "Full Name", Icons.Default.Person)
            IconField(username, { username = it }, "Username", Icons.Default.Person)
            IconField(email, { email = it }, "Email", Icons.Default.Email, keyboard = KeyboardType.Email)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IconField(password, { password = it }, "Password", Icons.Default.Lock, password = true, onDone = { vm.register(username, name, email, password) })
                if (password.isNotEmpty()) PasswordStrength(password)
            }
            PrimaryButton("Create Account", { vm.register(username, name, email, password) }, busy = state.busy, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("Already have an account? ", color = MaterialTheme.split.fg2, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp))
                Text("Log In", Modifier.clickable(onClick = onLogin), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LogoMark() {
    Box(
        Modifier.size(56.dp).shadow(12.dp, RoundedCornerShape(16.dp), spotColor = MaterialTheme.colorScheme.primary)
            .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) { androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.splitwise.app.R.drawable.ic_coin), null, Modifier.size(34.dp)) }
}

/** 0–3 strength from length and character variety; mirrors the design's 4-segment bar. */
fun passwordStrength(p: String): Int {
    var score = 0
    if (p.length >= 8) score++
    if (p.length >= 12 || (p.any { it.isDigit() } && p.any { it.isLetter() })) score++
    if (p.any { !it.isLetterOrDigit() } || (p.any { it.isUpperCase() } && p.any { it.isLowerCase() } && p.any { it.isDigit() })) score++
    return score
}

@Composable
private fun PasswordStrength(password: String) {
    val strength = passwordStrength(password)
    val c = MaterialTheme.split
    val color = when (strength) { 0, 1 -> MaterialTheme.colorScheme.error; 2 -> Color(0xFFD97706); else -> c.owed }
    val label = when (strength) { 0 -> "Too short — use at least 8 characters"; 1 -> "Weak — mix letters and numbers"; 2 -> "Good — add a symbol to make it stronger"; else -> "Strong" }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(4) { i ->
                Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (i < strength.coerceAtLeast(1) && password.length >= 1) color else c.border))
            }
        }
        Text(label, color = color, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium))
    }
}

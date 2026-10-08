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
fun AuthScreen(vm: AuthViewModel, justVerified: Boolean = false, pendingInvite: String? = null) {
    var register by rememberSaveable { mutableStateOf(false) }
    var forgot by rememberSaveable { mutableStateOf(false) }
    val state by vm.state.collectAsStateWithLifecycle()
    // Coming back from the emailed link: drop the stale "please verify" message.
    LaunchedEffect(justVerified) { if (justVerified) vm.clearError() }
    LaunchedEffect(pendingInvite) { vm.loadInvitePreview(pendingInvite) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            state.verifyEmail != null -> VerifyEmailScreen(state, onResend = { vm.resendVerification(state.verifyEmail!!) }, onBack = { register = false; vm.backToLogin() })
            forgot -> ForgotPasswordForm(vm, state, onBack = { forgot = false; vm.backToLogin() })
            register -> RegisterForm(vm, onLogin = { register = false; vm.clearError() })
            else -> LoginForm(vm, justVerified, onRegister = { register = true; vm.clearError() }, onForgot = { forgot = true; vm.clearError() })
        }
    }
}

@Composable
private fun VerifyEmailScreen(state: AuthViewModel.State, onResend: () -> Unit, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var cooldown by remember { mutableIntStateOf(0) }
    LaunchedEffect(cooldown) { if (cooldown > 0) { kotlinx.coroutines.delay(1000); cooldown-- } }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(88.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.split.primaryBg), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Email, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(20.dp))
        Text("Check your inbox", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            if (state.emailSent) "We sent a verification link to ${state.verifyEmail}. Open it to activate your account, then come back and log in."
            else "Your account was created, but we couldn't send the email just now. Tap Resend to try again.",
            color = MaterialTheme.split.fg2, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        state.notice?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.split.owed, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        state.error?.let { Spacer(Modifier.height(12.dp)); ErrorBanner(it) }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Open email app", {
            runCatching {
                context.startActivity(android.content.Intent.makeMainSelectorActivity(android.content.Intent.ACTION_MAIN, android.content.Intent.CATEGORY_APP_EMAIL).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        })
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { onResend(); cooldown = 30 }, enabled = cooldown == 0) {
            Text(if (cooldown > 0) "Resend email in ${cooldown}s" else "Resend email")
        }
        TextButton(onClick = onBack) { Text("Back to log in") }
    }
}

@Composable
private fun LoginForm(vm: AuthViewModel, justVerified: Boolean, onRegister: () -> Unit, onForgot: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val context = androidx.compose.ui.platform.LocalContext.current
    val enrollment by vm.biometric.collectAsStateWithLifecycle(initialValue = null)
    val fingerprint = enrollment?.takeIf { Biometric.state(context) == BiometricState.Ready }
    val unlockWithFingerprint: () -> Unit = {
        val e = fingerprint
        val activity = context.findFragmentActivity()
        if (e != null && activity != null) Biometric.decrypt(
            activity, e.blob, e.iv,
            onDone = { vm.loginWithRefresh(String(it)) },
            onError = vm::biometricFailed,
            onInvalidated = vm::biometricInvalidated,
        )
    }
    // Ask for the fingerprint once when the app opens signed out.
    var prompted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(fingerprint != null) { if (fingerprint != null && !prompted && !justVerified) { prompted = true; unlockWithFingerprint() } }

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
        state.invitePreview?.let { InviteBanner(it, Modifier.padding(bottom = 16.dp)) }
        if (justVerified && state.error == null) {
            Text("✓ Email verified. Log in to continue.", color = MaterialTheme.split.owed, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 16.dp))
        }
        state.notice?.let { Text(it, color = MaterialTheme.split.owed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp)) }
        state.error?.let { ErrorBanner(it, Modifier.padding(bottom = if (state.unverifiedLogin != null) 4.dp else 20.dp)) }
        state.unverifiedLogin?.let { who ->
            TextButton(onClick = { vm.resendVerification(who) }, modifier = Modifier.padding(bottom = 12.dp)) { Text("Resend verification email") }
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IconField(username, { username = it }, "Username", Icons.Default.Person, error = state.error != null)
            IconField(password, { password = it }, "Password", Icons.Default.Lock, password = true, error = state.error != null, onDone = { vm.login(username, password) })
            Text(
                "Forgot password?", Modifier.align(Alignment.End).clickable(onClick = onForgot),
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            )
            PrimaryButton("Log In", { vm.login(username, password) }, busy = state.busy, modifier = Modifier.padding(top = 4.dp))
            if (fingerprint != null) {
                OutlinedButton(
                    onClick = unlockWithFingerprint, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.Fingerprint, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (fingerprint!!.username.isNotBlank()) "Log in as ${fingerprint!!.username} with fingerprint" else "Log in with fingerprint", style = MaterialTheme.typography.titleSmall)
                }
            }
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
            state.invitePreview?.let { InviteBanner(it) }
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


@Composable
private fun ForgotPasswordForm(vm: AuthViewModel, state: AuthViewModel.State, onBack: () -> Unit) {
    var identifier by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        NavBar("Reset password", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.resetSent) {
                Text("Check your inbox", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "If an account matches, we've emailed a link to choose a new password. It works for one hour. " +
                        "Open it on this phone, set your password, then come back and log in.",
                    color = MaterialTheme.split.fg2, style = MaterialTheme.typography.bodyMedium,
                )
                PrimaryButton("Back to log in", onBack)
            } else {
                Text("Enter your username or the email you signed up with and we'll send you a reset link.", color = MaterialTheme.split.fg2, style = MaterialTheme.typography.bodyMedium)
                state.error?.let { ErrorBanner(it) }
                IconField(identifier, { identifier = it }, "Username or email", Icons.Default.Email, keyboard = KeyboardType.Email, onDone = { vm.forgotPassword(identifier) })
                PrimaryButton("Send reset link", { vm.forgotPassword(identifier) }, busy = state.busy)
            }
        }
    }
}


@Composable
private fun InviteBanner(invite: com.splitwise.app.data.InviteLookup, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.split.primaryBg).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("🎉 ${invite.inviterName} invited you to join \"${invite.groupName}\"", style = MaterialTheme.typography.titleSmall)
        Text(
            if (invite.emailHint.isNotBlank()) "Log in, or create an account with ${invite.emailHint}, and you'll join right away."
            else "Log in or create an account and you'll join right away.",
            color = MaterialTheme.split.fg2, style = MaterialTheme.typography.bodySmall,
        )
    }
}

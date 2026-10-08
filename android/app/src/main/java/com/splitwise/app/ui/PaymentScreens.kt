package com.splitwise.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitwise.app.data.Payment
import java.math.BigDecimal
import java.math.RoundingMode

/** What the user is about to settle; null shows payment history only. */
data class SettleTarget(val groupId: Int, val groupName: String, val payeeId: Int, val payeeName: String, val owed: String)

@Composable
fun SettleUpScreen(
    overview: Overview, userId: Int?, target: SettleTarget?,
    onPay: (Int, Int, String) -> Unit, onConfirm: (Payment) -> Unit, onCancel: (Payment) -> Unit, onBack: () -> Unit,
    onSettle: (SettleTarget) -> Unit = {},
) {
    val c = MaterialTheme.split
    val me = overview.me
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var amountText by rememberSaveable(target) { mutableStateOf(target?.owed?.toMoney()?.stripTrailingZeros()?.toPlainString() ?: "") }
    val amount = amountText.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)
    val owed = target?.owed?.toMoney()
    val amountError = when {
        target == null -> null
        amount == null || amount.signum() <= 0 -> "Enter an amount greater than zero."
        owed != null && amount > owed -> "That's more than the ${formatRs(owed)} you owe."
        else -> null
    }
    val shown = overview.payments.filter {
        when (filter) { 1 -> it.payer == userId; 2 -> it.payee == userId; 3 -> it.status == "pending"; else -> true }
    }

    LazyColumn(Modifier.fillMaxSize().systemBarsPadding().imePadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { NavBar("Settle Up", onBack) }
        if (target == null) hubSections(overview, onSettle, onConfirm, onCancel)
        if (target != null) {
            item {
                SplitCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Settlement in ${target.groupName}", color = c.fg2, style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Avatar(initials(me?.name.orEmpty(), me?.username.orEmpty()), me?.id ?: 0, 52.dp)
                                Text("You pay", color = c.fg2, style = MaterialTheme.typography.labelMedium)
                            }
                            Icon(Icons.Default.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Avatar(initials("", target.payeeName), target.payeeId, 52.dp)
                                Text("${target.payeeName} receives", color = c.fg2, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Rs", color = c.fg2, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(6.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                amountText, { v -> if (v.matches(Regex("""\d{0,9}([.]\d{0,2})?"""))) amountText = v }, singleLine = true,
                                textStyle = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp, color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                modifier = Modifier.widthIn(min = 60.dp, max = 220.dp).width(IntrinsicSize.Min),
                            )
                        }
                        amountError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Text(
                            "${target.payeeName} confirms they received it, and then your balance updates.",
                            color = c.fg3, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
            item {
                PrimaryButton(
                    "Confirm Payment of ${formatRs(amount ?: BigDecimal.ZERO)}",
                    { onPay(target.groupId, target.payeeId, amount!!.toPlainString()) },
                    Modifier.padding(16.dp), enabled = amountError == null,
                )
            }
        }
        item { SectionHeader(if (target == null) "History" else "Payment History") }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Sent", "Received", "Pending").forEachIndexed { i, label -> FilterChipPill(label, filter == i) { filter = i } }
            }
        }
        if (shown.isEmpty()) item { EmptyState("No payments yet.") }
        else item {
            SplitCard(Modifier.padding(16.dp, 12.dp).fillMaxWidth()) {
                shown.forEachIndexed { i, p ->
                    if (i > 0) Divider16()
                    PaymentRow(p, userId, onConfirm, onCancel)
                }
            }
        }
    }
}

@Composable
private fun FilterChipPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.split
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) MaterialTheme.colorScheme.primary else c.input)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    ) { Text(text, color = if (selected) androidx.compose.ui.graphics.Color.White else c.fg2, style = MaterialTheme.typography.titleSmall) }
}

@Composable
private fun PaymentRow(p: Payment, userId: Int?, onConfirm: (Payment) -> Unit, onCancel: (Payment) -> Unit) {
    val c = MaterialTheme.split
    val sent = p.payer == userId
    val other = if (sent) p.payeeUsername else p.payerUsername
    Column(Modifier.fillMaxWidth().padding(16.dp, 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(initials("", other), if (sent) p.payee else p.payer, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(if (sent) "You paid $other" else "$other paid you", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                Text("${p.groupName} · ${dayLabel(p.createdAt)}", color = c.fg2, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text((if (sent) "−" else "+") + formatRs(p.amount.toMoney()), color = if (sent) c.owe else c.owed, style = MaterialTheme.typography.titleMedium)
                StatusChip(if (p.status == "completed") "Completed" else "Pending", if (p.status == "completed") ChipKind.Done else ChipKind.Pending)
            }
        }
        if (p.status == "pending") {
            if (!sent) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SmallButton("Confirm received", { onConfirm(p) }) }
            else Row { SmallButton("Cancel payment", { onCancel(p) }, primary = false) }
        }
    }
}

// ───────────────────────── Profile ─────────────────────────

@Composable
fun EditProfileScreen(overview: Overview, onSave: (Int, String, String) -> Unit, onBack: () -> Unit) {
    val me = overview.me ?: return
    var name by rememberSaveable { mutableStateOf(me.name) }
    var email by rememberSaveable { mutableStateOf(me.email) }
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        NavBar("Edit Profile", onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IconField(name, { name = it }, "Full Name", androidx.compose.material.icons.Icons.Default.Person)
            IconField(email, { email = it }, "Email", androidx.compose.material.icons.Icons.Default.Email, keyboard = androidx.compose.ui.text.input.KeyboardType.Email)
            Text("Username @${me.username} can't be changed.", color = MaterialTheme.split.fg3, style = MaterialTheme.typography.bodySmall)
            PrimaryButton("Save changes", { onSave(me.id, name, email) }, enabled = name.isNotBlank() && email.isNotBlank())
        }
    }
}

@Composable
fun ChangePasswordScreen(onSave: (String, String) -> Unit, onBack: () -> Unit) {
    var old by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val mismatch = confirm.isNotEmpty() && confirm != new
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        NavBar("Change Password", onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IconField(old, { old = it }, "Current password", androidx.compose.material.icons.Icons.Default.Lock, password = true)
            IconField(new, { new = it }, "New password", androidx.compose.material.icons.Icons.Default.Lock, password = true)
            IconField(confirm, { confirm = it }, "Confirm new password", androidx.compose.material.icons.Icons.Default.Lock, password = true, error = mismatch)
            if (mismatch) Text("Passwords don't match.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            PrimaryButton("Update password", { onSave(old, new) }, enabled = old.isNotEmpty() && new.length >= 8 && !mismatch && confirm.isNotEmpty())
        }
    }
}

/** The "what do I need to do" part of Settle Up: debts to pay, payments to confirm, payments in flight. */
private fun androidx.compose.foundation.lazy.LazyListScope.hubSections(
    o: Overview, onSettle: (SettleTarget) -> Unit, onConfirm: (Payment) -> Unit, onCancel: (Payment) -> Unit,
) {
    val debts = o.debts().filter { it.remaining.signum() > 0 }
    val toConfirm = o.awaitingMyConfirmation()
    val sentPending = o.payments.filter { it.status == "pending" && it.payer == o.me?.id }
    val credits = o.credits()

    if (toConfirm.isNotEmpty()) {
        item { SectionHeader("Waiting for your confirmation") }
        item {
            SplitCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                toConfirm.forEachIndexed { i, p ->
                    if (i > 0) Divider16()
                    Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(initials("", p.payerUsername), p.payer, 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text("${p.payerUsername} says they paid you", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            Text("${formatRs(p.amount.toMoney())} · ${p.groupName}", color = MaterialTheme.split.fg2, style = MaterialTheme.typography.bodySmall)
                        }
                        SmallButton("Confirm", { onConfirm(p) })
                    }
                }
            }
        }
    }

    item { SectionHeader("You owe") }
    if (debts.isEmpty()) item { EmptyState("You don't owe anyone. 🎉") }
    else item {
        SplitCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            debts.forEachIndexed { i, d ->
                if (i > 0) Divider16()
                Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(initials("", d.payeeName), d.payeeId, 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text("You owe ${d.payeeName}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                        Text(d.groupName + if (d.pending.signum() > 0) " · ${formatRs(d.pending)} awaiting confirmation" else "", color = MaterialTheme.split.fg2, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(formatRs(d.remaining), color = MaterialTheme.split.owe, style = MaterialTheme.typography.titleMedium)
                        if (d.remaining.signum() > 0) SmallButton("Settle up", { onSettle(SettleTarget(d.groupId, d.groupName, d.payeeId, d.payeeName, d.remaining.toPlainString())) })
                    }
                }
            }
        }
    }

    if (sentPending.isNotEmpty()) {
        item { SectionHeader("Waiting for them to confirm") }
        item {
            SplitCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                sentPending.forEachIndexed { i, p ->
                    if (i > 0) Divider16()
                    Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("You paid ${p.payeeUsername}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            Text("${formatRs(p.amount.toMoney())} · ${p.groupName}", color = MaterialTheme.split.fg2, style = MaterialTheme.typography.bodySmall)
                        }
                        SmallButton("Cancel", { onCancel(p) }, primary = false)
                    }
                }
            }
        }
    }

    if (credits.isNotEmpty()) {
        item { SectionHeader("Owed to you") }
        item {
            SplitCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                credits.forEachIndexed { i, c ->
                    if (i > 0) Divider16()
                    Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${c.fromName} owes you", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            Text(c.groupName, color = MaterialTheme.split.fg2, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(formatRs(c.amount), color = MaterialTheme.split.owed, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

package com.splitwise.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.splitwise.app.data.Expense
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val symbols = DecimalFormatSymbols(Locale.US)

/** A display currency. Amounts are never converted: this only changes the symbol shown next to them. */
data class Currency(val code: String, val symbol: String, val name: String) {
    /** "Rs 4,800" for lettered symbols, "$4,800" for glyphs. */
    val prefix: String get() = if (symbol.all { it.isLetter() }) "$symbol " else symbol
}

val Currencies = listOf(
    Currency("PKR", "Rs", "Pakistani Rupee"), Currency("USD", "$", "US Dollar"), Currency("EUR", "€", "Euro"),
    Currency("GBP", "£", "British Pound"), Currency("INR", "₹", "Indian Rupee"), Currency("AED", "AED", "UAE Dirham"),
    Currency("SAR", "SAR", "Saudi Riyal"), Currency("QAR", "QAR", "Qatari Riyal"), Currency("CAD", "C$", "Canadian Dollar"),
    Currency("AUD", "A$", "Australian Dollar"), Currency("BDT", "৳", "Bangladeshi Taka"), Currency("TRY", "₺", "Turkish Lira"),
    Currency("MYR", "RM", "Malaysian Ringgit"), Currency("JPY", "¥", "Japanese Yen"),
)

fun currencyByCode(code: String?) = Currencies.firstOrNull { it.code == code } ?: Currencies.first()

/** The currency chosen in Account. Compose state, so every amount that reads it redraws when it changes. */
var activeCurrency: Currency by androidx.compose.runtime.mutableStateOf(Currencies.first())

/** "Rs 4,800" (whole amounts) or "Rs 4,800.50"; [forceDecimals] always shows two decimals. */
fun formatRs(amount: BigDecimal, forceDecimals: Boolean = false): String {
    val scaled = amount.setScale(2, RoundingMode.HALF_UP)
    val whole = !forceDecimals && scaled.stripTrailingZeros().scale() <= 0
    return activeCurrency.prefix + DecimalFormat(if (whole) "#,##0" else "#,##0.00", symbols).format(scaled)
}

fun String.toMoney(): BigDecimal = toBigDecimalOrNull() ?: BigDecimal.ZERO

/** What one user owes for [expense]: custom amount, custom percentage, or an equal share. */
fun shareOf(expense: Expense, userId: Int): BigDecimal {
    if (userId !in expense.splitOn) return BigDecimal.ZERO
    val total = expense.amount.toMoney()
    val detail = expense.splitDetails.firstOrNull { it.userId == userId }
    return when {
        detail?.amount != null -> detail.amount.toMoney()
        detail?.percentage != null -> total.multiply(detail.percentage.toMoney()).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        else -> total.divide(BigDecimal(expense.splitOn.size), 2, RoundingMode.HALF_UP)
    }
}

/** Positive: [userId] is owed money for this expense; negative: they owe it. */
fun netFor(expense: Expense, userId: Int): BigDecimal {
    val paid = if (userId in expense.paidBy) {
        expense.amount.toMoney().divide(BigDecimal(expense.paidBy.size), 2, RoundingMode.HALF_UP)
    } else BigDecimal.ZERO
    return paid - shareOf(expense, userId)
}

fun hasSplitMember(expense: Expense, userId: Int) = userId in expense.splitOn || userId in expense.paidBy

val GroupIcons = listOf("🏔️", "🏠", "🍽️", "✈️", "🎉", "🛒", "🎬", "⚽", "🏖️", "🚗", "🎓", "💼", "🎮", "☕", "🏕️", "🎁")
fun emojiFor(groupId: Int) = GroupIcons[groupId.mod(8)]

/** The group's chosen icon, or a stable default derived from its id. */
fun com.splitwise.app.data.Group.emoji() = icon.ifBlank { emojiFor(id) }

fun initials(name: String, username: String): String {
    val source = name.ifBlank { username }.trim()
    val parts = source.split(' ', '_').filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0][0]}${parts[1][0]}"
        source.length >= 2 -> source.take(2)
        else -> source
    }.uppercase()
}

private val dayFormat = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/** "Oct 6, 2026" in the device's time zone; falls back to the raw date prefix. */
fun dayLabel(iso: String): String = runCatching {
    java.time.OffsetDateTime.parse(iso).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(dayFormat)
}.getOrDefault(iso.take(10))

/** "2026-10" for an ISO timestamp in the device's time zone; used to group spending by month. */
fun monthKey(iso: String): String = runCatching {
    java.time.OffsetDateTime.parse(iso).atZoneSameInstant(java.time.ZoneId.systemDefault()).let { "%04d-%02d".format(it.year, it.monthValue) }
}.getOrDefault(iso.take(7))

fun currentMonthKey(): String = java.time.LocalDate.now().let { "%04d-%02d".format(it.year, it.monthValue) }

/** What [userId] personally spent and consumed. */
data class Spending(val paid: BigDecimal, val share: BigDecimal)

fun spendingFor(expenses: List<Expense>, userId: Int, month: String? = null): Spending {
    var paid = BigDecimal.ZERO
    var share = BigDecimal.ZERO
    expenses.filter { month == null || monthKey(it.createdAt) == month }.forEach { e ->
        if (userId in e.paidBy) paid += e.amount.toMoney().divide(BigDecimal(e.paidBy.size), 2, RoundingMode.HALF_UP)
        share += shareOf(e, userId)
    }
    return Spending(paid, share)
}

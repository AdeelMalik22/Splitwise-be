package com.splitwise.app.ui

import com.splitwise.app.data.Expense
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val symbols = DecimalFormatSymbols(Locale.US)

/** "Rs 4,800" (whole amounts) or "Rs 4,800.50"; [forceDecimals] always shows two decimals. */
fun formatRs(amount: BigDecimal, forceDecimals: Boolean = false): String {
    val scaled = amount.setScale(2, RoundingMode.HALF_UP)
    val whole = !forceDecimals && scaled.stripTrailingZeros().scale() <= 0
    return "Rs " + DecimalFormat(if (whole) "#,##0" else "#,##0.00", symbols).format(scaled)
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

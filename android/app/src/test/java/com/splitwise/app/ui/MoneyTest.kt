package com.splitwise.app.ui

import com.splitwise.app.data.Expense
import com.splitwise.app.data.SplitDetail
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {
    private fun expense(amount: String, paidBy: List<Int>, splitOn: List<Int>, details: List<SplitDetail> = emptyList()) =
        Expense(id = 1, name = "Dinner", amount = amount, paidBy = paidBy, splitOn = splitOn, splitDetails = details, groupId = 1)

    @Test fun formatsWholeAndFractionalAmounts() {
        assertEquals("Rs 4,800", formatRs(BigDecimal("4800.00")))
        assertEquals("Rs 4,800.50", formatRs(BigDecimal("4800.5")))
        assertEquals("Rs 8,450.00", formatRs(BigDecimal("8450"), forceDecimals = true))
    }

    @Test fun payerIsOwedTheOthersShares() {
        val e = expense("100.00", paidBy = listOf(1), splitOn = listOf(1, 2))
        assertEquals(BigDecimal("50.00"), netFor(e, 1))
        assertEquals(BigDecimal("-50.00"), netFor(e, 2))
    }

    @Test fun customAmountSplitIsRespected() {
        val e = expense("100.00", listOf(1), listOf(1, 2), listOf(SplitDetail(1, amount = "70.00"), SplitDetail(2, amount = "30.00")))
        assertEquals(BigDecimal("-30.00"), netFor(e, 2))
        assertEquals(BigDecimal("30.00"), netFor(e, 1))
    }

    @Test fun percentageSplitIsRespected() {
        val e = expense("200.00", listOf(1), listOf(1, 2), listOf(SplitDetail(1, percentage = "25"), SplitDetail(2, percentage = "75")))
        assertEquals(BigDecimal("150.00"), shareOf(e, 2))
    }

    @Test fun bystanderOwesNothing() {
        assertEquals(BigDecimal.ZERO, shareOf(expense("90.00", listOf(1), listOf(1)), 3))
    }

    @Test fun initialsFromNameOrUsername() {
        assertEquals("UK", initials("Umer Khan", "umer_k"))
        assertEquals("UM", initials("", "umer"))
        assertEquals("UK", initials("", "umer_khan"))
    }

    @Test fun passwordStrengthGrowsWithVariety() {
        assertEquals(0, passwordStrength("abc"))
        assertEquals(2, passwordStrength("abcdefg1"))
        assertEquals(3, passwordStrength("Abcdefg1!xyz"))
    }
}

class SpendingTest {
    private fun e(id: Int, amount: String, paidBy: List<Int>, splitOn: List<Int>, created: String) = com.splitwise.app.data.Expense(
        id = id, name = "x", amount = amount, paidBy = paidBy, splitOn = splitOn, groupId = 1, createdAt = created,
    )

    @Test fun soloExpenseCountsAsBothPaidAndShare() {
        val s = spendingFor(listOf(e(1, "500.00", listOf(1), listOf(1), "2026-10-03T10:00:00Z")), userId = 1)
        org.junit.Assert.assertEquals(BigDecimal("500.00"), s.paid)
        org.junit.Assert.assertEquals(BigDecimal("500.00"), s.share)
    }

    @Test fun sharedExpenseSplitsShareButNotPaid() {
        val list = listOf(e(1, "100.00", listOf(1), listOf(1, 2), "2026-10-03T10:00:00Z"), e(2, "60.00", listOf(2), listOf(1, 2), "2026-10-04T10:00:00Z"))
        val s = spendingFor(list, userId = 1)
        org.junit.Assert.assertEquals(BigDecimal("100.00"), s.paid)
        org.junit.Assert.assertEquals(BigDecimal("80.00"), s.share)
    }

    @Test fun monthFilterSeparatesMonths() {
        val list = listOf(e(1, "100.00", listOf(1), listOf(1), "2026-09-15T12:00:00Z"), e(2, "40.00", listOf(1), listOf(1), "2026-10-15T12:00:00Z"))
        org.junit.Assert.assertEquals(BigDecimal("40.00"), spendingFor(list, 1, "2026-10").paid)
        org.junit.Assert.assertEquals(BigDecimal("140.00"), spendingFor(list, 1).paid)
    }
}

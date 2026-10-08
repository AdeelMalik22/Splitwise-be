package com.splitwise.app.ui

import com.splitwise.app.data.Group
import com.splitwise.app.data.Member
import com.splitwise.app.data.OwedLine
import com.splitwise.app.data.Payment
import com.splitwise.app.data.Profile
import com.splitwise.app.data.Settlements
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class OverviewDebtsTest {
    private val me = Profile(1, "me")
    private fun overview(payments: List<Payment> = emptyList()) = Overview(
        me = me,
        groups = listOf(Group(10, "Trip")),
        balances = mapOf(10 to Settlements(youOwe = listOf(OwedLine(toUser = "sam", amount = "100.00")), owedToYou = listOf(OwedLine(fromUser = "kim", amount = "30.00")))),
        members = mapOf(10 to listOf(Member(1, "me"), Member(2, "sam"), Member(3, "kim"))),
        invites = emptyList(), notifications = emptyList(), activity = emptyList(), payments = payments,
    )

    @Test fun debtsPointAtThePayeesId() {
        val d = overview().debts().single()
        assertEquals(2, d.payeeId)
        assertEquals(BigDecimal("100.00"), d.remaining)
    }

    @Test fun pendingPaymentsReduceWhatCanStillBePaid() {
        val pending = Payment(5, group = 10, payer = 1, payee = 2, amount = "40.00", status = "pending")
        assertEquals(BigDecimal("60.00"), overview(listOf(pending)).debts().single().remaining)
    }

    @Test fun creditsAndConfirmations() {
        assertEquals("kim", overview().credits().single().fromName)
        val incoming = Payment(6, group = 10, payer = 3, payee = 1, amount = "30.00", status = "pending")
        assertEquals(listOf(6), overview(listOf(incoming)).awaitingMyConfirmation().map { it.id })
    }
}

package com.splitwise.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseRequestTest {
    @Test fun equalSplitSendsSplitOnOnly() {
        val json = AppJson.encodeToString(ExpenseRequest("Dinner", "", "100.00", listOf(1), splitOn = listOf(1, 2), groupId = 3))
        assertTrue(json.contains("\"split_on\":[1,2]"))
        assertFalse(json.contains("split_details"))
    }

    @Test fun exactSplitSendsDetailsOnlyAndOmitsNullFields() {
        val json = AppJson.encodeToString(
            ExpenseRequest("Hotel", "", "100.00", listOf(1), splitDetails = listOf(SplitShare(1, amount = "70.00"), SplitShare(2, amount = "30.00")), groupId = 3)
        )
        assertTrue(json.contains("\"split_details\":[{\"user_id\":1,\"amount\":\"70.00\"},{\"user_id\":2,\"amount\":\"30.00\"}]"))
        assertFalse(json.contains("split_on"))
        assertFalse(json.contains("percentage"))
    }

    @Test fun parsesExpenseWithNullShareFields() {
        val e = AppJson.decodeFromString<Expense>(
            """{"id":1,"name":"x","description":"","amount":"10.00","paid_by":[1],"split_on":[1,2],
               "split_details":[{"user_id":1,"amount":"4.00","percentage":null}],"group_id":2,"created_at":"2026-10-08T10:00:00Z"}"""
        )
        assertTrue(e.splitDetails.single().percentage == null && e.splitDetails.single().amount == "4.00")
    }
}

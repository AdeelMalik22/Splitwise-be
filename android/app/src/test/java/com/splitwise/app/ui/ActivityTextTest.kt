package com.splitwise.app.ui

import com.splitwise.app.data.ActivityItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActivityTextTest {
    private fun item(action: String, type: String, actor: Int = 1, meta: Map<String, String> = emptyMap(), group: Int? = 5) =
        ActivityItem(id = 1, actor = actor, actorUsername = "sam", action = action, entityType = type, groupId = group, metadata = meta)

    @Test fun describesYourOwnExpense() {
        val a = item("added", "expense", meta = mapOf("name" to "Dinner", "amount" to "960.00", "group_name" to "Trip"))
        assertEquals("You added \"Dinner\" in Trip · Rs 960", describeActivity(a, meId = 1))
    }

    @Test fun usesTheActorsNameForOthers() {
        val a = item("left", "group", actor = 2, meta = mapOf("group_name" to "Trip"))
        assertEquals("sam left \"Trip\"", describeActivity(a, meId = 1))
    }

    @Test fun describesRenameAndPayments() {
        assertEquals("You renamed \"Old\" to \"New\"", describeActivity(item("updated", "group", meta = mapOf("old_name" to "Old", "group_name" to "New")), 1))
        assertEquals("You paid umer · Rs 50 in Trip", describeActivity(item("paid", "payment", meta = mapOf("to" to "umer", "amount" to "50.00", "group_name" to "Trip")), 1))
    }

    @Test fun fallsBackForOldEntriesWithoutDetails() {
        assertEquals("You created a group", describeActivity(item("created", "group"), 1))
        assertEquals("You created group \"Trip\"", describeActivity(item("created", "group", meta = mapOf("group_name" to "Trip")), 1))
    }

    @Test fun rowsOpenOnlyGroupsThatStillExist() {
        assertEquals(5, activityGroupId(item("added", "expense"), setOf(5)))
        assertNull(activityGroupId(item("added", "expense"), setOf(9)))
        assertNull(activityGroupId(item("deleted", "group"), setOf(5)))
        assertNull(activityGroupId(item("left", "group"), setOf(5)))
    }
}

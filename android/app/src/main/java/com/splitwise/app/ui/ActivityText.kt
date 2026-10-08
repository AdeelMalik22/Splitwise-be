package com.splitwise.app.ui

import com.splitwise.app.data.ActivityItem
import java.math.BigDecimal

/** One-line, human description of a logged action, e.g. `You added "Dinner" in Trip · Rs 960`. */
fun describeActivity(a: ActivityItem, meId: Int?): String {
    val who = if (a.actor == meId) "You" else a.actorName.ifBlank { a.actorUsername }.ifBlank { "Someone" }
    val group = a.metadata["group_name"]?.let { " in $it" }.orEmpty()
    val name = a.metadata["name"]?.let { "\"$it\"" }.orEmpty()
    val amount = a.metadata["amount"]?.let { " · ${formatRs(it.toBigDecimalOrNull() ?: BigDecimal.ZERO)}" }.orEmpty()
    val groupName = a.metadata["group_name"]?.let { "\"$it\"" } ?: "a group"
    val groupLabel = a.metadata["group_name"]?.let { "group \"$it\"" } ?: "a group"

    return when (a.entityType to a.action) {
        "group" to "created" -> "$who created $groupLabel"
        "group" to "updated" -> a.metadata["old_name"]?.let { "$who renamed \"$it\" to $groupName" } ?: "$who updated $groupLabel"
        "group" to "deleted" -> "$who deleted $groupLabel"
        "group" to "joined" -> "$who joined $groupName"
        "group" to "left" -> "$who left $groupName"
        "member" to "removed" -> "$who removed ${a.metadata["member"] ?: "a member"} from $groupName"
        "expense" to "added" -> "$who added $name$group$amount"
        "expense" to "edited" -> "$who edited $name$group$amount"
        "expense" to "deleted" -> "$who deleted $name$group$amount"
        "payment" to "paid" -> "$who paid ${a.metadata["to"] ?: "someone"}$amount$group"
        "payment" to "confirmed" -> "$who confirmed a payment from ${a.metadata["from"] ?: "someone"}$amount$group"
        "payment" to "cancelled" -> "$who cancelled a payment to ${a.metadata["to"] ?: "someone"}$amount$group"
        else -> "$who ${a.action} ${a.entityType}${a.entityId?.let { " #$it" }.orEmpty()}"
    }
}

/** The group an activity row should open, if that group still exists for the user. */
fun activityGroupId(a: ActivityItem, existingGroupIds: Set<Int>): Int? =
    a.groupId?.takeIf { it in existingGroupIds && !(a.entityType == "group" && a.action in setOf("deleted", "left")) }

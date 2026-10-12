package com.splitwise.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakePendingWriteDao : PendingWriteDao {
    private val rows = mutableListOf<PendingWriteEntity>()
    private var nextId = 1L
    override suspend fun list(userId: Int) = rows.filter { it.userId == userId }.sortedBy { it.id }
    override suspend fun add(value: PendingWriteEntity): Long {
        val row = value.copy(id = nextId++)
        rows += row
        return row.id
    }
    override suspend fun remove(id: Long) { rows.removeAll { it.id == id } }
    override suspend fun deleteUser(userId: Int) { rows.removeAll { it.userId == userId } }
}

class OfflineWriteQueueTest {
    @Test
    fun queuedWritesAreScopedToTheirUserAndRemovedAfterSync() = runBlocking {
        val queue = OfflineWriteQueue(FakePendingWriteDao())
        queue.add(7, "expense", ExpenseRequest("Dinner", "", "100", listOf(7), groupId = 3, category = "other"))
        queue.add(8, "expense", ExpenseRequest("Taxi", "", "20", listOf(8), groupId = 4, category = "travel"))

        assertEquals(listOf("Dinner"), queue.list(7).map { queue.json.decodeFromString<ExpenseRequest>(it.payload).name })
        assertEquals(listOf("Taxi"), queue.list(8).map { queue.json.decodeFromString<ExpenseRequest>(it.payload).name })
        queue.remove(queue.list(7).single().id)
        assertEquals(emptyList<PendingWriteEntity>(), queue.list(7))
    }
}

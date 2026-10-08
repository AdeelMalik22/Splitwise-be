package com.splitwise.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException
import java.io.IOException

/** Turns any failure into a user-readable message (DRF error bodies are flattened). */
fun Throwable.userMessage(): String = when (this) {
    is HttpException -> {
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
        val parsed = body?.let { runCatching { flatten(AppJson.parseToJsonElement(it)) }.getOrNull() }
        when {
            !parsed.isNullOrBlank() -> parsed
            code() == 401 -> "Invalid credentials or expired session."
            code() == 429 -> "Too many requests. Try again in a minute."
            else -> "Server error (${code()})."
        }
    }
    is IOException -> "Can't reach the server. Check your connection."
    else -> message ?: "Something went wrong."
}

private fun flatten(e: JsonElement): String = when (e) {
    is JsonPrimitive -> e.content
    is JsonArray -> e.joinToString(" ") { flatten(it) }
    is JsonObject -> e.entries.joinToString(" ") { (k, v) ->
        if (k == "detail" || k == "non_field_errors") flatten(v) else "$k: ${flatten(v)}"
    }
}

private suspend fun <T> call(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

class Repository(private val tokens: TokenStore, private val clients: ApiClients) {
    private val api get() = clients.api

    val loggedIn: Flow<Boolean> = tokens.loggedIn
    val userId: Flow<Int?> = tokens.userId

    suspend fun login(username: String, password: String) = call {
        val pair = clients.auth.login(LoginRequest(username.trim(), password))
        tokens.save(pair.access, pair.refresh)
    }

    suspend fun register(username: String, name: String, email: String, password: String) = call {
        clients.auth.register(RegisterRequest(username.trim(), name.trim(), email.trim(), password))
        val pair = clients.auth.login(LoginRequest(username.trim(), password))
        tokens.save(pair.access, pair.refresh)
    }

    suspend fun logout() = tokens.clear()

    suspend fun groups() = call { api.groups().results }
    suspend fun createGroup(name: String, description: String) = call { api.createGroup(GroupRequest(name.trim(), description.trim())) }

    suspend fun members(groupId: Int) = call { api.members(groupId) }

    suspend fun expenses(groupId: Int) = call {
        api.expenses().filter { it.groupId == groupId }.sortedByDescending { it.createdAt }
    }

    suspend fun createExpense(request: ExpenseRequest) = call { api.createExpense(request) }

    suspend fun settlements(groupId: Int) = call {
        try {
            api.settlements(groupId)
        } catch (e: HttpException) {
            if (e.code() == 404) Settlements() else throw e // 404 = group has no expenses yet
        }
    }

    suspend fun searchUsers(query: String) = call { api.searchUsers(query.trim()) }
    suspend fun invite(groupId: Int, inviteeId: Int) = call { api.invite(InviteRequest(groupId, inviteeId)) }

    suspend fun invites() = call { api.invites().results }
    suspend fun respondToInvite(id: Int, accept: Boolean) = call {
        if (accept) api.acceptInvite(id) else api.declineInvite(id)
    }

    suspend fun notifications() = call { api.notifications().results }
    suspend fun markRead(id: Int) = call { api.markRead(id) }
}

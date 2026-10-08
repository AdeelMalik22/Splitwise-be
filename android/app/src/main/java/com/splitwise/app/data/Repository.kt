package com.splitwise.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException
import java.io.IOException

class EmailNotVerifiedException : Exception("Please verify your email address first. Check your inbox for the link.")

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
    val darkMode: Flow<Boolean?> = tokens.darkMode
    val alertsOn: Flow<Boolean> = tokens.alertsOn
    suspend fun setAlertsOn(on: Boolean) = tokens.setAlertsOn(on)
    suspend fun setDarkMode(dark: Boolean) = tokens.setDarkMode(dark)

    suspend fun login(username: String, password: String) = call {
        val pair = try {
            clients.auth.login(LoginRequest(username.trim(), password))
        } catch (e: HttpException) {
            // 403 from the login endpoint means "valid credentials, email not verified yet".
            if (e.code() == 403) throw EmailNotVerifiedException() else throw e
        }
        tokens.save(pair.access, pair.refresh)
    }

    /** Creates the account; the user must verify their email before they can log in. */
    suspend fun register(username: String, name: String, email: String, password: String) = call {
        clients.auth.register(RegisterRequest(username.trim(), name.trim(), email.trim(), password))
    }

    suspend fun resendVerification(identifier: String) = call { clients.auth.resendVerification(IdentifierRequest(identifier.trim())) }

    suspend fun logout() = tokens.clear()

    suspend fun groups() = call { api.groups().results }
    suspend fun createGroup(name: String, description: String, icon: String = "") =
        call { api.createGroup(GroupRequest(name.trim(), description.trim(), icon)) }
    suspend fun updateGroup(id: Int, name: String, description: String, icon: String) =
        call { api.updateGroup(id, GroupRequest(name.trim(), description.trim(), icon)) }
    suspend fun deleteGroup(id: Int) = call { api.deleteGroup(id) }
    suspend fun removeMember(groupId: Int, userId: Int) = call { api.removeMember(groupId, userId) }

    suspend fun members(groupId: Int) = call { api.members(groupId) }

    suspend fun expenses(groupId: Int) = call {
        api.expenses().filter { it.groupId == groupId }.sortedByDescending { it.createdAt }
    }

    suspend fun allExpenses() = call { api.expenses() }
    suspend fun createExpense(request: ExpenseRequest) = call { api.createExpense(request) }
    suspend fun expense(id: Int) = call { api.expense(id) }
    suspend fun updateExpense(id: Int, request: ExpenseRequest) = call { api.updateExpense(id, request) }

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

    suspend fun deleteExpense(id: Int) = call { api.deleteExpense(id) }

    suspend fun leaveGroup(groupId: Int) = call {
        val membership = api.memberships().results.firstOrNull { it.groupId == groupId }
            ?: throw IllegalStateException("You are not a member of this group.")
        api.leaveGroup(membership.id)
    }

    suspend fun payments() = call { api.payments().results }
    suspend fun createPayment(groupId: Int, payeeId: Int, amount: String) = call { api.createPayment(PaymentRequest(groupId, payeeId, amount)) }
    suspend fun confirmPayment(id: Int) = call { api.confirmPayment(id) }
    suspend fun cancelPayment(id: Int) = call { api.cancelPayment(id) }

    suspend fun updateProfile(id: Int, name: String, email: String) = call { api.updateProfile(id, ProfileUpdate(name.trim(), email.trim())) }
    suspend fun changePassword(old: String, new: String) = call { api.changePassword(ChangePasswordRequest(old, new)) }
    suspend fun deleteAccount(password: String) = call {
        api.deleteAccount(DeleteAccountRequest(password))
        tokens.clear()
    }

    suspend fun profile() = call { api.profile().results.first() }
    suspend fun activity() = call { api.activity().results }

    /** Settlements for every group at once; groups whose request fails are left out. */
    suspend fun balances(groups: List<Group>): Map<Int, Settlements> = coroutineScope {
        groups.map { g -> async { g.id to api.runCatchingSettlements(g.id) } }.awaitAll()
            .mapNotNull { (id, r) -> r?.let { id to it } }.toMap()
    }

    private suspend fun SplitwiseApi.runCatchingSettlements(id: Int): Settlements? = try {
        settlements(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        if (e.code() == 404) Settlements() else null
    } catch (e: Exception) {
        null
    }

    suspend fun notifications() = call { api.notifications().results }
    suspend fun markRead(id: Int) = call { api.markRead(id) }
}

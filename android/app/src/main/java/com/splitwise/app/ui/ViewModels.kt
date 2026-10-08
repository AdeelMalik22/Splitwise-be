package com.splitwise.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitwise.app.data.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Error(val message: String) : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
}

private fun <T> Result<T>.toLoad(): Load<T> =
    fold({ Load.Ready(it) }, { Load.Error(it.userMessage()) })

class AuthViewModel(private val repo: Repository) : ViewModel() {
    data class State(val busy: Boolean = false, val error: String? = null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isEmpty()) return fail("Enter your username and password.")
        run { repo.login(username, password) }
    }

    fun register(username: String, name: String, email: String, password: String) {
        if (username.isBlank() || email.isBlank()) return fail("Username and email are required.")
        if (password.length < 8) return fail("Password must be at least 8 characters.")
        run { repo.register(username, name, email, password) }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    private fun fail(msg: String) = _state.update { it.copy(error = msg) }

    private fun run(block: suspend () -> Result<Unit>) {
        if (_state.value.busy) return
        _state.value = State(busy = true)
        viewModelScope.launch {
            // On success the session flow flips and the UI leaves the auth screen.
            _state.value = block().fold({ State() }, { State(error = it.userMessage()) })
        }
    }
}

class SessionViewModel(private val repo: Repository) : ViewModel() {
    val loggedIn = repo.loggedIn
    val userId = repo.userId
    fun logout() = viewModelScope.launch { repo.logout() }
}

class GroupsViewModel(private val repo: Repository) : ViewModel() {
    private val _groups = MutableStateFlow<Load<List<Group>>>(Load.Loading)
    val groups: StateFlow<Load<List<Group>>> = _groups.asStateFlow()
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { _groups.value = repo.groups().toLoad() }
    }

    fun create(name: String, description: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.createGroup(name, description).fold(
                { refresh(); onDone() },
                { _actionError.value = it.userMessage() },
            )
        }
    }

    fun dismissError() { _actionError.value = null }
}

class GroupDetailViewModel(private val repo: Repository, private val groupId: Int) : ViewModel() {
    data class State(
        val expenses: Load<List<Expense>> = Load.Loading,
        val settlements: Load<Settlements> = Load.Loading,
        val members: Load<List<Member>> = Load.Loading,
        val searchResults: List<UserSummary> = emptyList(),
        val message: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { refresh() }

    /** [silent] polls in the background: failures keep the data already on screen. */
    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            val e = async { repo.expenses(groupId) }
            val s = async { repo.settlements(groupId) }
            val m = async { repo.members(groupId) }
            val (er, sr, mr) = Triple(e.await(), s.await(), m.await())
            _state.update { cur ->
                fun <T> pick(r: Result<T>, old: Load<T>): Load<T> =
                    if (silent && r.isFailure && old is Load.Ready) old else r.toLoad()
                cur.copy(expenses = pick(er, cur.expenses), settlements = pick(sr, cur.settlements), members = pick(mr, cur.members))
            }
        }
    }

    fun search(query: String) {
        if (query.trim().length < 2) return _state.update { it.copy(searchResults = emptyList()) }
        viewModelScope.launch {
            repo.searchUsers(query).fold(
                { found -> _state.update { it.copy(searchResults = found) } },
                { err -> _state.update { it.copy(message = err.userMessage()) } },
            )
        }
    }

    fun invite(user: UserSummary) {
        viewModelScope.launch {
            val msg = repo.invite(groupId, user.id).fold({ "Invite sent to ${user.username}." }, { it.userMessage() })
            _state.update { it.copy(message = msg, searchResults = emptyList()) }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

class AddExpenseViewModel(private val repo: Repository, private val groupId: Int) : ViewModel() {
    data class State(
        val members: Load<List<Member>> = Load.Loading,
        val busy: Boolean = false,
        val error: String? = null,
        val saved: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.update { it.copy(members = repo.members(groupId).toLoad()) } }
    }

    fun save(name: String, description: String, amountText: String, paidBy: Int?, splitOn: Set<Int>) {
        val amount = amountText.trim().toBigDecimalOrNull()
        val error = when {
            name.isBlank() -> "Enter a name."
            amount == null || amount <= BigDecimal.ZERO -> "Enter an amount greater than zero."
            paidBy == null -> "Choose who paid."
            splitOn.isEmpty() -> "Choose at least one person to split with."
            else -> null
        }
        if (error != null) return _state.update { it.copy(error = error) }
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = repo.createExpense(
                ExpenseRequest(name.trim(), description.trim(), amount!!.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                    listOf(paidBy!!), splitOn.toList(), groupId)
            )
            _state.update { s ->
                result.fold({ s.copy(busy = false, saved = true) }, { s.copy(busy = false, error = it.userMessage()) })
            }
        }
    }
}

class InboxViewModel(private val repo: Repository) : ViewModel() {
    data class State(
        val invites: Load<List<Invite>> = Load.Loading,
        val notifications: Load<List<AppNotification>> = Load.Loading,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val i = async { repo.invites() }
            val n = async { repo.notifications() }
            _state.update { it.copy(invites = i.await().toLoad(), notifications = n.await().toLoad()) }
        }
    }

    fun respond(invite: Invite, accept: Boolean) {
        viewModelScope.launch {
            repo.respondToInvite(invite.id, accept).fold(
                { refresh() },
                { err -> _state.update { it.copy(message = err.userMessage()) } },
            )
        }
    }

    fun markRead(n: AppNotification) {
        viewModelScope.launch { repo.markRead(n.id).onSuccess { refresh() } }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

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
    val darkMode = repo.darkMode
    fun setDarkMode(dark: Boolean) { viewModelScope.launch { repo.setDarkMode(dark) } }
    fun logout() { viewModelScope.launch { repo.logout() } }
}

/** Everything the signed-in tabs share, loaded together. */
data class Overview(
    val me: Profile?,
    val groups: List<Group>,
    val balances: Map<Int, Settlements>,
    val members: Map<Int, List<Member>>,
    val invites: List<Invite>,
    val notifications: List<AppNotification>,
    val activity: List<ActivityItem>,
) {
    fun owe(): BigDecimal = balances.values.flatMap { it.youOwe }.fold(BigDecimal.ZERO) { a, l -> a + l.amount.toMoney() }
    fun owed(): BigDecimal = balances.values.flatMap { it.owedToYou }.fold(BigDecimal.ZERO) { a, l -> a + l.amount.toMoney() }
    fun net(groupId: Int): BigDecimal {
        val s = balances[groupId] ?: return BigDecimal.ZERO
        return s.owedToYou.fold(BigDecimal.ZERO) { a, l -> a + l.amount.toMoney() } -
            s.youOwe.fold(BigDecimal.ZERO) { a, l -> a + l.amount.toMoney() }
    }
    fun pendingFor(userId: Int?) = invites.filter { it.status == "pending" && it.invitee == userId }
}

class OverviewViewModel(private val repo: Repository) : ViewModel() {
    private val _state = MutableStateFlow<Load<Overview>>(Load.Loading)
    val state: StateFlow<Load<Overview>> = _state.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init { refresh() }

    /** [silent] keeps what is on screen while reloading and after a transient failure. */
    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent && _state.value !is Load.Ready) _state.value = Load.Loading
            val groupsResult = repo.groups()
            val groups = groupsResult.getOrElse {
                if (!(silent && _state.value is Load.Ready)) _state.value = Load.Error(it.userMessage())
                return@launch
            }
            val me = async { repo.profile().getOrNull() }
            val balances = async { repo.balances(groups) }
            val members = groups.map { g -> g.id to async { repo.members(g.id).getOrDefault(emptyList()) } }
            val invites = async { repo.invites().getOrDefault(emptyList()) }
            val notifications = async { repo.notifications().getOrDefault(emptyList()) }
            val activity = async { repo.activity().getOrDefault(emptyList()) }
            _state.value = Load.Ready(
                Overview(
                    me = me.await(), groups = groups, balances = balances.await(),
                    members = members.associate { (id, d) -> id to d.await() },
                    invites = invites.await(), notifications = notifications.await(), activity = activity.await(),
                )
            )
        }
    }

    fun createGroup(name: String, description: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.createGroup(name, description).fold(
                { refresh(silent = true); onDone() },
                { _message.value = it.userMessage() },
            )
        }
    }

    fun respond(invite: Invite, accept: Boolean) {
        viewModelScope.launch {
            repo.respondToInvite(invite.id, accept).fold(
                { refresh(silent = true) },
                { _message.value = it.userMessage() },
            )
        }
    }

    fun markRead(n: AppNotification) {
        viewModelScope.launch { repo.markRead(n.id).onSuccess { refresh(silent = true) } }
    }

    fun messageShown() { _message.value = null }
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

class AddExpenseViewModel(private val repo: Repository) : ViewModel() {
    data class State(
        val groupId: Int? = null,
        val members: Load<List<Member>> = Load.Loading,
        val busy: Boolean = false,
        val error: String? = null,
        val saved: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun selectGroup(groupId: Int) {
        if (_state.value.groupId == groupId) return
        _state.update { it.copy(groupId = groupId, members = Load.Loading) }
        viewModelScope.launch {
            val result = repo.members(groupId).toLoad()
            _state.update { if (it.groupId == groupId) it.copy(members = result) else it }
        }
    }

    fun save(name: String, description: String, amountText: String, paidBy: Int?, splitOn: Set<Int>) {
        val amount = amountText.trim().toBigDecimalOrNull()
        val groupId = _state.value.groupId
        val error = when {
            groupId == null -> "Choose a group."
            name.isBlank() -> "Enter an expense name."
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
                    listOf(paidBy!!), splitOn.toList(), groupId!!)
            )
            _state.update { s ->
                result.fold({ s.copy(busy = false, saved = true) }, { s.copy(busy = false, error = it.userMessage()) })
            }
        }
    }
}

class InviteSearchViewModel(private val repo: Repository) : ViewModel() {
    data class State(val results: List<UserSummary> = emptyList(), val searched: Boolean = false, val message: String? = null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun search(query: String) {
        if (query.trim().length < 2) return _state.update { it.copy(results = emptyList(), searched = false) }
        viewModelScope.launch {
            repo.searchUsers(query).fold(
                { found -> _state.update { it.copy(results = found, searched = true) } },
                { err -> _state.update { it.copy(message = err.userMessage()) } },
            )
        }
    }

    fun invite(groupId: Int, user: UserSummary, onSent: () -> Unit) {
        viewModelScope.launch {
            repo.invite(groupId, user.id).fold(
                { _state.update { it.copy(message = "Invite sent to ${user.username}.") }; onSent() },
                { err -> _state.update { it.copy(message = err.userMessage()) } },
            )
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

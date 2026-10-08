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
    /**
     * [verifyEmail]: show the "check your inbox" screen for this address.
     * [unverifiedLogin]: the last login failed only because the email is unverified (offer to resend).
     */
    data class State(
        val busy: Boolean = false,
        val error: String? = null,
        val verifyEmail: String? = null,
        val unverifiedLogin: String? = null,
        val notice: String? = null,
        val emailSent: Boolean = true,
        val resetSent: Boolean = false,
        val invitePreview: InviteLookup? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isEmpty()) return fail("Enter your username and password.")
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            // On success the session flow flips and the UI leaves the auth screen.
            val preview = _state.value.invitePreview
            _state.value = repo.login(username, password).fold(
                { State() },
                { e -> if (e is EmailNotVerifiedException) State(error = e.message, unverifiedLogin = username.trim(), invitePreview = preview) else State(error = e.userMessage(), invitePreview = preview) },
            )
        }
    }

    fun register(username: String, name: String, email: String, password: String) {
        if (username.isBlank() || email.isBlank()) return fail("Username and email are required.")
        if (password.length < 8) return fail("Password must be at least 8 characters.")
        if (_state.value.busy) return
        val preview = _state.value.invitePreview
        _state.value = State(busy = true, invitePreview = preview)
        viewModelScope.launch {
            _state.value = repo.register(username, name, email, password).fold(
                { State(verifyEmail = email.trim(), emailSent = it.emailSent, invitePreview = preview) },
                { State(error = it.userMessage(), invitePreview = preview) },
            )
        }
    }

    fun resendVerification(identifier: String) {
        viewModelScope.launch {
            repo.resendVerification(identifier).fold(
                { _state.update { it.copy(notice = "If the account still needs verification, a new email is on its way.", error = null) } },
                { e -> _state.update { it.copy(error = e.userMessage()) } },
            )
        }
    }

    fun forgotPassword(identifier: String) {
        if (identifier.isBlank()) return fail("Enter your username or email.")
        if (_state.value.busy) return
        _state.value = State(busy = true)
        viewModelScope.launch {
            _state.value = repo.forgotPassword(identifier).fold(
                { State(resetSent = true) },
                { State(error = it.userMessage()) },
            )
        }
    }

    /** Shows who invited the person on the login/register screens (public lookup, no account needed). */
    fun loadInvitePreview(token: String?) {
        if (token == null) return _state.update { it.copy(invitePreview = null) }
        viewModelScope.launch {
            repo.lookupInvite(token).fold(
                { p -> _state.update { it.copy(invitePreview = p) } },
                { _state.update { it.copy(invitePreview = null) } },
            )
        }
    }

    fun backToLogin() { _state.update { State(invitePreview = it.invitePreview) } }
    fun clearError() = _state.update { it.copy(error = null, notice = null, unverifiedLogin = null) }
    private fun fail(msg: String) = _state.update { it.copy(error = msg) }
}

class SessionViewModel(private val repo: Repository) : ViewModel() {
    private val _epoch = MutableStateFlow(0)

    /** Changes on every sign-out, so screens created after the next sign-in start from a clean state. */
    val epoch: StateFlow<Int> = _epoch.asStateFlow()

    init {
        viewModelScope.launch { repo.loggedIn.collect { signedIn -> if (!signedIn) _epoch.update { it + 1 } } }
    }

    val loggedIn = repo.loggedIn
    val userId = repo.userId
    val darkMode = repo.darkMode
    val alertsOn = repo.alertsOn
    val pendingInvite = repo.pendingInvite
    fun setPendingInvite(token: String?) { viewModelScope.launch { repo.setPendingInvite(token) } }
    fun setAlertsOn(on: Boolean) { viewModelScope.launch { repo.setAlertsOn(on) } }
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
    val payments: List<Payment> = emptyList(),
    val expenses: List<Expense> = emptyList(),
) {
    fun spending(month: String? = null): Spending = spendingFor(expenses, me?.id ?: -1, month)

    fun owe(): BigDecimal = balances.values.flatMap { it.youOwe }.fold(BigDecimal.ZERO) { a, l -> a + l.amount.toMoney() }
    fun owed(): BigDecimal = balances.values.flatMap { it.owedToYou }.fold(BigDecimal.ZERO) { a, l -> a + l.amount.toMoney() }
    fun net(groupId: Int): BigDecimal {
        val s = balances[groupId] ?: return BigDecimal.ZERO
        return s.owedToYou.fold(BigDecimal.ZERO) { a, l -> a + l.amount.toMoney() } -
            s.youOwe.fold(BigDecimal.ZERO) { a, l -> a + l.amount.toMoney() }
    }
    fun pendingFor(userId: Int?) = invites.filter { it.status == "pending" && it.invitee == userId }

    /** Debts across all groups. [Debt.remaining] already subtracts payments waiting for confirmation. */
    fun debts(): List<Debt> {
        val myId = me?.id ?: return emptyList()
        return groups.flatMap { g ->
            val people = members[g.id].orEmpty()
            balances[g.id]?.youOwe.orEmpty().mapNotNull { line ->
                val payee = people.firstOrNull { it.username == line.toUser } ?: return@mapNotNull null
                val pending = payments.filter { it.status == "pending" && it.group == g.id && it.payer == myId && it.payee == payee.id }
                    .fold(BigDecimal.ZERO) { a, p -> a + p.amount.toMoney() }
                Debt(g.id, g.name, payee.id, payee.username, line.amount.toMoney(), pending)
            }
        }
    }

    fun credits(): List<Credit> = groups.flatMap { g ->
        balances[g.id]?.owedToYou.orEmpty().map { Credit(g.name, it.fromUser.orEmpty(), it.amount.toMoney()) }
    }

    fun awaitingMyConfirmation(): List<Payment> = payments.filter { it.status == "pending" && it.payee == me?.id }
}

data class Debt(val groupId: Int, val groupName: String, val payeeId: Int, val payeeName: String, val amount: BigDecimal, val pending: BigDecimal) {
    val remaining: BigDecimal get() = (amount - pending).max(BigDecimal.ZERO)
}

data class Credit(val groupName: String, val fromName: String, val amount: BigDecimal)

class OverviewViewModel(private val repo: Repository) : ViewModel() {
    private val _state = MutableStateFlow<Load<Overview>>(Load.Loading)
    val state: StateFlow<Load<Overview>> = _state.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init { refresh() }

    /** Pull-to-refresh: shows the spinner until the reload finishes. */
    fun pullRefresh() = refresh(silent = true, showSpinner = true)

    /** [silent] keeps what is on screen while reloading and after a transient failure. */
    fun refresh(silent: Boolean = false, showSpinner: Boolean = false) {
        viewModelScope.launch {
            if (showSpinner) _refreshing.value = true
            try { load(silent) } finally { _refreshing.value = false }
        }
    }

    private suspend fun load(silent: Boolean) {
        kotlinx.coroutines.coroutineScope {
            if (!silent && _state.value !is Load.Ready) _state.value = Load.Loading
            val groupsResult = repo.groups()
            val groups = groupsResult.getOrElse {
                if (!(silent && _state.value is Load.Ready)) _state.value = Load.Error(it.userMessage())
                return@coroutineScope
            }
            val me = async { repo.profile().getOrNull() }
            val balances = async { repo.balances(groups) }
            val members = groups.map { g -> g.id to async { repo.members(g.id).getOrDefault(emptyList()) } }
            val invites = async { repo.invites().getOrDefault(emptyList()) }
            val notifications = async { repo.notifications().getOrDefault(emptyList()) }
            val activity = async { repo.activity().getOrDefault(emptyList()) }
            val payments = async { repo.payments().getOrDefault(emptyList()) }
            val expenses = async { repo.allExpenses().getOrDefault(emptyList()) }
            _state.value = Load.Ready(
                Overview(
                    me = me.await(), groups = groups, balances = balances.await(),
                    members = members.associate { (id, d) -> id to d.await() },
                    invites = invites.await(), notifications = notifications.await(), activity = activity.await(),
                    payments = payments.await(), expenses = expenses.await(),
                )
            )
        }
    }

    fun createGroup(name: String, description: String, icon: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.createGroup(name, description, icon).fold(
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

    private fun <T> act(result: suspend () -> Result<T>, success: String? = null, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            result().fold(
                { refresh(silent = true); success?.let { _message.value = it }; onDone() },
                { _message.value = it.userMessage() },
            )
        }
    }

    fun pay(groupId: Int, payeeId: Int, amount: String, onDone: () -> Unit) =
        act({ repo.createPayment(groupId, payeeId, amount) }, "Payment recorded. They'll confirm once received.", onDone)

    fun confirmPayment(p: Payment) = act({ repo.confirmPayment(p.id) }, "Payment confirmed.")
    fun cancelPayment(p: Payment) = act({ repo.cancelPayment(p.id) }, "Payment cancelled.")

    fun updateGroup(id: Int, name: String, description: String, icon: String, onDone: () -> Unit) =
        act({ repo.updateGroup(id, name, description, icon) }, "Group updated.", onDone)

    fun deleteGroup(id: Int, onDone: () -> Unit) = act({ repo.deleteGroup(id) }, "Group deleted.", onDone)

    fun removeMember(groupId: Int, userId: Int) = act({ repo.removeMember(groupId, userId) }, "Member removed.")

    fun leaveGroup(groupId: Int, onDone: () -> Unit) = act({ repo.leaveGroup(groupId) }, "You left the group.", onDone)

    fun updateProfile(userId: Int, name: String, email: String, onDone: () -> Unit) =
        act({ repo.updateProfile(userId, name, email) }, "Profile updated.", onDone)

    fun changePassword(old: String, new: String, onDone: () -> Unit) =
        act({ repo.changePassword(old, new) }, "Password changed.", onDone)

    /** On success the session ends and the UI returns to the login screen. */
    fun deleteAccount(password: String) = act({ repo.deleteAccount(password) })
}

class GroupDetailViewModel(private val repo: Repository, private val groupId: Int) : ViewModel() {
    data class State(
        val expenses: Load<List<Expense>> = Load.Loading,
        val settlements: Load<Settlements> = Load.Loading,
        val members: Load<List<Member>> = Load.Loading,
        val searchResults: List<UserSummary> = emptyList(),
        val message: String? = null,
        val refreshing: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { refresh() }

    fun pullRefresh() {
        _state.update { it.copy(refreshing = true) }
        refresh(silent = true)
    }

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
                cur.copy(expenses = pick(er, cur.expenses), settlements = pick(sr, cur.settlements), members = pick(mr, cur.members), refreshing = false)
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

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repo.deleteExpense(expense.id).fold(
                { refresh(silent = true); _state.update { it.copy(message = "Expense deleted.") } },
                { err -> _state.update { it.copy(message = err.userMessage()) } },
            )
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

enum class SplitMode { Equal, Exact, Percent }

/** [values] holds the typed amount or percentage per member; ignored for equal splits. */
data class SplitInput(val mode: SplitMode, val members: Set<Int>, val values: Map<Int, String>)

class AddExpenseViewModel(private val repo: Repository) : ViewModel() {
    data class State(
        val groupId: Int? = null,
        val members: Load<List<Member>> = Load.Loading,
        val editing: Load<Expense>? = null,
        val busy: Boolean = false,
        val error: String? = null,
        val saved: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var editId: Int? = null

    /** Loads an existing expense so the form can be prefilled; its group is then fixed. */
    fun startEditing(expenseId: Int) {
        if (editId == expenseId) return
        editId = expenseId
        _state.update { it.copy(editing = Load.Loading) }
        viewModelScope.launch {
            val result = repo.expense(expenseId)
            _state.update { it.copy(editing = result.toLoad()) }
            result.onSuccess { selectGroup(it.groupId) }
        }
    }

    fun selectGroup(groupId: Int) {
        if (_state.value.groupId == groupId) return
        _state.update { it.copy(groupId = groupId, members = Load.Loading) }
        viewModelScope.launch {
            val result = repo.members(groupId).toLoad()
            _state.update { if (it.groupId == groupId) it.copy(members = result) else it }
        }
    }

    fun save(name: String, description: String, amountText: String, paidBy: Int?, split: SplitInput) {
        val amount = amountText.trim().toBigDecimalOrNull()?.setScale(2, java.math.RoundingMode.HALF_UP)
        val groupId = _state.value.groupId
        val shares = split.members.associateWith { split.values[it]?.trim()?.toBigDecimalOrNull() }
        val sum = shares.values.fold(BigDecimal.ZERO) { a, v -> a + (v ?: BigDecimal.ZERO) }
        val error = when {
            groupId == null -> "Choose a group."
            name.isBlank() -> "Enter an expense name."
            amount == null || amount <= BigDecimal.ZERO -> "Enter an amount greater than zero."
            paidBy == null -> "Choose who paid."
            split.members.isEmpty() -> "Choose at least one person to split with."
            split.mode != SplitMode.Equal && shares.values.any { it == null || it < BigDecimal.ZERO } -> "Enter a value for everyone in the split."
            split.mode == SplitMode.Exact && (sum - amount).abs() > BigDecimal("0.01") ->
                "Amounts add up to ${formatRs(sum, true)}, but the total is ${formatRs(amount, true)}."
            split.mode == SplitMode.Percent && (sum - BigDecimal(100)).abs() > BigDecimal("0.01") ->
                "Percentages add up to ${sum.stripTrailingZeros().toPlainString()}%, not 100%."
            else -> null
        }
        if (error != null) return _state.update { it.copy(error = error) }
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }

        val request = ExpenseRequest(
            name = name.trim(), description = description.trim(), amount = amount!!.toPlainString(),
            paidBy = listOf(paidBy!!), groupId = groupId!!,
            splitOn = if (split.mode == SplitMode.Equal) split.members.toList() else null,
            splitDetails = when (split.mode) {
                SplitMode.Equal -> null
                SplitMode.Exact -> shares.map { (id, v) -> SplitShare(id, amount = v!!.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()) }
                SplitMode.Percent -> shares.map { (id, v) -> SplitShare(id, percentage = v!!.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()) }
            },
        )
        viewModelScope.launch {
            val result = editId?.let { repo.updateExpense(it, request) } ?: repo.createExpense(request)
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

    fun inviteByEmail(groupId: Int, email: String, onSent: () -> Unit) {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            return _state.update { it.copy(message = "Enter a valid email address.") }
        }
        viewModelScope.launch {
            repo.inviteByEmail(groupId, email).fold(
                { _state.update { it.copy(message = "Invitation emailed to ${email.trim()}.") }; onSent() },
                { err -> _state.update { it.copy(message = err.userMessage()) } },
            )
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

class JoinViewModel(private val repo: Repository, private val token: String) : ViewModel() {
    data class State(val info: Load<InviteLookup> = Load.Loading, val joining: Boolean = false, val error: String? = null, val joined: JoinResult? = null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(info = Load.Loading) }
        viewModelScope.launch { _state.update { it.copy(info = repo.lookupInvite(token).toLoad()) } }
    }

    fun join() {
        if (_state.value.joining) return
        _state.update { it.copy(joining = true, error = null) }
        viewModelScope.launch {
            repo.joinWithInvite(token).fold(
                { r -> _state.update { it.copy(joining = false, joined = r) } },
                { e -> _state.update { it.copy(joining = false, error = e.userMessage()) } },
            )
        }
    }
}

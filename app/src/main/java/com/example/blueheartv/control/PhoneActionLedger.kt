package com.example.blueheartv.control

/**
 * Device-side safety ledger for run-scoped phone actions.
 *
 * It intentionally treats an action that was running during process death as
 * indeterminate. Replaying it might duplicate a tap, type or app launch, so
 * the only safe outcome is to reject that replay and let the server report a
 * recoverable failure.
 */
enum class PhoneActionState {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    INDETERMINATE,
}

sealed interface PhoneActionAdmission {
    data object Accepted : PhoneActionAdmission
    data object DuplicateCompleted : PhoneActionAdmission
    data object InFlight : PhoneActionAdmission
    data object RunCancelled : PhoneActionAdmission
    data object DuplicateRejected : PhoneActionAdmission
    data object IndeterminateReplay : PhoneActionAdmission
    data object RunLimitReached : PhoneActionAdmission
}

data class PhoneActionKey(
    val runId: String,
    val actionId: String,
)

data class PhoneActionLedgerSnapshot(
    val cancelledRuns: Set<String> = emptySet(),
    val actions: Map<PhoneActionKey, PhoneActionState> = emptyMap(),
)

interface PhoneActionLedgerStore {
    fun load(): PhoneActionLedgerSnapshot
    fun save(snapshot: PhoneActionLedgerSnapshot)
}

/** Test and fallback store; production injects the SharedPreferences-backed store. */
class InMemoryPhoneActionLedgerStore : PhoneActionLedgerStore {
    private var snapshot = PhoneActionLedgerSnapshot()

    override fun load(): PhoneActionLedgerSnapshot = snapshot.copy(
        cancelledRuns = snapshot.cancelledRuns.toSet(),
        actions = snapshot.actions.toMap(),
    )

    override fun save(snapshot: PhoneActionLedgerSnapshot) {
        this.snapshot = snapshot.copy(
            cancelledRuns = snapshot.cancelledRuns.toSet(),
            actions = snapshot.actions.toMap(),
        )
    }
}

class PhoneActionLedger(
    private val store: PhoneActionLedgerStore,
    private val maxActionsPerRun: Int = DEFAULT_MAX_ACTIONS_PER_RUN,
    private val maxStoredActions: Int = DEFAULT_MAX_STORED_ACTIONS,
    private val maxCancelledRuns: Int = DEFAULT_MAX_CANCELLED_RUNS,
) {
    private val restoredSnapshot = store.load()
    private val cancelledRuns = LinkedHashSet(restoredSnapshot.cancelledRuns)
    private val actions = LinkedHashMap(restoredSnapshot.actions)

    init {
        val abandonedRunIds = actions.entries
            .filter { (_, state) -> state == PhoneActionState.QUEUED || state == PhoneActionState.RUNNING }
            .mapTo(linkedSetOf()) { (key, _) -> key.runId }
        val changed = abandonedRunIds.isNotEmpty()
        if (changed) {
            // A process restart cannot prove whether a shell side effect was
            // applied.  Fence the whole logical run, not merely the recorded
            // action, so a delayed follow-up action cannot resume it.
            abandonedRunIds.forEach { runId ->
                cancelledRuns.remove(runId)
                cancelledRuns.add(runId)
            }
        }
        if (changed) {
            actions.replaceAll { _, state ->
                if (state == PhoneActionState.QUEUED || state == PhoneActionState.RUNNING) {
                    PhoneActionState.INDETERMINATE
                } else {
                    state
                }
            }
            persist()
        }
    }

    @Synchronized
    fun admit(runId: String, actionId: String): PhoneActionAdmission {
        if (runId in cancelledRuns) return PhoneActionAdmission.RunCancelled
        val key = PhoneActionKey(runId, actionId)
        when (actions[key]) {
            PhoneActionState.SUCCEEDED -> return PhoneActionAdmission.DuplicateCompleted
            PhoneActionState.QUEUED,
            PhoneActionState.RUNNING -> return PhoneActionAdmission.InFlight
            PhoneActionState.CANCELLED,
            PhoneActionState.FAILED -> return PhoneActionAdmission.DuplicateRejected
            PhoneActionState.INDETERMINATE -> return PhoneActionAdmission.IndeterminateReplay
            null -> Unit
        }
        if (actions.keys.count { it.runId == runId } >= maxActionsPerRun) {
            return PhoneActionAdmission.RunLimitReached
        }
        actions[key] = PhoneActionState.QUEUED
        persist()
        return PhoneActionAdmission.Accepted
    }

    @Synchronized
    fun markRunning(runId: String, actionId: String): Boolean {
        val key = PhoneActionKey(runId, actionId)
        if (runId in cancelledRuns || actions[key] != PhoneActionState.QUEUED) return false
        actions[key] = PhoneActionState.RUNNING
        persist()
        return true
    }

    @Synchronized
    fun complete(runId: String, actionId: String, state: PhoneActionState) {
        require(state in terminalStates) { "Only terminal phone action states may be persisted." }
        val key = PhoneActionKey(runId, actionId)
        if (actions[key] == null) return
        actions[key] = if (runId in cancelledRuns) PhoneActionState.CANCELLED else state
        persist()
    }

    @Synchronized
    fun cancelRun(runId: String) {
        cancelledRuns.remove(runId)
        cancelledRuns.add(runId)
        actions.replaceAll { key, state ->
            if (key.runId == runId && state in setOf(PhoneActionState.QUEUED, PhoneActionState.RUNNING)) {
                PhoneActionState.CANCELLED
            } else {
                state
            }
        }
        persist()
    }

    @Synchronized
    fun actionState(runId: String, actionId: String): PhoneActionState? =
        actions[PhoneActionKey(runId, actionId)]

    @Synchronized
    fun isRunCancelled(runId: String): Boolean = runId in cancelledRuns

    private fun persist() {
        trimOldEntries()
        store.save(
            PhoneActionLedgerSnapshot(
                cancelledRuns = cancelledRuns.toSet(),
                actions = actions.toMap(),
            ),
        )
    }

    private fun trimOldEntries() {
        while (cancelledRuns.size > maxCancelledRuns) {
            cancelledRuns.remove(cancelledRuns.first())
        }
        while (actions.size > maxStoredActions) {
            actions.remove(actions.entries.first().key)
        }
    }

    private companion object {
        const val DEFAULT_MAX_ACTIONS_PER_RUN = 16
        const val DEFAULT_MAX_STORED_ACTIONS = 512
        const val DEFAULT_MAX_CANCELLED_RUNS = 128
        val terminalStates = setOf(
            PhoneActionState.SUCCEEDED,
            PhoneActionState.FAILED,
            PhoneActionState.CANCELLED,
            PhoneActionState.INDETERMINATE,
        )
    }
}

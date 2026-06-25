package com.example.blueheartv.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneActionLedgerTest {

    @Test
    fun duplicateCompletedAction_isNeverAdmittedForSecondExecution() {
        val ledger = PhoneActionLedger(InMemoryPhoneActionLedgerStore())

        assertTrue(ledger.admit("run-1", "action-1") is PhoneActionAdmission.Accepted)
        assertTrue(ledger.markRunning("run-1", "action-1"))
        ledger.complete("run-1", "action-1", PhoneActionState.SUCCEEDED)

        assertEquals(
            PhoneActionAdmission.DuplicateCompleted,
            ledger.admit("run-1", "action-1"),
        )
    }

    @Test
    fun cancelledRun_rejectsQueuedAndFutureActions() {
        val ledger = PhoneActionLedger(InMemoryPhoneActionLedgerStore())
        ledger.admit("run-1", "action-1")

        ledger.cancelRun("run-1")

        assertEquals(
            PhoneActionAdmission.RunCancelled,
            ledger.admit("run-1", "action-1"),
        )
        assertEquals(
            PhoneActionAdmission.RunCancelled,
            ledger.admit("run-1", "action-2"),
        )
        assertEquals(PhoneActionState.CANCELLED, ledger.actionState("run-1", "action-1"))
    }

    @Test
    fun processRestart_marksStartedActionIndeterminate_andBlocksReplay() {
        val store = InMemoryPhoneActionLedgerStore()
        val first = PhoneActionLedger(store)
        first.admit("run-1", "action-1")
        first.markRunning("run-1", "action-1")

        val restored = PhoneActionLedger(store)

        assertEquals(PhoneActionState.INDETERMINATE, restored.actionState("run-1", "action-1"))
        assertEquals(
            PhoneActionAdmission.RunCancelled,
            restored.admit("run-1", "action-1"),
        )
    }

    @Test
    fun processRestart_cancelsTheWholeRunSoANewActionCannotResumeIt() {
        val store = InMemoryPhoneActionLedgerStore()
        val first = PhoneActionLedger(store)
        first.admit("run-1", "action-1")
        first.markRunning("run-1", "action-1")

        val restored = PhoneActionLedger(store)

        assertTrue(restored.isRunCancelled("run-1"))
        assertEquals(
            PhoneActionAdmission.RunCancelled,
            restored.admit("run-1", "action-2"),
        )
    }
}

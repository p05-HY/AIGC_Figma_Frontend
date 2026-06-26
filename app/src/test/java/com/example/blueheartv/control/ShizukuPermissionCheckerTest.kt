package com.example.blueheartv.control

import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuPermissionCheckerTest {

    @Test
    fun needsPermissionTreatsMissingBinderAsUnavailableInsteadOfThrowing() {
        val checker = ShizukuPermissionChecker(
            isPreV11 = { false },
            checkSelfPermission = { throw IllegalStateException("binder haven't been received") },
            requestPermission = {},
        )

        assertTrue(checker.needsPermission())
    }

    @Test
    fun requestIfNeededReturnsBinderUnavailableWhenPermissionCheckThrows() {
        val checker = ShizukuPermissionChecker(
            isPreV11 = { false },
            checkSelfPermission = { throw IllegalStateException("binder haven't been received") },
            requestPermission = {},
        )

        assertEquals(
            ShizukuPermissionRequestResult.BinderUnavailable,
            checker.requestIfNeeded(7001),
        )
    }

    @Test
    fun requestIfNeededRequestsPermissionWhenBinderIsReadyAndPermissionMissing() {
        var requestedCode: Int? = null
        val checker = ShizukuPermissionChecker(
            isPreV11 = { false },
            checkSelfPermission = { PackageManager.PERMISSION_DENIED },
            requestPermission = { requestedCode = it },
        )

        assertEquals(
            ShizukuPermissionRequestResult.Requested,
            checker.requestIfNeeded(7001),
        )
        assertEquals(7001, requestedCode)
    }
}

package com.example

import com.example.shizuku.ShizukuConnectionState
import com.example.shizuku.ShizukuStatusAction
import org.junit.Assert.assertEquals
import org.junit.Test

class ShizukuStatusActionTest {

    @Test
    fun `pill action matches the current Shizuku state`() {
        assertEquals(
            ShizukuStatusAction.RequestPermission,
            ShizukuConnectionState.PermissionDenied(canAskAgain = true).statusAction(),
        )
        assertEquals(
            ShizukuStatusAction.Refresh,
            ShizukuConnectionState.PermissionGranted.statusAction(),
        )
        assertEquals(
            ShizukuStatusAction.Refresh,
            ShizukuConnectionState.ServiceConnected(2000, "UID 2000 Shell").statusAction(),
        )
        assertEquals(
            ShizukuStatusAction.ShowGuide,
            ShizukuConnectionState.ServiceDisconnected("Binder stopped").statusAction(),
        )
        assertEquals(
            ShizukuStatusAction.ShowGuide,
            ShizukuConnectionState.NotRunning.statusAction(),
        )
        assertEquals(
            ShizukuStatusAction.ShowGuide,
            ShizukuConnectionState.NotInstalled.statusAction(),
        )
    }
}

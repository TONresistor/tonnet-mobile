package com.tonnet.browser.data

import com.tonnet.browser.core.NetworkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyResetCoordinatorTest {
    @Test
    fun `network switch starts only after data and proxy cleanup complete`() {
        val coordinator = PrivacyResetCoordinator()

        assertTrue(coordinator.begin(NetworkMode.TUNNEL_2_HOP))
        assertNull(coordinator.onProxyCleared())
        assertEquals(NetworkMode.TUNNEL_2_HOP, coordinator.onDataCleared())
        assertTrue(coordinator.switchRequested)
        assertNull(coordinator.onDataCleared())

        coordinator.complete()
        assertFalse(coordinator.inProgress)
    }

    @Test
    fun `only one privacy reset can run and cancellation permits a retry`() {
        val coordinator = PrivacyResetCoordinator()

        assertTrue(coordinator.begin(NetworkMode.DIRECT))
        assertFalse(coordinator.begin(NetworkMode.TUNNEL_2_HOP))
        coordinator.cancel()

        assertTrue(coordinator.begin(NetworkMode.TUNNEL_2_HOP))
        assertEquals(NetworkMode.TUNNEL_2_HOP, coordinator.pendingMode)
    }
}

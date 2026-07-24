package com.tonnet.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyHealthSupervisorTest {
    @Test
    fun `tunnel recovery returns to ready when Tonutils publishes a new route`() {
        val supervisor = ProxyHealthSupervisor()

        assertEquals(
            ProxyHealthAction.Recovering,
            supervisor.observe(ProxyContract.STATE_RECOVERING, nowMs = 1_000L),
        )
        assertEquals(
            ProxyHealthAction.Ready,
            supervisor.observe(ProxyContract.STATE_READY, nowMs = 10_000L),
        )
    }

    @Test
    fun `tunnel recovery fails closed after ninety seconds`() {
        val supervisor = ProxyHealthSupervisor()
        supervisor.observe(ProxyContract.STATE_RECOVERING, nowMs = 1_000L)

        assertEquals(
            ProxyHealthAction.Recovering,
            supervisor.observe(ProxyContract.STATE_RECOVERING, nowMs = 90_999L),
        )
        assertTrue(
            supervisor.observe(ProxyContract.STATE_RECOVERING, nowMs = 91_000L) is
                ProxyHealthAction.Failed,
        )
    }

    @Test
    fun `stopped failed and invalid native states fail closed`() {
        val states = listOf(
            ProxyContract.STATE_STOPPED,
            ProxyContract.STATE_FAILED,
            ProxyContract.STATE_STARTING,
            Int.MAX_VALUE,
        )

        states.forEach { state ->
            assertTrue(
                "expected native state $state to fail",
                ProxyHealthSupervisor().observe(state, nowMs = 0L) is ProxyHealthAction.Failed,
            )
        }
    }
}

package com.tonnet.browser.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyBindingLifecycleTest {
    @Test
    fun `process disconnect keeps the binding and desired mode for Android recovery`() {
        val lifecycle = ProxyBindingLifecycle()
        val epoch = requireNotNull(lifecycle.request(1))

        assertEquals(BindingLossAction.RECOVER, lifecycle.onServiceDisconnected(epoch))
        assertEquals(1, lifecycle.desiredMode(epoch))
        assertNull(lifecycle.beginBindingIfNeeded())
    }

    @Test
    fun `dead binding is replaced without losing the desired mode`() {
        val lifecycle = ProxyBindingLifecycle()
        val oldEpoch = requireNotNull(lifecycle.request(1))

        assertEquals(BindingLossAction.REBIND, lifecycle.onBindingDied(oldEpoch))
        val newEpoch = requireNotNull(lifecycle.beginBindingIfNeeded())

        assertNotEquals(oldEpoch, newEpoch)
        assertEquals(1, lifecycle.desiredMode(newEpoch))
    }

    @Test
    fun `null binding fails instead of rebinding forever`() {
        val lifecycle = ProxyBindingLifecycle()
        val epoch = requireNotNull(lifecycle.request(1))

        assertEquals(BindingLossAction.FAIL, lifecycle.onNullBinding(epoch))
        assertNull(lifecycle.currentMode())
        assertNull(lifecycle.beginBindingIfNeeded())
    }

    @Test
    fun `callbacks from an obsolete binding are ignored`() {
        val lifecycle = ProxyBindingLifecycle()
        val oldEpoch = requireNotNull(lifecycle.request(0))
        assertEquals(BindingLossAction.REBIND, lifecycle.onBindingDied(oldEpoch))
        val newEpoch = requireNotNull(lifecycle.beginBindingIfNeeded())

        assertEquals(BindingLossAction.IGNORE, lifecycle.onServiceDisconnected(oldEpoch))
        assertFalse(lifecycle.accepts(oldEpoch))
        assertTrue(lifecycle.accepts(newEpoch))
    }

}

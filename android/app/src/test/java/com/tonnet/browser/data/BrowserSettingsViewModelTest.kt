package com.tonnet.browser.data

import com.tonnet.browser.core.NetworkMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a setting is applied only after it is persisted`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository()
        val viewModel = BrowserSettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.setNetworkMode(NetworkMode.TUNNEL_2_HOP)
        assertEquals(SettingsField.NETWORK_MODE, viewModel.state.value.pendingField)
        assertEquals(NetworkMode.DIRECT, viewModel.state.value.settings.networkMode)

        advanceUntilIdle()

        assertEquals(NetworkMode.TUNNEL_2_HOP, viewModel.state.value.settings.networkMode)
        assertNull(viewModel.state.value.pendingField)
        assertEquals(
            BrowserSettingsEffect.NetworkModeSaved(NetworkMode.TUNNEL_2_HOP),
            viewModel.effects.first(),
        )
    }

    @Test
    fun `a failed setting write preserves the persisted state`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository().apply {
            writeFailure = IllegalStateException("write failed")
        }
        val viewModel = BrowserSettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.setJavaScriptEnabled(false)
        advanceUntilIdle()

        assertEquals(BrowserSettingsEffect.SaveFailed, viewModel.effects.first())
        assertFalse(viewModel.state.value.pendingField != null)
        assertEquals(true, viewModel.state.value.settings.javaScriptEnabled)
    }

    @Test
    fun `anti fingerprinting changes only after persistence`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository()
        val viewModel = BrowserSettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.setAntiFingerprintingEnabled(false)
        assertEquals(SettingsField.ANTI_FINGERPRINTING, viewModel.state.value.pendingField)
        assertEquals(true, viewModel.state.value.settings.antiFingerprintingEnabled)

        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.settings.antiFingerprintingEnabled)
        assertNull(viewModel.state.value.pendingField)
        assertEquals(
            BrowserSettingsEffect.AntiFingerprintingSaved(false),
            viewModel.effects.first(),
        )
    }

    @Test
    fun `clear on exit changes only after persistence`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository()
        val viewModel = BrowserSettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.setClearOnExit(true)
        assertEquals(SettingsField.CLEAR_ON_EXIT, viewModel.state.value.pendingField)
        assertEquals(false, viewModel.state.value.settings.clearOnExit)

        advanceUntilIdle()

        assertEquals(true, viewModel.state.value.settings.clearOnExit)
        assertNull(viewModel.state.value.pendingField)
        assertEquals(
            BrowserSettingsEffect.ClearOnExitSaved(true),
            viewModel.effects.first(),
        )
    }

    private class FakeSettingsRepository : BrowserSettingsRepository {
        private val mutableSettings = MutableStateFlow(BrowserSettings())
        override val settings: Flow<BrowserSettings> = mutableSettings
        var writeFailure: Exception? = null

        override suspend fun setNetworkMode(mode: NetworkMode) {
            failIfRequested()
            mutableSettings.value = mutableSettings.value.copy(networkMode = mode)
        }

        override suspend fun setJavaScriptEnabled(enabled: Boolean) {
            failIfRequested()
            mutableSettings.value = mutableSettings.value.copy(javaScriptEnabled = enabled)
        }

        override suspend fun setAntiFingerprintingEnabled(enabled: Boolean) {
            failIfRequested()
            mutableSettings.value = mutableSettings.value.copy(
                antiFingerprintingEnabled = enabled,
            )
        }

        override suspend fun setClearOnExit(enabled: Boolean) {
            failIfRequested()
            mutableSettings.value = mutableSettings.value.copy(clearOnExit = enabled)
        }

        override suspend fun setHomepage(homepage: String?) {
            failIfRequested()
            mutableSettings.value = mutableSettings.value.copy(homepage = homepage)
        }

        private fun failIfRequested() {
            writeFailure?.let { throw it }
        }
    }
}

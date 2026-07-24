package com.tonnet.browser.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tonnet.browser.core.NetworkMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SettingsField {
    NETWORK_MODE,
    JAVASCRIPT,
    ANTI_FINGERPRINTING,
    CLEAR_ON_EXIT,
    HOMEPAGE,
}

data class BrowserSettingsUiState(
    val settings: BrowserSettings = BrowserSettings(),
    val initialized: Boolean = false,
    val pendingField: SettingsField? = null,
)

sealed interface BrowserSettingsEffect {
    data class NetworkModeSaved(val mode: NetworkMode) : BrowserSettingsEffect
    data class JavaScriptSaved(val enabled: Boolean) : BrowserSettingsEffect
    data class AntiFingerprintingSaved(val enabled: Boolean) : BrowserSettingsEffect
    data class ClearOnExitSaved(val enabled: Boolean) : BrowserSettingsEffect
    data class HomepageSaved(val homepage: String?) : BrowserSettingsEffect
    data object SaveFailed : BrowserSettingsEffect
    data object LoadFailed : BrowserSettingsEffect
}

class BrowserSettingsViewModel(
    private val repository: BrowserSettingsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BrowserSettingsUiState())
    val state: StateFlow<BrowserSettingsUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<BrowserSettingsEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            try {
                repository.settings.collect { persisted ->
                    mutableState.update {
                        it.copy(
                            settings = persisted,
                            initialized = true,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                effectChannel.send(BrowserSettingsEffect.LoadFailed)
            }
        }
    }

    fun setNetworkMode(mode: NetworkMode) {
        mutate(
            field = SettingsField.NETWORK_MODE,
            write = { repository.setNetworkMode(mode) },
            success = BrowserSettingsEffect.NetworkModeSaved(mode),
        )
    }

    fun setJavaScriptEnabled(enabled: Boolean) {
        mutate(
            field = SettingsField.JAVASCRIPT,
            write = { repository.setJavaScriptEnabled(enabled) },
            success = BrowserSettingsEffect.JavaScriptSaved(enabled),
        )
    }

    fun setAntiFingerprintingEnabled(enabled: Boolean) {
        mutate(
            field = SettingsField.ANTI_FINGERPRINTING,
            write = { repository.setAntiFingerprintingEnabled(enabled) },
            success = BrowserSettingsEffect.AntiFingerprintingSaved(enabled),
        )
    }

    fun setClearOnExit(enabled: Boolean) {
        mutate(
            field = SettingsField.CLEAR_ON_EXIT,
            write = { repository.setClearOnExit(enabled) },
            success = BrowserSettingsEffect.ClearOnExitSaved(enabled),
        )
    }

    fun setHomepage(homepage: String?) {
        mutate(
            field = SettingsField.HOMEPAGE,
            write = { repository.setHomepage(homepage) },
            success = BrowserSettingsEffect.HomepageSaved(homepage),
        )
    }

    private fun mutate(
        field: SettingsField,
        write: suspend () -> Unit,
        success: BrowserSettingsEffect,
    ) {
        if (mutableState.value.pendingField != null) return
        mutableState.update { it.copy(pendingField = field) }
        viewModelScope.launch {
            try {
                write()
                effectChannel.send(success)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                effectChannel.send(BrowserSettingsEffect.SaveFailed)
            } finally {
                mutableState.update { it.copy(pendingField = null) }
            }
        }
    }

    class Factory(
        private val repository: BrowserSettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(BrowserSettingsViewModel::class.java))
            return BrowserSettingsViewModel(repository) as T
        }
    }
}

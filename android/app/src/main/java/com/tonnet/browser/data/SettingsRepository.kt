package com.tonnet.browser.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tonnet.browser.core.NetworkMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.tonnetSettings by preferencesDataStore(name = "tonnet_settings_v2")

data class BrowserSettings(
    val networkMode: NetworkMode = NetworkMode.DIRECT,
    val javaScriptEnabled: Boolean = true,
    val antiFingerprintingEnabled: Boolean = true,
    val clearOnExit: Boolean = false,
    val homepage: String? = null,
)

interface BrowserSettingsRepository {
    val settings: Flow<BrowserSettings>
    suspend fun setNetworkMode(mode: NetworkMode)
    suspend fun setJavaScriptEnabled(enabled: Boolean)
    suspend fun setAntiFingerprintingEnabled(enabled: Boolean)
    suspend fun setClearOnExit(enabled: Boolean)
    suspend fun setHomepage(homepage: String?)
}

class SettingsRepository(context: Context) : BrowserSettingsRepository {
    private val store = context.applicationContext.tonnetSettings

    override val settings: Flow<BrowserSettings> = store.data.map { preferences ->
        BrowserSettings(
            networkMode = NetworkMode.fromWire(
                preferences[NETWORK_MODE] ?: NetworkMode.DIRECT.wireValue,
            ),
            javaScriptEnabled = preferences[JAVASCRIPT_ENABLED] ?: true,
            antiFingerprintingEnabled = preferences[ANTI_FINGERPRINTING_ENABLED] ?: true,
            clearOnExit = preferences[CLEAR_ON_EXIT] ?: false,
            homepage = preferences[HOMEPAGE],
        )
    }.distinctUntilChanged()

    override suspend fun setNetworkMode(mode: NetworkMode) {
        store.edit { it[NETWORK_MODE] = mode.wireValue }
    }

    override suspend fun setJavaScriptEnabled(enabled: Boolean) {
        store.edit { it[JAVASCRIPT_ENABLED] = enabled }
    }

    override suspend fun setAntiFingerprintingEnabled(enabled: Boolean) {
        store.edit { it[ANTI_FINGERPRINTING_ENABLED] = enabled }
    }

    override suspend fun setClearOnExit(enabled: Boolean) {
        store.edit { it[CLEAR_ON_EXIT] = enabled }
    }

    override suspend fun setHomepage(homepage: String?) {
        store.edit { preferences ->
            if (homepage == null) {
                preferences.remove(HOMEPAGE)
            } else {
                preferences[HOMEPAGE] = homepage
            }
        }
    }

    private companion object {
        val NETWORK_MODE = intPreferencesKey("network_mode")
        val JAVASCRIPT_ENABLED = booleanPreferencesKey("javascript_enabled")
        val ANTI_FINGERPRINTING_ENABLED = booleanPreferencesKey("anti_fingerprinting_enabled")
        val CLEAR_ON_EXIT = booleanPreferencesKey("clear_on_exit")
        val HOMEPAGE = stringPreferencesKey("homepage")
    }
}

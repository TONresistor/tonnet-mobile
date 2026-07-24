package com.tonnet.browser.ui

import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.tonnet.browser.BuildConfig
import com.tonnet.browser.R
import com.tonnet.browser.data.SettingsField
import com.tonnet.browser.databinding.ActivityMainBinding

internal class SettingsPanelController(
    private val binding: ActivityMainBinding,
    private val callbacks: Callbacks,
) {
    private val content = binding.settingsPanelContent

    val blurTarget
        get() = binding.settingsPanel

    fun configure() {
        content.tunnelSettingRow.setOnClickListener { content.tunnelSwitch.performClick() }
        content.homepageSettingRow.setOnClickListener { callbacks.onHomepageRequested() }
        content.javascriptSettingRow.setOnClickListener {
            content.javascriptSwitch.performClick()
        }
        content.antiFingerprintingSettingRow.setOnClickListener {
            content.antiFingerprintingSwitch.performClick()
        }
        content.clearOnExitSettingRow.setOnClickListener {
            content.clearOnExitSwitch.performClick()
        }
        content.languageSettingRow.setOnClickListener { callbacks.onLanguageRequested() }
        content.clearDataSettingRow.setOnClickListener { callbacks.onClearDataRequested() }
        content.aboutSettingRow.setOnClickListener { callbacks.onAboutRequested() }
        content.tunnelSwitch.setOnCheckedChangeListener(callbacks::onTunnelChanged)
        content.javascriptSwitch.setOnCheckedChangeListener(callbacks::onJavaScriptChanged)
        content.antiFingerprintingSwitch.setOnCheckedChangeListener(
            callbacks::onAntiFingerprintingChanged,
        )
        content.clearOnExitSwitch.setOnCheckedChangeListener(callbacks::onClearOnExitChanged)
    }

    fun applyInsets(topInset: Int, bottomInset: Int, appBarHeight: Int, bottomChromeHeight: Int) {
        content.settingsAppBar.updateLayoutParams<FrameLayout.LayoutParams> {
            topMargin = topInset
        }
        content.settingsScroll.updateLayoutParams<FrameLayout.LayoutParams> {
            topMargin = topInset + appBarHeight
        }
        content.settingsScroll.setPadding(0, 0, 0, bottomInset + bottomChromeHeight)
    }

    fun show() {
        binding.settingsPanel.visibility = View.VISIBLE
        binding.settingsPanel.requestFocus()
    }

    fun hide() {
        binding.settingsPanel.visibility = View.GONE
    }

    fun setVisible(visible: Boolean) {
        binding.settingsPanel.isVisible = visible
    }

    fun render(state: SettingsPanelState) {
        content.tunnelSwitch.setCheckedSilently(state.tunnelEnabled)
        content.javascriptSwitch.setCheckedSilently(state.javaScriptEnabled)
        content.antiFingerprintingSwitch.setCheckedSilently(state.antiFingerprintingEnabled)
        content.clearOnExitSwitch.setCheckedSilently(state.clearOnExit)
        content.javascriptSettingDescription.setText(
            if (state.javaScriptEnabled) {
                R.string.javascript_enabled
            } else {
                R.string.javascript_disabled
            },
        )
        content.tunnelSwitch.isEnabled = !state.networkRestarting
        content.tunnelSettingRow.isEnabled = !state.networkRestarting

        val writeInProgress = state.pendingField != null
        content.javascriptSwitch.isEnabled = !writeInProgress
        content.javascriptSettingRow.isEnabled = !writeInProgress
        val antiFingerprintingAvailable = !writeInProgress && state.javaScriptEnabled
        content.antiFingerprintingSwitch.isEnabled = antiFingerprintingAvailable
        content.antiFingerprintingSettingRow.isEnabled = antiFingerprintingAvailable
        content.antiFingerprintingSettingDescription.setText(
            when {
                !state.javaScriptEnabled -> R.string.anti_fingerprinting_javascript_disabled
                state.antiFingerprintingEnabled -> R.string.anti_fingerprinting_enabled
                else -> R.string.anti_fingerprinting_disabled
            },
        )
        content.homepageSettingRow.isEnabled = !writeInProgress
        content.clearOnExitSwitch.isEnabled = !writeInProgress
        content.clearOnExitSettingRow.isEnabled = !writeInProgress
        content.homepageSettingValue.text = state.homepageDisplay
        content.languageSettingValue.setText(state.languageNameRes)
        content.aboutSettingValue.text = binding.root.context.getString(
            R.string.about_summary,
            binding.root.context.getString(R.string.app_name),
            BuildConfig.VERSION_NAME,
        )
        content.tunnelSettingDescription.setText(
            when {
                state.networkRestarting -> R.string.network_connecting
                state.tunnelEnabled -> R.string.tunnel_explanation
                else -> R.string.direct_explanation
            },
        )
    }

    fun applyLocalizedText() {
        content.settingsTitle.setText(R.string.settings)
        content.tunnelSettingTitle.setText(R.string.tunnel)
        content.tunnelSwitch.contentDescription = binding.root.context.getString(R.string.tunnel)
        content.homepageSettingTitle.setText(R.string.homepage)
        content.javascriptSettingTitle.setText(R.string.javascript)
        content.javascriptSwitch.contentDescription =
            binding.root.context.getString(R.string.javascript)
        content.antiFingerprintingSettingTitle.setText(R.string.anti_fingerprinting)
        content.antiFingerprintingSwitch.contentDescription =
            binding.root.context.getString(R.string.anti_fingerprinting)
        content.clearOnExitSettingTitle.setText(R.string.clear_on_exit)
        content.clearOnExitSettingDescription.setText(R.string.clear_on_exit_description)
        content.clearOnExitSwitch.contentDescription =
            binding.root.context.getString(R.string.clear_on_exit)
        content.languageSettingTitle.setText(R.string.language)
        content.clearDataSettingTitle.setText(R.string.clear_data)
        content.aboutSettingTitle.setText(R.string.about)
    }

    interface Callbacks {
        fun onTunnelChanged(enabled: Boolean)
        fun onHomepageRequested()
        fun onJavaScriptChanged(enabled: Boolean)
        fun onAntiFingerprintingChanged(enabled: Boolean)
        fun onClearOnExitChanged(enabled: Boolean)
        fun onLanguageRequested()
        fun onClearDataRequested()
        fun onAboutRequested()
    }
}

internal data class SettingsPanelState(
    val tunnelEnabled: Boolean,
    val javaScriptEnabled: Boolean,
    val antiFingerprintingEnabled: Boolean,
    val clearOnExit: Boolean,
    val networkRestarting: Boolean,
    val pendingField: SettingsField?,
    val homepageDisplay: String,
    @param:androidx.annotation.StringRes val languageNameRes: Int,
)

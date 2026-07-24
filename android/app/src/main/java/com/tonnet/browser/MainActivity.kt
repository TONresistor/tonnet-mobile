package com.tonnet.browser

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tonnet.browser.core.NetworkMode
import com.tonnet.browser.core.TonUrlPolicy
import com.tonnet.browser.data.Bookmark
import com.tonnet.browser.data.BookmarkRepository
import com.tonnet.browser.data.BrowserSettingsEffect
import com.tonnet.browser.data.BrowserSettingsViewModel
import com.tonnet.browser.data.EncryptedBookmarkStore
import com.tonnet.browser.data.LegacyDataMigrator
import com.tonnet.browser.data.SettingsField
import com.tonnet.browser.data.SettingsRepository
import com.tonnet.browser.databinding.ActivityMainBinding
import com.tonnet.browser.localization.AppLanguage
import com.tonnet.browser.ui.AddressPresentation
import com.tonnet.browser.ui.BookmarksBottomSheet
import com.tonnet.browser.ui.BrowserChromeState
import com.tonnet.browser.ui.BrowserChromePolicy
import com.tonnet.browser.ui.BrowserOverlay
import com.tonnet.browser.ui.BrowserSheetHost
import com.tonnet.browser.ui.BrowserSheetItem
import com.tonnet.browser.ui.BrowserSwipeRefreshLayout
import com.tonnet.browser.ui.LanguageBottomSheet
import com.tonnet.browser.ui.SettingsPanelController
import com.tonnet.browser.ui.SettingsPanelState
import com.tonnet.browser.ui.StartupNavigation
import com.tonnet.browser.ui.TabsBottomSheet
import com.tonnet.browser.ui.TelegramDialogs
import com.tonnet.browser.ui.TabBarBlurPolicy
import com.tonnet.browser.web.TonWebViewEvents
import com.tonnet.browser.web.TonWebViewFactory
import com.tonnet.browser.web.TonPageError
import com.tonnet.browser.web.BrowserNavigationController
import com.tonnet.browser.web.BrowserTabController
import com.tonnet.browser.web.BrowserTabController.TabSession
import com.tonnet.browser.web.CloseTabResult
import com.tonnet.browser.web.CreateTabResult
import com.tonnet.browser.web.LoadResult
import com.tonnet.browser.web.PrivateNetworkController
import com.tonnet.browser.web.PrivateNetworkState
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView
import kotlinx.coroutines.launch

class MainActivity :
    AppCompatActivity(),
    TonWebViewEvents,
    BrowserSheetHost,
    PrivateNetworkController.Listener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var root: FrameLayout
    private lateinit var browserSurface: BlurTarget
    private lateinit var browserRefresh: BrowserSwipeRefreshLayout
    private lateinit var webContainer: FrameLayout
    private lateinit var startPanel: View
    private lateinit var landingSearchContainer: View
    private lateinit var landingSearchInput: EditText
    private lateinit var landingSearchButton: TextView
    private lateinit var exploreSubtitle: TextView
    private lateinit var errorPanel: LinearLayout
    private lateinit var errorTitle: TextView
    private lateinit var errorMessage: TextView
    private lateinit var retryButton: View
    private lateinit var addressTonIcon: ImageView
    private lateinit var addressTonSiteBadge: TextView
    private lateinit var addressBar: EditText
    private lateinit var addressClearButton: View
    private lateinit var addressStarButton: ImageButton
    private lateinit var homeButton: ImageButton
    private lateinit var refreshButton: View
    private lateinit var bookmarksButton: View
    private lateinit var backButton: TextView
    private lateinit var forwardButton: TextView
    private lateinit var newTabButton: TextView
    private lateinit var tabsButton: TextView
    private lateinit var settingsButton: TextView
    private lateinit var pageProgress: ProgressBar
    private lateinit var topChrome: View
    private lateinit var bottomChrome: FrameLayout
    private lateinit var tabbarBlur: BlurView
    private lateinit var settingsController: SettingsPanelController

    private lateinit var networkController: PrivateNetworkController
    private lateinit var navigationController: BrowserNavigationController
    private lateinit var bookmarks: BookmarkRepository
    private val settingsViewModel by viewModels<BrowserSettingsViewModel> {
        BrowserSettingsViewModel.Factory(SettingsRepository(applicationContext))
    }

    private var bookmarkSnapshot: List<Bookmark> = emptyList()
    private var networkMode = NetworkMode.DIRECT
    private var javaScriptEnabled = true
    private var antiFingerprintingEnabled = true
    private var clearOnExit = false
    private var homepage: String? = null
    private var destroyed = false
    private var activeOverlay = BrowserOverlay.NONE
    private var imeVisible = false
    private var tabbarBlurTarget: BlurTarget? = null
    private var confirmationDialog: Dialog? = null
    private var currentError: LocalizedError? = null
    private var settingsInitialized = false

    private val proxyReady: Boolean
        get() = ::networkController.isInitialized && networkController.state.ready
    private val networkFailed: Boolean
        get() = ::networkController.isInitialized && networkController.state.failed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        val migrationResult = LegacyDataMigrator.runOnce(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindViews()
        applyInsets()

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        bookmarks = EncryptedBookmarkStore(this)
        networkController = PrivateNetworkController(this, this)
        val tabController = BrowserTabController(
            container = webContainer,
            createWebView = {
                TonWebViewFactory.create(
                    context = this,
                    profileName = networkController.profileName,
                    javaScriptEnabled = javaScriptEnabled,
                    antiFingerprintingEnabled = antiFingerprintingEnabled,
                    fingerprintingSeed = networkController.fingerprintingSeed,
                    events = this,
                    proxyPort = networkController::currentProxyPort,
                )
            },
        )
        navigationController = BrowserNavigationController(tabController) {
            getString(R.string.new_tab)
        }
        configureControls()

        if (migrationResult.isFailure) {
            showBlockingError(
                R.string.private_data_error_title,
                R.string.legacy_data_reset_failed,
            )
            return
        }

        val unsupported = networkController.unsupportedFeatures()
        if (unsupported.isNotEmpty()) {
            showBlockingError(
                R.string.webview_update_title,
                R.string.webview_update_message,
            )
            return
        }

        navigationController.queue(intentAddress(intent))

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    settingsViewModel.state.collect { state ->
                        if (!state.initialized) return@collect
                        networkMode = state.settings.networkMode
                        javaScriptEnabled = state.settings.javaScriptEnabled
                        antiFingerprintingEnabled = state.settings.antiFingerprintingEnabled
                        clearOnExit = state.settings.clearOnExit
                        homepage = state.settings.homepage
                        if (!settingsInitialized) {
                            settingsInitialized = true
                            networkController.prepareSession(
                                clearPersistedData = clearOnExit,
                            ) { result ->
                                if (destroyed) return@prepareSession
                                if (result.isFailure) {
                                    showBlockingError(
                                        R.string.private_session_error_title,
                                        R.string.private_session_start_failed,
                                    )
                                    return@prepareSession
                                }
                                navigationController.queue(
                                    StartupNavigation.firstAddress(
                                        navigationController.state.pendingAddress,
                                        homepage,
                                    ),
                                )
                                networkController.start(networkMode)
                            }
                        }
                        renderSettings()
                    }
                }
                launch {
                    settingsViewModel.effects.collect(::onSettingsEffect)
                }
            }
        }
        refreshBookmarks()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val webView = activeSession()?.webView
                when {
                    activeOverlay == BrowserOverlay.SETTINGS -> hideSettingsPage()
                    errorPanel.isVisible -> hidePageError()
                    webView?.canGoBack() == true -> webView.goBack()
                    !startPanel.isVisible -> showHome()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentAddress(intent)?.let { address ->
            if (proxyReady) navigate(address) else navigationController.queue(address)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        confirmationDialog?.dismiss()
        confirmationDialog = null
        applyLocalizedText()
    }

    override fun onDestroy() {
        destroyed = true
        confirmationDialog?.dismiss()
        confirmationDialog = null
        destroyAllTabs()
        if (::networkController.isInitialized) {
            networkController.close(clearBrowsingData = clearOnExit)
        }
        super.onDestroy()
    }

    private fun bindViews() {
        root = binding.root
        browserSurface = binding.browserSurface
        browserRefresh = binding.browserRefresh
        webContainer = binding.webContainer
        startPanel = binding.startPanel
        landingSearchContainer = binding.landingSearchContainer
        landingSearchInput = binding.landingSearchInput
        landingSearchButton = binding.landingSearchButton
        exploreSubtitle = binding.exploreSubtitle
        errorPanel = binding.errorPanel
        errorTitle = binding.errorTitle
        errorMessage = binding.errorMessage
        retryButton = binding.retryButton
        addressTonIcon = binding.addressTonIcon
        addressTonSiteBadge = binding.addressTonsiteBadge
        addressBar = binding.addressBar
        addressClearButton = binding.addressClearButton
        addressStarButton = binding.addressStarButton
        homeButton = binding.homeButton
        refreshButton = binding.refreshButton
        bookmarksButton = binding.bookmarksButton
        backButton = binding.backButton
        forwardButton = binding.forwardButton
        newTabButton = binding.newTabButton
        tabsButton = binding.tabsButton
        settingsButton = binding.settingsButton
        pageProgress = binding.pageProgress
        topChrome = binding.topChrome
        bottomChrome = binding.bottomChrome
        tabbarBlur = binding.tabbarBlur
        settingsController = SettingsPanelController(
            binding = binding,
            callbacks = object : SettingsPanelController.Callbacks {
                override fun onTunnelChanged(enabled: Boolean) = onTunnelSettingChanged(enabled)
                override fun onHomepageRequested() = showHomepageDialog()
                override fun onJavaScriptChanged(enabled: Boolean) =
                    onJavaScriptSettingChanged(enabled)
                override fun onAntiFingerprintingChanged(enabled: Boolean) =
                    onAntiFingerprintingSettingChanged(enabled)
                override fun onClearOnExitChanged(enabled: Boolean) =
                    onClearOnExitSettingChanged(enabled)
                override fun onLanguageRequested() = showLanguageSheet()
                override fun onClearDataRequested() = confirmClearData()
                override fun onAboutRequested() = showAboutDialog()
            },
        )
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            topChrome.updateLayoutParams<FrameLayout.LayoutParams> {
                topMargin = systemBars.top
            }
            browserSurface.updateLayoutParams<FrameLayout.LayoutParams> {
                topMargin = systemBars.top + dp(TOP_CHROME_HEIGHT_DP)
            }
            bottomChrome.updateLayoutParams<FrameLayout.LayoutParams> {
                bottomMargin = systemBars.bottom
            }
            settingsController.applyInsets(
                topInset = systemBars.top,
                bottomInset = systemBars.bottom,
                appBarHeight = dp(SETTINGS_APP_BAR_HEIGHT_DP),
                bottomChromeHeight = dp(BOTTOM_CHROME_HEIGHT_DP),
            )
            startPanel.setPadding(
                0,
                0,
                0,
                if (imeVisible) ime.bottom else systemBars.bottom + dp(BOTTOM_CHROME_HEIGHT_DP),
            )
            renderChromeVisibility()
            insets
        }
    }

    private fun configureControls() {
        addressBar.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_GO) {
                navigate(addressBar.text.toString())
                true
            } else false
        }
        addressBar.setOnFocusChangeListener { _, focused ->
            if (focused) {
                navigationController.state.address?.let { address ->
                    val display = AddressPresentation.display(address, focused = true)
                    if (addressBar.text.toString() != display) addressBar.setText(display)
                    addressBar.selectAll()
                }
            } else {
                renderAddress()
            }
            updateControls()
        }
        addressBar.doAfterTextChanged { updateControls() }
        addressClearButton.setOnClickListener {
            addressBar.setText("")
            addressBar.requestFocus()
        }
        addressStarButton.setOnClickListener { toggleCurrentBookmark() }
        homeButton.setOnClickListener { openHome() }
        refreshButton.setOnClickListener { reloadActivePage() }
        bookmarksButton.setOnClickListener { showBookmarksSheet() }
        landingSearchInput.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_GO) {
                submitLandingSearch()
                true
            } else {
                false
            }
        }
        landingSearchInput.setOnFocusChangeListener { _, focused ->
            landingSearchContainer.isSelected = focused
        }
        landingSearchButton.setOnClickListener {
            if (networkFailed) retryCurrentFailure() else submitLandingSearch()
        }
        backButton.setOnClickListener { navigateBackFromChrome() }
        forwardButton.setOnClickListener { navigateForwardFromChrome() }
        newTabButton.setOnClickListener { createNewTab(true) }
        tabsButton.setOnClickListener { showTabsSheet() }
        settingsButton.setOnClickListener {
            if (activeOverlay == BrowserOverlay.SETTINGS) {
                hideSettingsPage()
            } else {
                showSettingsPage()
            }
        }
        settingsController.configure()
        retryButton.setOnClickListener { retryCurrentFailure() }
        browserRefresh.setColorSchemeResources(R.color.tonnet_accent)
        browserRefresh.setProgressBackgroundColorSchemeResource(R.color.tonnet_surface_bright)
        browserRefresh.setOnChildScrollUpCallback { _, _ ->
            activeSession()?.webView?.canScrollVertically(-1) == true
        }
        browserRefresh.setOnRefreshListener {
            if (!reloadActivePage()) browserRefresh.isRefreshing = false
        }
        browserRefresh.canGoBack = {
            activeOverlay == BrowserOverlay.NONE &&
                webContainer.isVisible &&
                activeSession()?.webView?.canGoBack() == true
        }
        browserRefresh.canGoForward = {
            activeOverlay == BrowserOverlay.NONE &&
                webContainer.isVisible &&
                activeSession()?.webView?.canGoForward() == true
        }
        browserRefresh.onGoBack = { activeSession()?.webView?.goBack() }
        browserRefresh.onGoForward = { activeSession()?.webView?.goForward() }
        configureTabBarBlur()
        applyLocalizedText()
        renderUi()
    }

    private fun openHome() {
        val configuredHomepage = homepage
        if (configuredHomepage.isNullOrBlank()) {
            showHome()
        } else {
            navigate(configuredHomepage)
        }
    }

    private fun reloadActivePage(): Boolean {
        val webView = activeSession()?.webView
        if (
            !proxyReady ||
            navigationController.state.address.isNullOrBlank() ||
            !webContainer.isVisible ||
            activeOverlay != BrowserOverlay.NONE ||
            webView == null
        ) {
            return false
        }
        hidePageError()
        webView.reload()
        return true
    }

    private fun navigateBackFromChrome() {
        if (activeOverlay == BrowserOverlay.SETTINGS) {
            hideSettingsPage()
            return
        }
        val webView = activeSession()?.webView
        when {
            webView?.canGoBack() == true -> webView.goBack()
            !startPanel.isVisible -> showHome()
        }
    }

    private fun navigateForwardFromChrome() {
        if (activeOverlay == BrowserOverlay.SETTINGS) {
            hideSettingsPage()
            return
        }
        activeSession()?.webView?.takeIf(WebView::canGoForward)?.goForward()
    }

    private fun navigate(input: String): Boolean {
        if (!proxyReady) {
            navigationController.queue(input)
            Toast.makeText(this, R.string.network_connecting, Toast.LENGTH_SHORT).show()
            return false
        }
        val result = navigationController.load(input)
        when (result) {
            LoadResult.InvalidAddress -> {
                Toast.makeText(this, R.string.invalid_address, Toast.LENGTH_SHORT).show()
                return false
            }
            LoadResult.LimitReached -> {
                Toast.makeText(this, R.string.maximum_tabs, Toast.LENGTH_SHORT).show()
                return false
            }
            is LoadResult.Loaded -> Unit
        }
        hideKeyboard()
        hidePageError()
        startPanel.visibility = View.GONE
        webContainer.visibility = View.VISIBLE
        refreshAddress()
        updateControls()
        return true
    }

    private fun submitLandingSearch() {
        if (!proxyReady) {
            Toast.makeText(this, R.string.network_connecting, Toast.LENGTH_SHORT).show()
            return
        }
        if (navigate(landingSearchInput.text.toString())) {
            landingSearchInput.setText("")
        }
    }

    private fun ensureActiveTab() {
        val existing = navigationController.all().firstOrNull()
        if (existing == null) {
            createNewTab(true)
        } else {
            switchToTab(navigationController.state.activeTabId ?: existing.model.id)
        }
    }

    private fun createNewTab(activate: Boolean): TabSession? {
        return when (val result = navigationController.create(activate = false)) {
            CreateTabResult.LimitReached -> {
                Toast.makeText(this, R.string.maximum_tabs, Toast.LENGTH_SHORT).show()
                null
            }
            is CreateTabResult.Created -> {
                if (activate) switchToTab(result.session.model.id)
                updateControls()
                result.session
            }
        }
    }

    private fun switchToTab(id: Long) {
        if (!proxyReady) return
        hideKeyboard()
        val next = navigationController.activate(id) ?: return
        presentActiveTab(next)
    }

    private fun presentActiveTab(next: TabSession) {
        val webView = requireNotNull(next.webView)

        val url = next.model.currentUrl
        startPanel.visibility = if (url == null) View.VISIBLE else View.GONE
        webContainer.visibility = if (url == null) View.GONE else View.VISIBLE
        if (url == null) {
            landingSearchInput.setText("")
            landingSearchInput.clearFocus()
        }
        refreshAddress()
        if (url != null && webView.url == null && next.savedState == null) webView.loadUrl(url)
        updateControls()
    }

    private fun closeTab(id: Long): Boolean {
        return when (val result = navigationController.close(id)) {
            CloseTabResult.NotFound -> false
            is CloseTabResult.Closed -> {
                result.activeSession?.let(::presentActiveTab)
                updateControls()
                true
            }
        }
    }

    private fun destroyAllTabs() {
        if (::browserRefresh.isInitialized) browserRefresh.isRefreshing = false
        if (::navigationController.isInitialized) navigationController.destroyAll()
    }

    private fun showHome() {
        hideKeyboard()
        hidePageError()
        browserRefresh.isRefreshing = false
        navigationController.resetToHome()
        startPanel.visibility = View.VISIBLE
        webContainer.visibility = View.GONE
        landingSearchInput.setText("")
        refreshAddress()
        updateControls()
    }

    private fun updateControls() {
        val active = activeSession()
        val navigation = navigationController.state
        val state = BrowserChromeState(
            address = navigation.address,
            addressHasText = addressBar.text?.isNotBlank() == true,
            addressFocused = addressBar.hasFocus(),
            isBookmarked = navigation.isBookmarked,
            canGoBack = active?.webView?.canGoBack() == true,
            canGoForward = active?.webView?.canGoForward() == true,
            proxyReady = proxyReady,
            tabCount = navigationController.size.coerceAtLeast(1),
            overlay = activeOverlay,
        )
        backButton.isEnabled =
            state.overlay == BrowserOverlay.SETTINGS || state.canGoBack || state.hasPage
        forwardButton.isEnabled = state.canGoForward
        homeButton.isEnabled = proxyReady
        homeButton.setImageResource(
            if (startPanel.isVisible) {
                R.drawable.telegram_settings_icon_homepage
            } else {
                R.drawable.telegram_icon_home_outline
            },
        )
        refreshButton.isEnabled = state.canReload
        bookmarksButton.isEnabled = ::bookmarks.isInitialized
        newTabButton.isEnabled = proxyReady
        tabsButton.isEnabled = proxyReady
        addressClearButton.isVisible = state.showClearAddress
        addressStarButton.isVisible = state.showBookmarkToggle
        addressStarButton.setImageResource(
            if (state.isBookmarked) {
                R.drawable.telegram_icon_star_filled
            } else {
                R.drawable.telegram_icon_star_outline
            },
        )
        addressStarButton.contentDescription = getString(
            if (state.isBookmarked) {
                R.string.action_remove_bookmark
            } else {
                R.string.action_add_bookmark
            },
        )
        updateRefreshState()
        tabsButton.text = getString(R.string.tab_count_label, state.tabCount)
        tabsButton.contentDescription = getString(R.string.tab_count_label, state.tabCount)
        tabsButton.isSelected = state.overlay == BrowserOverlay.TABS
        settingsButton.isSelected = state.overlay == BrowserOverlay.SETTINGS
        renderAddress()
        renderLandingNetworkState()
        renderSettings()
        renderChromeVisibility()
    }

    private fun updateRefreshState() {
        val enabled =
            proxyReady &&
                !navigationController.state.address.isNullOrBlank() &&
                webContainer.isVisible &&
                activeOverlay == BrowserOverlay.NONE &&
                !errorPanel.isVisible &&
                !networkFailed
        browserRefresh.isEnabled = enabled
        if (!enabled) browserRefresh.isRefreshing = false
    }

    private fun toggleCurrentBookmark() {
        val active = activeSession() ?: return
        val url = active.model.currentUrl ?: return
        addressStarButton.isEnabled = false
        lifecycleScope.launch {
            bookmarks.toggle(url, active.model.title).fold(
                onSuccess = { added ->
                    navigationController.setBookmarked(added)
                    Toast.makeText(
                        this@MainActivity,
                        if (added) R.string.bookmark_added else R.string.bookmark_removed,
                        Toast.LENGTH_SHORT,
                    ).show()
                    refreshBookmarks()
                },
                onFailure = {
                    Toast.makeText(
                        this@MainActivity,
                        R.string.bookmarks_unavailable,
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
            addressStarButton.isEnabled = true
            updateControls()
        }
    }

    private fun removeBookmark(url: String, onComplete: (Boolean) -> Unit) {
        lifecycleScope.launch {
            bookmarks.remove(url).fold(
                onSuccess = { removed ->
                    if (removed) {
                        bookmarkSnapshot = bookmarkSnapshot.filterNot { it.url == url }
                        if (navigationController.state.address == url) {
                            navigationController.setBookmarked(false)
                        }
                        updateControls()
                    }
                    onComplete(removed)
                },
                onFailure = {
                    Toast.makeText(
                        this@MainActivity,
                        R.string.bookmarks_unavailable,
                        Toast.LENGTH_SHORT,
                    ).show()
                    onComplete(false)
                },
            )
        }
    }

    private fun refreshCurrentBookmarkState() {
        val bookmarked = navigationController.state.address
            ?.let { address -> bookmarkSnapshot.any { it.url == address } } == true
        navigationController.setBookmarked(bookmarked)
    }

    private fun refreshBookmarks(after: (() -> Unit)? = null) {
        if (!::bookmarks.isInitialized) return
        lifecycleScope.launch {
            bookmarks.all().fold(
                onSuccess = { loaded ->
                    bookmarkSnapshot = loaded
                    refreshCurrentBookmarkState()
                    updateControls()
                    after?.invoke()
                },
                onFailure = {
                    Toast.makeText(
                        this@MainActivity,
                        R.string.bookmarks_unavailable,
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }

    private fun showTabsSheet() {
        if (supportFragmentManager.findFragmentByTag(TABS_SHEET_TAG) != null) return
        activeOverlay = BrowserOverlay.TABS
        updateControls()
        TabsBottomSheet().show(supportFragmentManager, TABS_SHEET_TAG)
    }

    private fun showBookmarksSheet() {
        if (supportFragmentManager.findFragmentByTag(BOOKMARKS_SHEET_TAG) != null) return
        refreshBookmarks {
            if (
                !isFinishing &&
                !isDestroyed &&
                supportFragmentManager.findFragmentByTag(BOOKMARKS_SHEET_TAG) == null
            ) {
                BookmarksBottomSheet().show(supportFragmentManager, BOOKMARKS_SHEET_TAG)
            }
        }
    }

    private fun showLanguageSheet() {
        if (supportFragmentManager.findFragmentByTag(LANGUAGE_SHEET_TAG) != null) return
        LanguageBottomSheet().show(supportFragmentManager, LANGUAGE_SHEET_TAG)
    }

    private fun showSettingsPage() {
        hideKeyboard()
        activeOverlay = BrowserOverlay.SETTINGS
        settingsController.show()
        updateControls()
    }

    private fun hideSettingsPage() {
        activeOverlay = BrowserOverlay.NONE
        settingsController.hide()
        updateControls()
    }

    private fun showHomepageDialog() {
        confirmationDialog?.dismiss()
        confirmationDialog = TelegramDialogs.showTextInput(
            context = this,
            title = getString(R.string.homepage),
            message = getString(R.string.homepage_dialog_message),
            hint = getString(R.string.homepage_hint),
            initialValue = homepage.orEmpty(),
            primaryLabel = getString(R.string.action_save),
            secondaryLabel = getString(android.R.string.cancel),
            onConfirmed = ::saveHomepage,
            onDismissed = { confirmationDialog = null },
        )
    }

    private fun saveHomepage(input: String): Boolean {
        val normalized = if (input.isBlank()) {
            null
        } else {
            TonUrlPolicy.normalize(input).getOrElse {
                Toast.makeText(this, R.string.invalid_address, Toast.LENGTH_SHORT).show()
                return false
            }.value
        }
        settingsViewModel.setHomepage(normalized)
        return true
    }

    private fun showAboutDialog() {
        confirmationDialog?.dismiss()
        confirmationDialog = TelegramDialogs.showMessage(
            context = this,
            title = getString(R.string.about),
            message = getString(
                R.string.about_message,
                getString(R.string.app_name),
                BuildConfig.VERSION_NAME,
                getString(R.string.brand_description),
            ),
            buttonLabel = getString(android.R.string.ok),
            onDismissed = { confirmationDialog = null },
        )
    }

    private fun onTunnelSettingChanged(enabled: Boolean) {
        val mode = if (enabled) NetworkMode.TUNNEL_2_HOP else NetworkMode.DIRECT
        if (
            mode == networkMode ||
            networkController.state.resetting ||
            settingsViewModel.state.value.pendingField != null
        ) {
            renderSettings()
            return
        }
        settingsViewModel.setNetworkMode(mode)
    }

    private fun onJavaScriptSettingChanged(enabled: Boolean) {
        if (
            enabled == javaScriptEnabled ||
            settingsViewModel.state.value.pendingField != null
        ) {
            renderSettings()
            return
        }
        settingsViewModel.setJavaScriptEnabled(enabled)
    }

    private fun applyJavaScriptSetting(enabled: Boolean) {
        javaScriptEnabled = enabled
        recreateTabsForWebSettingsChange()
    }

    private fun onAntiFingerprintingSettingChanged(enabled: Boolean) {
        if (
            enabled == antiFingerprintingEnabled ||
            settingsViewModel.state.value.pendingField != null
        ) {
            renderSettings()
            return
        }
        settingsViewModel.setAntiFingerprintingEnabled(enabled)
    }

    private fun applyAntiFingerprintingSetting(enabled: Boolean) {
        antiFingerprintingEnabled = enabled
        recreateTabsForWebSettingsChange()
    }

    private fun onClearOnExitSettingChanged(enabled: Boolean) {
        if (
            enabled == clearOnExit ||
            settingsViewModel.state.value.pendingField != null
        ) {
            renderSettings()
            return
        }
        settingsViewModel.setClearOnExit(enabled)
    }

    private fun recreateTabsForWebSettingsChange() {
        navigationController.recreateWebViews()

        val activeId = navigationController.state.activeTabId
        if (proxyReady && activeId != null) {
            switchToTab(activeId)
        } else {
            updateControls()
        }
    }

    private fun onSettingsEffect(effect: BrowserSettingsEffect) {
        when (effect) {
            is BrowserSettingsEffect.NetworkModeSaved -> {
                networkMode = effect.mode
                restartNetwork(effect.mode)
            }
            is BrowserSettingsEffect.JavaScriptSaved -> {
                applyJavaScriptSetting(effect.enabled)
            }
            is BrowserSettingsEffect.AntiFingerprintingSaved -> {
                applyAntiFingerprintingSetting(effect.enabled)
            }
            is BrowserSettingsEffect.ClearOnExitSaved -> {
                clearOnExit = effect.enabled
                renderSettings()
            }
            is BrowserSettingsEffect.HomepageSaved -> {
                homepage = effect.homepage
                renderSettings()
            }
            BrowserSettingsEffect.SaveFailed -> {
                renderSettings()
                Toast.makeText(this, R.string.settings_save_failed, Toast.LENGTH_SHORT).show()
            }
            BrowserSettingsEffect.LoadFailed -> {
                showBlockingError(
                    R.string.private_data_error_title,
                    R.string.private_data_reset_failed,
                )
            }
        }
    }

    override fun tabSheetItems(): List<BrowserSheetItem> = navigationController.all().map { tab ->
        BrowserSheetItem(
            key = tab.model.id.toString(),
            title = tab.model.title.take(120),
            supportingText = tab.model.currentUrl?.let { AddressPresentation.display(it, focused = false) },
            active = tab.model.id == navigationController.state.activeTabId,
        )
    }

    override fun bookmarkSheetItems(): List<BrowserSheetItem> =
        bookmarkSnapshot.map { bookmark ->
            BrowserSheetItem(
                key = bookmark.url,
                title = bookmark.title,
                supportingText = AddressPresentation.display(bookmark.url, focused = false),
            )
        }

    override fun languageSheetItems(): List<BrowserSheetItem> {
        val currentLanguage = AppLanguage.fromLanguageTag(
            resources.configuration.locales[0]?.toLanguageTag(),
        )
        return AppLanguage.entries.map { language ->
            BrowserSheetItem(
                key = language.languageTag,
                title = getString(language.nativeNameRes),
                supportingText = null,
                active = language == currentLanguage,
            )
        }
    }

    override fun onTabSelected(key: String) {
        key.toLongOrNull()?.let(::switchToTab)
    }

    override fun onBookmarkSelected(key: String) {
        navigate(key)
    }

    override fun onLanguageSelected(key: String) {
        val language = AppLanguage.findSupported(key) ?: return
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() == language.languageTag) return
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(language.languageTag),
        )
    }

    override fun onNewTabRequested() {
        createNewTab(true)
    }

    override fun onTabCloseRequested(key: String): Boolean =
        key.toLongOrNull()?.let(::closeTab) == true

    override fun onBookmarkRemoveRequested(key: String, onComplete: (Boolean) -> Unit) {
        removeBookmark(key, onComplete)
    }

    override fun onBrowserSheetDismissed() {
        if (activeOverlay == BrowserOverlay.TABS) {
            activeOverlay = BrowserOverlay.NONE
            updateControls()
        }
    }

    private fun restartNetwork(mode: NetworkMode) {
        networkController.switchTo(mode)
    }

    private fun confirmClearData() {
        confirmationDialog?.dismiss()
        confirmationDialog = TelegramDialogs.showConfirmation(
            context = this,
            title = getString(R.string.clear_data),
            message = getString(R.string.clear_data_confirmation),
            primaryLabel = getString(R.string.clear_data),
            secondaryLabel = getString(android.R.string.cancel),
            onConfirmed = ::clearBrowsingData,
            onDismissed = { confirmationDialog = null },
        )
    }

    private fun clearBrowsingData() {
        networkController.clearBrowsingData()
    }

    private fun retryCurrentFailure() {
        hidePageError()
        networkController.retry(networkMode)
    }

    private fun configureTabBarBlur() {
        if (!TabBarBlurPolicy.isSupported(Build.VERSION.SDK_INT)) return
        val target = if (activeOverlay == BrowserOverlay.SETTINGS) {
            settingsController.blurTarget
        } else {
            browserSurface
        }
        if (tabbarBlurTarget === target) return
        tabbarBlurTarget = target
        runCatching {
            tabbarBlur.clipToOutline = true
            tabbarBlur
                .setupWith(target, TabBarBlurPolicy.SCALE_FACTOR, true)
                .setBlurRadius(TabBarBlurPolicy.RADIUS_PX)
        }.onFailure {
            tabbarBlur.setBackgroundResource(R.drawable.tonnet_tabbar_background_opaque)
        }
    }

    private fun renderUi() {
        updateControls()
    }

    private fun renderChromeVisibility() {
        val visibility = BrowserChromePolicy.visibility(activeOverlay, imeVisible)
        settingsController.setVisible(visibility.showSettings)
        topChrome.isVisible = visibility.showTop
        bottomChrome.isVisible = visibility.showBottom
        configureTabBarBlur()
    }

    private fun renderAddress() {
        val currentAddress = navigationController.state.address
        val focused = addressBar.hasFocus()
        val showBadge = AddressPresentation.shouldShowTonSiteBadge(currentAddress, focused)
        addressTonSiteBadge.isVisible = showBadge
        addressTonIcon.isVisible = !showBadge
        if (focused) return
        val display = AddressPresentation.display(currentAddress, focused)
        if (addressBar.text.toString() != display) {
            addressBar.setText(display)
        }
    }

    private fun refreshAddress() {
        refreshCurrentBookmarkState()
        if (!addressBar.hasFocus()) renderAddress()
    }

    private fun renderSettings() {
        val pendingField = settingsViewModel.state.value.pendingField
        val state = SettingsPanelState(
            tunnelEnabled = networkMode == NetworkMode.TUNNEL_2_HOP,
            javaScriptEnabled = javaScriptEnabled,
            antiFingerprintingEnabled = antiFingerprintingEnabled,
            clearOnExit = clearOnExit,
            networkRestarting =
                networkController.state.resetting ||
                    (!proxyReady && !networkFailed) ||
                    pendingField == SettingsField.NETWORK_MODE,
            pendingField = pendingField,
            homepageDisplay = homepage?.let {
                AddressPresentation.display(it, focused = false)
            } ?: getString(R.string.homepage_default),
            languageNameRes = AppLanguage.fromLanguageTag(
                resources.configuration.locales[0]?.toLanguageTag(),
            ).nativeNameRes,
        )
        settingsController.render(state)
    }

    private fun renderLandingNetworkState() {
        landingSearchButton.isEnabled = proxyReady || networkFailed
        landingSearchButton.alpha = if (landingSearchButton.isEnabled) 1f else 0.65f
        landingSearchButton.setText(
            when {
                proxyReady -> R.string.action_go
                networkFailed -> R.string.action_retry
                else -> R.string.landing_connecting
            },
        )
    }

    private fun applyLocalizedText() {
        exploreSubtitle.setText(R.string.brand_description)
        landingSearchInput.setHint(R.string.landing_search_placeholder)
        addressBar.setHint(R.string.address_hint)
        retryButton.contentDescription = getString(R.string.action_retry)
        (retryButton as? TextView)?.setText(R.string.action_retry)

        backButton.setText(R.string.action_back)
        backButton.contentDescription = getString(R.string.action_back)
        forwardButton.setText(R.string.action_forward)
        forwardButton.contentDescription = getString(R.string.action_forward)
        newTabButton.setText(R.string.new_tab_short)
        newTabButton.contentDescription = getString(R.string.new_tab)
        homeButton.contentDescription = getString(R.string.action_home)
        refreshButton.contentDescription = getString(R.string.action_refresh)
        bookmarksButton.contentDescription = getString(R.string.bookmarks)
        addressClearButton.contentDescription = getString(R.string.action_clear_address)
        settingsButton.setText(R.string.settings)
        settingsButton.contentDescription = getString(R.string.settings)
        settingsController.applyLocalizedText()

        navigationController.all().filter { it.model.currentUrl == null }.forEach { tab ->
            tab.model.title = getString(R.string.new_tab)
        }
        currentError?.let(::renderError)
        updateControls()
    }

    private fun showPageError(
        @StringRes titleRes: Int,
        @StringRes messageRes: Int,
        vararg messageArguments: Any,
    ) {
        currentError = LocalizedError(titleRes, messageRes, messageArguments.toList())
        renderError(requireNotNull(currentError))
        retryButton.visibility = View.VISIBLE
        errorPanel.visibility = View.VISIBLE
        updateRefreshState()
    }

    private fun renderError(error: LocalizedError) {
        errorTitle.setText(error.titleRes)
        errorMessage.text = getString(error.messageRes, *error.messageArguments.toTypedArray()).take(300)
    }

    private fun hidePageError() {
        currentError = null
        errorPanel.visibility = View.GONE
        updateRefreshState()
    }

    override fun currentMode(): NetworkMode = networkMode

    override fun onNetworkStateChanged(state: PrivateNetworkState) {
        if (destroyed) return
        renderLandingNetworkState()
        renderSettings()
    }

    override fun onNetworkResetStarted() {
        browserRefresh.isRefreshing = false
        destroyAllTabs()
        startPanel.visibility = View.VISIBLE
        webContainer.visibility = View.GONE
    }

    override fun onBrowsingDataResetStarted() {
        browserRefresh.isRefreshing = false
        destroyAllTabs()
    }

    override fun onNetworkReady() {
        hidePageError()
        ensureActiveTab()
        navigationController.takeQueued()?.let(::navigate)
    }

    override fun onReadyRetryRequested() {
        activeSession()?.webView?.reload()
    }

    override fun onNetworkUnavailable() {
        browserRefresh.isRefreshing = false
        activeSession()?.model?.currentUrl?.let { currentUrl ->
            if (navigationController.state.pendingAddress == null) {
                navigationController.queue(currentUrl)
            }
        }
        destroyAllTabs()
        startPanel.visibility = View.VISIBLE
        webContainer.visibility = View.GONE
        showPageError(
            R.string.network_unavailable,
            R.string.network_unavailable_message,
        )
    }

    override fun onPrivateDataFailure() {
        browserRefresh.isRefreshing = false
        destroyAllTabs()
        showBlockingError(
            R.string.private_data_error_title,
            R.string.private_data_reset_failed,
        )
    }

    override fun onBrowsingDataCleared() {
        ensureActiveTab()
        Toast.makeText(this, R.string.clear_data_done, Toast.LENGTH_SHORT).show()
    }

    private fun showBlockingError(
        @StringRes titleRes: Int,
        @StringRes messageRes: Int,
        vararg messageArguments: Any,
    ) {
        showPageError(titleRes, messageRes, *messageArguments)
        retryButton.visibility = View.GONE
        addressBar.isEnabled = false
        backButton.isEnabled = false
        forwardButton.isEnabled = false
        homeButton.isEnabled = false
        refreshButton.isEnabled = false
        bookmarksButton.isEnabled = false
        newTabButton.isEnabled = false
    }

    override fun onPageStarted(view: WebView, url: String?) {
        val session = navigationController.onPageStarted(view, url) ?: return
        if (session.model.id == navigationController.state.activeTabId) {
            refreshAddress()
            hidePageError()
            startPanel.visibility = View.GONE
            webContainer.visibility = View.VISIBLE
            pageProgress.visibility = View.VISIBLE
            updateControls()
        }
    }

    override fun onPageFinished(view: WebView, url: String?) {
        if (navigationController.onPageFinished(view)) {
            browserRefresh.isRefreshing = false
            pageProgress.visibility = View.GONE
            updateControls()
        }
    }

    override fun onTitleChanged(view: WebView, title: String?) {
        navigationController.onTitleChanged(view, title)
    }

    override fun onProgressChanged(view: WebView, progress: Int) {
        if (navigationController.onProgressChanged(view, progress)) {
            pageProgress.progress = navigationController.state.progress
            pageProgress.visibility =
                if (navigationController.state.progress in 1..99) View.VISIBLE else View.GONE
        }
    }

    override fun onTonSiteLink(url: String) {
        navigate(url)
    }

    override fun onWalletLink(url: String) {
        if (!TonUrlPolicy.isWalletUri(url)) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.wallet_not_found, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.wallet_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNavigationBlocked(url: String?) {
        Toast.makeText(this, R.string.invalid_address, Toast.LENGTH_SHORT).show()
    }

    override fun onPageError(view: WebView, error: TonPageError) {
        if (tabFor(view)?.model?.id != navigationController.state.activeTabId) return
        browserRefresh.isRefreshing = false
        when (error) {
            TonPageError.DownloadsDisabled -> showPageError(
                R.string.downloads_unavailable,
                R.string.downloads_disabled,
            )
            TonPageError.LoadFailed -> showPageError(
                R.string.tonsite_unavailable,
                R.string.tonsite_load_failed,
            )
            is TonPageError.ProxyHttpError -> showPageError(
                R.string.tonsite_unavailable,
                R.string.ton_proxy_http_error,
                error.statusCode,
            )
        }
    }

    override fun onRendererGone(view: WebView) {
        val session = tabFor(view) ?: return
        if (session.model.id == navigationController.state.activeTabId) {
            browserRefresh.isRefreshing = false
        }
        if (session.model.id == navigationController.state.activeTabId && !destroyed) {
            showPageError(
                R.string.renderer_restarted,
                R.string.renderer_restarted_message,
            )
            val replacement = navigationController.replaceRenderer(session)
            webContainer.addView(
                replacement,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            session.model.currentUrl?.let(replacement::loadUrl)
        } else {
            navigationController.discardRenderer(session)
        }
    }

    private fun activeSession(): TabSession? =
        if (::navigationController.isInitialized) navigationController.active() else null

    private fun tabFor(webView: WebView): TabSession? =
        if (::navigationController.isInitialized) navigationController.forWebView(webView) else null

    private fun intentAddress(intent: Intent?): String? {
        val data = intent?.data ?: return null
        return data.toString().takeIf(TonUrlPolicy::isTonSiteUri)
    }

    private fun hideKeyboard() {
        val focusedView = currentFocus
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
            focusedView?.windowToken ?: root.windowToken,
            0,
        )
        focusedView?.clearFocus()
        addressBar.clearFocus()
        landingSearchInput.clearFocus()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class LocalizedError(
        @param:StringRes val titleRes: Int,
        @param:StringRes val messageRes: Int,
        val messageArguments: List<Any>,
    )

    private companion object {
        const val TOP_CHROME_HEIGHT_DP = 56
        const val BOTTOM_CHROME_HEIGHT_DP = 72
        const val SETTINGS_APP_BAR_HEIGHT_DP = 64
        const val TABS_SHEET_TAG = "tabs_sheet"
        const val BOOKMARKS_SHEET_TAG = "bookmarks_sheet"
        const val LANGUAGE_SHEET_TAG = "language_sheet"
    }
}

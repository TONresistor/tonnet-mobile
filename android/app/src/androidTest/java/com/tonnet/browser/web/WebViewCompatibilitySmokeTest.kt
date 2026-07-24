package com.tonnet.browser.web

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(maxSdkVersion = 28)
class WebViewCompatibilitySmokeTest {
    @Test
    fun unsupportedProviderIsReportedBeforeSessionCreation() {
        assertTrue(WebViewSessionManager().unsupportedFeatures().isNotEmpty())
    }
}

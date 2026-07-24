package com.tonnet.browser.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JavaScriptPrivacyPolicyTest {
    @Test
    fun `advanced surfaces are included only when enabled`() {
        val enabled = JavaScriptPrivacyPolicy.documentStartScript(
            antiFingerprintingEnabled = true,
            sessionSeed = VALID_SEED,
        )
        val disabled = JavaScriptPrivacyPolicy.documentStartScript(
            antiFingerprintingEnabled = false,
            sessionSeed = VALID_SEED,
        )

        assertTrue(enabled.contains("protectedCanvasCopy"))
        assertTrue(enabled.contains("protectWebGL"))
        assertTrue(enabled.contains("protectedAudioBuffers"))
        assertTrue(enabled.contains(VALID_SEED))
        assertTrue(enabled.contains("location.ancestorOrigins"))
        assertTrue(enabled.contains("documentNonce"))
        assertTrue(enabled.contains("siteContext"))
        assertFalse(disabled.contains("protectedCanvasCopy"))
        assertFalse(disabled.contains("protectWebGL"))
        assertFalse(disabled.contains("protectedAudioBuffers"))
        assertFalse(disabled.contains(VALID_SEED))
        assertTrue(disabled.contains("'gpu'"))
    }

    @Test
    fun `privacy patches preserve native descriptors and skip absent APIs`() {
        val script = JavaScriptPrivacyPolicy.documentStartScript(
            antiFingerprintingEnabled = true,
            sessionSeed = VALID_SEED,
        )

        assertTrue(script.contains("findPropertyOwner"))
        assertTrue(script.contains("nativeFunctions"))
        assertTrue(script.contains("Object.getOwnPropertyDescriptor"))
        assertFalse(script.contains("configurable: false"))
        assertFalse(script.contains("define(globalThis, name, undefined)"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `session seed accepts only generated hexadecimal values`() {
        JavaScriptPrivacyPolicy.documentStartScript(
            antiFingerprintingEnabled = true,
            sessionSeed = "not-a-private-seed",
        )
    }

    private companion object {
        const val VALID_SEED = "0123456789abcdef0123456789abcdef"
    }
}

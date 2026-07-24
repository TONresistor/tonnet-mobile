package com.tonnet.browser.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TonUrlPolicyTest {
    @Test
    fun `expands a simple user search to a TON domain`() {
        val result = TonUrlPolicy.normalizeUserInput("  PIRACY  ").getOrThrow()

        assertEquals("piracy.ton", result.host)
        assertEquals("http://piracy.ton/", result.value)
        assertTrue(TonUrlPolicy.normalize("piracy").isFailure)
        assertTrue(!TonUrlPolicy.isAllowed("piracy"))
    }

    @Test
    fun `does not expand incomplete paths queries or dotted names`() {
        val rejected = listOf(
            "piracy/path",
            "piracy?q=1",
            "piracy#section",
            "foo.bar",
        )

        rejected.forEach { value ->
            assertTrue("expected rejection: $value", TonUrlPolicy.normalizeUserInput(value).isFailure)
        }
        assertEquals(
            "http://piracy.ton/path",
            TonUrlPolicy.normalizeUserInput("piracy.ton/path").getOrThrow().value,
        )
    }

    @Test
    fun `normalizes bare TON domains`() {
        val result = TonUrlPolicy.normalize("  FOUNDATION.TON/path?q=1  ").getOrThrow()
        assertEquals("foundation.ton", result.host)
        assertEquals("http://foundation.ton/path?q=1", result.value)
    }

    @Test
    fun `normalizes TON Site deep links but rejects wallet links`() {
        assertEquals(
            "http://site.ton/path?q=1",
            TonUrlPolicy.normalize("tonsite://site.ton/path?q=1").getOrThrow().value,
        )
        assertTrue(TonUrlPolicy.normalize("ton://site.ton").isFailure)
        assertTrue(TonUrlPolicy.isTonSiteUri("TONSITE://site.ton"))
        assertTrue(!TonUrlPolicy.isTonSiteUri("ton://transfer/example"))
        assertTrue(TonUrlPolicy.isWalletUri("ton://transfer/example"))
        assertTrue(!TonUrlPolicy.isWalletUri("tonsite://site.ton"))
    }

    @Test
    fun `allows direct ADNL names`() {
        assertTrue(TonUrlPolicy.normalize("abcdef_0123.adnl").isSuccess)
    }

    @Test
    fun `allows Telegram TON DNS names`() {
        val result = TonUrlPolicy.normalize("  COLLECTIBLE.T.ME/path  ").getOrThrow()

        assertEquals("collectible.t.me", result.host)
        assertEquals("http://collectible.t.me/path", result.value)
    }

    @Test
    fun `rejects deceptive or external hosts`() {
        val rejected = listOf(
            "example.com",
            "site.ton.example.com",
            "site.t.me.example.com",
            "site.ton@evil.example",
            "http://evil.example/?next=site.ton",
            "http://.ton",
            "http://t.me",
            "http://site..ton",
            "http://localhost",
            "http://127.0.0.1",
        )
        rejected.forEach { value -> assertTrue("expected rejection: $value", TonUrlPolicy.normalize(value).isFailure) }
    }

    @Test
    fun `rejects unsupported schemes ports and syntax`() {
        val rejected = listOf(
            "https://site.ton",
            "file:///site.ton",
            "javascript:alert(1)",
            "http://site.ton:8080",
            "http://site.ton\\@evil.example",
            "http://sit e.ton",
            "http://sité.ton",
        )
        rejected.forEach { value -> assertTrue("expected rejection: $value", TonUrlPolicy.normalize(value).isFailure) }
    }
}

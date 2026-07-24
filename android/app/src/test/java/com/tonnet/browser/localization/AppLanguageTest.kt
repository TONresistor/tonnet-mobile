package com.tonnet.browser.localization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class AppLanguageTest {
    @Test
    fun `supported language tags resolve to their application language`() {
        AppLanguage.entries.forEach { language ->
            assertEquals(language, AppLanguage.fromLanguageTag(language.languageTag))
            assertEquals(
                language,
                AppLanguage.fromLanguageTag("${language.languageTag}-001"),
            )
        }
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag("en-US"))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.fromLanguageTag("ru-RU"))
    }

    @Test
    fun `unsupported or empty locales use the English fallback`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag(null))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag(""))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag("ar-EG"))
    }

    @Test
    fun `selection accepts only explicitly supported tags`() {
        AppLanguage.entries.forEach { language ->
            assertEquals(language, AppLanguage.findSupported(language.languageTag))
        }
        assertNull(AppLanguage.findSupported("ru-RU"))
        assertNull(AppLanguage.findSupported("ar"))
    }

    @Test
    fun `language registry contains every bottom sheet option exactly once`() {
        assertEquals(
            listOf(
                "en",
                "ru",
                "fr",
                "de",
                "zh",
                "es",
                "it",
                "ja",
                "ko",
                "tr",
                "vi",
                "hi",
                "pt",
                "uk",
                "my",
            ),
            AppLanguage.entries.map(AppLanguage::languageTag),
        )
        assertEquals(
            AppLanguage.entries.size,
            AppLanguage.entries.map(AppLanguage::nativeNameRes).distinct().size,
        )
    }

    @Test
    fun `android locale config stays aligned with the language registry`() {
        val localeConfig = File("src/main/res/xml/locales_config.xml").readText()
        val declaredTags = Regex("""<locale android:name="([^"]+)"\s*/>""")
            .findAll(localeConfig)
            .map { match -> match.groupValues[1] }
            .toList()

        assertEquals(AppLanguage.entries.map(AppLanguage::languageTag), declaredTags)
    }
}

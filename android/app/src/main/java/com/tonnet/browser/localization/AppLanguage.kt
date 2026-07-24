package com.tonnet.browser.localization

import androidx.annotation.StringRes
import com.tonnet.browser.R
import java.util.Locale

enum class AppLanguage(
    val languageTag: String,
    @get:StringRes val nativeNameRes: Int,
) {
    ENGLISH("en", R.string.language_english_native),
    RUSSIAN("ru", R.string.language_russian_native),
    FRENCH("fr", R.string.language_french_native),
    GERMAN("de", R.string.language_german_native),
    CHINESE("zh", R.string.language_chinese_native),
    SPANISH("es", R.string.language_spanish_native),
    ITALIAN("it", R.string.language_italian_native),
    JAPANESE("ja", R.string.language_japanese_native),
    KOREAN("ko", R.string.language_korean_native),
    TURKISH("tr", R.string.language_turkish_native),
    VIETNAMESE("vi", R.string.language_vietnamese_native),
    HINDI("hi", R.string.language_hindi_native),
    PORTUGUESE("pt", R.string.language_portuguese_native),
    UKRAINIAN("uk", R.string.language_ukrainian_native),
    MYANMAR("my", R.string.language_myanmar_native),
    ;

    companion object {
        fun fromLanguageTag(languageTag: String?): AppLanguage {
            val language = languageTag
                ?.takeIf(String::isNotBlank)
                ?.let(Locale::forLanguageTag)
                ?.language
            return entries.firstOrNull { it.languageTag == language } ?: ENGLISH
        }

        fun findSupported(languageTag: String): AppLanguage? =
            entries.firstOrNull { it.languageTag == languageTag }
    }
}

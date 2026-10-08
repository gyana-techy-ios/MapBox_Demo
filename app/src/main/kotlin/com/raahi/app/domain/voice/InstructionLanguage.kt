package com.raahi.app.domain.voice

import java.util.Locale

object InstructionLanguage {
    private val supportedTags = setOf("da", "de", "en", "es", "fr", "id", "it", "nl", "ru", "sv", "tr", "vi")
    private const val FALLBACK = "en"

    fun apiTagFor(locale: Locale): String = locale.language.takeIf { it in supportedTags } ?: FALLBACK

    fun speechLocaleFor(locale: Locale): Locale {
        val tag = apiTagFor(locale)
        return if (tag == FALLBACK && locale.language == FALLBACK) locale else if (tag == FALLBACK) Locale.forLanguageTag("en-IN") else Locale.forLanguageTag(tag)
    }
}

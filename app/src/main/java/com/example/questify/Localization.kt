package com.example.questify

import java.util.Locale

enum class AppLanguage(val code: String) {
    EN("en"),
    AR("ar");

    companion object {
        fun resolve(raw: String?): AppLanguage {
            val normalized = raw.orEmpty().trim().lowercase(Locale.getDefault())
            val candidate = if (normalized.isBlank() || normalized == "system") {
                Locale.getDefault().toLanguageTag().lowercase(Locale.getDefault())
            } else {
                normalized
            }
            return entries.firstOrNull { candidate == it.code || candidate.startsWith("${it.code}-") } ?: EN
        }
    }
}

fun localize(
    lang: String?,
    en: String,
    vararg translations: Pair<AppLanguage, String>
): String {
    val resolved = AppLanguage.resolve(lang)
    return translations.firstOrNull { it.first == resolved }?.second ?: en
}



























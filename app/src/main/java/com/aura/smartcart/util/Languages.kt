package com.aura.smartcart.util

/** Which recogniser can read a script. */
enum class Script { LATIN, DEVANAGARI, ARABIC }

/**
 * Languages for voice input and scanning. Roman Urdu is Urdu written in English letters,
 * so it is heard with the Indian-English recogniser (which writes Hindustani words in Latin script).
 */
enum class InputLanguage(
    val code: String,
    val label: String,
    val speechTag: String,
    val script: Script
) {
    ENGLISH("en", "English", "en-US", Script.LATIN),
    ROMAN_URDU("ur-Latn", "Roman Urdu", "en-IN", Script.LATIN),
    URDU("ur", "اردو", "ur-PK", Script.ARABIC),
    HINDI("hi", "हिन्दी", "hi-IN", Script.DEVANAGARI),
    ARABIC("ar", "العربية", "ar-SA", Script.ARABIC);

    companion object {
        fun fromCode(code: String?): InputLanguage? = entries.firstOrNull { it.code == code }

        /** Default input language for an app UI language. */
        fun forAppLanguage(appLang: String): InputLanguage = when (appLang) {
            "ur" -> URDU
            "hi" -> HINDI
            "ar" -> ARABIC
            else -> ENGLISH
        }
    }
}

/** App UI languages. */
enum class AppLanguage(val tag: String, val label: String) {
    ENGLISH("en", "English"),
    URDU("ur", "اردو"),
    HINDI("hi", "हिन्दी"),
    ARABIC("ar", "العربية");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { tag != null && tag.startsWith(it.tag) } ?: ENGLISH
    }
}

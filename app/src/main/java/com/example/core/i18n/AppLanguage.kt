package com.example.core.i18n

enum class AppLanguage(val code: String, val nativeName: String) {
    EN("en", "English"),
    RU("ru", "Русский"),
    TR("tr", "Türkçe"),
    TK("tk", "Türkmençe");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: EN
    }
}

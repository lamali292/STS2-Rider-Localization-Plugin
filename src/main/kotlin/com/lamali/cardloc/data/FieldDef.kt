package com.lamali.cardloc.data

/** "cards" -> "cards.json"; names that already end in ".json" (any case) are left alone. */
fun String.withJsonSuffix(): String {
    val name = trim()
    return if (name.isEmpty() || name.endsWith(".json", ignoreCase = true)) name else "$name.json"
}

data class FieldDef(
    val name: String,
    val file: String,
    val optional: Boolean = true
)
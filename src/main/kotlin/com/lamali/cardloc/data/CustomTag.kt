package com.lamali.cardloc.data

data class CustomTag(
    val tag: String,
    val label: String,
    val color: String,
    val bold: Boolean = false,
    val italic: Boolean = false
)
package com.lamali.cardloc.data

data class CardLocConfig(
    val localizationBase: String,
    val projectId: String,
    val presets: List<CardLocPreset>,
    val pinnedColors: List<PinnedColor>? = emptyList(),
    val customTags: List<CustomTag>? = emptyList(),
)
package com.lamali.cardloc.data

data class CardLocConfig(
    val localizationBase: String,
    /** Legacy: only used to derive the key prefix when [keyPrefix] is not set. */
    val projectId: String,
    /** Prefix of every localization key, e.g. "DOWNFALL-". Overrides [projectId]. */
    val keyPrefix: String? = null,
    val presets: List<CardLocPreset>,
    val pinnedColors: List<PinnedColor>? = emptyList(),
    val customTags: List<CustomTag>? = emptyList(),
)
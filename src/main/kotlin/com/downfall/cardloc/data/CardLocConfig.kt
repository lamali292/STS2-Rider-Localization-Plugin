package com.downfall.cardloc.data

data class CardLocConfig(
    val localizationBase: String,
    val projectId: String,
    val presets: List<CardLocPreset>
)
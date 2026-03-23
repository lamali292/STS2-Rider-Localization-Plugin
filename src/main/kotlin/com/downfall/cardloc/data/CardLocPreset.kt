package com.downfall.cardloc.data

data class CardLocPreset(
    val id: String,
    val markers: List<String>,
    val fields: List<FieldDef>
)
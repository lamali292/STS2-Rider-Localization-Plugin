package com.lamali.cardloc.data

data class CardLocPreset(
    val id: String,
    val markers: List<String>,
    val fields: List<FieldDef>
)
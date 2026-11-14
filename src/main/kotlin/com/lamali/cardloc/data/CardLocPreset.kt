package com.lamali.cardloc.data

/**
 * @param base id of the project-default preset this one builds on. When set, [markers] and
 * [fields] are only the *additions* to that default (see [resolve]).
 */
data class CardLocPreset(
    val id: String,
    val markers: List<String>,
    val fields: List<FieldDef>,
    val base: String? = null
) {
    /** The effective preset: the default named [base] plus this preset's additions. */
    fun resolve(defaults: List<CardLocPreset>): CardLocPreset {
        val baseId = base ?: return this
        val d = defaults.firstOrNull { it.id == baseId } ?: return this
        return copy(
            markers = (d.markers + markers).distinct(),
            fields = d.fields + fields.filter { f -> d.fields.none { it.file == f.file && it.name == f.name } }
        )
    }
}

package com.ridvanozdemir.socialdiet.data.ai

data class CalorieReference(
    val caloriesPer100g: Float,
    val sourceName: String
)

object TurkishNutritionReference {
    private val references = mapOf(
        // TürKomp values are per edible 100 g. Generic classes use an average
        // where TürKomp has more than one close regional/type match.
        "asure" to CalorieReference(94f, "TürKomp"),
        "baklava" to CalorieReference(435f, "TürKomp"),
        "borek" to CalorieReference(284f, "TürKomp"),
        "lokum" to CalorieReference(359f, "TürKomp"),
        "manti" to CalorieReference(286f, "TürKomp"),
        "simit" to CalorieReference(368f, "TürKomp")
    )

    fun forFood(foodKey: String?): CalorieReference? =
        foodKey?.let(references::get)
}

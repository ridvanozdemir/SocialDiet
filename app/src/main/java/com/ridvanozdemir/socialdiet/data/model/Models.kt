package com.ridvanozdemir.socialdiet.data.model

data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val heightCm: Int? = null,
    val startWeightKg: Double? = null,
    val currentWeightKg: Double? = null,
    val targetWeightKg: Double? = null,
    val dailyCalorieTarget: Int? = null,
    val programCompleted: Boolean = false
)

data class Meal(
    val id: String = "",
    val userId: String = "",
    val mealType: String = "OTHER",
    val imageUrl: String? = null,
    val aiLabel: String = "",
    val aiCalories: Int? = null,
    val confirmedCalories: Int = 0,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

data class Friendship(
    val userA: String = "",
    val userB: String = "",
    val status: String = "PENDING",
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

data class WeightEntry(
    val id: String = "",
    val userId: String = "",
    val dateIso: String = "",
    val weightKg: Double = 0.0
)

data class DailyStat(
    val userId: String = "",
    val dateIso: String = "",
    val calorieTarget: Int = 0,
    val calorieTotal: Int = 0,
    val adherenceScore: Int = 0
)

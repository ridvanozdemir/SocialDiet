package com.ridvanozdemir.socialdiet.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.ridvanozdemir.socialdiet.data.model.UserProfile
import java.util.Locale

data class RegistrationInput(
    val email: String,
    val password: String,
    val username: String,
    val displayName: String,
    val heightCm: Int,
    val startWeightKg: Double,
    val targetWeightKg: Double,
    val dailyCalorieTarget: Int
)

class FirebaseRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val currentUserId: String?
        get() = auth.currentUser?.uid

    fun addAuthStateListener(onChanged: (FirebaseUser?) -> Unit): FirebaseAuth.AuthStateListener {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            onChanged(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        return listener
    }

    fun removeAuthStateListener(listener: FirebaseAuth.AuthStateListener) {
        auth.removeAuthStateListener(listener)
    }

    fun signIn(email: String, password: String, onResult: (Result<Unit>) -> Unit) {
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun register(input: RegistrationInput, onResult: (Result<Unit>) -> Unit) {
        val normalizedUsername = normalizeUsername(input.username)
        if (!USERNAME_REGEX.matches(normalizedUsername)) {
            onResult(Result.failure(IllegalArgumentException("Kullanıcı adı 3-20 karakter olmalı ve yalnızca a-z, 0-9, nokta veya alt çizgi içermeli.")))
            return
        }

        auth.createUserWithEmailAndPassword(input.email.trim(), input.password)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                if (user == null) {
                    onResult(Result.failure(IllegalStateException("Kullanıcı oluşturulamadı.")))
                    return@addOnSuccessListener
                }

                val usernameRef = firestore.collection("usernames").document(normalizedUsername)
                val userRef = firestore.collection("users").document(user.uid)

                firestore.runTransaction { transaction ->
                    if (transaction.get(usernameRef).exists()) {
                        throw FirebaseFirestoreException(
                            "Bu kullanıcı adı zaten kullanılıyor.",
                            FirebaseFirestoreException.Code.ABORTED
                        )
                    }

                    val profile = hashMapOf<String, Any>(
                        "uid" to user.uid,
                        "email" to input.email.trim(),
                        "username" to normalizedUsername,
                        "displayName" to input.displayName.trim(),
                        "heightCm" to input.heightCm,
                        "startWeightKg" to input.startWeightKg,
                        "currentWeightKg" to input.startWeightKg,
                        "targetWeightKg" to input.targetWeightKg,
                        "dailyCalorieTarget" to input.dailyCalorieTarget,
                        "programCompleted" to isGoalCompleted(
                            input.startWeightKg,
                            input.startWeightKg,
                            input.targetWeightKg
                        ),
                        "createdAt" to FieldValue.serverTimestamp()
                    )

                    transaction.set(
                        usernameRef,
                        mapOf(
                            "uid" to user.uid,
                            "username" to normalizedUsername,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                    transaction.set(userRef, profile)
                }.addOnSuccessListener {
                    onResult(Result.success(Unit))
                }.addOnFailureListener { error ->
                    user.delete().addOnCompleteListener {
                        auth.signOut()
                        onResult(Result.failure(error))
                    }
                }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun observeProfile(
        userId: String,
        onProfile: (UserProfile?) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration {
        return firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                onProfile(snapshot?.toObject(UserProfile::class.java))
            }
    }

    fun updateCurrentWeight(weightKg: Double, onResult: (Result<Unit>) -> Unit) {
        val uid = currentUserId
        if (uid == null) {
            onResult(Result.failure(IllegalStateException("Oturum açık değil.")))
            return
        }

        val userRef = firestore.collection("users").document(uid)
        userRef.get()
            .addOnSuccessListener { snapshot ->
                val start = snapshot.getDouble("startWeightKg")
                val target = snapshot.getDouble("targetWeightKg")
                if (start == null || target == null) {
                    onResult(Result.failure(IllegalStateException("Kilo hedefi bilgileri eksik.")))
                    return@addOnSuccessListener
                }

                val batch = firestore.batch()
                batch.update(
                    userRef,
                    mapOf(
                        "currentWeightKg" to weightKg,
                        "programCompleted" to isGoalCompleted(start, weightKg, target),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    userRef.collection("weights").document(),
                    mapOf(
                        "userId" to uid,
                        "weightKg" to weightKg,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit()
                    .addOnSuccessListener { onResult(Result.success(Unit)) }
                    .addOnFailureListener { onResult(Result.failure(it)) }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun saveMeal(
        userId: String,
        mealType: String,
        aiLabel: String,
        aiConfidence: Double?,
        calorieSource: String,
        aiCalories: Int,
        confirmedCalories: Int,
        estimatedMassGrams: Double,
        fatGrams: Double,
        carbsGrams: Double,
        proteinGrams: Double,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (currentUserId != userId) {
            onResult(Result.failure(IllegalStateException("Bu kullanıcı için öğün kaydedilemez.")))
            return
        }

        if (confirmedCalories !in 0..10000 || estimatedMassGrams !in 1.0..5000.0) {
            onResult(Result.failure(IllegalArgumentException("Öğün değerleri geçersiz.")))
            return
        }

        val mealRef = firestore.collection("meals").document()
        val meal = hashMapOf<String, Any?>(
            "id" to mealRef.id,
            "userId" to userId,
            "mealType" to mealType,
            "imageUrl" to null,
            "aiLabel" to aiLabel,
            "aiConfidence" to aiConfidence,
            "calorieSource" to calorieSource,
            "aiCalories" to aiCalories,
            "confirmedCalories" to confirmedCalories,
            "estimatedMassGrams" to estimatedMassGrams,
            "fatGrams" to fatGrams,
            "carbsGrams" to carbsGrams,
            "proteinGrams" to proteinGrams,
            "createdAt" to FieldValue.serverTimestamp()
        )

        mealRef.set(meal)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun signOut() {
        auth.signOut()
    }

    companion object {
        private val USERNAME_REGEX = Regex("^[a-z0-9._]{3,20}$")

        fun normalizeUsername(value: String): String =
            value.trim().lowercase(Locale.ROOT)

        fun isGoalCompleted(start: Double, current: Double, target: Double): Boolean {
            return when {
                target < start -> current <= target
                target > start -> current >= target
                else -> true
            }
        }
    }
}

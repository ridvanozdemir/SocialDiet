package com.ridvanozdemir.socialdiet.data

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.ridvanozdemir.socialdiet.data.model.UserProfile
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt

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

data class ProfileSetupInput(
    val username: String,
    val displayName: String,
    val heightCm: Int,
    val startWeightKg: Double,
    val targetWeightKg: Double,
    val dailyCalorieTarget: Int
)

data class SocialProfile(
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val progressPercent: Int = 0,
    val programCompleted: Boolean = false
)

data class FriendRequestItem(
    val friendshipId: String,
    val profile: SocialProfile
)

data class FriendsSnapshot(
    val incoming: List<FriendRequestItem> = emptyList(),
    val outgoing: List<FriendRequestItem> = emptyList(),
    val friends: List<SocialProfile> = emptyList()
)

data class TodaySummary(
    val dateIso: String,
    val calorieTarget: Int,
    val calorieTotal: Int,
    val adherenceScore: Int,
    val breakfastCalories: Int,
    val lunchCalories: Int,
    val dinnerCalories: Int,
    val snackCalories: Int
)

data class LeaderboardEntry(
    val uid: String,
    val username: String,
    val displayName: String,
    val dailyScore: Int,
    val weeklyScore: Int,
    val isCurrentUser: Boolean
)

class FirebaseRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val currentUserId: String?
        get() = auth.currentUser?.uid

    val currentUserEmail: String?
        get() = auth.currentUser?.email

    val currentUserDisplayName: String?
        get() = auth.currentUser?.displayName

    fun usesPasswordProvider(): Boolean =
        auth.currentUser?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true

    fun usesGoogleProvider(): Boolean =
        auth.currentUser?.providerData?.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } == true

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

    fun signInWithGoogleIdToken(idToken: String, onResult: (Result<Unit>) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun register(input: RegistrationInput, onResult: (Result<Unit>) -> Unit) {
        val normalizedUsername = normalizeUsername(input.username)
        if (!USERNAME_REGEX.matches(normalizedUsername)) {
            onResult(Result.failure(IllegalArgumentException(USERNAME_ERROR)))
            return
        }

        auth.createUserWithEmailAndPassword(input.email.trim(), input.password)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                if (user == null) {
                    onResult(Result.failure(IllegalStateException("Kullanıcı oluşturulamadı.")))
                    return@addOnSuccessListener
                }

                createProfileDocuments(
                    user = user,
                    username = normalizedUsername,
                    displayName = input.displayName,
                    heightCm = input.heightCm,
                    startWeightKg = input.startWeightKg,
                    targetWeightKg = input.targetWeightKg,
                    dailyCalorieTarget = input.dailyCalorieTarget
                ) { profileResult ->
                    profileResult.onSuccess {
                        user.sendEmailVerification()
                            .addOnCompleteListener {
                                // Verification can also be resent from the verification screen.
                                onResult(Result.success(Unit))
                            }
                    }.onFailure { error ->
                        user.delete().addOnCompleteListener {
                            auth.signOut()
                            onResult(Result.failure(error))
                        }
                    }
                }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun completeCurrentUserProfile(
        input: ProfileSetupInput,
        onResult: (Result<Unit>) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onResult(Result.failure(IllegalStateException("Oturum açık değil.")))
            return
        }
        val normalizedUsername = normalizeUsername(input.username)
        if (!USERNAME_REGEX.matches(normalizedUsername)) {
            onResult(Result.failure(IllegalArgumentException(USERNAME_ERROR)))
            return
        }

        createProfileDocuments(
            user = user,
            username = normalizedUsername,
            displayName = input.displayName,
            heightCm = input.heightCm,
            startWeightKg = input.startWeightKg,
            targetWeightKg = input.targetWeightKg,
            dailyCalorieTarget = input.dailyCalorieTarget,
            onResult = onResult
        )
    }

    private fun createProfileDocuments(
        user: FirebaseUser,
        username: String,
        displayName: String,
        heightCm: Int,
        startWeightKg: Double,
        targetWeightKg: Double,
        dailyCalorieTarget: Int,
        onResult: (Result<Unit>) -> Unit
    ) {
        val normalizedUsername = normalizeUsername(username)
        val usernameRef = firestore.collection("usernames").document(normalizedUsername)
        val userRef = firestore.collection("users").document(user.uid)
        val publicRef = firestore.collection("publicProfiles").document(user.uid)
        val completed = isGoalCompleted(startWeightKg, startWeightKg, targetWeightKg)

        firestore.runTransaction { transaction ->
            if (transaction.get(usernameRef).exists()) {
                throw FirebaseFirestoreException(
                    "Bu kullanıcı adı zaten kullanılıyor.",
                    FirebaseFirestoreException.Code.ABORTED
                )
            }

            val profile = hashMapOf<String, Any?>(
                "uid" to user.uid,
                "email" to user.email,
                "username" to normalizedUsername,
                "displayName" to displayName.trim(),
                "heightCm" to heightCm,
                "startWeightKg" to startWeightKg,
                "currentWeightKg" to startWeightKg,
                "targetWeightKg" to targetWeightKg,
                "dailyCalorieTarget" to dailyCalorieTarget,
                "programCompleted" to completed,
                "createdAt" to FieldValue.serverTimestamp()
            )
            val publicProfile = hashMapOf<String, Any>(
                "uid" to user.uid,
                "username" to normalizedUsername,
                "displayName" to displayName.trim(),
                "progressPercent" to 0,
                "programCompleted" to completed,
                "updatedAt" to FieldValue.serverTimestamp()
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
            transaction.set(publicRef, publicProfile)
        }.addOnSuccessListener {
            onResult(Result.success(Unit))
        }.addOnFailureListener { error ->
            onResult(Result.failure(error))
        }
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

    fun ensurePublicProfile(profile: UserProfile) {
        if (profile.uid.isBlank() || profile.username.isBlank()) return
        val progress = progressPercent(
            profile.startWeightKg,
            profile.currentWeightKg,
            profile.targetWeightKg
        )
        firestore.collection("publicProfiles").document(profile.uid).set(
            mapOf(
                "uid" to profile.uid,
                "username" to profile.username,
                "displayName" to profile.displayName,
                "progressPercent" to progress,
                "programCompleted" to profile.programCompleted,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )
    }

    fun sendVerificationEmail(onResult: (Result<Unit>) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onResult(Result.failure(IllegalStateException("Oturum açık değil.")))
            return
        }
        user.sendEmailVerification()
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun reloadCurrentUser(onResult: (Result<FirebaseUser>) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onResult(Result.failure(IllegalStateException("Oturum açık değil.")))
            return
        }
        user.reload()
            .addOnSuccessListener {
                val refreshed = auth.currentUser
                if (refreshed == null) {
                    onResult(Result.failure(IllegalStateException("Kullanıcı bilgisi yenilenemedi.")))
                } else {
                    onResult(Result.success(refreshed))
                }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun sendPasswordReset(email: String, onResult: (Result<Unit>) -> Unit) {
        if (email.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("E-posta adresini gir.")))
            return
        }
        auth.sendPasswordResetEmail(email.trim())
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun changePassword(
        currentPassword: String,
        newPassword: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val user = auth.currentUser
        val email = user?.email
        if (user == null || email.isNullOrBlank()) {
            onResult(Result.failure(IllegalStateException("Oturum bilgisi bulunamadı.")))
            return
        }
        if (!usesPasswordProvider()) {
            onResult(Result.failure(IllegalStateException("Bu hesap şifre ile giriş kullanmıyor.")))
            return
        }
        if (currentPassword.isBlank() || newPassword.length < 6) {
            onResult(Result.failure(IllegalArgumentException("Mevcut şifreni ve en az 6 karakterlik yeni şifreni gir.")))
            return
        }

        val credential = EmailAuthProvider.getCredential(email, currentPassword)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                user.updatePassword(newPassword)
                    .addOnSuccessListener { onResult(Result.success(Unit)) }
                    .addOnFailureListener { onResult(Result.failure(it)) }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
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

                val completed = isGoalCompleted(start, weightKg, target)
                val progress = progressPercent(start, weightKg, target)
                val batch = firestore.batch()
                batch.update(
                    userRef,
                    mapOf(
                        "currentWeightKg" to weightKg,
                        "programCompleted" to completed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    firestore.collection("publicProfiles").document(uid),
                    mapOf(
                        "uid" to uid,
                        "username" to (snapshot.getString("username") ?: ""),
                        "displayName" to (snapshot.getString("displayName") ?: ""),
                        "progressPercent" to progress,
                        "programCompleted" to completed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
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
            .addOnSuccessListener {
                // Keep the social score current without making meal saving depend on this refresh.
                loadTodaySummary(userId) { }
                onResult(Result.success(Unit))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun searchUsers(query: String, onResult: (Result<List<SocialProfile>>) -> Unit) {
        val normalized = normalizeUsername(query)
        if (normalized.length < 2) {
            onResult(Result.failure(IllegalArgumentException("Arama için en az 2 karakter gir.")))
            return
        }
        firestore.collection("publicProfiles")
            .orderBy("username")
            .startAt(normalized)
            .endAt(normalized + "\uf8ff")
            .limit(10)
            .get()
            .addOnSuccessListener { snapshot ->
                val currentUid = currentUserId
                val profiles = snapshot.documents
                    .mapNotNull(::socialProfileFrom)
                    .filter { it.uid != currentUid }
                onResult(Result.success(profiles))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun sendFriendRequest(targetUid: String, onResult: (Result<Unit>) -> Unit) {
        val uid = currentUserId
        if (uid == null) {
            onResult(Result.failure(IllegalStateException("Oturum açık değil.")))
            return
        }
        if (uid == targetUid) {
            onResult(Result.failure(IllegalArgumentException("Kendine arkadaşlık isteği gönderemezsin.")))
            return
        }

        val targetRef = firestore.collection("publicProfiles").document(targetUid)
        targetRef.get().addOnSuccessListener { target ->
            if (!target.exists()) {
                onResult(Result.failure(IllegalArgumentException("Kullanıcı bulunamadı.")))
                return@addOnSuccessListener
            }
            val id = friendshipId(uid, targetUid)
            val ref = firestore.collection("friendships").document(id)
            ref.get().addOnSuccessListener { existing ->
                if (existing.exists()) {
                    onResult(Result.failure(IllegalStateException("Bu kullanıcıyla zaten bir arkadaşlık veya bekleyen istek var.")))
                    return@addOnSuccessListener
                }
                val pair = listOf(uid, targetUid).sorted()
                ref.set(
                    mapOf(
                        "userA" to pair[0],
                        "userB" to pair[1],
                        "requesterId" to uid,
                        "recipientId" to targetUid,
                        "status" to "PENDING",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).addOnSuccessListener {
                    onResult(Result.success(Unit))
                }.addOnFailureListener { onResult(Result.failure(it)) }
            }.addOnFailureListener { onResult(Result.failure(it)) }
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun loadFriendsSnapshot(userId: String, onResult: (Result<FriendsSnapshot>) -> Unit) {
        if (currentUserId != userId) {
            onResult(Result.failure(IllegalStateException("Arkadaş listesine erişilemiyor.")))
            return
        }
        val collection = firestore.collection("friendships")
        collection.whereEqualTo("userA", userId).get()
            .addOnSuccessListener { first ->
                collection.whereEqualTo("userB", userId).get()
                    .addOnSuccessListener { second ->
                        val docs = (first.documents + second.documents).distinctBy { it.id }
                        buildFriendsSnapshot(userId, docs, onResult)
                    }
                    .addOnFailureListener { onResult(Result.failure(it)) }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun buildFriendsSnapshot(
        userId: String,
        docs: List<DocumentSnapshot>,
        onResult: (Result<FriendsSnapshot>) -> Unit
    ) {
        data class RawConnection(
            val id: String,
            val otherUid: String,
            val status: String,
            val requesterId: String,
            val recipientId: String
        )

        val raw = docs.mapNotNull { doc ->
            val a = doc.getString("userA") ?: return@mapNotNull null
            val b = doc.getString("userB") ?: return@mapNotNull null
            val other = if (a == userId) b else a
            RawConnection(
                id = doc.id,
                otherUid = other,
                status = doc.getString("status") ?: "PENDING",
                requesterId = doc.getString("requesterId") ?: "",
                recipientId = doc.getString("recipientId") ?: ""
            )
        }
        val ids = raw.map { it.otherUid }.toSet()
        fetchSocialProfiles(ids) { profilesResult ->
            profilesResult.onSuccess { profiles ->
                val byId = profiles.associateBy { it.uid }
                fun profile(uid: String) = byId[uid] ?: SocialProfile(
                    uid = uid,
                    username = "kullanici",
                    displayName = "SocialDiet kullanıcısı"
                )

                val incoming = raw
                    .filter { it.status == "PENDING" && it.recipientId == userId }
                    .map { FriendRequestItem(it.id, profile(it.otherUid)) }
                    .sortedBy { it.profile.username }
                val outgoing = raw
                    .filter { it.status == "PENDING" && it.requesterId == userId }
                    .map { FriendRequestItem(it.id, profile(it.otherUid)) }
                    .sortedBy { it.profile.username }
                val friends = raw
                    .filter { it.status == "ACCEPTED" }
                    .map { profile(it.otherUid) }
                    .distinctBy { it.uid }
                    .sortedBy { it.username }

                onResult(Result.success(FriendsSnapshot(incoming, outgoing, friends)))
            }.onFailure { onResult(Result.failure(it)) }
        }
    }

    private fun fetchSocialProfiles(
        ids: Set<String>,
        onResult: (Result<List<SocialProfile>>) -> Unit
    ) {
        if (ids.isEmpty()) {
            onResult(Result.success(emptyList()))
            return
        }
        val remaining = AtomicInteger(ids.size)
        val profiles = mutableListOf<SocialProfile>()
        var firstError: Throwable? = null
        ids.forEach { uid ->
            firestore.collection("publicProfiles").document(uid).get()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        socialProfileFrom(task.result)?.let { profile ->
                            synchronized(profiles) { profiles += profile }
                        }
                    } else if (firstError == null) {
                        firstError = task.exception
                    }
                    if (remaining.decrementAndGet() == 0) {
                        val error = firstError
                        if (error != null) onResult(Result.failure(error))
                        else onResult(Result.success(profiles.toList()))
                    }
                }
        }
    }

    fun acceptFriendRequest(friendshipId: String, onResult: (Result<Unit>) -> Unit) {
        val uid = currentUserId
        if (uid == null) {
            onResult(Result.failure(IllegalStateException("Oturum açık değil.")))
            return
        }
        val ref = firestore.collection("friendships").document(friendshipId)
        ref.get().addOnSuccessListener { doc ->
            if (!doc.exists() || doc.getString("recipientId") != uid || doc.getString("status") != "PENDING") {
                onResult(Result.failure(IllegalStateException("Bu istek artık geçerli değil.")))
                return@addOnSuccessListener
            }
            ref.update(
                mapOf(
                    "status" to "ACCEPTED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).addOnSuccessListener { onResult(Result.success(Unit)) }
                .addOnFailureListener { onResult(Result.failure(it)) }
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun rejectFriendRequest(friendshipId: String, onResult: (Result<Unit>) -> Unit) {
        val uid = currentUserId
        val ref = firestore.collection("friendships").document(friendshipId)
        ref.get().addOnSuccessListener { doc ->
            if (uid == null || !doc.exists() || doc.getString("recipientId") != uid || doc.getString("status") != "PENDING") {
                onResult(Result.failure(IllegalStateException("Bu istek artık geçerli değil.")))
                return@addOnSuccessListener
            }
            ref.delete().addOnSuccessListener { onResult(Result.success(Unit)) }
                .addOnFailureListener { onResult(Result.failure(it)) }
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun removeFriend(friendUid: String, onResult: (Result<Unit>) -> Unit) {
        val uid = currentUserId
        if (uid == null) {
            onResult(Result.failure(IllegalStateException("Oturum açık değil.")))
            return
        }
        val ref = firestore.collection("friendships").document(friendshipId(uid, friendUid))
        ref.get().addOnSuccessListener { doc ->
            val a = doc.getString("userA")
            val b = doc.getString("userB")
            if (!doc.exists() || doc.getString("status") != "ACCEPTED" || (a != uid && b != uid)) {
                onResult(Result.failure(IllegalStateException("Arkadaşlık bulunamadı.")))
                return@addOnSuccessListener
            }
            ref.delete().addOnSuccessListener { onResult(Result.success(Unit)) }
                .addOnFailureListener { onResult(Result.failure(it)) }
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun loadTodaySummary(userId: String, onResult: (Result<TodaySummary>) -> Unit) {
        if (currentUserId != userId) {
            onResult(Result.failure(IllegalStateException("Günlük verilere erişilemiyor.")))
            return
        }
        val today = LocalDate.now()
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { profile ->
                val target = profile.getLong("dailyCalorieTarget")?.toInt() ?: 0
                firestore.collection("meals").whereEqualTo("userId", userId).get()
                    .addOnSuccessListener { meals ->
                        var breakfast = 0
                        var lunch = 0
                        var dinner = 0
                        var snack = 0
                        meals.documents.forEach { meal ->
                            val mealDate = meal.getTimestamp("createdAt")?.toDate()?.toInstant()
                                ?.atZone(ZoneId.systemDefault())?.toLocalDate()
                            if (mealDate != today) return@forEach
                            val calories = meal.getLong("confirmedCalories")?.toInt() ?: 0
                            when (meal.getString("mealType")) {
                                "BREAKFAST" -> breakfast += calories
                                "LUNCH" -> lunch += calories
                                "DINNER" -> dinner += calories
                                else -> snack += calories
                            }
                        }
                        val total = breakfast + lunch + dinner + snack
                        val score = adherenceScore(total, target)
                        val summary = TodaySummary(
                            dateIso = today.toString(),
                            calorieTarget = target,
                            calorieTotal = total,
                            adherenceScore = score,
                            breakfastCalories = breakfast,
                            lunchCalories = lunch,
                            dinnerCalories = dinner,
                            snackCalories = snack
                        )
                        writeDailyStats(userId, summary) { statResult ->
                            statResult.onSuccess { onResult(Result.success(summary)) }
                                .onFailure { onResult(Result.failure(it)) }
                        }
                    }
                    .addOnFailureListener { onResult(Result.failure(it)) }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun writeDailyStats(
        userId: String,
        summary: TodaySummary,
        onResult: (Result<Unit>) -> Unit
    ) {
        val dateKey = LocalDate.parse(summary.dateIso).format(DateTimeFormatter.BASIC_ISO_DATE)
        val id = "${userId}_$dateKey"
        val batch = firestore.batch()
        batch.set(
            firestore.collection("dailyStats").document(id),
            mapOf(
                "userId" to userId,
                "dateIso" to summary.dateIso,
                "calorieTarget" to summary.calorieTarget,
                "calorieTotal" to summary.calorieTotal,
                "adherenceScore" to summary.adherenceScore,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            firestore.collection("publicDailyStats").document(id),
            mapOf(
                "userId" to userId,
                "dateIso" to summary.dateIso,
                "adherenceScore" to summary.adherenceScore,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit()
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun loadLeaderboard(userId: String, onResult: (Result<List<LeaderboardEntry>>) -> Unit) {
        loadTodaySummary(userId) { todayResult ->
            todayResult.onFailure {
                onResult(Result.failure(it))
                return@loadTodaySummary
            }
            loadFriendsSnapshot(userId) { friendsResult ->
                friendsResult.onFailure {
                    onResult(Result.failure(it))
                    return@loadFriendsSnapshot
                }
                val friends = friendsResult.getOrThrow().friends
                firestore.collection("publicProfiles").document(userId).get()
                    .addOnSuccessListener { selfDoc ->
                        val self = socialProfileFrom(selfDoc) ?: SocialProfile(
                            uid = userId,
                            username = "sen",
                            displayName = "Sen"
                        )
                        val participants = (listOf(self) + friends).distinctBy { it.uid }
                        loadLeaderboardScores(participants, userId, onResult)
                    }
                    .addOnFailureListener { onResult(Result.failure(it)) }
            }
        }
    }

    private fun loadLeaderboardScores(
        profiles: List<SocialProfile>,
        currentUid: String,
        onResult: (Result<List<LeaderboardEntry>>) -> Unit
    ) {
        if (profiles.isEmpty()) {
            onResult(Result.success(emptyList()))
            return
        }
        val dates = (0..6).map { LocalDate.now().minusDays(it.toLong()) }
        val scores = profiles.associate { it.uid to IntArray(dates.size) }.toMutableMap()
        val remaining = AtomicInteger(profiles.size * dates.size)
        var firstError: Throwable? = null

        profiles.forEach { profile ->
            dates.forEachIndexed { index, date ->
                val key = date.format(DateTimeFormatter.BASIC_ISO_DATE)
                val id = "${profile.uid}_$key"
                firestore.collection("publicDailyStats").document(id).get()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val value = task.result?.getLong("adherenceScore")?.toInt() ?: 0
                            scores[profile.uid]?.set(index, value)
                        } else if (firstError == null) {
                            firstError = task.exception
                        }

                        if (remaining.decrementAndGet() == 0) {
                            val error = firstError
                            if (error != null) {
                                onResult(Result.failure(error))
                            } else {
                                val entries = profiles.map { p ->
                                    val values = scores[p.uid] ?: IntArray(7)
                                    LeaderboardEntry(
                                        uid = p.uid,
                                        username = p.username,
                                        displayName = p.displayName,
                                        dailyScore = values.firstOrNull() ?: 0,
                                        weeklyScore = values.average().roundToInt(),
                                        isCurrentUser = p.uid == currentUid
                                    )
                                }.sortedWith(
                                    compareByDescending<LeaderboardEntry> { it.weeklyScore }
                                        .thenByDescending { it.dailyScore }
                                        .thenBy { it.username }
                                )
                                onResult(Result.success(entries))
                            }
                        }
                    }
            }
        }
    }

    fun deleteAccount(
        currentPassword: String?,
        googleIdToken: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onResult(Result.failure(IllegalStateException("Oturum açık değil.")))
            return
        }

        reauthenticateForSensitiveAction(user, currentPassword, googleIdToken) { authResult ->
            authResult.onFailure {
                onResult(Result.failure(it))
                return@reauthenticateForSensitiveAction
            }
            firestore.collection("users").document(user.uid).get()
                .addOnSuccessListener { profile ->
                    val username = profile.getString("username") ?: ""
                    deleteUserFirestoreData(user.uid, username) { deletionResult ->
                        deletionResult.onFailure {
                            onResult(Result.failure(it))
                            return@deleteUserFirestoreData
                        }
                        user.delete()
                            .addOnSuccessListener { onResult(Result.success(Unit)) }
                            .addOnFailureListener { onResult(Result.failure(it)) }
                    }
                }
                .addOnFailureListener { onResult(Result.failure(it)) }
        }
    }

    private fun reauthenticateForSensitiveAction(
        user: FirebaseUser,
        currentPassword: String?,
        googleIdToken: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        val providers = user.providerData.map { it.providerId }.toSet()
        when {
            EmailAuthProvider.PROVIDER_ID in providers -> {
                val email = user.email
                if (email.isNullOrBlank() || currentPassword.isNullOrBlank()) {
                    onResult(Result.failure(IllegalArgumentException("Hesabı silmek için mevcut şifreni gir.")))
                    return
                }
                user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword))
                    .addOnSuccessListener { onResult(Result.success(Unit)) }
                    .addOnFailureListener { onResult(Result.failure(it)) }
            }
            GoogleAuthProvider.PROVIDER_ID in providers -> {
                if (googleIdToken.isNullOrBlank()) {
                    onResult(Result.failure(IllegalArgumentException("Hesabı silmeden önce Google hesabınla yeniden doğrulama yap.")))
                    return
                }
                user.reauthenticate(GoogleAuthProvider.getCredential(googleIdToken, null))
                    .addOnSuccessListener { onResult(Result.success(Unit)) }
                    .addOnFailureListener { onResult(Result.failure(it)) }
            }
            else -> onResult(Result.failure(IllegalStateException("Bu hesap türü için yeniden doğrulama desteklenmiyor.")))
        }
    }

    private fun deleteUserFirestoreData(
        uid: String,
        username: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val refs = linkedSetOf<DocumentReference>()
        val userRef = firestore.collection("users").document(uid)
        refs += userRef
        refs += firestore.collection("publicProfiles").document(uid)
        if (username.isNotBlank()) refs += firestore.collection("usernames").document(normalizeUsername(username))

        collectQuery(userRef.collection("weights"), refs) {
            collectQuery(firestore.collection("meals").whereEqualTo("userId", uid), refs) {
                collectQuery(firestore.collection("friendships").whereEqualTo("userA", uid), refs) {
                    collectQuery(firestore.collection("friendships").whereEqualTo("userB", uid), refs) {
                        collectQuery(firestore.collection("dailyStats").whereEqualTo("userId", uid), refs) {
                            collectQuery(firestore.collection("publicDailyStats").whereEqualTo("userId", uid), refs) {
                                deleteReferencesInBatches(refs.toList(), 0, onResult)
                            } onError@{ error -> onResult(Result.failure(error)) }
                        } onError@{ error -> onResult(Result.failure(error)) }
                    } onError@{ error -> onResult(Result.failure(error)) }
                } onError@{ error -> onResult(Result.failure(error)) }
            } onError@{ error -> onResult(Result.failure(error)) }
        } onError@{ error -> onResult(Result.failure(error)) }
    }

    private fun collectQuery(
        query: Query,
        refs: MutableSet<DocumentReference>,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        query.get()
            .addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { refs += it.reference }
                onSuccess()
            }
            .addOnFailureListener(onError)
    }

    private fun deleteReferencesInBatches(
        refs: List<DocumentReference>,
        offset: Int,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (offset >= refs.size) {
            onResult(Result.success(Unit))
            return
        }
        val end = (offset + 400).coerceAtMost(refs.size)
        val batch = firestore.batch()
        refs.subList(offset, end).forEach(batch::delete)
        batch.commit()
            .addOnSuccessListener {
                deleteReferencesInBatches(refs, end, onResult)
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun signOut() {
        auth.signOut()
    }

    private fun socialProfileFrom(doc: DocumentSnapshot?): SocialProfile? {
        if (doc == null || !doc.exists()) return null
        val uid = doc.getString("uid") ?: doc.id
        val username = doc.getString("username") ?: return null
        return SocialProfile(
            uid = uid,
            username = username,
            displayName = doc.getString("displayName") ?: username,
            progressPercent = doc.getLong("progressPercent")?.toInt() ?: 0,
            programCompleted = doc.getBoolean("programCompleted") ?: false
        )
    }

    companion object {
        private val USERNAME_REGEX = Regex("^[a-z0-9._]{3,20}$")
        private const val USERNAME_ERROR =
            "Kullanıcı adı 3-20 karakter olmalı ve yalnızca a-z, 0-9, nokta veya alt çizgi içermeli."

        fun normalizeUsername(value: String): String =
            value.trim().lowercase(Locale.ROOT)

        fun isGoalCompleted(start: Double, current: Double, target: Double): Boolean {
            return when {
                target < start -> current <= target
                target > start -> current >= target
                else -> true
            }
        }

        fun progressPercent(start: Double?, current: Double?, target: Double?): Int {
            if (start == null || current == null || target == null) return 0
            val total = abs(start - target)
            if (total == 0.0) return 100
            val completed = if (target < start) start - current else current - start
            return ((completed / total) * 100.0).roundToInt().coerceIn(0, 100)
        }

        fun adherenceScore(total: Int, target: Int): Int {
            if (target <= 0) return 0
            if (total.toDouble() / target < 0.75) return 0
            val deviationPercent = abs(total - target).toDouble() / target * 100.0
            return (100.0 - deviationPercent).roundToInt().coerceIn(0, 100)
        }

        fun friendshipId(uidA: String, uidB: String): String =
            listOf(uidA, uidB).sorted().joinToString("_")
    }
}

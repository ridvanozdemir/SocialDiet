package com.ridvanozdemir.socialdiet.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ridvanozdemir.socialdiet.MainActivity
import com.ridvanozdemir.socialdiet.R
import com.ridvanozdemir.socialdiet.data.FirebaseRepository
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

enum class MealReminderType(
    val firestoreValue: String,
    val hour: Int,
    val displayName: String,
    val missingMessage: String,
    val dailyTargetShare: Double,
    val fallbackCalories: Int,
    val notificationId: Int
) {
    BREAKFAST(
        firestoreValue = "BREAKFAST",
        hour = 12,
        displayName = "Kahvaltı",
        missingMessage = "Kahvaltı kalorisi girmediniz.",
        dailyTargetShare = 0.25,
        fallbackCalories = 400,
        notificationId = 1201
    ),
    LUNCH(
        firestoreValue = "LUNCH",
        hour = 15,
        displayName = "Öğle yemeği",
        missingMessage = "Öğle yemeği kalorisi girmediniz.",
        dailyTargetShare = 0.35,
        fallbackCalories = 600,
        notificationId = 1202
    ),
    DINNER(
        firestoreValue = "DINNER",
        hour = 21,
        displayName = "Akşam yemeği",
        missingMessage = "Akşam yemeği kalorisi girmediniz.",
        dailyTargetShare = 0.40,
        fallbackCalories = 700,
        notificationId = 1203
    );

    companion object {
        fun fromName(value: String?): MealReminderType? =
            entries.firstOrNull { it.name == value }
    }
}

object MealReminderScheduler {
    private const val USER_TAG_PREFIX = "meal-reminder-user-"
    private const val WORK_PREFIX = "meal-reminder"

    fun scheduleAll(context: Context, userId: String) {
        MealReminderType.entries.forEach { type ->
            scheduleNext(context.applicationContext, userId, type)
        }
    }

    fun userTag(userId: String): String = USER_TAG_PREFIX + userId

    fun scheduleNext(context: Context, userId: String, type: MealReminderType) {
        val now = ZonedDateTime.now()
        var target = now.toLocalDate()
            .atTime(type.hour, 0)
            .atZone(now.zone)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }

        val delayMillis = Duration.between(now, target).toMillis().coerceAtLeast(0L)
        val targetDate = target.toLocalDate().toString()
        val workName = listOf(
            WORK_PREFIX,
            userId,
            type.name.lowercase(Locale.ROOT),
            target.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE)
        ).joinToString("-")

        val request = OneTimeWorkRequestBuilder<MealReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(MealReminderWorker.KEY_USER_ID, userId)
                    .putString(MealReminderWorker.KEY_MEAL_TYPE, type.name)
                    .putString(MealReminderWorker.KEY_TARGET_DATE, targetDate)
                    .build()
            )
            .addTag(userTag(userId))
            .addTag(WORK_PREFIX)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}

class MealReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.success()
        val type = MealReminderType.fromName(inputData.getString(KEY_MEAL_TYPE))
            ?: return Result.success()
        val targetDate = inputData.getString(KEY_TARGET_DATE)
            ?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
            ?: return Result.success()

        val signedInUid = FirebaseAuth.getInstance().currentUser?.uid
        if (signedInUid != userId) {
            return Result.success()
        }

        // A heavily delayed WorkManager job must never add yesterday's meal to today.
        if (LocalDate.now() != targetDate) {
            MealReminderScheduler.scheduleNext(applicationContext, userId, type)
            return Result.success()
        }

        return try {
            val autoAdd = MealReminderStore.autoAddIfMissing(userId, type, targetDate)
            if (autoAdd.added) {
                MealReminderNotifications.show(
                    context = applicationContext,
                    type = type,
                    calories = autoAdd.calories,
                    basedOnHistory = autoAdd.basedOnHistory
                )
            }
            MealReminderScheduler.scheduleNext(applicationContext, userId, type)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_MEAL_TYPE = "meal_type"
        const val KEY_TARGET_DATE = "target_date"
    }
}

object MealAutoEstimateReconciler {
    fun observeAndReconcile(userId: String): ListenerRegistration {
        val firestore = FirebaseFirestore.getInstance()
        val reconciling = AtomicBoolean(false)

        return firestore.collection("meals")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                if (!reconciling.compareAndSet(false, true)) return@addSnapshotListener

                val today = LocalDate.now()
                val todayDocs = snapshot.documents.filter { MealReminderStore.documentDate(it) == today }
                val refsToDelete = linkedSetOf<com.google.firebase.firestore.DocumentReference>()

                MealReminderType.entries.forEach { type ->
                    val sameType = todayDocs.filter {
                        it.getString("mealType") == type.firestoreValue
                    }
                    val hasActualEntry = sameType.any { !MealReminderStore.isAutoEstimate(it) }
                    if (hasActualEntry) {
                        sameType
                            .filter(MealReminderStore::isAutoEstimate)
                            .mapTo(refsToDelete) { it.reference }
                    }
                }

                if (refsToDelete.isEmpty()) {
                    reconciling.set(false)
                    return@addSnapshotListener
                }

                val batch = firestore.batch()
                refsToDelete.forEach(batch::delete)
                batch.commit().addOnCompleteListener { task ->
                    reconciling.set(false)
                    if (task.isSuccessful) {
                        // saveMeal() may have calculated dailyStats before the temporary
                        // estimate was removed, so refresh the score after reconciliation.
                        FirebaseRepository().loadTodaySummary(userId) { }
                    }
                }
            }
    }
}

private data class AutoAddResult(
    val added: Boolean,
    val calories: Int = 0,
    val basedOnHistory: Boolean = false
)

private object MealReminderStore {
    private const val AUTO_SOURCE = "auto_average"
    private const val AUTO_LABEL = "Otomatik öğün ortalaması"
    private const val AUTO_DATE_FIELD = "autoEstimateDateIso"

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    fun autoAddIfMissing(
        userId: String,
        type: MealReminderType,
        targetDate: LocalDate
    ): AutoAddResult {
        val mealQuery = firestore.collection("meals").whereEqualTo("userId", userId)
        val snapshot = Tasks.await(mealQuery.get())
        val targetDayMeals = snapshot.documents.filter { documentDate(it) == targetDate }
        val sameTypeToday = targetDayMeals.filter {
            it.getString("mealType") == type.firestoreValue
        }
        val actualExists = sameTypeToday.any { !isAutoEstimate(it) }
        val automaticEntries = sameTypeToday.filter(::isAutoEstimate)

        if (actualExists) {
            deleteDocuments(automaticEntries)
            if (automaticEntries.isNotEmpty()) {
                refreshDailyStats(userId, targetDate)
            }
            return AutoAddResult(added = false)
        }

        if (automaticEntries.isNotEmpty()) {
            return AutoAddResult(added = false)
        }

        val profile = Tasks.await(firestore.collection("users").document(userId).get())
        val calorieTarget = profile.getLong("dailyCalorieTarget")?.toInt() ?: 0

        val history = snapshot.documents
            .asSequence()
            .filter { it.getString("mealType") == type.firestoreValue }
            .filterNot(::isAutoEstimate)
            .mapNotNull { doc ->
                val date = documentDate(doc) ?: return@mapNotNull null
                if (!date.isBefore(targetDate)) return@mapNotNull null
                val calories = doc.getLong("confirmedCalories")?.toInt() ?: return@mapNotNull null
                if (calories !in 1..10000) return@mapNotNull null
                val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                createdAt to calories
            }
            .sortedByDescending { it.first }
            .take(10)
            .map { it.second }
            .toList()

        val basedOnHistory = history.isNotEmpty()
        val calories = if (basedOnHistory) {
            history.average().roundToInt().coerceIn(1, 10000)
        } else if (calorieTarget > 0) {
            (calorieTarget * type.dailyTargetShare).roundToInt().coerceIn(1, 10000)
        } else {
            type.fallbackCalories
        }

        val autoRef = firestore.collection("meals").document(
            autoDocumentId(userId, targetDate, type)
        )
        Tasks.await(
            autoRef.set(
                mapOf(
                    "id" to autoRef.id,
                    "userId" to userId,
                    "mealType" to type.firestoreValue,
                    "imageUrl" to null,
                    "aiLabel" to AUTO_LABEL,
                    "aiConfidence" to null,
                    "calorieSource" to AUTO_SOURCE,
                    "aiCalories" to calories,
                    "confirmedCalories" to calories,
                    "estimatedMassGrams" to 0.0,
                    "fatGrams" to 0.0,
                    "carbsGrams" to 0.0,
                    "proteinGrams" to 0.0,
                    "isAutoEstimate" to true,
                    AUTO_DATE_FIELD to targetDate.toString(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        )

        refreshDailyStats(userId, targetDate)
        return AutoAddResult(
            added = true,
            calories = calories,
            basedOnHistory = basedOnHistory
        )
    }

    fun isAutoEstimate(doc: DocumentSnapshot): Boolean =
        doc.getBoolean("isAutoEstimate") == true ||
            doc.getString("calorieSource") == AUTO_SOURCE

    fun documentDate(doc: DocumentSnapshot): LocalDate? {
        val explicitDate = doc.getString(AUTO_DATE_FIELD)
            ?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
        if (explicitDate != null) return explicitDate

        return doc.getTimestamp("createdAt")
            ?.toDate()
            ?.toInstant()
            ?.atZone(java.time.ZoneId.systemDefault())
            ?.toLocalDate()
    }

    private fun autoDocumentId(
        userId: String,
        date: LocalDate,
        type: MealReminderType
    ): String = "auto_${userId}_${date.format(DateTimeFormatter.BASIC_ISO_DATE)}_${type.name.lowercase(Locale.ROOT)}"

    private fun deleteDocuments(documents: List<DocumentSnapshot>) {
        if (documents.isEmpty()) return
        val batch = firestore.batch()
        documents.forEach { batch.delete(it.reference) }
        Tasks.await(batch.commit())
    }

    private fun refreshDailyStats(userId: String, date: LocalDate) {
        val profile = Tasks.await(firestore.collection("users").document(userId).get())
        val target = profile.getLong("dailyCalorieTarget")?.toInt() ?: 0
        val meals = Tasks.await(
            firestore.collection("meals")
                .whereEqualTo("userId", userId)
                .get()
        )

        var total = 0
        meals.documents.forEach { meal ->
            if (documentDate(meal) != date) return@forEach
            total += meal.getLong("confirmedCalories")?.toInt() ?: 0
        }

        val score = FirebaseRepository.adherenceScore(total, target)
        val dateKey = date.format(DateTimeFormatter.BASIC_ISO_DATE)
        val id = "${userId}_$dateKey"
        val batch = firestore.batch()
        batch.set(
            firestore.collection("dailyStats").document(id),
            mapOf(
                "userId" to userId,
                "dateIso" to date.toString(),
                "calorieTarget" to target,
                "calorieTotal" to total,
                "adherenceScore" to score,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            firestore.collection("publicDailyStats").document(id),
            mapOf(
                "userId" to userId,
                "dateIso" to date.toString(),
                "adherenceScore" to score,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        Tasks.await(batch.commit())
    }
}

private object MealReminderNotifications {
    private const val CHANNEL_ID = "meal_reminders"

    fun show(
        context: Context,
        type: MealReminderType,
        calories: Int,
        basedOnHistory: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        createChannel(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            type.notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val estimateText = if (basedOnHistory) {
            "Geçmiş ${type.displayName.lowercase(Locale("tr", "TR"))} girişlerinizin ortalaması olan $calories kcal otomatik eklendi."
        } else {
            "Günlük hedefinize göre yaklaşık $calories kcal otomatik eklendi."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Öğün hatırlatması")
            .setContentText("${type.missingMessage} $estimateText")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${type.missingMessage} $estimateText")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(type.notificationId, notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Öğün hatırlatmaları",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Girilmeyen kahvaltı, öğle ve akşam öğünleri için hatırlatmalar"
            }
        )
    }
}

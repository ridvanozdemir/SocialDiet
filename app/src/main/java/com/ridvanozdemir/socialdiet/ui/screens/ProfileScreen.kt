package com.ridvanozdemir.socialdiet.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ridvanozdemir.socialdiet.data.FirebaseRepository
import com.ridvanozdemir.socialdiet.data.model.UserProfile
import kotlin.math.abs

@Composable
fun ProfileScreen(
    repository: FirebaseRepository,
    userId: String,
    onSignOut: () -> Unit
) {
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var weightText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    DisposableEffect(userId) {
        val registration = repository.observeProfile(
            userId = userId,
            onProfile = { profile = it },
            onError = { statusMessage = it.message ?: "Profil yüklenemedi." }
        )
        onDispose { registration.remove() }
    }

    LaunchedEffect(profile?.currentWeightKg) {
        profile?.currentWeightKg?.let { weightText = it.toString() }
    }

    val currentProfile = profile
    ScreenShell("Profil") {
        if (currentProfile == null) {
            InfoCard("Profil", "Profil bilgileri yükleniyor...")
        } else {
            InfoCard(
                "@${currentProfile.username}",
                currentProfile.displayName.ifBlank { "SocialDiet kullanıcısı" }
            )
            InfoCard(
                "Kilo hedefi",
                "Başlangıç ${formatKg(currentProfile.startWeightKg)} • Güncel ${formatKg(currentProfile.currentWeightKg)} • Hedef ${formatKg(currentProfile.targetWeightKg)}"
            )
            InfoCard(
                "İlerleme",
                "${progressPercent(currentProfile)}% tamamlandı${if (currentProfile.programCompleted) " • Hedef tamamlandı 🏆" else ""}"
            )
            InfoCard(
                "Günlük hedef",
                "${currentProfile.dailyCalorieTarget ?: 0} kcal"
            )

            OutlinedTextField(
                value = weightText,
                onValueChange = { weightText = it },
                label = { Text("Güncel kilo (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            statusMessage?.let {
                Text(
                    text = it,
                    color = if (it == "Kilo güncellendi.") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Button(
                enabled = !saving,
                onClick = {
                    val weight = weightText.trim().replace(',', '.').toDoubleOrNull()
                    if (weight == null || weight <= 0) {
                        statusMessage = "Geçerli bir kilo değeri gir."
                        return@Button
                    }
                    saving = true
                    statusMessage = null
                    repository.updateCurrentWeight(weight) { result ->
                        saving = false
                        statusMessage = if (result.isSuccess) {
                            "Kilo güncellendi."
                        } else {
                            result.exceptionOrNull()?.message ?: "Kilo güncellenemedi."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (saving) "Kaydediliyor..." else "Kiloyu Güncelle")
            }
        }

        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Çıkış Yap")
        }
    }
}

private fun formatKg(value: Double?): String = value?.let { "%.1f kg".format(it) } ?: "-"

private fun progressPercent(profile: UserProfile): Int {
    val start = profile.startWeightKg ?: return 0
    val current = profile.currentWeightKg ?: return 0
    val target = profile.targetWeightKg ?: return 0
    val total = abs(start - target)
    if (total == 0.0) return 100
    val completed = if (target < start) start - current else current - start
    return ((completed / total) * 100.0).toInt().coerceIn(0, 100)
}
